package com.portfolio.service;

import com.portfolio.dao.PortfolioDAO;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Alert service that detects significant price changes and publishes notifications.
 *
 * <p>After each background price refresh, this service compares the new price
 * against the previous one. If the absolute percentage change meets or exceeds
 * the configured threshold, an alert is:</p>
 * <ol>
 *   <li>Printed to the console (with ANSI colour coding).</li>
 *   <li>Appended to a file-based alert log.</li>
 *   <li>Persisted to the {@code alerts_log} database table.</li>
 * </ol>
 *
 * <p>To prevent duplicate alerts for the same unchanged price, the service
 * tracks the last alerted price per symbol in a {@link ConcurrentHashMap}.
 * An alert fires only when the new price differs from the last alerted price
 * <em>and</em> the change exceeds the threshold.</p>
 *
 * <p>Thread safety: this class is designed to be called from multiple refresh
 * threads. The {@link ConcurrentHashMap} for last-alerted prices is
 * thread-safe, and file I/O is serialised via a {@code synchronized} block
 * on the writer to prevent interleaved log lines.</p>
 */
public class AlertService {

    private static final Logger LOGGER = Logger.getLogger(AlertService.class.getName());
    private static final DateTimeFormatter DT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int PERCENT_SCALE = 2;

    // ANSI colour codes for console output
    private static final String ANSI_RESET  = "\u001B[0m";
    private static final String ANSI_RED    = "\u001B[31m";
    private static final String ANSI_GREEN  = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_BOLD   = "\u001B[1m";

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    /** Per-symbol alert threshold (overrides global when set). */
    private final ConcurrentHashMap<String, BigDecimal> perSymbolThresholds = new ConcurrentHashMap<>();

    /**
     * Tracks the last price for which an alert was fired, per symbol.
     * Prevents duplicate alerts when the price hasn't changed between refreshes.
     */
    private final ConcurrentHashMap<String, BigDecimal> lastAlertedPrice = new ConcurrentHashMap<>();

    private volatile BigDecimal globalThreshold;
    private final PortfolioDAO dao;
    private final String alertLogFile;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    /**
     * Creates the alert service.
     *
     * @param globalThreshold default percentage threshold for all symbols (e.g. 5.0)
     * @param dao             DAO for persisting alerts to {@code alerts_log}
     * @param alertLogFile    path to the file-based alert log
     */
    public AlertService(BigDecimal globalThreshold, PortfolioDAO dao, String alertLogFile) {
        this.globalThreshold = globalThreshold;
        this.dao             = dao;
        this.alertLogFile    = alertLogFile;
    }

    // -----------------------------------------------------------------------
    // Alert evaluation
    // -----------------------------------------------------------------------

