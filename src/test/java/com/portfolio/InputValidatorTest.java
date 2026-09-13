package com.portfolio;

import com.portfolio.util.InputValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InputValidator Tests")
class InputValidatorTest {

    @Nested
    @DisplayName("Symbol Validation")
    class SymbolValidationTests {

        @Test
        @DisplayName("Rejects null, empty, and whitespace-only symbol")
        void rejectsEmptySymbol() {
            assertNotNull(InputValidator.validateSymbol(null));
            assertNotNull(InputValidator.validateSymbol(""));
            assertNotNull(InputValidator.validateSymbol("   "));
        }

        @Test
        @DisplayName("Rejects purely numeric symbol like '3'")
        void rejectsNumericSymbol() {
            String error = InputValidator.validateSymbol("3");
            assertNotNull(error);
            assertTrue(error.contains("Purely numeric") || error.contains("Invalid ticker symbol"));

            assertNotNull(InputValidator.validateSymbol("12345"));
        }

        @Test
        @DisplayName("Rejects symbols longer than 5 letters")
        void rejectsLongSymbols() {
            assertNotNull(InputValidator.validateSymbol("TOOLONG"));
            assertNotNull(InputValidator.validateSymbol("ABCDEF"));
        }

        @Test
        @DisplayName("Rejects symbols containing punctuation or special characters")
        void rejectsSpecialCharacters() {
            assertNotNull(InputValidator.validateSymbol("AAP$"));
            assertNotNull(InputValidator.validateSymbol("A-PL"));
            assertNotNull(InputValidator.validateSymbol("AAPL."));
        }

        @Test
        @DisplayName("Accepts valid symbols and normalizes case and whitespace")
        void acceptsValidSymbols() {
            assertNull(InputValidator.validateSymbol("AAPL"));
            assertNull(InputValidator.validateSymbol("aapl"));
            assertNull(InputValidator.validateSymbol(" msft "));
            assertNull(InputValidator.validateSymbol("T"));
            assertNull(InputValidator.validateSymbol("GOOGL"));

            assertEquals("AAPL", InputValidator.cleanSymbol("aapl"));
            assertEquals("MSFT", InputValidator.cleanSymbol("  msft  "));
        }
    }

    @Nested
    @DisplayName("Company Name Validation")
    class CompanyNameValidationTests {

        @Test
        @DisplayName("Rejects null, empty, and whitespace company name")
        void rejectsEmptyName() {
            assertNotNull(InputValidator.validateCompanyName(null));
            assertNotNull(InputValidator.validateCompanyName(""));
            assertNotNull(InputValidator.validateCompanyName("   "));
        }

        @Test
        @DisplayName("Rejects company name exceeding 100 characters")
        void rejectsExcessiveLength() {
            String longName = "A".repeat(101);
            assertNotNull(InputValidator.validateCompanyName(longName));
        }

        @Test
        @DisplayName("Accepts valid names and formats to Title Case")
        void formatsToTitleCase() {
            assertNull(InputValidator.validateCompanyName("appl"));
            assertNull(InputValidator.validateCompanyName("apple inc."));

            assertEquals("Appl", InputValidator.formatCompanyName("appl"));
            assertEquals("Apple Inc.", InputValidator.formatCompanyName("apple inc."));
            assertEquals("Microsoft Corporation", InputValidator.formatCompanyName("MICROSOFT CORPORATION"));
            assertEquals("Alphabet Inc.", InputValidator.formatCompanyName("  alphabet   inc.  "));
        }
    }

    @Nested
    @DisplayName("Quantity Validation")
    class QuantityValidationTests {

        @Test
        @DisplayName("Rejects null, blank, and non-numeric inputs")
        void rejectsInvalidInputs() {
            assertNotNull(InputValidator.validateQuantity(null));
            assertNotNull(InputValidator.validateQuantity(""));
            assertNotNull(InputValidator.validateQuantity("   "));
            assertNotNull(InputValidator.validateQuantity("abc"));
            assertNotNull(InputValidator.validateQuantity("10shares"));
        }

        @Test
        @DisplayName("Rejects zero and negative quantities")
        void rejectsZeroAndNegative() {
            assertNotNull(InputValidator.validateQuantity("0"));
            assertNotNull(InputValidator.validateQuantity("-5"));
            assertNotNull(InputValidator.validateQuantity("-0.01"));
        }

        @Test
        @DisplayName("Accepts valid positive integers and decimals")
        void acceptsValidQuantities() {
            assertNull(InputValidator.validateQuantity("10"));
            assertNull(InputValidator.validateQuantity("2.5"));
            assertNull(InputValidator.validateQuantity("100.1234"));

            assertEquals(new BigDecimal("10"), InputValidator.parseQuantity("10"));
            assertEquals(new BigDecimal("2.5"), InputValidator.parseQuantity("2.5"));
        }
    }

    @Nested
    @DisplayName("Buy Price Validation")
    class BuyPriceValidationTests {

        @Test
        @DisplayName("Rejects null, blank, and non-numeric inputs")
        void rejectsInvalidInputs() {
            assertNotNull(InputValidator.validateBuyPrice(null));
            assertNotNull(InputValidator.validateBuyPrice(""));
            assertNotNull(InputValidator.validateBuyPrice("   "));
            assertNotNull(InputValidator.validateBuyPrice("price"));
            assertNotNull(InputValidator.validateBuyPrice("$150"));
        }

        @Test
        @DisplayName("Rejects zero and negative prices")
        void rejectsZeroAndNegative() {
            assertNotNull(InputValidator.validateBuyPrice("0"));
            assertNotNull(InputValidator.validateBuyPrice("-150.00"));
        }

        @Test
        @DisplayName("Accepts valid positive prices and parses to BigDecimal")
        void acceptsValidPrices() {
            assertNull(InputValidator.validateBuyPrice("150.00"));
            assertNull(InputValidator.validateBuyPrice("0.01"));
            assertNull(InputValidator.validateBuyPrice("3200.50"));

            assertEquals(new BigDecimal("150.00"), InputValidator.parseBuyPrice("150.00"));
        }
    }
}
