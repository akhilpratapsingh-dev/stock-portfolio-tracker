package com.portfolio;

import com.portfolio.model.Portfolio;
import com.portfolio.model.Stock;
import com.portfolio.service.AlertService;
import com.portfolio.service.RiskAnalysisService;
import com.portfolio.service.RiskAnalysisService.RiskEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the core domain classes and services.
 *
 * <p>These tests are designed to run without a database or network — they
 * use in-memory {@link Portfolio} instances and mock/stub dependencies where
 * needed.</p>
 */
class PortfolioTrackerTest {

    // =======================================================================
    // Stock model tests
    // =======================================================================

    @Nested
    @DisplayName("Stock — financial calculations")
    class StockCalculationTests {

        private Stock stock;

        @BeforeEach
        void setUp() {
            // 10 shares of AAPL bought at $150, now worth $180
            stock = new Stock("AAPL", "Apple Inc.", BigDecimal.TEN,
                    new BigDecimal("150.00"), new BigDecimal("180.00"));
        }

        @Test
        @DisplayName("Invested value = qty × buyPrice")
        void investedValue() {
            assertEquals(new BigDecimal("1500.0000"), stock.getInvestedValue());
        }

        @Test
        @DisplayName("Current value = qty × currentPrice")
        void currentValue() {
            assertEquals(new BigDecimal("1800.0000"), stock.getCurrentValue());
        }

        @Test
        @DisplayName("P&L = currentValue − investedValue (positive gain)")
        void profitLossPositive() {
            assertEquals(new BigDecimal("300.0000"), stock.getProfitLoss());
        }

        @Test
        @DisplayName("P&L% = (P&L / invested) × 100")
        void profitLossPercent() {
            // (300 / 1500) * 100 = 20.00%
            assertEquals(new BigDecimal("20.00"), stock.getProfitLossPercent());
        }

        @Test
        @DisplayName("P&L is negative when current price < buy price")
        void profitLossNegative() {
            stock.setCurrentPrice(new BigDecimal("120.00"));
            assertTrue(stock.getProfitLoss().signum() < 0,
                    "P&L should be negative when current price is below buy price");
        }

        @Test
        @DisplayName("P&L% = 0 when invested value is zero (edge case)")
        void profitLossPercentZeroInvested() {
            Stock zeroStock = new Stock("X", "Test", BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO);
            assertEquals(BigDecimal.ZERO, zeroStock.getProfitLossPercent());
        }
    }

    // =======================================================================
    // Portfolio tests
    // =======================================================================

    @Nested
    @DisplayName("Portfolio — thread-safe operations and aggregates")
    class PortfolioTests {

        private Portfolio portfolio;
        private Stock aapl;
        private Stock msft;

        @BeforeEach
        void setUp() {
            portfolio = new Portfolio();
            aapl = new Stock("AAPL", "Apple Inc.", new BigDecimal("10"),
                    new BigDecimal("150.00"), new BigDecimal("180.00"));
            msft = new Stock("MSFT", "Microsoft Corp.", new BigDecimal("5"),
                    new BigDecimal("300.00"), new BigDecimal("320.00"));
        }

        @Test
        @DisplayName("addHolding stores and retrieves by symbol")
        void addAndRetrieve() {
            portfolio.addHolding(aapl);
            Optional<Stock> found = portfolio.getHolding("AAPL");
            assertTrue(found.isPresent());
            assertEquals("AAPL", found.get().getSymbol());
        }

        @Test
        @DisplayName("getHolding is case-insensitive")
        void caseInsensitiveLookup() {
            portfolio.addHolding(aapl);
            assertTrue(portfolio.getHolding("aapl").isPresent());
            assertTrue(portfolio.getHolding("Aapl").isPresent());
        }

        @Test
        @DisplayName("removeHolding returns true and removes the stock")
        void removeHolding() {
            portfolio.addHolding(aapl);
            assertTrue(portfolio.removeHolding("AAPL"));
            assertFalse(portfolio.contains("AAPL"));
        }

        @Test
        @DisplayName("removeHolding returns false for unknown symbol")
        void removeNonExistent() {
            assertFalse(portfolio.removeHolding("UNKNOWN"));
        }

        @Test
        @DisplayName("totalInvested sums all holdings")
        void totalInvested() {
            portfolio.addHolding(aapl); // 10 × 150 = 1500
            portfolio.addHolding(msft); //  5 × 300 = 1500
            // total = 3000
            assertEquals(0, portfolio.getTotalInvested().compareTo(new BigDecimal("3000")));
        }

