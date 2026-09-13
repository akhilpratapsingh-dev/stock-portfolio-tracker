package com.portfolio.ui;

import com.portfolio.concurrency.PriceRefreshScheduler;
import com.portfolio.dao.PortfolioDAO;
import com.portfolio.model.Portfolio;
import com.portfolio.model.Stock;
import com.portfolio.service.AlertService;
import com.portfolio.service.PortfolioService;
import com.portfolio.service.RiskAnalysisService;
import com.portfolio.service.RiskAnalysisService.RiskEntry;
import com.portfolio.util.InputValidator;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.logging.Logger;

/**
 * Scanner-based console menu for the Stock Portfolio Tracker.
 *
 * <p>This class is the sole entry point for user interaction. It delegates all
 * business logic to the service layer and never accesses the DAO or model
 * directly (except to read display data from {@link Stock} value objects).</p>
 *
 * <p>Design principles:</p>
 * <ul>
 *   <li>Never crashes on bad input — every numeric parse is wrapped in a
 *       try-catch that prompts the user to re-enter the value.</li>
 *   <li>Never blocks the menu while background refresh runs — the scheduler
 *       runs on daemon threads.</li>
 *   <li>All output uses fixed-width formatting for aligned table display.</li>
 * </ul>
 */
public class ConsoleMenu {

    private static final Logger LOGGER = Logger.getLogger(ConsoleMenu.class.getName());

