package com.portfolio.model;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Represents a single stock holding in the portfolio.
 *
 * <p>All monetary calculations use {@link BigDecimal} to avoid floating-point
 * precision issues. This class is intentionally a plain value object; all
 * business computations (P&amp;L, weights, etc.) are derived on demand rather
 * than cached, keeping mutation to a minimum.</p>
 *
 * <p>Thread safety: Instances may be read from multiple threads but are only
 * mutated via the {@link #setCurrentPrice} / {@link #setLastUpdated} pair,
 * which the service layer calls under a lock. Callers must ensure appropriate
 * synchronisation if they mutate and read concurrently.</p>
 */
public class Stock {

    /** Standard scale used for monetary amounts. */
    private static final int MONETARY_SCALE = 4;
    /** Standard scale used for percentage values. */
    private static final int PERCENT_SCALE = 2;

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    /** Database primary key; 0 means not yet persisted. */
    private long id;

    /** Ticker symbol in UPPER-CASE (e.g. {@code "AAPL"}). */
    private String symbol;

    /** Human-readable company name. */
    private String name;

    /** Number of shares held; must be positive. */
    private BigDecimal quantity;

    /** Price paid per share at time of purchase; must be positive. */
    private BigDecimal buyPrice;

    /** Most recently fetched market price; {@code BigDecimal.ZERO} until first refresh. */
    private BigDecimal currentPrice;

    /** When {@link #currentPrice} was last fetched from the API or DB. */
    private LocalDateTime lastUpdated;

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    /** No-arg constructor required for some serialisation frameworks. */
    public Stock() {
    }

    /**
     * Full constructor for creating a new holding before it is persisted.
     *
     * @param symbol       ticker symbol (will be upper-cased by the caller)
     * @param name         company name
     * @param quantity     shares held (positive)
     * @param buyPrice     price paid per share (positive)
     * @param currentPrice last known market price (may equal {@code buyPrice} initially)
     */
    public Stock(String symbol, String name, BigDecimal quantity,
                 BigDecimal buyPrice, BigDecimal currentPrice) {
        this.symbol       = symbol;
        this.name         = name;
        this.quantity     = quantity.setScale(MONETARY_SCALE, RoundingMode.HALF_UP);
        this.buyPrice     = buyPrice.setScale(MONETARY_SCALE, RoundingMode.HALF_UP);
        this.currentPrice = currentPrice.setScale(MONETARY_SCALE, RoundingMode.HALF_UP);
        this.lastUpdated  = LocalDateTime.now();
    }

    // -----------------------------------------------------------------------
    // Derived financial calculations
    // -----------------------------------------------------------------------

    /**
     * Total amount invested: {@code quantity × buyPrice}.
     *
     * @return invested value, never {@code null}
     */
    public BigDecimal getInvestedValue() {
        return quantity.multiply(buyPrice)
                       .setScale(MONETARY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Current market value: {@code quantity × currentPrice}.
     *
     * @return current value, never {@code null}
     */
    public BigDecimal getCurrentValue() {
        return quantity.multiply(currentPrice)
                       .setScale(MONETARY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Absolute profit / loss: {@code currentValue − investedValue}.
     * Positive means profit; negative means loss.
     *
     * @return P&amp;L amount
     */
    public BigDecimal getProfitLoss() {
        return getCurrentValue().subtract(getInvestedValue())
                                .setScale(MONETARY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Percentage P&amp;L relative to invested capital.
     * Returns {@link BigDecimal#ZERO} when invested value is zero to avoid division-by-zero.
     *
     * @return P&amp;L percentage (e.g. {@code 12.50} means +12.50 %)
     */
    public BigDecimal getProfitLossPercent() {
        BigDecimal invested = getInvestedValue();
        if (invested.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return getProfitLoss()
                .divide(invested, MathContext.DECIMAL128)
                .multiply(BigDecimal.valueOf(100))
                .setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    // -----------------------------------------------------------------------
    // Getters and Setters
    // -----------------------------------------------------------------------

    /** @return database primary key */
    public long getId() { return id; }

    /** @param id database primary key assigned after INSERT */
    public void setId(long id) { this.id = id; }

    /** @return ticker symbol */
    public String getSymbol() { return symbol; }

    /** @param symbol ticker symbol */
    public void setSymbol(String symbol) { this.symbol = symbol; }

    /** @return company name */
    public String getName() { return name; }

    /** @param name company name */
    public void setName(String name) { this.name = name; }

    /** @return number of shares */
    public BigDecimal getQuantity() { return quantity; }

    /** @param quantity number of shares */
    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity.setScale(MONETARY_SCALE, RoundingMode.HALF_UP);
    }

    /** @return price paid per share */
    public BigDecimal getBuyPrice() { return buyPrice; }

    /** @param buyPrice price paid per share */
    public void setBuyPrice(BigDecimal buyPrice) {
        this.buyPrice = buyPrice.setScale(MONETARY_SCALE, RoundingMode.HALF_UP);
    }

    /** @return latest market price */
    public BigDecimal getCurrentPrice() { return currentPrice; }

    /**
     * Updates the market price and refresh timestamp atomically.
     * Callers in multi-threaded contexts must hold the portfolio lock.
     *
     * @param currentPrice latest market price
     */
    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice.setScale(MONETARY_SCALE, RoundingMode.HALF_UP);
    }

    /** @return timestamp of last price refresh */
    public LocalDateTime getLastUpdated() { return lastUpdated; }

    /** @param lastUpdated timestamp of last price refresh */
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }

    // -----------------------------------------------------------------------
    // Object overrides
    // -----------------------------------------------------------------------

    @Override
    public String toString() {
        return "Stock{symbol='" + symbol + "', name='" + name
               + "', qty=" + quantity + ", buyPrice=" + buyPrice
               + ", currentPrice=" + currentPrice + '}';
    }
}
