package com.portfolio;

import com.portfolio.api.StockPriceClient;
import com.portfolio.concurrency.PriceRefreshScheduler;
import com.portfolio.dao.PortfolioDAO;
import com.portfolio.model.Portfolio;
import com.portfolio.service.AlertService;
import com.portfolio.service.PortfolioService;
import com.portfolio.service.RiskAnalysisService;
import com.portfolio.ui.ConsoleMenu;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Properties;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Application entry point for the Multi-threaded Stock Portfolio Tracker.
 *
 * <p>Responsibilities:</p>
 * <ol>
 *   <li>Configure {@code java.util.logging}.</li>
 *   <li>Load {@code application.properties} from the classpath.</li>
 *   <li>Wire up all components (DAO, services, scheduler, UI).</li>
 *   <li>Load existing portfolio data from the database.</li>
 *   <li>Start the background price refresh scheduler.</li>
 *   <li>Hand control to the {@link ConsoleMenu} event loop.</li>
 * </ol>
 *
 * <p>If the Alpha Vantage API key is missing or is the placeholder value, the
 * application starts in <em>offline mode</em>: it loads previously saved prices
 * from the database and skips all live price fetches. A clear warning is printed
 * to the console so the user knows they need to configure a valid key.</p>
 */
public class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    /** Classpath location of the properties file. */
    private static final String PROPS_FILE = "application.properties";

    /**
     * Application entry point.
     *
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) {
        configureLogging();
        LOGGER.info("Starting Stock Portfolio Tracker...");

        // ------------------------------------------------------------------
        // 1. Load configuration
        // ------------------------------------------------------------------
        Properties props = loadProperties();

        String dbUrl       = props.getProperty("db.url",       "jdbc:sqlite:portfolio.db");
        String dbUsername  = props.getProperty("db.username",  "");
        String dbPassword  = props.getProperty("db.password",  "");
        String apiKey      = props.getProperty("alpha.vantage.api.key", "").trim();
        String baseUrl     = props.getProperty("alpha.vantage.base.url",
                                               "https://www.alphavantage.co/query");
        String alertLogFile = props.getProperty("alert.log.file", "alerts.log");

        long refreshInterval;
        try {
            refreshInterval = Long.parseLong(props.getProperty("refresh.interval.seconds", "60"));
            if (refreshInterval < 10) {
                System.out.println("[WARN] refresh.interval.seconds is very low (" + refreshInterval
                        + "s). Alpha Vantage free tier allows 25 calls/day. Setting to 60s.");
                refreshInterval = 60;
            }
        } catch (NumberFormatException e) {
            System.out.println("[WARN] Invalid refresh.interval.seconds; defaulting to 60.");
            refreshInterval = 60;
        }

        BigDecimal alertThreshold;
        try {
            alertThreshold = new BigDecimal(props.getProperty("default.alert.threshold", "5.0"));
        } catch (NumberFormatException e) {
            alertThreshold = BigDecimal.valueOf(5.0);
        }

        BigDecimal concentrationThreshold;
        try {
            concentrationThreshold = new BigDecimal(
                    props.getProperty("concentration.risk.threshold", "40.0"));
        } catch (NumberFormatException e) {
            concentrationThreshold = BigDecimal.valueOf(40.0);
        }

        // ------------------------------------------------------------------
        // 2. Initialise DAO and verify DB connection
        // ------------------------------------------------------------------
        System.out.println("[INFO] Connecting to database: " + dbUrl);
        PortfolioDAO dao;
        try {
            dao = new PortfolioDAO(dbUrl, dbUsername, dbPassword);
            if (!dao.isConnectionHealthy()) {
                System.err.println("[ERROR] Cannot connect to database. Exiting.");
                System.exit(1);
            }
        } catch (RuntimeException e) {
            System.err.println("[ERROR] Failed to initialise database: " + e.getMessage());
            System.exit(1);
            return;
        }

        // ------------------------------------------------------------------
        // 3. Initialise API client (may be null in offline mode)
        // ------------------------------------------------------------------
        StockPriceClient priceClient = null;
        boolean offlineMode = false;

        if (apiKey.isEmpty() || "YOUR_ALPHA_VANTAGE_API_KEY_HERE".equals(apiKey)) {
            System.out.println();
            System.out.println("╔══════════════════════════════════════════════════════════╗");
            System.out.println("║  ⚠  OFFLINE MODE: Alpha Vantage API key not configured.  ║");
            System.out.println("║  Set alpha.vantage.api.key in application.properties     ║");
            System.out.println("║  Live prices unavailable; using last-saved DB prices.    ║");
            System.out.println("╚══════════════════════════════════════════════════════════╝");
            System.out.println();
            offlineMode = true;
        } else {
            try {
                priceClient = new StockPriceClient(apiKey, baseUrl);
                System.out.println("[INFO] Alpha Vantage client initialised. Refresh interval: "
                        + refreshInterval + "s.");
            } catch (IllegalArgumentException e) {
                System.out.println("[WARN] " + e.getMessage());
                System.out.println("[INFO] Starting in offline mode.");
                offlineMode = true;
            }
        }

        // ------------------------------------------------------------------
        // 4. Assemble services
        // ------------------------------------------------------------------
        Portfolio portfolio = new Portfolio();

        AlertService alertService = new AlertService(alertThreshold, dao, alertLogFile);

        PortfolioService portfolioService = new PortfolioService(
                portfolio, dao, priceClient, alertService);

        RiskAnalysisService riskService = new RiskAnalysisService(concentrationThreshold);

        PriceRefreshScheduler scheduler = new PriceRefreshScheduler(
                portfolioService, portfolio, refreshInterval);

        // ------------------------------------------------------------------
        // 5. Load persisted holdings from DB
        // ------------------------------------------------------------------
        System.out.println("[INFO] Loading portfolio from database...");
        try {
            portfolioService.loadFromDatabase();
            System.out.println("[INFO] Loaded " + portfolio.size() + " holding(s).");
        } catch (RuntimeException e) {
            System.err.println("[ERROR] Failed to load portfolio from database: " + e.getMessage());
            // Continue with empty portfolio rather than crashing
        }

        // ------------------------------------------------------------------
        // 6. Start background refresh scheduler
        // ------------------------------------------------------------------
        if (!offlineMode) {
            scheduler.start();
            System.out.println("[INFO] Background price refresh started (every " + refreshInterval + "s).");
        }

        // ------------------------------------------------------------------
        // 7. Start console UI
        // ------------------------------------------------------------------
        ConsoleMenu menu = new ConsoleMenu(
                portfolioService, alertService, riskService, scheduler, dao, portfolio);
        menu.start();

        // ------------------------------------------------------------------
        // 8. Post-exit cleanup (menu.start() returns only after "Exit")
        // ------------------------------------------------------------------
        LOGGER.info("Application exited normally.");
        System.exit(0);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Loads {@code application.properties} from the classpath, and overlays any
     * overrides from a local file in the working directory or environment variables.
     *
     * @return loaded properties, or a default-empty {@link Properties} on failure
     */
    private static Properties loadProperties() {
        Properties props = new Properties();

        // 1. Classpath defaults
        try (InputStream is = Main.class.getClassLoader().getResourceAsStream(PROPS_FILE)) {
            if (is != null) {
                props.load(is);
                LOGGER.info("Configuration loaded from classpath: " + PROPS_FILE);
            }
        } catch (IOException ignored) {}

        // 2. Working directory overrides (e.g. application.properties next to the JAR)
        File localFile = new File(PROPS_FILE);
        if (localFile.exists() && localFile.isFile()) {
            try (InputStream fis = new FileInputStream(localFile)) {
                props.load(fis);
                LOGGER.info("Loaded configuration overrides from local file: " + localFile.getAbsolutePath());
            } catch (IOException e) {
                System.err.println("[WARN] Could not load local " + PROPS_FILE + ": " + e.getMessage());
            }
        }

        // 3. Environment variable override
        String envKey = System.getenv("ALPHA_VANTAGE_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            props.setProperty("alpha.vantage.api.key", envKey.trim());
        }

        return props;
    }

    /**
     * Configures {@code java.util.logging} to print to the console at INFO level.
     * Suppresses noisy library loggers (e.g. HTTP client internals).
     */
    private static void configureLogging() {
        // Root logger: WARN and above only (to avoid flooding the console)
        Logger root = Logger.getLogger("");
        root.setLevel(Level.WARNING);
        for (var handler : root.getHandlers()) {
            root.removeHandler(handler);
        }

        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.WARNING);
        handler.setFormatter(new SimpleFormatter());
        root.addHandler(handler);

        // Our own package: INFO level
        Logger appLogger = Logger.getLogger("com.portfolio");
        appLogger.setLevel(Level.INFO);

        // Suppress verbose HTTP client logs
        Logger.getLogger("jdk.internal.httpclient").setLevel(Level.SEVERE);
    }
}
