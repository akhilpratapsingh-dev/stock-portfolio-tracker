package com.portfolio.util;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Utility class providing validation and normalization methods for user inputs
 * in the Stock Portfolio Tracker application.
 */
public final class InputValidator {

    /**
     * Regex pattern for a valid stock ticker symbol:
     * Only letters (A-Z), length between 1 and 5 characters.
     */
    private static final Pattern SYMBOL_PATTERN = Pattern.compile("^[A-Z]{1,5}$");

    /**
     * Maximum allowed length for a company name.
     */
    public static final int MAX_COMPANY_NAME_LENGTH = 100;

    private InputValidator() {
        // Utility class; prevent instantiation
    }

    // -----------------------------------------------------------------------
    // Validation Methods
    // -----------------------------------------------------------------------

    /**
     * Validates a stock ticker symbol.
     *
     * <p>Rules:</p>
     * <ul>
     *   <li>Must not be null or blank.</li>
     *   <li>Trimmed and converted to upper-case before checking format.</li>
     *   <li>Must contain only alphabetic characters (A-Z), 1 to 5 letters.</li>
     *   <li>Purely numeric inputs (e.g. "3") are rejected.</li>
     * </ul>
     *
     * @param symbol raw symbol input string
     * @return an error message describing the problem, or {@code null} if valid
     */
    public static String validateSymbol(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return "Ticker symbol cannot be empty.";
        }
        String cleaned = cleanSymbol(symbol);
        if (cleaned.matches("^\\d+$")) {
            return "Invalid ticker symbol '" + symbol.trim() + "'. Purely numeric symbols are not allowed.";
        }
        if (!SYMBOL_PATTERN.matcher(cleaned).matches()) {
            return "Invalid ticker symbol '" + symbol.trim() + "'. Symbol must contain only letters (1 to 5 characters), e.g. AAPL, MSFT, TSLA.";
        }
        return null;
    }

    /**
     * Validates a company name.
     *
     * <p>Rules:</p>
     * <ul>
     *   <li>Must not be null or blank.</li>
     *   <li>Trimmed before checking.</li>
     *   <li>Must not exceed 100 characters.</li>
     * </ul>
     *
     * @param name raw company name input
     * @return an error message describing the problem, or {@code null} if valid
     */
    public static String validateCompanyName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "Company name cannot be empty.";
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_COMPANY_NAME_LENGTH) {
            return "Company name cannot exceed " + MAX_COMPANY_NAME_LENGTH + " characters (entered " + trimmed.length() + ").";
        }
        return null;
    }

    /**
     * Validates a quantity input string.
     *
     * <p>Rules:</p>
     * <ul>
     *   <li>Must not be null or blank.</li>
     *   <li>Must parse as a valid positive integer or decimal greater than 0.</li>
     *   <li>Rejects negative, zero, and non-numeric inputs.</li>
     * </ul>
     *
     * @param input raw quantity input string
     * @return an error message describing the problem, or {@code null} if valid
     */
    public static String validateQuantity(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "Quantity cannot be empty.";
        }
        try {
            BigDecimal qty = new BigDecimal(input.trim());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                return "Quantity must be greater than zero.";
            }
            return null;
        } catch (NumberFormatException e) {
            return "Invalid quantity '" + input.trim() + "'. Please enter a positive number (e.g. 10 or 2.5).";
        }
    }

    /**
     * Validates a buy price per share input string.
     *
     * <p>Rules:</p>
     * <ul>
     *   <li>Must not be null or blank.</li>
     *   <li>Must parse as a valid positive decimal greater than 0 using {@link BigDecimal}.</li>
     *   <li>Rejects negative, zero, and non-numeric inputs.</li>
     * </ul>
     *
     * @param input raw buy price input string
     * @return an error message describing the problem, or {@code null} if valid
     */
    public static String validateBuyPrice(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "Buy price cannot be empty.";
        }
        try {
            BigDecimal price = new BigDecimal(input.trim());
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                return "Buy price must be greater than zero.";
            }
            return null;
        } catch (NumberFormatException e) {
            return "Invalid buy price '" + input.trim() + "'. Please enter a valid positive decimal amount (e.g. 150.00).";
        }
    }

    // -----------------------------------------------------------------------
    // Cleaning & Normalization Helpers
    // -----------------------------------------------------------------------

    /**
     * Cleans and normalizes a ticker symbol by trimming whitespace and converting to uppercase.
     *
     * @param symbol raw symbol
     * @return trimmed, upper-case symbol (or empty string if null)
     */
    public static String cleanSymbol(String symbol) {
        return (symbol == null) ? "" : symbol.trim().toUpperCase();
    }

    /**
     * Normalizes a company name into Title Case (capitalizing the first letter of each word).
     * E.g. "appl" -> "Appl", "apple inc." -> "Apple Inc.", "MICROSOFT CORP" -> "Microsoft Corp".
     *
     * @param name raw company name
     * @return Title-cased company name (or empty string if null/blank)
     */
    public static String formatCompanyName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String[] words = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (!word.isEmpty()) {
                if (i > 0) {
                    sb.append(" ");
                }
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    sb.append(word.substring(1).toLowerCase());
                }
            }
        }
        return sb.toString();
    }

    /**
     * Parses a validated quantity string into a {@link BigDecimal}.
     *
     * @param input quantity input
     * @return parsed BigDecimal
     */
    public static BigDecimal parseQuantity(String input) {
        return new BigDecimal(input.trim());
    }

    /**
     * Parses a validated buy price string into a {@link BigDecimal}.
     *
     * @param input buy price input
     * @return parsed BigDecimal
     */
    public static BigDecimal parseBuyPrice(String input) {
        return new BigDecimal(input.trim());
    }

    // -----------------------------------------------------------------------
    // Optional ValidationResult Wrapper
    // -----------------------------------------------------------------------

    /**
     * Generic validation result holder containing validity flag, error message, and cleaned value.
     *
     * @param <T> type of the parsed/cleaned value
     */
    public static final class ValidationResult<T> {
        private final boolean valid;
        private final String errorMessage;
        private final T value;

        public ValidationResult(boolean valid, String errorMessage, T value) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.value = value;
        }

        public static <T> ValidationResult<T> ok(T value) {
            return new ValidationResult<>(true, null, value);
        }

        public static <T> ValidationResult<T> error(String errorMessage) {
            return new ValidationResult<>(false, errorMessage, null);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public T getValue() {
            return value;
        }
    }

    public static ValidationResult<String> validateAndCleanSymbol(String symbol) {
        String err = validateSymbol(symbol);
        return (err == null) ? ValidationResult.ok(cleanSymbol(symbol)) : ValidationResult.error(err);
    }

    public static ValidationResult<String> validateAndFormatCompanyName(String name) {
        String err = validateCompanyName(name);
        return (err == null) ? ValidationResult.ok(formatCompanyName(name)) : ValidationResult.error(err);
    }

    public static ValidationResult<BigDecimal> validateAndParseQuantity(String input) {
        String err = validateQuantity(input);
        return (err == null) ? ValidationResult.ok(parseQuantity(input)) : ValidationResult.error(err);
    }

    public static ValidationResult<BigDecimal> validateAndParseBuyPrice(String input) {
        String err = validateBuyPrice(input);
        return (err == null) ? ValidationResult.ok(parseBuyPrice(input)) : ValidationResult.error(err);
    }
}
