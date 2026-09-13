package com.portfolio.dao;

import com.portfolio.model.Stock;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JDBC-based data-access object for portfolio holdings and alert logs.
 *
 * <p>Design decisions:</p>
 * <ul>
 *   <li>Every public method obtains a {@link Connection} from
 *       {@link #getConnection()} and closes it in a try-with-resources block,
 *       so the DAO is stateless and safe to use from any thread.</li>
 *   <li>All SQL uses {@link PreparedStatement} to prevent SQL injection.</li>
 *   <li>SQLExceptions are caught, logged, and re-thrown as unchecked
 *       {@link RuntimeException}s so callers do not need to clutter their
 *       code with checked-exception handling.</li>
 *   <li>The class auto-detects MySQL vs SQLite from the JDBC URL and uses
 *       the correct {@code AUTO_INCREMENT} / {@code AUTOINCREMENT} keyword
 *       when creating the schema.</li>
 * </ul>
 */
public class PortfolioDAO {

    private static final Logger LOGGER = Logger.getLogger(PortfolioDAO.class.getName());

    // -----------------------------------------------------------------------
    // SQL statements
    // -----------------------------------------------------------------------

    private static final String SQL_CREATE_HOLDINGS =
            "CREATE TABLE IF NOT EXISTS holdings (" +
            "  id            INTEGER PRIMARY KEY, " +
            "  symbol        VARCHAR(20)    NOT NULL UNIQUE, " +
            "  name          VARCHAR(255)   NOT NULL, " +
            "  quantity      DECIMAL(15,4)  NOT NULL, " +
            "  buy_price     DECIMAL(15,4)  NOT NULL, " +
            "  current_price DECIMAL(15,4)  NOT NULL DEFAULT 0, " +
            "  last_updated  DATETIME       NOT NULL" +
            ")";

    private static final String SQL_CREATE_HOLDINGS_MYSQL =
            "CREATE TABLE IF NOT EXISTS holdings (" +
            "  id            INT            NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
            "  symbol        VARCHAR(20)    NOT NULL UNIQUE, " +
            "  name          VARCHAR(255)   NOT NULL, " +
            "  quantity      DECIMAL(15,4)  NOT NULL, " +
            "  buy_price     DECIMAL(15,4)  NOT NULL, " +
            "  current_price DECIMAL(15,4)  NOT NULL DEFAULT 0.0000, " +
            "  last_updated  DATETIME       NOT NULL" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

    private static final String SQL_CREATE_ALERTS_LOG =
            "CREATE TABLE IF NOT EXISTS alerts_log (" +
            "  id             INTEGER PRIMARY KEY, " +
            "  symbol         VARCHAR(20)    NOT NULL, " +
            "  previous_price DECIMAL(15,4)  NOT NULL, " +
            "  current_price  DECIMAL(15,4)  NOT NULL, " +
            "  percent_change DECIMAL(10,4)  NOT NULL, " +
            "  direction      VARCHAR(4)     NOT NULL, " +
            "  created_at     DATETIME       NOT NULL" +
            ")";

    private static final String SQL_CREATE_ALERTS_LOG_MYSQL =
            "CREATE TABLE IF NOT EXISTS alerts_log (" +
            "  id             INT            NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
            "  symbol         VARCHAR(20)    NOT NULL, " +
            "  previous_price DECIMAL(15,4)  NOT NULL, " +
            "  current_price  DECIMAL(15,4)  NOT NULL, " +
            "  percent_change DECIMAL(10,4)  NOT NULL, " +
            "  direction      VARCHAR(4)     NOT NULL, " +
            "  created_at     DATETIME       NOT NULL" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

    private static final String SQL_CREATE_IDX_SYMBOL =
            "CREATE INDEX IF NOT EXISTS idx_alerts_symbol ON alerts_log (symbol)";

    private static final String SQL_INSERT_HOLDING =
            "INSERT INTO holdings (symbol, name, quantity, buy_price, current_price, last_updated) " +
            "VALUES (?, ?, ?, ?, ?, ?)";

    private static final String SQL_UPDATE_HOLDING =
            "UPDATE holdings SET name=?, quantity=?, buy_price=?, current_price=?, last_updated=? " +
            "WHERE symbol=?";

    private static final String SQL_UPDATE_PRICE =
            "UPDATE holdings SET current_price=?, last_updated=? WHERE symbol=?";

    private static final String SQL_DELETE_HOLDING =
            "DELETE FROM holdings WHERE symbol=?";

    private static final String SQL_SELECT_ALL_HOLDINGS =
            "SELECT id, symbol, name, quantity, buy_price, current_price, last_updated " +
            "FROM holdings ORDER BY symbol";

    private static final String SQL_SELECT_HOLDING_BY_SYMBOL =
            "SELECT id, symbol, name, quantity, buy_price, current_price, last_updated " +
            "FROM holdings WHERE symbol=?";

    private static final String SQL_INSERT_ALERT =
            "INSERT INTO alerts_log (symbol, previous_price, current_price, percent_change, direction, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?)";

    private static final String SQL_SELECT_RECENT_ALERTS =
            "SELECT id, symbol, previous_price, current_price, percent_change, direction, created_at " +
            "FROM alerts_log ORDER BY created_at DESC LIMIT ?";

    // -----------------------------------------------------------------------
    // Connection configuration
    // -----------------------------------------------------------------------

    private final String url;
    private final String username;
    private final String password;
    private final boolean isMySql;

    static {
        // Explicitly register JDBC drivers to ensure compatibility in shaded uber JARs
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ignored) {
            // Driver may not be present if running with MySQL-only
        }
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ignored) {
            // Driver may not be present if running with SQLite-only
        }
    }

    /**
     * Constructs the DAO and initialises the database schema.
     *
     * @param url      JDBC URL (e.g. {@code jdbc:sqlite:portfolio.db} or {@code jdbc:mysql://...})
     * @param username DB username (ignored for SQLite)
     * @param password DB password (ignored for SQLite)
     */
    public PortfolioDAO(String url, String username, String password) {
        this.url      = url;
        this.username = username;
        this.password = password;
        this.isMySql  = url != null && url.startsWith("jdbc:mysql");
        initSchema();
    }

    /**
     * Opens and returns a new JDBC connection.
     * The caller is responsible for closing it (use try-with-resources).
     *
     * @return a live database connection
     * @throws SQLException if a connection cannot be established
     */
    public Connection getConnection() throws SQLException {
        if (isMySql) {
            return DriverManager.getConnection(url, username, password);
        }
        // SQLite does not use username/password
        return DriverManager.getConnection(url);
    }

    // -----------------------------------------------------------------------
    // Schema initialisation
    // -----------------------------------------------------------------------

    /**
     * Creates all required tables if they do not exist.
     * Called once from the constructor.
     */
    private void initSchema() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            if (isMySql) {
                stmt.execute(SQL_CREATE_HOLDINGS_MYSQL);
                stmt.execute(SQL_CREATE_ALERTS_LOG_MYSQL);
            } else {
                stmt.execute(SQL_CREATE_HOLDINGS);
                stmt.execute(SQL_CREATE_ALERTS_LOG);
                stmt.execute(SQL_CREATE_IDX_SYMBOL);
            }
            LOGGER.info("Database schema initialised successfully.");

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to initialise database schema", e);
            throw new RuntimeException("Database schema initialisation failed: " + e.getMessage(), e);
        }
    }

    // -----------------------------------------------------------------------
    // Holdings CRUD
    // -----------------------------------------------------------------------

    /**
     * Inserts a new holding into the database.
     * The {@code id} field of the supplied {@link Stock} is updated with the
     * generated primary key.
     *
     * @param stock the holding to persist
     * @throws RuntimeException wrapping {@link SQLException} on failure
     */
    public void insertHolding(Stock stock) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT_HOLDING,
                     Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, stock.getSymbol().toUpperCase());
            ps.setString(2, stock.getName());
            ps.setBigDecimal(3, stock.getQuantity());
            ps.setBigDecimal(4, stock.getBuyPrice());
            ps.setBigDecimal(5, stock.getCurrentPrice());
            ps.setTimestamp(6, toTimestamp(stock.getLastUpdated()));
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    stock.setId(keys.getLong(1));
                }
            }
            LOGGER.info("Inserted holding: " + stock.getSymbol());

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to insert holding: " + stock.getSymbol(), e);
            throw new RuntimeException("Failed to insert holding '" + stock.getSymbol() + "': " + e.getMessage(), e);
        }
    }

    /**
     * Updates an existing holding's mutable fields.
     *
     * @param stock holding with updated values; matched by {@code symbol}
     */
    public void updateHolding(Stock stock) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_HOLDING)) {

            ps.setString(1, stock.getName());
            ps.setBigDecimal(2, stock.getQuantity());
            ps.setBigDecimal(3, stock.getBuyPrice());
            ps.setBigDecimal(4, stock.getCurrentPrice());
            ps.setTimestamp(5, toTimestamp(stock.getLastUpdated()));
            ps.setString(6, stock.getSymbol().toUpperCase());
            int rows = ps.executeUpdate();
            if (rows == 0) {
                LOGGER.warning("updateHolding: no row found for symbol " + stock.getSymbol());
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to update holding: " + stock.getSymbol(), e);
            throw new RuntimeException("Failed to update holding '" + stock.getSymbol() + "': " + e.getMessage(), e);
        }
    }

    /**
     * Updates only the current price and timestamp for a holding.
     * Lighter than {@link #updateHolding} — called by the background scheduler.
     *
     * @param symbol       ticker symbol
     * @param currentPrice latest market price
     * @param lastUpdated  when the price was fetched
     */
    public void updatePrice(String symbol, BigDecimal currentPrice, LocalDateTime lastUpdated) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_PRICE)) {

            ps.setBigDecimal(1, currentPrice);
            ps.setTimestamp(2, toTimestamp(lastUpdated));
            ps.setString(3, symbol.toUpperCase());
            ps.executeUpdate();

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to update price for " + symbol, e);
            throw new RuntimeException("Failed to update price for '" + symbol + "': " + e.getMessage(), e);
        }
    }

    /**
     * Deletes a holding by symbol.
     *
     * @param symbol ticker symbol (case-insensitive)
     * @return {@code true} if a row was deleted
     */
    public boolean deleteHolding(String symbol) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_DELETE_HOLDING)) {

            ps.setString(1, symbol.toUpperCase());
            int rows = ps.executeUpdate();
            if (rows > 0) {
                LOGGER.info("Deleted holding: " + symbol.toUpperCase());
                return true;
            }
            return false;

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to delete holding: " + symbol, e);
            throw new RuntimeException("Failed to delete holding '" + symbol + "': " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all holdings from the database.
     *
     * @return list of all holdings, possibly empty
     */
    public List<Stock> findAllHoldings() {
        List<Stock> stocks = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SELECT_ALL_HOLDINGS);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                stocks.add(mapRowToStock(rs));
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to fetch all holdings", e);
            throw new RuntimeException("Failed to fetch holdings: " + e.getMessage(), e);
        }
        return stocks;
    }

    /**
     * Finds a single holding by its ticker symbol.
     *
     * @param symbol ticker symbol (case-insensitive)
     * @return an {@link Optional} with the holding if found
     */
    public Optional<Stock> findBySymbol(String symbol) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SELECT_HOLDING_BY_SYMBOL)) {

            ps.setString(1, symbol.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToStock(rs));
                }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to find holding by symbol: " + symbol, e);
            throw new RuntimeException("Failed to find holding '" + symbol + "': " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    // -----------------------------------------------------------------------
    // Alert log
    // -----------------------------------------------------------------------

    /**
     * Persists an alert event to the {@code alerts_log} table.
     *
     * @param symbol         ticker symbol
     * @param previousPrice  price before refresh
     * @param currentPrice   price after refresh
     * @param percentChange  absolute percentage change
     * @param direction      {@code "UP"} or {@code "DOWN"}
     * @param createdAt      when the alert fired
     */
    public void insertAlert(String symbol, BigDecimal previousPrice, BigDecimal currentPrice,
                            BigDecimal percentChange, String direction, LocalDateTime createdAt) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT_ALERT)) {

            ps.setString(1, symbol.toUpperCase());
            ps.setBigDecimal(2, previousPrice);
            ps.setBigDecimal(3, currentPrice);
            ps.setBigDecimal(4, percentChange);
            ps.setString(5, direction);
            ps.setTimestamp(6, toTimestamp(createdAt));
            ps.executeUpdate();

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to insert alert for " + symbol, e);
            // Alerts are non-critical; log and continue rather than crash
        }
    }

    /**
     * Returns the most recent {@code limit} alert records, newest first.
     *
     * @param limit maximum number of rows to return
     * @return list of alert records as string arrays
     *         [symbol, previousPrice, currentPrice, percentChange, direction, createdAt]
     */
    public List<String[]> findRecentAlerts(int limit) {
        List<String[]> alerts = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SELECT_RECENT_ALERTS)) {

            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    alerts.add(new String[]{
                        rs.getString("symbol"),
                        rs.getString("previous_price"),
                        rs.getString("current_price"),
                        rs.getString("percent_change"),
                        rs.getString("direction"),
                        rs.getString("created_at")
                    });
                }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to fetch recent alerts", e);
        }
        return alerts;
    }

    // -----------------------------------------------------------------------
    // Helper utilities
    // -----------------------------------------------------------------------

    /**
     * Maps a {@link ResultSet} row to a {@link Stock} object.
     *
     * @param rs ResultSet positioned at the row to map
     * @return populated Stock
     * @throws SQLException on column access error
     */
    private Stock mapRowToStock(ResultSet rs) throws SQLException {
        Stock stock = new Stock();
        stock.setId(rs.getLong("id"));
        stock.setSymbol(rs.getString("symbol"));
        stock.setName(rs.getString("name"));
        stock.setQuantity(rs.getBigDecimal("quantity"));
        stock.setBuyPrice(rs.getBigDecimal("buy_price"));
        stock.setCurrentPrice(rs.getBigDecimal("current_price"));

        Timestamp ts = rs.getTimestamp("last_updated");
        stock.setLastUpdated(ts != null ? ts.toLocalDateTime() : LocalDateTime.now());
        return stock;
    }

    /**
     * Converts a {@link LocalDateTime} to a {@link Timestamp} for JDBC.
     *
     * @param ldt local date-time; if {@code null}, uses now
     * @return JDBC Timestamp
     */
    private Timestamp toTimestamp(LocalDateTime ldt) {
        return Timestamp.valueOf(ldt != null ? ldt : LocalDateTime.now());
    }

    /**
     * Checks whether the database connection is reachable.
     * Useful for startup health checks.
     *
     * @return {@code true} if a connection can be established
     */
    public boolean isConnectionHealthy() {
        try (Connection conn = getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            LOGGER.info("Connected to: " + meta.getDatabaseProductName()
                        + " " + meta.getDatabaseProductVersion());
            return true;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database health check failed", e);
            return false;
        }
    }
}