        @Test
        @DisplayName("totalCurrentValue sums all holdings at current price")
        void totalCurrentValue() {
            portfolio.addHolding(aapl); // 10 × 180 = 1800
            portfolio.addHolding(msft); //  5 × 320 = 1600
            // total = 3400
            assertEquals(0, portfolio.getTotalCurrentValue().compareTo(new BigDecimal("3400")));
        }

        @Test
        @DisplayName("updatePrice changes the in-memory current price")
        void updatePrice() {
            portfolio.addHolding(aapl);
            portfolio.updatePrice("AAPL", new BigDecimal("200.00"));
            BigDecimal updated = portfolio.getHolding("AAPL")
                    .map(Stock::getCurrentPrice)
                    .orElse(BigDecimal.ZERO);
            assertEquals(0, updated.compareTo(new BigDecimal("200.00")));
        }

        @Test
        @DisplayName("concentrationPercent correct for two equal holdings")
        void concentrationPercent() {
            // Both holdings have equal current value (1800 each = 3600 total)
            // AAPL concentration = 1800/3600 = 50%
            portfolio.addHolding(aapl); // curr = 1800
            portfolio.addHolding(msft); // curr = 1600
            // AAPL = 1800 / 3400 ≈ 52.94%
            BigDecimal aaplConc = portfolio.getConcentrationPercent("AAPL");
            assertTrue(aaplConc.compareTo(new BigDecimal("52")) > 0
                            && aaplConc.compareTo(new BigDecimal("54")) < 0,
                    "AAPL concentration should be ~52.94%, got: " + aaplConc);
        }

        @Test
        @DisplayName("getOverallReturnPercent is zero for empty portfolio")
        void overallReturnEmpty() {
            assertEquals(BigDecimal.ZERO, portfolio.getOverallReturnPercent());
        }

        @Test
        @DisplayName("getAllHoldings returns all stored holdings")
        void getAllHoldings() {
            portfolio.addHolding(aapl);
            portfolio.addHolding(msft);
            List<Stock> all = portfolio.getAllHoldings();
            assertEquals(2, all.size());
        }
    }

    // =======================================================================
    // AlertService tests
    // =======================================================================

    @Nested
    @DisplayName("AlertService — threshold management and alert logic")
    class AlertServiceTests {

        /**
         * Minimal stub DAO that accepts all calls without a real database.
         */
        private static class NoOpDAO extends com.portfolio.dao.PortfolioDAO {
            NoOpDAO() {
                // Use in-memory SQLite so the schema still initialises
                super("jdbc:sqlite::memory:", "", "");
            }
        }

        private AlertService alertService;

        @BeforeEach
        void setUp() {
            alertService = new AlertService(new BigDecimal("5.0"),
                    new NoOpDAO(), "test_alerts.log");
        }

        @Test
        @DisplayName("Global threshold is returned correctly")
        void globalThreshold() {
            assertEquals(0, alertService.getGlobalThreshold().compareTo(new BigDecimal("5.0")));
        }

        @Test
        @DisplayName("setGlobalThreshold updates the threshold")
        void setGlobalThreshold() {
            alertService.setGlobalThreshold(new BigDecimal("10.0"));
            assertEquals(0, alertService.getGlobalThreshold().compareTo(new BigDecimal("10.0")));
        }

        @Test
        @DisplayName("setPerSymbolThreshold overrides global for that symbol")
        void perSymbolThreshold() {
            alertService.setPerSymbolThreshold("AAPL", new BigDecimal("2.0"));
            assertEquals(0, alertService.getEffectiveThreshold("AAPL")
                    .compareTo(new BigDecimal("2.0")));
            // Other symbols still use global
            assertEquals(0, alertService.getEffectiveThreshold("MSFT")
                    .compareTo(new BigDecimal("5.0")));
        }

        @Test
        @DisplayName("setGlobalThreshold ignores zero or negative values")
        void invalidThreshold() {
            BigDecimal original = alertService.getGlobalThreshold();
            alertService.setGlobalThreshold(BigDecimal.ZERO);
            assertEquals(original, alertService.getGlobalThreshold());
            alertService.setGlobalThreshold(new BigDecimal("-1"));
            assertEquals(original, alertService.getGlobalThreshold());
        }

