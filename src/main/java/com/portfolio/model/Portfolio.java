package com.portfolio.model;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Thread-safe in-memory representation of the user's stock portfolio.
 *
 * <p><b>Thread safety strategy</b><br>
 * All holdings are stored in a {@link ConcurrentHashMap} keyed by upper-case
 * symbol. ConcurrentHashMap guarantees that individual get/put operations are
 * atomic. However, compound operations (check-then-act, iterate-and-aggregate)
 * are guarded by {@code synchronized(this)} blocks so the map is never seen in
 * a partially-updated state by another thread.</p>
 *
 * <p>The {@link com.portfolio.concurrency.PriceRefreshScheduler} updates
 * individual {@link Stock#setCurrentPrice} values directly on the Stock objects
 * already stored in the map. Because those writes happen inside a
 * {@code synchronized(portfolio)} block in
 * {@link com.portfolio.service.PortfolioService#updatePrice}, and the console
 * menu reads the portfolio inside the same lock, there is no data race.</p>
 */
public class Portfolio {

    private static final Logger LOGGER = Logger.getLogger(Portfolio.class.getName());

    /** Scale for percentage computations. */
    private static final int PERCENT_SCALE = 2;

    /** Primary storage: symbol → Stock. ConcurrentHashMap used for safe iteration. */
    private final ConcurrentHashMap<String, Stock> holdings = new ConcurrentHashMap<>();

    // -----------------------------------------------------------------------
    // Mutation methods — all synchronised for compound operations
    // -----------------------------------------------------------------------

    /**
     * Adds or replaces a holding.
     *
     * @param stock the holding to add; {@code stock.getSymbol()} must not be null
     */
    public synchronized void addHolding(Stock stock) {
        holdings.put(stock.getSymbol().toUpperCase(), stock);
        LOGGER.fine("Portfolio: added/updated holding " + stock.getSymbol());
    }

    /**
     * Removes a holding by symbol.
     *
     * @param symbol ticker symbol (case-insensitive)
     * @return {@code true} if the holding existed and was removed
     */
    public synchronized boolean removeHolding(String symbol) {
        Stock removed = holdings.remove(symbol.toUpperCase());
        if (removed != null) {
            LOGGER.fine("Portfolio: removed holding " + symbol.toUpperCase());
            return true;
        }
        return false;
    }

    /**
     * Updates the current price of an existing holding.
     * Called by the background refresh thread; no-op if symbol not found.
     *
     * @param symbol       ticker symbol
     * @param currentPrice new market price
     */
    public synchronized void updatePrice(String symbol, BigDecimal currentPrice) {
        Stock stock = holdings.get(symbol.toUpperCase());
        if (stock != null) {
            stock.setCurrentPrice(currentPrice);
            stock.setLastUpdated(java.time.LocalDateTime.now());
        }
    }

    // -----------------------------------------------------------------------
    // Query methods
    // -----------------------------------------------------------------------

    /**
     * Looks up a holding by symbol.
     *
     * @param symbol ticker symbol (case-insensitive)
     * @return an {@link Optional} containing the holding, or empty if not present
     */
    public Optional<Stock> getHolding(String symbol) {
        return Optional.ofNullable(holdings.get(symbol.toUpperCase()));
    }

    /**
     * Returns an immutable snapshot of all holdings.
     * Callers can safely iterate without holding the portfolio lock.
     *
     * @return unmodifiable list of all holdings
     */
    public synchronized List<Stock> getAllHoldings() {
        return Collections.unmodifiableList(new ArrayList<>(holdings.values()));
    }

    /**
     * Returns the symbols of all currently tracked holdings.
     *
     * @return unmodifiable collection of upper-case ticker symbols
     */
    public synchronized Collection<String> getSymbols() {
        return Collections.unmodifiableSet(holdings.keySet());
    }

    /** @return {@code true} if there are no holdings */
    public boolean isEmpty() {
        return holdings.isEmpty();
    }

    /** @return number of distinct holdings */
    public int size() {
        return holdings.size();
    }

    /**
     * Returns {@code true} if a holding with the given symbol already exists.
     *
     * @param symbol ticker symbol (case-insensitive)
     */
    public boolean contains(String symbol) {
        return holdings.containsKey(symbol.toUpperCase());
    }

    // -----------------------------------------------------------------------
    // Aggregate calculations
    // -----------------------------------------------------------------------

    /**
     * Total capital invested across all holdings.
     *
     * @return sum of (quantity × buyPrice) for every holding
     */
    public synchronized BigDecimal getTotalInvested() {
        return holdings.values().stream()
                .map(Stock::getInvestedValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Total current market value across all holdings.
     *
     * @return sum of (quantity × currentPrice) for every holding
     */
    public synchronized BigDecimal getTotalCurrentValue() {
        return holdings.values().stream()
                .map(Stock::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Total absolute profit / loss: {@code totalCurrentValue − totalInvested}.
     *
     * @return total P&amp;L amount
     */
    public synchronized BigDecimal getTotalProfitLoss() {
        return getTotalCurrentValue().subtract(getTotalInvested());
    }

    /**
     * Overall portfolio return as a percentage.
     * Returns {@link BigDecimal#ZERO} when total invested is zero.
     *
     * @return overall return % (e.g. {@code 8.43} means +8.43 %)
     */
    public synchronized BigDecimal getOverallReturnPercent() {
        BigDecimal invested = getTotalInvested();
        if (invested.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return getTotalProfitLoss()
                .divide(invested, MathContext.DECIMAL128)
                .multiply(BigDecimal.valueOf(100))
                .setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Calculates the percentage of total current portfolio value represented
     * by a single holding.
     *
     * @param symbol ticker symbol
     * @return concentration percentage, or {@link BigDecimal#ZERO} if not found / empty portfolio
     */
    public synchronized BigDecimal getConcentrationPercent(String symbol) {
        BigDecimal totalValue = getTotalCurrentValue();
        if (totalValue.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        Stock stock = holdings.get(symbol.toUpperCase());
        if (stock == null) {
            return BigDecimal.ZERO;
        }
        return stock.getCurrentValue()
                    .divide(totalValue, MathContext.DECIMAL128)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    @Override
    public String toString() {
        return "Portfolio{holdings=" + holdings.size()
               + ", totalInvested=" + getTotalInvested()
               + ", totalCurrentValue=" + getTotalCurrentValue() + '}';
    }
}
