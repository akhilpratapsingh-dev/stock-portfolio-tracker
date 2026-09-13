package com.portfolio.service;

import com.portfolio.model.Portfolio;
import com.portfolio.model.Stock;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

/**
 * Performs concentration and risk analysis on the portfolio.
 *
 * <p>The primary concern addressed here is <em>concentration risk</em>: when
 * a single holding represents too large a fraction of the total portfolio, the
 * investor is over-exposed to that stock's volatility. A widely used rule of
 * thumb is to flag any position above 40% of total current value.</p>
 *
 * <p>This service is stateless — it reads a snapshot from the portfolio on
 * each call and returns computed results without mutating any shared state.
 * This makes it trivially thread-safe.</p>
 */
public class RiskAnalysisService {

    private static final Logger LOGGER = Logger.getLogger(RiskAnalysisService.class.getName());

    /** Threshold above which a holding is considered over-concentrated. */
    private final BigDecimal concentrationRiskThreshold;

    /**
     * Constructs the risk analysis service.
     *
     * @param concentrationRiskThreshold percentage (e.g. 40.0) above which
     *        a single holding is flagged as "⚠ CONCENTRATION RISK"
     */
    public RiskAnalysisService(BigDecimal concentrationRiskThreshold) {
        this.concentrationRiskThreshold = concentrationRiskThreshold;
    }

    // -----------------------------------------------------------------------
    // Analysis methods
    // -----------------------------------------------------------------------

    /**
     * Analyses all holdings in the portfolio and returns a list of
     * {@link RiskEntry} objects describing each holding's concentration.
     *
     * <p>Results are sorted by weight (highest first) for display clarity.</p>
     *
     * @param portfolio the live portfolio to analyse
     * @return list of risk entries, sorted by weight descending
     */
    public List<RiskEntry> analyseConcentration(Portfolio portfolio) {
        List<Stock> holdings = portfolio.getAllHoldings();
        BigDecimal totalValue = portfolio.getTotalCurrentValue();
        List<RiskEntry> entries = new ArrayList<>();

        if (totalValue.compareTo(BigDecimal.ZERO) == 0) {
            LOGGER.info("Portfolio total value is zero; skipping concentration analysis.");
            return entries;
        }

        for (Stock stock : holdings) {
            BigDecimal weight = portfolio.getConcentrationPercent(stock.getSymbol());
            boolean isRisk = weight.compareTo(concentrationRiskThreshold) >= 0;
            entries.add(new RiskEntry(stock.getSymbol(), stock.getName(),
                                      stock.getCurrentValue(), weight, isRisk));
        }

        // Sort: riskiest first, then by weight descending
        entries.sort(Comparator.comparing(RiskEntry::isConcentrationRisk).reversed()
                               .thenComparing(Comparator.comparing(RiskEntry::getWeightPercent).reversed()));

        return entries;
    }

    /**
     * Returns {@code true} if any holding exceeds the concentration risk threshold.
     *
     * @param portfolio the portfolio to check
     * @return {@code true} if at least one holding is over-concentrated
     */
    public boolean hasConcentrationRisk(Portfolio portfolio) {
        return analyseConcentration(portfolio).stream()
                .anyMatch(RiskEntry::isConcentrationRisk);
    }

    /**
     * Returns the configured concentration risk threshold.
     *
     * @return threshold percentage
     */
    public BigDecimal getConcentrationRiskThreshold() {
        return concentrationRiskThreshold;
    }

    // -----------------------------------------------------------------------
    // Inner class: RiskEntry
    // -----------------------------------------------------------------------

    /**
     * Value object describing a single holding's risk metrics.
     */
    public static class RiskEntry {

        private final String symbol;
        private final String name;
        private final BigDecimal currentValue;
        private final BigDecimal weightPercent;
        private final boolean concentrationRisk;

        /**
         * Constructs a risk entry.
         *
         * @param symbol            ticker symbol
         * @param name              company name
         * @param currentValue      current market value of the holding
         * @param weightPercent     percentage of total portfolio current value
         * @param concentrationRisk {@code true} if weight exceeds threshold
         */
        public RiskEntry(String symbol, String name, BigDecimal currentValue,
                         BigDecimal weightPercent, boolean concentrationRisk) {
            this.symbol            = symbol;
            this.name              = name;
            this.currentValue      = currentValue;
            this.weightPercent     = weightPercent;
            this.concentrationRisk = concentrationRisk;
        }

        /** @return ticker symbol */
        public String getSymbol() { return symbol; }

        /** @return company name */
        public String getName() { return name; }

        /** @return current market value of this holding */
        public BigDecimal getCurrentValue() { return currentValue; }

        /** @return this holding's percentage of total portfolio value */
        public BigDecimal getWeightPercent() { return weightPercent; }

        /** @return {@code true} if this holding exceeds the concentration threshold */
        public boolean isConcentrationRisk() { return concentrationRisk; }

        @Override
        public String toString() {
            return "RiskEntry{symbol='" + symbol + "', weight=" + weightPercent
                   + "%, risk=" + concentrationRisk + '}';
        }
    }
}