        @Test
        @DisplayName("checkAndFireAlert does not throw on equal prices")
        void alertNoChangeNoCrash() {
            BigDecimal price = new BigDecimal("150.00");
            assertDoesNotThrow(() ->
                    alertService.checkAndFireAlert("AAPL", price, price));
        }

        @Test
        @DisplayName("checkAndFireAlert does not throw on null inputs")
        void alertNullNoCrash() {
            assertDoesNotThrow(() ->
                    alertService.checkAndFireAlert("AAPL", null, new BigDecimal("150")));
        }
    }

    // =======================================================================
    // RiskAnalysisService tests
    // =======================================================================

    @Nested
    @DisplayName("RiskAnalysisService — concentration risk detection")
    class RiskAnalysisTests {

        private RiskAnalysisService riskService;
        private Portfolio portfolio;

        @BeforeEach
        void setUp() {
            riskService = new RiskAnalysisService(new BigDecimal("40.0"));
            portfolio   = new Portfolio();
        }

        @Test
        @DisplayName("Empty portfolio returns empty entries list")
        void emptyPortfolio() {
            List<RiskEntry> entries = riskService.analyseConcentration(portfolio);
            assertTrue(entries.isEmpty());
        }

        @Test
        @DisplayName("Single holding is always 100% — flagged as risk")
        void singleHoldingIsAlwaysRisk() {
            portfolio.addHolding(new Stock("AAPL", "Apple Inc.", BigDecimal.TEN,
                    new BigDecimal("150"), new BigDecimal("180")));
            List<RiskEntry> entries = riskService.analyseConcentration(portfolio);
            assertEquals(1, entries.size());
            assertTrue(entries.get(0).isConcentrationRisk(),
                    "A single holding at 100% should always be flagged");
        }

        @Test
        @DisplayName("Well-diversified portfolio (all < 40%) has no risk flags")
        void diversifiedPortfolio() {
            // Four equal holdings: each 25%
            String[] syms = {"AAPL", "MSFT", "GOOGL", "AMZN"};
            for (String sym : syms) {
                portfolio.addHolding(new Stock(sym, sym + " Corp", new BigDecimal("10"),
                        new BigDecimal("100"), new BigDecimal("100")));
            }
            List<RiskEntry> entries = riskService.analyseConcentration(portfolio);
            long riskCount = entries.stream().filter(RiskEntry::isConcentrationRisk).count();
            assertEquals(0, riskCount, "No holding should be flagged in a 4-way equal split");
        }

        @Test
        @DisplayName("Over-concentrated holding is flagged")
        void overConcentratedHolding() {
            // AAPL: current value = 9000, MSFT: current value = 1000 → AAPL = 90%
            portfolio.addHolding(new Stock("AAPL", "Apple Inc.", new BigDecimal("100"),
                    new BigDecimal("90"), new BigDecimal("90")));  // 9000
            portfolio.addHolding(new Stock("MSFT", "Microsoft", new BigDecimal("10"),
                    new BigDecimal("100"), new BigDecimal("100"))); // 1000
            List<RiskEntry> entries = riskService.analyseConcentration(portfolio);
            RiskEntry aaplEntry = entries.stream()
                    .filter(e -> "AAPL".equals(e.getSymbol()))
                    .findFirst()
                    .orElseThrow();
            assertTrue(aaplEntry.isConcentrationRisk());
        }

        @Test
        @DisplayName("hasConcentrationRisk returns true when any holding > threshold")
        void hasRisk() {
            portfolio.addHolding(new Stock("AAPL", "Apple", BigDecimal.TEN,
                    new BigDecimal("100"), new BigDecimal("1000"))); // dominates
            portfolio.addHolding(new Stock("MSFT", "Microsoft", BigDecimal.ONE,
                    new BigDecimal("100"), new BigDecimal("10")));
            assertTrue(riskService.hasConcentrationRisk(portfolio));
        }

        @Test
        @DisplayName("Entries are sorted: riskiest first")
        void sortedByRiskThenWeight() {
            // Two equal holdings — no risk
            portfolio.addHolding(new Stock("A", "Alpha", BigDecimal.TEN,
                    new BigDecimal("100"), new BigDecimal("1000"))); // 50%
            portfolio.addHolding(new Stock("B", "Beta", BigDecimal.TEN,
                    new BigDecimal("100"), new BigDecimal("1000"))); // 50%
            // Both are risks (> 40%), order by weight should be stable
            List<RiskEntry> entries = riskService.analyseConcentration(portfolio);
            assertTrue(entries.get(0).isConcentrationRisk());
        }
    }
}