    // ANSI codes
    private static final String ANSI_RESET  = "\u001B[0m";
    private static final String ANSI_CYAN   = "\u001B[36m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_GREEN  = "\u001B[32m";
    private static final String ANSI_RED    = "\u001B[31m";
    private static final String ANSI_BOLD   = "\u001B[1m";
    private static final String ANSI_DIM    = "\u001B[2m";

    // Column widths for portfolio table
    private static final int W_SYMBOL  = 8;
    private static final int W_NAME    = 22;
    private static final int W_QTY     = 10;
    private static final int W_PRICE   = 12;
    private static final int W_VALUE   = 14;
    private static final int W_PNL     = 14;
    private static final int W_PCT     = 9;
    private static final int W_ALERT   = 18;

    // -----------------------------------------------------------------------
    // Dependencies
    // -----------------------------------------------------------------------

    private final PortfolioService    portfolioService;
    private final AlertService        alertService;
    private final RiskAnalysisService riskService;
    private final PriceRefreshScheduler scheduler;
    private final PortfolioDAO        dao;
    private final Portfolio           portfolio;
    private final Scanner             scanner;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    /**
     * Creates the console menu.
     *
     * @param portfolioService  business logic for managing holdings
     * @param alertService      alert threshold management and firing
     * @param riskService       concentration risk analysis
     * @param scheduler         background price refresh scheduler
     * @param dao               DAO (only used for "View Recent Alerts" query)
     * @param portfolio         shared portfolio (for totals)
     */
    public ConsoleMenu(PortfolioService portfolioService,
                       AlertService alertService,
                       RiskAnalysisService riskService,
                       PriceRefreshScheduler scheduler,
                       PortfolioDAO dao,
                       Portfolio portfolio) {
        this.portfolioService = portfolioService;
        this.alertService     = alertService;
        this.riskService      = riskService;
        this.scheduler        = scheduler;
        this.dao              = dao;
        this.portfolio        = portfolio;
        this.scanner          = new Scanner(System.in);
    }

    // -----------------------------------------------------------------------
    // Main loop
    // -----------------------------------------------------------------------

    /**
     * Starts the console menu event loop.
     * Blocks until the user selects "Exit".
     */
    public void start() {
        printBanner();
        boolean running = true;
        while (running) {
            printMenu();
            int choice = readInt("Enter choice: ", 1, 9);
            switch (choice) {
                case 1 -> handleAddHolding();
                case 2 -> handleRemoveHolding();
                case 3 -> handleViewPortfolio();
                case 4 -> handleRefreshNow();
                case 5 -> handleSetAlertThreshold();
                case 6 -> handleViewRecentAlerts();
                case 7 -> handleRiskAnalysis();
                case 8 -> handleSimulatePriceChange();
                case 9 -> running = false;
                default -> System.out.println("Invalid choice. Please try again.");
            }
        }
        handleExit();
    }

    // -----------------------------------------------------------------------
    // Menu handlers
    // -----------------------------------------------------------------------

    /** Handler for menu option 1: Add Stock Holding. */
    private void handleAddHolding() {
        printSectionHeader("Add Stock Holding");

        // 1. Validate ticker symbol
        String symbol;
        while (true) {
            symbol = readValidSymbol("Ticker symbol (e.g. AAPL): ");
            if (symbol == null || symbol.isEmpty()) {
                System.out.println("  Cancelled.");
                return;
            }

            if (portfolio.contains(symbol)) {
                System.out.println(ANSI_RED + "  ✗ Symbol '" + symbol + "' already exists in portfolio." + ANSI_RESET);
                return;
            }
            break;
        }

        // 2. Validate company name (auto-formatted to Title Case)
        String name = readValidCompanyName("Company name: ");
        if (name == null || name.isEmpty()) {
            System.out.println("  Cancelled.");
            return;
        }

        // 3. Validate quantity
        BigDecimal qty = readValidQuantity("Quantity (shares): ");
        if (qty == null) {
            System.out.println("  Cancelled.");
            return;
        }

        // 4. Validate buy price
        BigDecimal buyPrice = readValidBuyPrice("Buy price per share ($): ");
        if (buyPrice == null) {
            System.out.println("  Cancelled.");
            return;
        }

        System.out.println(ANSI_DIM + "  Fetching current price... (this may take a moment)" + ANSI_RESET);

        // Only call PortfolioService once all four fields pass validation
        Optional<Stock> result = portfolioService.addStock(symbol, name, qty, buyPrice);
        if (result.isPresent()) {
            Stock s = result.get();
            System.out.printf("%n  %s✔ Added: %s (%s)%n", ANSI_GREEN, s.getSymbol(), s.getName());
            System.out.printf("     Qty: %.4f  |  Buy Price: $%.4f  |  Current Price: $%.4f%s%n",
                    s.getQuantity(), s.getBuyPrice(), s.getCurrentPrice(), ANSI_RESET);
        } else {
            System.out.println(ANSI_RED + "  ✗ Failed to add holding. Check the symbol and inputs." + ANSI_RESET);
        }
    }

    /** Handler for menu option 2: Remove Stock Holding. */
    private void handleRemoveHolding() {
        printSectionHeader("Remove Stock Holding");

        if (portfolio.isEmpty()) {
            System.out.println("  Portfolio is empty. Nothing to remove.");
            return;
        }

        System.out.println("  Current holdings: " + portfolio.getSymbols());
        String symbol = readValidSymbol("Enter symbol to remove: ");
        if (symbol.isEmpty()) return;

        if (!portfolio.contains(symbol)) {
            System.out.println(ANSI_RED + "  ✗ Symbol '" + symbol + "' not found in portfolio." + ANSI_RESET);
            return;
        }

        System.out.print("  Are you sure you want to remove " + symbol + "? (yes/no): ");
        String confirm = scanner.nextLine().trim().toLowerCase();
        if (!"yes".equals(confirm) && !"y".equals(confirm)) {
            System.out.println("  Cancelled.");
            return;
        }

        boolean removed = portfolioService.removeHolding(symbol);
        if (removed) {
            System.out.println(ANSI_GREEN + "  ✔ Removed: " + symbol + ANSI_RESET);
        } else {
            System.out.println(ANSI_RED + "  ✗ Failed to remove " + symbol + ANSI_RESET);
        }
    }

    /** Handler for menu option 3: View Portfolio. */
    private void handleViewPortfolio() {
        printSectionHeader("Portfolio Overview");

        List<Stock> holdings = portfolioService.getAllHoldings();
        if (holdings.isEmpty()) {
            System.out.println("  Portfolio is empty. Add some holdings first (Option 1).");
            return;
        }

        // Print the table
        printPortfolioTable(holdings);
        printPortfolioTotals();

        // Show concentration warning if any holding is over-concentrated
        if (riskService.hasConcentrationRisk(portfolio)) {
            System.out.printf("%n  %s⚠  Concentration risk detected! Run Option 7 for details.%s%n",
                    ANSI_YELLOW, ANSI_RESET);
        }
    }

    /** Handler for menu option 4: Refresh Prices Now. */
    private void handleRefreshNow() {
        printSectionHeader("Manual Price Refresh");
        if (portfolio.isEmpty()) {
            System.out.println("  Portfolio is empty. Add holdings first.");
            return;
        }

        System.out.println("  1. Refresh all holdings");
        System.out.println("  2. Refresh specific symbol");
        System.out.println("  3. Back to main menu");

        int choice = readInt("  Choice: ", 1, 3);
        if (choice == 3) return;

        if (choice == 1) {
            System.out.println("  Submitting price refresh tasks... (running in background)");
            scheduler.refreshNow();
            System.out.println("  Refresh submitted. Prices will update shortly.");
            System.out.println(ANSI_DIM + "  (Tip: View Portfolio after a few seconds to see updated prices.)" + ANSI_RESET);
        } else {
            System.out.println("  Current symbols: " + portfolio.getSymbols());
            String symbol = readValidSymbol("  Enter symbol to refresh: ");
            if (symbol.isEmpty()) return;

            if (!portfolio.contains(symbol)) {
                System.out.println(ANSI_RED + "  ✗ Symbol '" + symbol + "' not found in portfolio." + ANSI_RESET);
                return;
            }
            System.out.println("  Refreshing price for " + symbol + "...");
            portfolioService.refreshPrice(symbol);
            System.out.println(ANSI_GREEN + "  ✔ Price refresh completed for " + symbol + "." + ANSI_RESET);
        }
    }

    /** Handler for menu option 5: Set Alert Threshold. */
    private void handleSetAlertThreshold() {
        printSectionHeader("Set Alert Threshold");

        System.out.printf("  Current global threshold: %.2f%%%n", alertService.getGlobalThreshold());
        System.out.println("  1. Set global threshold");
        System.out.println("  2. Set per-symbol threshold");
        System.out.println("  3. Back to main menu");

        int choice = readInt("  Choice: ", 1, 3);
        if (choice == 3) return;

        if (choice == 1) {
            BigDecimal newThreshold = readPositiveDecimal("  New global threshold (%): ");
            alertService.setGlobalThreshold(newThreshold);
            System.out.printf("%s  ✔ Global threshold set to %.2f%%%s%n",
                    ANSI_GREEN, newThreshold, ANSI_RESET);

        } else {
            if (portfolio.isEmpty()) {
                System.out.println("  No holdings in portfolio.");
                return;
            }
            System.out.println("  Current symbols: " + portfolio.getSymbols());
            String symbol = readValidSymbol("  Symbol: ");
            if (symbol.isEmpty()) return;
            if (!portfolio.contains(symbol)) {
                System.out.println(ANSI_RED + "  Symbol '" + symbol + "' not in portfolio." + ANSI_RESET);
                return;
            }
            BigDecimal newThreshold = readPositiveDecimal("  Threshold for " + symbol + " (%): ");
            alertService.setPerSymbolThreshold(symbol, newThreshold);
            System.out.printf("%s  ✔ Per-symbol threshold for %s set to %.2f%%%s%n",
                    ANSI_GREEN, symbol, newThreshold, ANSI_RESET);
        }
    }

    /** Handler for menu option 6: View Recent Alerts. */
    private void handleViewRecentAlerts() {
        printSectionHeader("Recent Price Alerts");

        List<String[]> alerts = dao.findRecentAlerts(20);
        if (alerts.isEmpty()) {
            System.out.println("  No alerts have been fired yet.");
            System.out.printf("  (Current threshold: %.2f%%)%n", alertService.getGlobalThreshold());
            return;
        }

        // Header
        System.out.printf("  %-12s %-8s %-14s %-14s %-12s %-5s%n",
                "Time", "Symbol", "Prev Price", "Curr Price", "Change%", "Dir");
        System.out.println("  " + "─".repeat(68));

        for (String[] alert : alerts) {
            String symbol   = alert[0];
            String prevStr  = alert[1];
            String currStr  = alert[2];
            String pctStr   = alert[3];
            String dir      = alert[4];
            String ts       = alert[5];

            String colour = "UP".equals(dir) ? ANSI_GREEN : ANSI_RED;
            String arrow  = "UP".equals(dir) ? "▲" : "▼";

            // Shorten timestamp for display
            String displayTs = ts != null && ts.length() >= 16 ? ts.substring(0, 16) : ts;

            System.out.printf("  %-12s %-8s $%-13s $%-13s %s%+8.2f%% %s %s%n",
                    displayTs, symbol, prevStr, currStr,
                    colour, Double.parseDouble(pctStr != null ? pctStr : "0"),
                    arrow, ANSI_RESET);
        }
    }

    /** Handler for menu option 7: Risk / Concentration Analysis. */
    private void handleRiskAnalysis() {
        printSectionHeader("Risk / Concentration Analysis");

        if (portfolio.isEmpty()) {
            System.out.println("  Portfolio is empty.");
            return;
        }

        List<RiskEntry> entries = riskService.analyseConcentration(portfolio);
        BigDecimal threshold    = riskService.getConcentrationRiskThreshold();

        System.out.printf("  Concentration risk threshold: %.1f%%%n%n", threshold);

        // Header
        System.out.printf("  %-8s %-22s %12s %10s %s%n",
                "SYMBOL", "COMPANY", "CURR VALUE", "WEIGHT%", "STATUS");
        System.out.println("  " + "─".repeat(75));

        for (RiskEntry entry : entries) {
            String statusIcon = entry.isConcentrationRisk()
                    ? ANSI_YELLOW + "⚠  CONCENTRATION RISK" + ANSI_RESET
                    : ANSI_GREEN  + "✔  OK" + ANSI_RESET;

            System.out.printf("  %-8s %-22s %12.2f %9.2f%%  %s%n",
                    entry.getSymbol(),
                    truncate(entry.getName(), 22),
                    entry.getCurrentValue(),
                    entry.getWeightPercent(),
                    statusIcon);
        }

        System.out.println();
        if (riskService.hasConcentrationRisk(portfolio)) {
            System.out.println(ANSI_YELLOW + "  ⚠  ACTION RECOMMENDED: Consider rebalancing holdings above "
                               + threshold + "% to reduce concentration risk." + ANSI_RESET);
        } else {
            System.out.println(ANSI_GREEN + "  ✔  Portfolio is well-diversified. No concentration risk detected."
                               + ANSI_RESET);
        }
    }

    /** Handler for menu option 8: Exit. */
    private void handleExit() {
        System.out.println();
        System.out.println(ANSI_CYAN + "  Shutting down..." + ANSI_RESET);
        scheduler.stop();
        System.out.println("  Price refresh scheduler stopped.");
        System.out.println(ANSI_BOLD + "  Goodbye! Your portfolio data has been saved." + ANSI_RESET);
        System.out.println();
    }

    // -----------------------------------------------------------------------
    // Display helpers
    // -----------------------------------------------------------------------

    /** Prints the welcome banner. */
    private void printBanner() {
        System.out.println();
        System.out.println(ANSI_CYAN + ANSI_BOLD);
        System.out.println("  ╔══════════════════════════════════════════════════════╗");
        System.out.println("  ║     📈  Multi-threaded Stock Portfolio Tracker       ║");
        System.out.println("  ║        Powered by Alpha Vantage + Java 17+           ║");
        System.out.println("  ╚══════════════════════════════════════════════════════╝");
        System.out.println(ANSI_RESET);
    }

    /** Prints the main menu. */
    private void printMenu() {
        System.out.println();
        System.out.println(ANSI_BOLD + "  ── Main Menu ──────────────────────────────────" + ANSI_RESET);
        System.out.println("   1. Add Stock Holding");
        System.out.println("   2. Remove Stock Holding");
        System.out.println("   3. View Portfolio");
        System.out.println("   4. Refresh Prices Now");
        System.out.println("   5. Set Alert Threshold");
        System.out.println("   6. View Recent Alerts");
        System.out.println("   7. Risk / Concentration Analysis");
        System.out.println(ANSI_YELLOW + "   8. Simulate Price Change  [Testing Only]" + ANSI_RESET);
        System.out.println("   9. Exit");
        System.out.println();
    }

    /** Prints a section header line. */
    private void printSectionHeader(String title) {
        System.out.println();
        System.out.println(ANSI_CYAN + ANSI_BOLD
                + "  ── " + title + " " + "─".repeat(Math.max(0, 44 - title.length()))
                + ANSI_RESET);
    }

    /**
     * Prints the portfolio as an aligned table with all financial columns.
     *
     * @param holdings list of holdings to display
     */
    private void printPortfolioTable(List<Stock> holdings) {
        // Column header
        System.out.printf("  %-" + W_SYMBOL + "s  %-" + W_NAME + "s  %" + W_QTY + "s  "
                + "%" + W_PRICE + "s  %" + W_PRICE + "s  "
                + "%" + W_VALUE + "s  %" + W_VALUE + "s  "
                + "%" + W_PNL   + "s  %" + W_PCT + "s%n",
                "SYMBOL", "COMPANY", "QTY",
                "BUY PRICE", "CURR PRICE",
                "INVESTED", "CURR VALUE",
                "P&L", "P&L%");

        int totalWidth = W_SYMBOL + W_NAME + W_QTY + W_PRICE * 2 + W_VALUE * 2 + W_PNL + W_PCT + 18;
        System.out.println("  " + "─".repeat(totalWidth));

        for (Stock s : holdings) {
            BigDecimal pnl    = s.getProfitLoss();
            BigDecimal pnlPct = s.getProfitLossPercent();
            String pnlColour  = pnl.signum() >= 0 ? ANSI_GREEN : ANSI_RED;
            String sign       = pnl.signum() >= 0 ? "+" : "";

            System.out.printf("  %-" + W_SYMBOL + "s  %-" + W_NAME + "s  %" + W_QTY + ".4f  "
                    + "%" + W_PRICE + ".4f  %" + W_PRICE + ".4f  "
                    + "%" + W_VALUE + ".2f  %" + W_VALUE + ".2f  "
                    + "%s%" + W_PNL + ".2f  %" + W_PCT + ".2f%%%s%n",
                    s.getSymbol(),
                    truncate(s.getName(), W_NAME),
                    s.getQuantity(),
                    s.getBuyPrice(),
                    s.getCurrentPrice(),
                    s.getInvestedValue(),
                    s.getCurrentValue(),
                    pnlColour,
                    pnl,
                    pnlPct,
                    ANSI_RESET);
        }
        System.out.println("  " + "─".repeat(totalWidth));
    }

    /** Prints portfolio totals below the table. */
    private void printPortfolioTotals() {
        BigDecimal totalInvested   = portfolio.getTotalInvested();
        BigDecimal totalCurrent    = portfolio.getTotalCurrentValue();
        BigDecimal totalPnL        = portfolio.getTotalProfitLoss();
        BigDecimal overallReturn   = portfolio.getOverallReturnPercent();
        String     pnlColour       = totalPnL.signum() >= 0 ? ANSI_GREEN : ANSI_RED;

        System.out.printf("  %s%-32s  Total Invested: $%,12.2f  |  Curr Value: $%,12.2f  |  P&L: %s$%+,12.2f (%+.2f%%)%s%n",
                ANSI_BOLD,
                "PORTFOLIO TOTALS",
                totalInvested,
                totalCurrent,
                pnlColour,
                totalPnL,
                overallReturn,
                ANSI_RESET);
    }

    // -----------------------------------------------------------------------
    // Input helpers
    // -----------------------------------------------------------------------

    /**
     * Prompts the user for an integer within [min, max].
     * Re-prompts on invalid input; never throws.
     *
     * @param prompt prompt string
     * @param min    minimum valid value
     * @param max    maximum valid value
     * @return validated integer in [min, max]
     */
    private int readInt(String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            try {
                if (!scanner.hasNextLine()) {
                    return max; // Default to Exit on EOF
                }
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }
                try {
                    int value = Integer.parseInt(line);
                    if (value >= min && value <= max) {
                        return value;
                    }
                    System.out.println("  Please enter a number between " + min + " and " + max + ".");
                } catch (NumberFormatException e) {
                    System.out.println("  Invalid input. Please enter a whole number.");
                }
            } catch (java.util.NoSuchElementException e) {
                return max; // Exit on EOF
            }
        }
    }

    /**
     * Prompts the user for a positive {@link BigDecimal}.
     * Re-prompts on invalid or non-positive input.
     *
     * @param prompt prompt string
     * @return positive BigDecimal
     */
    private BigDecimal readPositiveDecimal(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                if (!scanner.hasNextLine()) {
                    return BigDecimal.ONE;
                }
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }
                try {
                    BigDecimal value = new BigDecimal(line);
                    if (value.compareTo(BigDecimal.ZERO) > 0) {
                        return value;
                    }
                    System.out.println("  Value must be greater than zero.");
                } catch (NumberFormatException e) {
                    System.out.println("  Invalid number. Please try again (e.g. 123.45).");
                }
            } catch (java.util.NoSuchElementException e) {
                return BigDecimal.ONE;
            }
        }
    }

    /**
     * Prompts the user for a non-blank string.
     * Re-prompts on blank or empty input.
     *
     * @param prompt prompt string
     * @return non-blank, trimmed string
     */
    private String readNonBlankString(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                if (!scanner.hasNextLine()) {
                    return "";
                }
                String line = scanner.nextLine().trim();
                if (!line.isBlank()) {
                    return line;
                }
                System.out.println("  Input cannot be blank. Please try again.");
            } catch (java.util.NoSuchElementException e) {
                return "";
            }
        }
    }

    /**
     * Prompts the user for a valid ticker symbol using {@link InputValidator#validateSymbol(String)}.
     * Re-prompts until a valid symbol format is entered or EOF is encountered.
     *
     * @param prompt prompt message to display
     * @return cleaned, upper-case symbol (or empty string on EOF)
     */
    private String readValidSymbol(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                if (!scanner.hasNextLine()) {
                    return "";
                }
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }
                String error = InputValidator.validateSymbol(line);
                if (error != null) {
                    System.out.println(ANSI_RED + "  " + error + ANSI_RESET);
                    continue;
                }
                return InputValidator.cleanSymbol(line);
            } catch (java.util.NoSuchElementException e) {
                return "";
            }
        }
    }

    /**
     * Prompts the user for a valid company name using {@link InputValidator#validateCompanyName(String)}.
     * Re-prompts until a valid name is entered or EOF is encountered.
     *
     * @param prompt prompt message to display
     * @return Title-cased company name (or empty string on EOF)
     */
    private String readValidCompanyName(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                if (!scanner.hasNextLine()) {
                    return "";
                }
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }
                String error = InputValidator.validateCompanyName(line);
                if (error != null) {
                    System.out.println(ANSI_RED + "  " + error + ANSI_RESET);
                    continue;
                }
                return InputValidator.formatCompanyName(line);
            } catch (java.util.NoSuchElementException e) {
                return "";
            }
        }
    }

    /**
     * Prompts the user for a valid quantity using {@link InputValidator#validateQuantity(String)}.
     * Re-prompts until a valid positive number is entered or EOF is encountered.
     *
     * @param prompt prompt message to display
     * @return validated positive {@link BigDecimal} quantity, or {@code null} on EOF
     */
    private BigDecimal readValidQuantity(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                if (!scanner.hasNextLine()) {
                    return null;
                }
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }
                String error = InputValidator.validateQuantity(line);
                if (error != null) {
                    System.out.println(ANSI_RED + "  " + error + ANSI_RESET);
                    continue;
                }
                return InputValidator.parseQuantity(line);
            } catch (java.util.NoSuchElementException e) {
                return null;
            }
        }
    }

    /**
     * Prompts the user for a valid buy price using {@link InputValidator#validateBuyPrice(String)}.
     * Re-prompts until a valid positive decimal is entered or EOF is encountered.
     *
     * @param prompt prompt message to display
     * @return validated positive {@link BigDecimal} buy price, or {@code null} on EOF
     */
    private BigDecimal readValidBuyPrice(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                if (!scanner.hasNextLine()) {
                    return null;
                }
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }
                String error = InputValidator.validateBuyPrice(line);
                if (error != null) {
                    System.out.println(ANSI_RED + "  " + error + ANSI_RESET);
                    continue;
                }
                return InputValidator.parseBuyPrice(line);
            } catch (java.util.NoSuchElementException e) {
                return null;
            }
        }
    }

    /**
     * Handler for menu option 8: Simulate Price Change (Testing Only).
     *
     * <p>Lets you inject an arbitrary price for any portfolio symbol, bypassing
     * the Alpha Vantage API. Internally calls
     * {@link PortfolioService#applySimulatedPrice(String, BigDecimal)}, which
     * follows the <em>identical</em> code path as the real background scheduler:
     * {@code portfolio.updatePrice → dao.updatePrice → alertService.checkAndFireAlert}.</p>
     *
     * <p>This means testing this option is a valid proof that the real alert
     * system works — there is no separate fake logic involved.</p>
     */
    private void handleSimulatePriceChange() {
        printSectionHeader("Simulate Price Change  [Testing Only]");

        if (portfolio.isEmpty()) {
            System.out.println("  Portfolio is empty. Add a holding first (Option 1).");
            return;
        }

        System.out.println("  Current holdings: " + portfolio.getSymbols());
        System.out.printf("  Current alert threshold: %.2f%%%n%n", alertService.getGlobalThreshold());
        System.out.println(ANSI_YELLOW
                + "  ⚠  This bypasses the Alpha Vantage API and directly sets the price."
                + ANSI_RESET);
        System.out.println(ANSI_YELLOW
                + "     An alert fires if |change| >= threshold AND the new price differs"
                + ANSI_RESET);
        System.out.println(ANSI_YELLOW
                + "     from the last alerted price (duplicate-suppression is active)."
                + ANSI_RESET);
        System.out.println();

        // 1. Symbol
        String symbol = readValidSymbol("  Symbol to simulate: ");
        if (symbol == null || symbol.isEmpty()) {
            System.out.println("  Cancelled.");
            return;
        }
        if (!portfolio.contains(symbol)) {
            System.out.println(ANSI_RED + "  ✗ Symbol '" + symbol + "' not found in portfolio." + ANSI_RESET);
            return;
        }

        // Show the current (before) price so the user knows what to compare against
        portfolio.getHolding(symbol).ifPresent(s ->
            System.out.printf("  Current price for %s: $%.4f%n", symbol, s.getCurrentPrice()));

        // 2. Fake new price
        System.out.println();
        BigDecimal fakePrice = readValidBuyPrice("  Enter simulated new price ($): ");
        if (fakePrice == null) {
            System.out.println("  Cancelled.");
            return;
        }

        System.out.println();
        System.out.println("  Applying simulated price and running alert check...");
        System.out.println();

        // 3. Apply — this calls the real AlertService.checkAndFireAlert() internally
        boolean updated = portfolioService.applySimulatedPrice(symbol, fakePrice);

        if (updated) {
            System.out.printf("%n%s  ✔ Price updated: %s → $%.4f%s%n",
                    ANSI_GREEN, symbol, fakePrice, ANSI_RESET);
            System.out.println(ANSI_GREEN
                    + "  If |change%| >= threshold, an alert was just printed above this line,"
                    + ANSI_RESET);
            System.out.println(ANSI_GREEN
                    + "  appended to alerts.log, and inserted into alerts_log DB table."
                    + ANSI_RESET);
        } else {
            System.out.println(ANSI_RED
                    + "  ✗ Could not apply simulated price. Symbol not found or invalid price."
                    + ANSI_RESET);
        }
    }

    /**
     * Truncates a string to the given maximum length, appending "…" if truncated.
     *
     * @param s      string to truncate
     * @param maxLen maximum length
     * @return truncated string
     */
    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 1) + "…";
    }
}