    /**
     * Evaluates whether a price change warrants an alert and fires one if so.
     *
     * <p>An alert fires when:</p>
     * <ul>
     *   <li>The price has changed from the previous refresh.</li>
     *   <li>The absolute percentage change meets or exceeds the effective threshold.</li>
     *   <li>The new price is different from the last price for which an alert was sent
     *       (prevents re-alerting on the same value).</li>
     * </ul>
     *
     * @param symbol        ticker symbol
     * @param previousPrice price before refresh
     * @param newPrice      price after refresh
     */
    public void checkAndFireAlert(String symbol, BigDecimal previousPrice, BigDecimal newPrice) {
        if (previousPrice == null || newPrice == null
                || previousPrice.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        if (newPrice.compareTo(previousPrice) == 0) {
            return;  // No change
        }

        BigDecimal percentChange = computePercentChange(previousPrice, newPrice);
        BigDecimal absChange = percentChange.abs();
        BigDecimal threshold = getEffectiveThreshold(symbol);

        if (absChange.compareTo(threshold) < 0) {
            return;  // Below threshold
        }

        // Avoid duplicate alert for the same price
        BigDecimal lastAlerted = lastAlertedPrice.get(symbol);
        if (lastAlerted != null && lastAlerted.compareTo(newPrice) == 0) {
            return;
        }
        lastAlertedPrice.put(symbol, newPrice);

        String direction = newPrice.compareTo(previousPrice) > 0 ? "UP" : "DOWN";
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(DT_FORMAT);

        fireAlert(symbol, previousPrice, newPrice, percentChange, direction, timestamp, now);
    }

    /**
     * Fires the alert: prints to console, writes to file, persists to DB.
     */
    private void fireAlert(String symbol, BigDecimal previousPrice, BigDecimal newPrice,
                           BigDecimal percentChange, String direction,
                           String timestamp, LocalDateTime now) {
        String colour = "UP".equals(direction) ? ANSI_GREEN : ANSI_RED;
        String arrow  = "UP".equals(direction) ? "▲" : "▼";

        String consoleLine = String.format(
            "%n%s%s🔔 PRICE ALERT [%s] %s %s  %s → %s  (%s%s%%) %s%s",
            ANSI_BOLD, colour,
            timestamp,
            symbol,
            arrow,
            formatPrice(previousPrice),
            formatPrice(newPrice),
            "UP".equals(direction) ? "+" : "",
            percentChange.toPlainString(),
            direction,
            ANSI_RESET
        );
        System.out.println(consoleLine);

        String logLine = String.format("[%s] ALERT | %s | %s | prev=%.4f | curr=%.4f | change=%+.2f%% | %s",
                timestamp, symbol, arrow,
                previousPrice, newPrice, percentChange.doubleValue(), direction);

        writeToAlertLog(logLine);
        persistAlert(symbol, previousPrice, newPrice, percentChange, direction, now);

        LOGGER.info("Alert fired for " + symbol + ": " + direction + " " + percentChange + "%");
    }

    // -----------------------------------------------------------------------
    // Threshold management
    // -----------------------------------------------------------------------

    /**
     * Sets the global alert threshold for all symbols without a per-symbol override.
     *
     * @param threshold percentage threshold (e.g. 5.0 for 5%)
     */
    public void setGlobalThreshold(BigDecimal threshold) {
        if (threshold == null || threshold.compareTo(BigDecimal.ZERO) <= 0) {
            LOGGER.warning("Invalid threshold value: " + threshold);
            return;
        }
        this.globalThreshold = threshold;
        LOGGER.info("Global alert threshold set to " + threshold + "%");
    }

    /**
     * Returns the current global alert threshold.
     *
     * @return global threshold
     */
    public BigDecimal getGlobalThreshold() {
        return globalThreshold;
    }

    /**
     * Sets a per-symbol alert threshold that overrides the global threshold.
     *
     * @param symbol    ticker symbol (case-insensitive)
     * @param threshold percentage threshold
     */
    public void setPerSymbolThreshold(String symbol, BigDecimal threshold) {
        if (threshold == null || threshold.compareTo(BigDecimal.ZERO) <= 0) {
            LOGGER.warning("Invalid per-symbol threshold for " + symbol + ": " + threshold);
            return;
        }
        perSymbolThresholds.put(symbol.toUpperCase(), threshold);
        LOGGER.info("Per-symbol alert threshold for " + symbol.toUpperCase()
                    + " set to " + threshold + "%");
    }

    /**
     * Returns the effective threshold for a given symbol
     * (per-symbol override if set, otherwise the global threshold).
     *
     * @param symbol ticker symbol
     * @return effective threshold
     */
    public BigDecimal getEffectiveThreshold(String symbol) {
        return perSymbolThresholds.getOrDefault(symbol.toUpperCase(), globalThreshold);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Computes percentage change: {@code ((newPrice - previousPrice) / previousPrice) * 100}.
     * Positive for price increases, negative for decreases.
     */
    private BigDecimal computePercentChange(BigDecimal previous, BigDecimal current) {
        return current.subtract(previous)
                .divide(previous, MathContext.DECIMAL128)
                .multiply(BigDecimal.valueOf(100))
                .setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    /** Formats a price for display. */
    private String formatPrice(BigDecimal price) {
        return String.format("$%.2f", price.doubleValue());
    }

    /** Appends a line to the file-based alert log. */
    private synchronized void writeToAlertLog(String line) {
        try (PrintWriter writer = new PrintWriter(
                new BufferedWriter(new FileWriter(alertLogFile, true)))) {
            writer.println(line);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to write to alert log file: " + alertLogFile, e);
        }
    }

    /** Persists the alert to the database; silently skips on failure. */
    private void persistAlert(String symbol, BigDecimal previous, BigDecimal current,
                              BigDecimal percentChange, String direction, LocalDateTime now) {
        try {
            dao.insertAlert(symbol, previous, current, percentChange, direction, now);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to persist alert for " + symbol + " to DB", e);
        }
    }
}
