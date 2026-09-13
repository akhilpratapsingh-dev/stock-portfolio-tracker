package com.portfolio.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP client for fetching real-time stock prices from the Alpha Vantage API.
 *
 * <p>Uses the {@code GLOBAL_QUOTE} endpoint, which returns the latest price
 * for a given ticker symbol. The free tier allows 25 requests per day (as of
 * 2024); the scheduler respects this by refreshing infrequently.</p>
 *
 * <p>Failure modes handled gracefully:</p>
 * <ul>
 *   <li>Network unreachable → returns empty Optional; caller uses last-known price.</li>
 *   <li>HTTP non-200 status → same.</li>
 *   <li>Rate-limit response ({"Note":"..."}) → logged, returns empty Optional.</li>
 *   <li>Invalid / delisted symbol → returns empty Optional.</li>
 *   <li>Malformed JSON → returns empty Optional.</li>
 *   <li>Missing API key → throws {@link IllegalStateException} at construction time.</li>
 * </ul>
 *
 * <p>Thread safety: this class is stateless except for the shared
 * {@link HttpClient} and {@link ObjectMapper}, both of which are thread-safe
 * by design. Multiple threads may call {@link #fetchCurrentPrice} concurrently.</p>
 */
public class StockPriceClient {

    private static final Logger LOGGER = Logger.getLogger(StockPriceClient.class.getName());

    /** Alpha Vantage endpoint function. */
    private static final String FUNCTION = "GLOBAL_QUOTE";

    /** Connection and read timeout for each HTTP call. */
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    /**
     * Creates the client.
     *
     * @param apiKey  Alpha Vantage API key; must not be blank
     * @param baseUrl base URL (e.g. {@code https://www.alphavantage.co/query})
     * @throws IllegalArgumentException if {@code apiKey} is null or blank
     */
    public StockPriceClient(String apiKey, String baseUrl) {
        if (apiKey == null || apiKey.isBlank() || "YOUR_ALPHA_VANTAGE_API_KEY_HERE".equals(apiKey.trim())) {
            throw new IllegalArgumentException(
                "Alpha Vantage API key is missing or is still the placeholder value. " +
                "Set alpha.vantage.api.key in application.properties."
            );
        }
        this.apiKey = apiKey.trim();
        this.baseUrl = baseUrl != null ? baseUrl : "https://www.alphavantage.co/query";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Fetches the current trading price for a ticker symbol from Alpha Vantage.
     *
     * <p>On any error (network, rate limit, invalid symbol, bad JSON) the method
     * returns an empty {@link Optional} and logs a warning; it never throws.</p>
     *
     * @param symbol upper-case ticker symbol (e.g. {@code "AAPL"})
     * @return current price wrapped in Optional, or empty if unavailable
     */
    public Optional<BigDecimal> fetchCurrentPrice(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            LOGGER.warning("fetchCurrentPrice called with blank symbol.");
            return Optional.empty();
        }

        String url = buildUrl(symbol.toUpperCase());
        LOGGER.fine("Fetching price for " + symbol + " from: " + url);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                LOGGER.warning("Alpha Vantage returned HTTP " + response.statusCode()
                               + " for symbol " + symbol);
                return Optional.empty();
            }

            return parsePrice(symbol, response.body());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warning("Price fetch interrupted for symbol: " + symbol);
            return Optional.empty();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Network error fetching price for " + symbol + ": " + e.getMessage(), e);
            return Optional.empty();
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Parses the Alpha Vantage JSON response and extracts the price.
     *
     * <p>Expected response structure (abbreviated):</p>
     * <pre>{@code
     * {
     *   "Global Quote": {
     *     "01. symbol": "AAPL",
     *     "05. price": "182.6300",
     *     ...
     *   }
     * }
     * }</pre>
     *
     * @param symbol ticker (for logging)
     * @param body   raw JSON response body
     * @return price, or empty Optional on parse failure
     */
    private Optional<BigDecimal> parsePrice(String symbol, String body) {
        try {
            JsonNode root = objectMapper.readTree(body);

            // Check for rate-limit message
            if (root.has("Note")) {
                LOGGER.warning("Alpha Vantage rate limit reached. Message: " + root.get("Note").asText());
                return Optional.empty();
            }

            // Check for information message (API key issues, etc.)
            if (root.has("Information")) {
                LOGGER.warning("Alpha Vantage information message: " + root.get("Information").asText());
                return Optional.empty();
            }

            // Check for error message
            if (root.has("Error Message")) {
                LOGGER.warning("Alpha Vantage error for symbol " + symbol
                               + ": " + root.get("Error Message").asText());
                return Optional.empty();
            }

            JsonNode globalQuote = root.get("Global Quote");
            if (globalQuote == null || globalQuote.isEmpty()) {
                LOGGER.warning("Alpha Vantage returned empty 'Global Quote' for symbol: " + symbol
                               + ". The symbol may be invalid or delisted.");
                return Optional.empty();
            }

            JsonNode priceNode = globalQuote.get("05. price");
            if (priceNode == null || priceNode.isNull()) {
                LOGGER.warning("No '05. price' field in Global Quote for symbol: " + symbol);
                return Optional.empty();
            }

            String priceStr = priceNode.asText().trim();
            if (priceStr.isEmpty() || priceStr.equals("0.0000")) {
                LOGGER.warning("Price is zero or empty for symbol: " + symbol
                               + ". Market may be closed or symbol invalid.");
                // Return the value anyway; let the caller decide
            }

            BigDecimal price = new BigDecimal(priceStr);
            LOGGER.info("Fetched price for " + symbol + ": " + price);
            return Optional.of(price);

        } catch (NumberFormatException e) {
            LOGGER.log(Level.WARNING, "Could not parse price value for " + symbol, e);
            return Optional.empty();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to parse JSON response for " + symbol + ": " + e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Builds the full Alpha Vantage query URL.
     *
     * @param symbol upper-case ticker symbol
     * @return complete URL string
     */
    private String buildUrl(String symbol) {
        return baseUrl
               + "?function=" + FUNCTION
               + "&symbol=" + symbol
               + "&apikey=" + apiKey;
    }
}
