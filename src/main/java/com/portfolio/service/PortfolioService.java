package com.portfolio.service;

import com.portfolio.api.StockPriceClient;
import com.portfolio.dao.PortfolioDAO;
import com.portfolio.model.Portfolio;
import com.portfolio.model.Stock;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Core business logic for managing the stock portfolio.
 *
 * <p>This service owns the single shared {@link Portfolio} instance and is the
 * only class that mutates it. All mutations go through both the in-memory
 * {@link Portfolio} and the persistent {@link PortfolioDAO} so they stay in
 * sync at all times.</p>
 *
 * <p>At startup, {@link #loadFromDatabase()} re-populates the in-memory
 * portfolio from the database so previous data survives restarts.</p>
 *
 * <p>Thread safety: all write operations on the Portfolio are synchronised
 * through {@link Portfolio}'s own synchronised methods. The service itself
 * is effectively single-writer (console menu) + background-reader/updater
 * (price refresh scheduler), which is handled by the Portfolio's internal
 * synchronisation.</p>
 */
public class PortfolioService {

    private static final Logger LOGGER = Logger.getLogger(PortfolioService.class.getName());

    // -----------------------------------------------------------------------
    // Dependencies
    // -----------------------------------------------------------------------

    private final Portfolio portfolio;
    private final PortfolioDAO dao;
    private final StockPriceClient priceClient;
    private final AlertService alertService;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    /**
     * Constructs the service.
     *
     * @param portfolio    shared in-memory portfolio
     * @param dao          persistence layer
     * @param priceClient  Alpha Vantage HTTP client (may be {@code null} in offline mode)
     * @param alertService alert publishing service
     */
    public PortfolioService(Portfolio portfolio,
                            PortfolioDAO dao,
                            StockPriceClient priceClient,
                            AlertService alertService) {
        this.portfolio    = portfolio;
        this.dao          = dao;
        this.priceClient  = priceClient;
        this.alertService = alertService;
    }

    // -----------------------------------------------------------------------
    // Portfolio Management
    // -----------------------------------------------------------------------

    /**
     * Loads all holdings from the database into the in-memory portfolio.
     * Call once at application startup.
     */
    public void loadFromDatabase() {
        try {
            List<Stock> stocks = dao.findAllHoldings();
            for (Stock stock : stocks) {
                portfolio.addHolding(stock);
            }
            LOGGER.info("Loaded " + stocks.size() + " holding(s) from database.");
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Failed to load portfolio from database", e);
            throw e;
        }
    }

    /**
     * Adds a new stock holding to the portfolio and persists it.
     *
     * <p>Validation rules:</p>
     * <ul>
     *   <li>Symbol must not be blank.</li>
     *   <li>Quantity must be positive.</li>
     *   <li>Buy price must be positive.</li>
     *   <li>Symbol must not already exist in the portfolio.</li>
     * </ul>
     *
     * @param symbol    ticker symbol (will be upper-cased)
     * @param name      company name
     * @param quantity  number of shares (positive)
     * @param buyPrice  price paid per share (positive)
     * @return the newly created {@link Stock}, or empty if validation fails
     */
    public Optional<Stock> addHolding(String symbol, String name,
                                      BigDecimal quantity, BigDecimal buyPrice) {
        // Validate inputs
        if (symbol == null || symbol.isBlank()) {
            LOGGER.warning("Cannot add holding: symbol is blank.");
            return Optional.empty();
        }
        if (name == null || name.isBlank()) {
            LOGGER.warning("Cannot add holding: name is blank.");
            return Optional.empty();
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            LOGGER.warning("Cannot add holding: quantity must be positive.");
            return Optional.empty();
        }
        if (buyPrice == null || buyPrice.compareTo(BigDecimal.ZERO) <= 0) {
            LOGGER.warning("Cannot add holding: buy price must be positive.");
            return Optional.empty();
        }

        String upperSymbol = symbol.toUpperCase().trim();
        if (portfolio.contains(upperSymbol)) {
            LOGGER.warning("Cannot add holding: symbol " + upperSymbol + " already exists.");
            return Optional.empty();
        }

        // Attempt a live price fetch; fall back to buyPrice if unavailable
        BigDecimal currentPrice = fetchCurrentPriceOrFallback(upperSymbol, buyPrice);

        Stock stock = new Stock(upperSymbol, name.trim(), quantity, buyPrice, currentPrice);
        stock.setLastUpdated(LocalDateTime.now());

        // Persist first; if DB fails, we don't corrupt in-memory state
        dao.insertHolding(stock);
        portfolio.addHolding(stock);

        LOGGER.info("Added holding: " + upperSymbol + " qty=" + quantity + " buyPrice=" + buyPrice);
        return Optional.of(stock);
    }

    /**
     * Adds a new stock holding to the portfolio and persists it.
     * Alias for {@link #addHolding(String, String, BigDecimal, BigDecimal)}.
     *
     * @param symbol    ticker symbol
     * @param name      company name
     * @param quantity  number of shares
     * @param buyPrice  price paid per share
     * @return the newly created {@link Stock}, or empty if validation fails
     */
    public Optional<Stock> addStock(String symbol, String name,
                                   BigDecimal quantity, BigDecimal buyPrice) {
        return addHolding(symbol, name, quantity, buyPrice);
    }

    /**
     * Removes a holding from the portfolio and the database.
     *
     * @param symbol ticker symbol (case-insensitive)
     * @return {@code true} if the holding existed and was removed
     */
    public boolean removeHolding(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return false;
        }
        String upperSymbol = symbol.toUpperCase().trim();
        if (!portfolio.contains(upperSymbol)) {
            return false;
        }
        boolean removedFromDb = dao.deleteHolding(upperSymbol);
        portfolio.removeHolding(upperSymbol);
        LOGGER.info("Removed holding: " + upperSymbol);
        return removedFromDb;
    }

    /**
     * Returns a snapshot of all holdings in the portfolio.
     *
     * @return immutable list of all holdings
     */
    public List<Stock> getAllHoldings() {
        return portfolio.getAllHoldings();
    }

    /**
     * Returns the shared in-memory portfolio.
     * Used by the scheduler and risk analysis service.
     *
     * @return the portfolio
     */
    public Portfolio getPortfolio() {
        return portfolio;
    }

    // -----------------------------------------------------------------------
    // Price refresh
    // -----------------------------------------------------------------------

    /**
     * Applies a manually supplied price to a holding, bypassing the Alpha
     * Vantage API entirely, and immediately runs the same alert-check logic
     * as the real background scheduler.
     *
     * <p>Use this for <b>testing only</b> — it lets you inject an arbitrary
     * price to confirm that the alert pipeline (console, file, DB) fires
     * correctly without waiting for real market volatility.</p>
     *
     * <p>Code path is identical to {@link #refreshPrice(String)} from the
     * point where a new price is received:</p>
     * <ol>
     *   <li>Capture the previous price from in-memory portfolio.</li>
     *   <li>Update in-memory portfolio via synchronized {@link Portfolio#updatePrice}.</li>
     *   <li>Persist updated price to DB via {@link PortfolioDAO#updatePrice}.</li>
     *   <li>Call {@link AlertService#checkAndFireAlert} — exact same call as the scheduler.</li>
     * </ol>
     *
     * @param symbol        upper-case ticker symbol (must already exist in portfolio)
     * @param simulatedPrice the fake price to apply
     * @return {@code true} if the holding was found and updated; {@code false} if not found
     */
    public boolean applySimulatedPrice(String symbol, BigDecimal simulatedPrice) {
        if (symbol == null || symbol.isBlank()) return false;
        if (simulatedPrice == null || simulatedPrice.compareTo(BigDecimal.ZERO) <= 0) return false;

        String upperSymbol = symbol.toUpperCase().trim();
        Optional<Stock> holdingOpt = portfolio.getHolding(upperSymbol);
        if (holdingOpt.isEmpty()) {
            LOGGER.warning("applySimulatedPrice: symbol '" + upperSymbol + "' not found in portfolio.");
            return false;
        }

        BigDecimal previousPrice = holdingOpt.get().getCurrentPrice();

        // Update in-memory portfolio (thread-safe via synchronized method)
        portfolio.updatePrice(upperSymbol, simulatedPrice);

        // Persist to DB (same as real refresh)
        try {
            dao.updatePrice(upperSymbol, simulatedPrice, LocalDateTime.now());
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Failed to persist simulated price for " + upperSymbol, e);
        }

        // Fire alert through the REAL AlertService — not a fake code path
        alertService.checkAndFireAlert(upperSymbol, previousPrice, simulatedPrice);

        LOGGER.info("Simulated price applied for " + upperSymbol
                    + ": " + previousPrice + " → " + simulatedPrice);
        return true;
    }

    /**
     * Refreshes the price for a single symbol. Called from the background
     * scheduler's per-symbol tasks.
     *
     * <p>Workflow:</p>
     * <ol>
     *   <li>Record previous price from in-memory portfolio.</li>
     *   <li>Fetch latest price from Alpha Vantage.</li>
     *   <li>On success: update both in-memory portfolio and DB; check alert.</li>
     *   <li>On failure: leave price unchanged and log a staleness warning.</li>
     * </ol>
     *
     * @param symbol upper-case ticker symbol
     */
    public void refreshPrice(String symbol) {
        Optional<Stock> holdingOpt = portfolio.getHolding(symbol);
        if (holdingOpt.isEmpty()) {
            return;  // Holding may have been removed between scheduling and execution
        }

        Stock holding = holdingOpt.get();
        BigDecimal previousPrice = holding.getCurrentPrice();

        if (priceClient == null) {
            LOGGER.warning("Price client unavailable (offline mode); skipping refresh for " + symbol);
            return;
        }

        Optional<BigDecimal> fetchedOpt = priceClient.fetchCurrentPrice(symbol);
        if (fetchedOpt.isEmpty()) {
            LOGGER.warning("Could not fetch live price for " + symbol
                           + ". Using last known price: " + previousPrice + " (STALE)");
            return;  // Leave existing price as-is
        }

        BigDecimal newPrice = fetchedOpt.get();

        // Skip update if price hasn't changed (avoids duplicate alerts)
        if (newPrice.compareTo(previousPrice) == 0) {
            LOGGER.fine("Price unchanged for " + symbol + ": " + newPrice);
            return;
        }

        // Update in-memory portfolio (thread-safe via synchronized method)
        portfolio.updatePrice(symbol, newPrice);

        // Persist to DB (non-critical; log warning if it fails)
        try {
            dao.updatePrice(symbol, newPrice, LocalDateTime.now());
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Failed to persist updated price for " + symbol, e);
        }

        // Check and fire alert if threshold exceeded
        alertService.checkAndFireAlert(symbol, previousPrice, newPrice);

        LOGGER.info("Refreshed " + symbol + ": " + previousPrice + " → " + newPrice);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Attempts to fetch a live price; returns {@code fallback} on failure.
     *
     * @param symbol   ticker symbol
     * @param fallback value to use when the API is unavailable
     * @return fetched price or fallback
     */
    private BigDecimal fetchCurrentPriceOrFallback(String symbol, BigDecimal fallback) {
        if (priceClient == null) {
            return fallback;
        }
        try {
            return priceClient.fetchCurrentPrice(symbol).orElse(fallback);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Live price fetch failed for " + symbol + "; using buy price as initial.", e);
            return fallback;
        }
    }
}
