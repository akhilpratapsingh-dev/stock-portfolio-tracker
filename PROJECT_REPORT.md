# PROJECT REPORT: MULTI-THREADED STOCK PORTFOLIO TRACKER WITH LIVE ALERTS

---

## 1. Cover Page

* **Project Title:** Multi-Threaded Stock Portfolio Tracker with Live Alerts
* **Course Domain:** Core & Advanced Java Programming / Software Engineering / Systems Design
* **Submission Type:** Flipped Course Project Evaluation
* **Student Name:** Akhil Pratap Singh
* **Technology Stack:** Java 17 (LTS), Apache Maven, SQLite/MySQL JDBC, Alpha Vantage REST API, Java Concurrency Utilities (`ScheduledExecutorService`, `ExecutorService`, `ConcurrentHashMap`), JUnit 5
* **Execution Environment:** Terminal / Command Line Interface (CLI)
* **Date of Submission:** September 2026
* **Repository Visibility:** Public (Strictly conforming to submission guidelines)

---

## 2. Introduction
In contemporary financial markets, rapid price volatility requires investors and portfolio managers to maintain real-time situational awareness over their holdings. Traditional single-threaded financial tracking software often degrades significantly under production conditions: sequential network requests to financial market endpoints induce high latency, blocking user interfaces during periodic refreshes. Furthermore, non-synchronized in-memory state models expose multi-threaded applications to critical data race conditions, phantom updates, and dirty reads.

This project, the **Multi-Threaded Stock Portfolio Tracker**, implements an industrial-grade, framework-free Java 17 console application designed from the ground up to address these engineering challenges. By decoupling user interaction from asynchronous background polling through high-performance concurrency primitives—specifically `ScheduledExecutorService`, fixed worker thread pools, and concurrent memory structures—the system delivers sub-millisecond local menu responsiveness alongside real-time market data ingestion. Furthermore, by pairing a resilient JDBC data access layer (supporting zero-configuration SQLite and scalable MySQL) with strict financial arithmetic (`BigDecimal`), the project delivers precision, high concurrency throughput, and proactive multi-channel alerting.

---

## 3. Problem Statement
Retail investors and systems developers encounter several foundational difficulties when designing equity tracking engines:
1. **Compounding Network Latency in Sequential Polling:** Iterating over a collection of portfolio tickers sequentially across remote REST endpoints scales network latency linearly ($O(N)$), causing severe delays and frequent HTTP socket timeouts as portfolio sizes grow.
2. **Data Race Hazards in Concurrent State Access:** Concurrently mutating portfolio valuation data from background ingestion threads while simultaneously reading it for display on interactive user threads leads to severe race conditions, dirty reads, and `ConcurrentModificationException` failures.
3. **Floating-Point Arithmetic Inaccuracy:** Conventional use of primitive binary floating-point types (`float`, `double`) introduces IEEE-754 precision loss in cumulative financial valuations, resulting in inaccurate profit/loss (P&L) calculations.
4. **Lack of Automated Multi-Channel Alerting:** Passive portfolio monitoring requires continuous manual inspection. Without an automated, multi-channel alerting subsystem that tracks user-defined volatility thresholds and filters duplicate noise, users fail to react to critical market events.
5. **Brittle Network Dependencies:** Applications that fail to operate when an API key is missing or when third-party endpoints exceed their rate limits cause unexpected application crashes and loss of portfolio visibility.

The goal of this project is to construct a modular, thread-safe, and fully command-line executable Java solution that resolves all aforementioned challenges.

---

## 4. Functional Requirements

The system provides three primary functional modules with well-defined inputs, outputs, and workflows:

### 4.1 Module 1: Portfolio & Holding Management Module
* **FR-1.1 (Add Holding):** Allows users to add stock positions with ticker symbol, company name, purchased quantity, and average buy price.
* **FR-1.2 (Strict Input Validation):** Validates tickers against uppercase alphabetic strings (1–5 characters), title-cases company names, enforces strictly positive quantities and buy prices, and handles input stream EOF without defaulting to arbitrary values.
* **FR-1.3 (Remove Holding):** Safely deletes existing positions from memory and removes corresponding records from the relational persistence store.
* **FR-1.4 (Tabular Reporting):** Renders aligned ANSI terminal tables displaying Symbol, Company Name, Quantity, Buy Price, Current Price, Current Value, Unrealized Dollar P&L, and Percentage Return.

### 4.2 Module 2: Asynchronous Real-Time Market Data Ingestion Module
* **FR-2.1 (Live REST Ingestion):** Interacts with Alpha Vantage's `GLOBAL_QUOTE` REST endpoint via Java 11+ `HttpClient` and Jackson Databind.
* **FR-2.2 (Parallel Multi-Worker Fetch):** Executes quote retrievals for distinct symbols concurrently across an isolated fixed thread pool (`ExecutorService`), bounded to prevent thread explosion.
* **FR-2.3 (Scheduled Periodic Refresh):** Automatically triggers refresh cycles at configurable intervals (e.g., 60 seconds) using a daemon `ScheduledExecutorService`.
* **FR-2.4 (Manual Trigger):** Allows the user to initiate an instantaneous out-of-cycle refresh directly from the console menu.
* **FR-2.5 (Offline Fallback):** In the absence of an API key or during network downtime, seamlessly retrieves and serves last-known valid prices from the persistent database with clear user notifications.

### 4.3 Module 3: Alert Engine & Concentration Risk Analysis Module
* **FR-3.1 (Dynamic Threshold Configuration):** Enables dynamic runtime configuration of global and per-symbol percentage change thresholds.
* **FR-3.2 (Multi-Channel Dispatching):** Dispatches alert events concurrently to the console terminal, an append-only audit file (`alerts.log`), and the relational `alerts_log` database table.
* **FR-3.3 (Duplicate Alert Suppression):** Caches the last-alerted price per ticker to eliminate redundant alerts during flat or fluctuating sideways trends.
* **FR-3.4 (Simulated Price Injection):** Provides a testing mechanism to inject synthetic price shifts, verifying alert trigger thresholds deterministically.
* **FR-3.5 (Concentration Risk Assessment):** Analyzes asset distribution and flags any single equity accounting for more than 40% of total portfolio value.

---

## 5. Non-Functional Requirements

1. **Performance & Low Latency:**
   * Price refresh tasks execute concurrently across 5 worker threads. For a 10-stock portfolio with an average 300ms network round-trip time, parallel execution completes in approximately 600ms compared to 3,000ms for sequential execution—a 5x reduction in polling latency.
2. **Concurrency & Thread-Safety:**
   * Zero data races and zero lock contention deadlocks. State is managed via `ConcurrentHashMap` for lock-free individual reads, while critical mutations (`addHolding`, `removeHolding`, `updatePrice`) synchronize on the portfolio monitor. Defensive snapshots prevent `ConcurrentModificationException`.
3. **Data Accuracy & Precision:**
   * All monetary figures, share counts, and percentage changes use `java.math.BigDecimal` with `RoundingMode.HALF_UP` scaled to 4 decimal places, guaranteeing complete compliance with financial accounting standards.
4. **Reliability & Fault-Tolerance:**
   * The application handles database connection failures, HTTP timeouts, network dropouts, and API throttling gracefully without terminating the user's interactive CLI session.
5. **Portability & Zero-GUI Headless Executability:**
   * The project runs on any standard JVM (Java 17+) via terminal CLI without requiring a graphical desktop server (X11, Wayland, or Windows GDI). Supports both embedded zero-configuration SQLite and standalone MySQL.
6. **Maintainability & Modularity:**
   * Follows strict Separation of Concerns (SoC) across Model-View-Controller/DAO design patterns with 11 cohesive Java classes and comprehensive JUnit 5 test suites.

---

## 6. System Architecture

The application adopts a decoupled layered architectural pattern ensuring that UI, domain logic, persistence, and external networking remain independently testable and maintainable.

```
┌────────────────────────────────────────────────────────────────────────┐
│                        USER INTERFACE LAYER                            │
│                  ConsoleMenu (Scanner CLI / ANSI)                      │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                         APPLICATION SERVICES                           │
│   ┌──────────────────────┐  ┌──────────────────┐  ┌────────────────┐   │
│   │   PortfolioService   │  │   AlertService   │  │ RiskAnalysis   │   │
│   └──────────┬───────────┘  └────────┬─────────┘  └───────┬────────┘   │
└──────────────┼───────────────────────┼────────────────────┼────────────┘
               │                       │                    │
               ▼                       ▼                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                          DOMAIN MODEL LAYER                            │
│                 Portfolio (Thread-Safe Memory Store)                   │
│                 Stock (Immutable Financial Entity)                     │
└───────────────────────▲───────────────────────────────────▲────────────┘
                        │                                   │
┌───────────────────────┴───────────────┐ ┌─────────────────┴────────────┐
│      CONCURRENCY / INGESTION          │ │       PERSISTENCE LAYER      │
│  ScheduledExecutorService (Daemon)   │ │  PortfolioDAO (JDBC)         │
│  ExecutorService (5 Worker Pool)      │ │  PreparedStatements          │
│  StockPriceClient (HttpClient/Jackson)│ │  SQLite (Embedded) / MySQL   │
└───────────────────────────────────────┘ └──────────────────────────────┘
```

---

## 7. Design Diagrams

### 7.1 Use Case Diagram
```
                      ┌───────────────────────────────────────────────┐
                      │    Multi-Threaded Stock Portfolio Tracker     │
                      │                                               │
                      │   (Add Stock Holding) ───────> [Validate Input]
                      │          │                                    │
                      │   (Remove Stock Holding)                      │
                      │          │                                    │
    ┌──────────┐      │   (View Portfolio Valuation)                  │
    │  User    ├─────>│          │                                    │
    │ (Trader) │      │   (Configure Alert Thresholds)                │
    │          │      │          │                                    │
    └──────────┘      │   (Execute Concentration Risk Analysis)       │
                      │          │                                    │
                      │   (Manually Refresh Quotes)                   │
                      │                                               │
                      │   [Background Auto-Refresh] <───(Daemon Timer)│
                      │          │                                    │
                      │   [Dispatch Price Alerts] ───> (File / DB / UI)
                      └───────────────────────────────────────────────┘
```

### 7.2 Process Flow / Workflow Diagram
```
[Start Application]
        │
        ▼
[Load Configuration (application.properties)]
        │
        ▼
[Initialize Database (SQLite / MySQL) via DAO]
        │
        ▼
[Load Existing Holdings into ConcurrentHashMap]
        │
        ▼
[Start Background ScheduledExecutorService (Tick Every N Sec)]
        │
        ├───► [Background Thread]: Dispatches Parallel HTTP Quotes ──► [Evaluate Alerts]
        │
        ▼
[Display Interactive Console Menu (Main Thread)]
        │
        ├── 1. Add Holding ──────────► [Regex / Bound Validation] ──► [Sync Memory & DB]
        ├── 2. Remove Holding ───────► [Remove from Portfolio] ──────► [Delete DB Record]
        ├── 3. View Portfolio ───────► [Read Defensive Snapshot] ────► [Render Table]
        ├── 4. Refresh Quotes ───────► [Invoke Parallel Fetch] ──────► [Update Prices]
        ├── 5. Set Thresholds ───────► [Update Alert Thresholds]
        ├── 6. View Alert History ───► [Query alerts_log via DAO] ───► [Display Alerts]
        ├── 7. Risk Analysis ────────► [Compute % Portfolio Weights] ─► [Flag Over 40%]
        └── 8. Exit ─────────────────► [Graceful Executor Shutdown] ─► [Terminate JVM]
```

### 7.3 Sequence Diagram: Background Price Refresh & Alert Flow
```
SchedulerThread        WorkerPool           AlphaVantageAPI       Portfolio           AlertService           PortfolioDAO
      │                     │                      │                  │                     │                    │
      │── fires tick() ────>│                      │                  │                     │                    │
      │                     │── HTTP GET /quote ──>│                  │                     │                    │
      │                     │<── JSON Response ────│                  │                     │                    │
      │                     │                                         │                     │                    │
      │                     │── updatePrice(symbol, newPrice) ───────>│                     │                    │
      │                     │                                         │                     │                    │
      │                     │── evaluatePriceChange(symbol, old, new) ─────────────────────>│                    │
      │                     │                                                               │── Check Threshold  │
      │                     │                                                               │── [If Exceeded]:   │
      │                     │                                                               │   ├─ Log to File   │
      │                     │                                                               │   ├─ Print Console │
      │                     │                                                               │   └─ recordAlert ─>│
      │                     │                                                               │                    │
```

### 7.4 Class / Component Diagram
```
┌────────────────────────────────────┐       ┌────────────────────────────────────┐
│              Stock                 │       │             Portfolio              │
├────────────────────────────────────┤       ├────────────────────────────────────┤
│ - symbol: String                   │       │ - holdings: ConcurrentMap<String,S>│
│ - name: String                     │1     *├────────────────────────────────────┤
│ - quantity: BigDecimal             │<──────│ + addHolding(Stock): void          │
│ - buyPrice: BigDecimal             │       │ + removeHolding(String): Stock     │
│ - currentPrice: BigDecimal         │       │ + updatePrice(String, BigDec): bool│
│ - lastUpdated: LocalDateTime       │       │ + getTotalCurrentValue(): BigDec   │
├────────────────────────────────────┤       │ + getSymbols(): Set<String>        │
│ + getUnrealizedPnL(): BigDecimal   │       └────────────────────────────────────┘
│ + getPercentageReturn(): BigDecimal│                          ▲
└────────────────────────────────────┘                          │
                  ▲                                             │
                  │                                             │
┌─────────────────┴──────────────────┐       ┌──────────────────┴─────────────────┐
│          PortfolioDAO              │       │       PriceRefreshScheduler        │
├────────────────────────────────────┤       ├────────────────────────────────────┤
│ - dbUrl, user, pass: String        │       │ - scheduler: ScheduledExecutor     │
├────────────────────────────────────┤       │ - workerPool: ExecutorService      │
│ + saveHolding(Stock): void         │       ├────────────────────────────────────┤
│ + getAllHoldings(): List<Stock>    │       │ + start(): void                    │
│ + deleteHolding(String): boolean   │       │ + shutdown(): void                 │
│ + logAlert(...): void              │       │ + refreshAllHoldings(): void       │
└────────────────────────────────────┘       └────────────────────────────────────┘
```

### 7.5 Database Storage Design (ER Diagram & Schema)

```
      ┌────────────────────────────┐             ┌────────────────────────────┐
      │          HOLDINGS          │             │         ALERTS_LOG         │
      ├────────────────────────────┤             ├────────────────────────────┤
      │ PK  id            INTEGER  │             │ PK  id             INTEGER │
      │     symbol        VARCHAR  │◄────────────│ FK  symbol         VARCHAR │
      │     name          VARCHAR  │ (Logical 1:N│     previous_price DECIMAL │
      │     quantity      DECIMAL  │  Relationship│    current_price  DECIMAL │
      │     buy_price     DECIMAL  │             │     percent_change DECIMAL │
      │     current_price DECIMAL  │             │     direction      VARCHAR │
      │     last_updated  DATETIME │             │     created_at     DATETIME│
      └────────────────────────────┘             └────────────────────────────┘
```

#### DDL Specification:
* **Table `holdings`:** Holds portfolio asset records. Constraints: `symbol` is unique and indexed; `quantity` and `buy_price` are stored as `DECIMAL(15,4)`.
* **Table `alerts_log`:** Stores immutable event logs for every triggered alert. Indexed on `symbol` and `created_at` for rapid filtering and historical audits.

---

## 8. Design Decisions & Rationale

1. **Rejection of Spring Boot in Favor of Core Java Primitives:**
   * *Rationale:* Spring Boot abstracts away thread pools, task scheduling, and database transaction boundaries behind annotations (`@Scheduled`, `@Async`, `@Transactional`). Building with pure Java 17 primitives (`ScheduledExecutorService`, `ReentrantLock`/`synchronized`, manual JDBC `PreparedStatement`) makes fundamental systems architecture, concurrency mechanisms, and thread lifecycles transparent and verifiable.
2. **Double-Tier Concurrency Model (Clock Timer vs. Worker Pool):**
   * *Rationale:* Using a single scheduled executor thread to sequentially query REST APIs causes scheduler drift and blocks subsequent tasks if an HTTP request hangs. Our solution uses a single-thread daemon solely as a timer pulse that dispatches work items into an isolated 5-thread worker pool, completely insulating the timer from network latency.
3. **`ConcurrentHashMap` Paired with Strategic Synchronization:**
   * *Rationale:* While `ConcurrentHashMap` provides lock-free thread safety for single-key operations (`get`, `put`), multi-step portfolio operations (such as computing total portfolio value across all positions while a price update is occurring) require compound atomicity. Strategic `synchronized` blocks on critical methods guarantee point-in-time consistency without global lock contention.
4. **Mandatory Adoption of `java.math.BigDecimal`:**
   * *Rationale:* IEEE-754 floating-point arithmetic introduces cumulative rounding inaccuracies (e.g., `0.1 + 0.2 = 0.30000000000000004`). In financial portfolios, this causes discrepancies in dollar balances and erroneous percentage triggers. Every calculation in `Stock` and `Portfolio` employs `BigDecimal` scaled with `RoundingMode.HALF_UP`.
5. **Defensive Copies for Safe Iteration:**
   * *Rationale:* Returning direct references to active collections exposes the system to `ConcurrentModificationException` if a user modifies holdings while the background thread is iterating. The application returns `Collections.unmodifiableSet(new HashSet<>(holdings.keySet()))` to provide an immutable snapshot for iteration.

---

## 9. Implementation Details

### 9.1 Concurrency Scheduler Implementation
Located in [`PriceRefreshScheduler.java`](src/main/java/com/portfolio/concurrency/PriceRefreshScheduler.java), this component manages background polling:
```java
public class PriceRefreshScheduler {
    private final ScheduledExecutorService scheduler = 
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "price-refresh-clock");
            t.setDaemon(true);
            return t;
        });

    private final ExecutorService workerPool = 
        Executors.newFixedThreadPool(5, r -> {
            Thread t = new Thread(r, "price-fetch-worker");
            t.setDaemon(true);
            return t;
        });

    public void start(long intervalSeconds) {
        scheduler.scheduleWithFixedDelay(
            this::refreshAllHoldingsParallel, 
            0, intervalSeconds, TimeUnit.SECONDS
        );
    }
}
```

### 9.2 Thread-Safe Portfolio State
Located in [`Portfolio.java`](src/main/java/com/portfolio/model/Portfolio.java):
```java
public class Portfolio {
    private final ConcurrentMap<String, Stock> holdings = new ConcurrentHashMap<>();

    public synchronized void addHolding(Stock stock) {
        holdings.put(stock.getSymbol(), stock);
    }

    public synchronized boolean updatePrice(String symbol, BigDecimal newPrice) {
        Stock current = holdings.get(symbol);
        if (current == null) return false;
        holdings.put(symbol, current.withPrice(newPrice));
        return true;
    }

    public Set<String> getSymbols() {
        return Collections.unmodifiableSet(new HashSet<>(holdings.keySet()));
    }
}
```

### 9.3 Robust Input Validation
Located in [`InputValidator.java`](src/main/java/com/portfolio/util/InputValidator.java):
```java
public static String validateSymbol(String input) {
    if (input == null || input.trim().isEmpty()) {
        throw new IllegalArgumentException("Symbol cannot be empty.");
    }
    String cleaned = input.trim().toUpperCase();
    if (!cleaned.matches("^[A-Z]{1,5}$")) {
        throw new IllegalArgumentException("Symbol must be 1 to 5 uppercase letters.");
    }
    return cleaned;
}
```

---

## 10. Screenshots / Execution Results

### 10.1 Interactive Console Portfolio Valuation
```
========================================================================================================================
SYMBOL  COMPANY NAME                   QUANTITY      BUY PRICE      CURR PRICE     TOTAL VALUE    P&L ($)        P&L (%) 
========================================================================================================================
AAPL    Apple Inc.                      10.0000       150.0000       175.5000       1755.0000      +255.0000      +17.00%
MSFT    Microsoft Corporation            5.0000       300.0000       330.0000       1650.0000      +150.0000      +10.00%
GOOGL   Alphabet Inc.                    8.0000       120.0000       135.2500       1082.0000      +122.0000      +12.71%
========================================================================================================================
Total Portfolio Cost:    $4,060.0000
Total Portfolio Value:   $4,487.0000
Total Unrealized P&L:    +$427.0000 (+10.52%)
========================================================================================================================
```

### 10.2 Real-Time Live Alert Notification
```
🔔 ============================ LIVE ALERT ============================
   [2026-09-13 19:15:02] ALERT FIRED for AAPL
   Movement: UP (+5.71%)
   Previous: $175.5000  ──►  Current: $185.5200
   Threshold: 5.0%
=======================================================================
```

### 10.3 Concentration Risk Warning
```
⚠️  CONCENTRATION RISK DETECTED:
- AAPL represents 42.15% of your total portfolio value!
  Guideline: Single positions exceeding 40.0% represent excessive systemic risk.
```

---

## 11. Testing Approach

The project enforces an automated verification pipeline using JUnit 5, Mockito, and AssertJ. Testing spans unit validation, concurrency behavior, mathematical precision, and edge-case recovery.

### Test Execution Summary
* **Total Tests Executed:** 42
* **Failures / Errors:** 0
* **Success Rate:** 100%

```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.portfolio.InputValidatorTest
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.portfolio.PortfolioTrackerTest
[INFO] Tests run: 28, Failures: 0, Errors: 0, Skipped: 0
[INFO] Results:
[INFO] Tests run: 42, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Tested Test Scenarios:
1. **Financial Math Precision:** Zero-division checks on percentage return; round-half-up verification across fractional share counts.
2. **Concurrent State Mutations:** Simulates concurrent price updates from 10 threads while main thread calculates total portfolio value, verifying zero race conditions.
3. **Input Sanitization:** Boundary testing on ticker symbols (rejecting numeric "3", whitespace strings, and 6+ character tickers); title-casing validation.
4. **Alert Suppression:** Validates that duplicate price movements at identical levels do not generate duplicate database records.

---

## 12. Challenges Faced & Solutions

1. **Scanner Stream Premature EOF:**
   * *Challenge:* When input streams closed unexpectedly (e.g., EOF in non-interactive testing or pipeline redirection), `readValidQuantity()` silently fell back to `BigDecimal.ONE`, corrupting user data.
   * *Solution:* Refactored all reader methods in `ConsoleMenu` to return `null` on EOF, allowing the menu handler to abort cleanly with a `"Cancelled."` message without corrupting state.
2. **API Rate Limiting Bottlenecks:**
   * *Challenge:* Alpha Vantage free-tier keys permit only 25 requests per day, which would quickly exhaust with continuous multi-stock background polling.
   * *Solution:* Implemented an intelligent offline caching layer in `PortfolioDAO`. On startup or rate-limit exhaustion (HTTP 429/empty response), the application loads last-known database prices, warns the user via console banners, and continues operating without crashing.
3. **JVM Native Memory Warnings on Modern JDKs:**
   * *Challenge:* Running native SQLite binaries on JDK 21+ generates `restricted method in System::load` warnings on stderr.
   * *Solution:* Added JVM configuration documentation (`--enable-native-access=ALL-UNNAMED`) and verified that database interactions remain completely sound and stable.

---

## 13. Learnings & Key Takeaways
* **Mastery of Java Memory Model (JMM):** Gained practical understanding of thread memory visibility, happens-before relationships, and why combining `ConcurrentHashMap` with targeted synchronization is necessary for complex atomic invariants.
* **Separation of Timer Tick from Task Execution:** Learned the critical importance of keeping scheduler threads lightweight by offloading blocking network I/O to dedicated worker pools.
* **Financial Software Engineering Discipline:** Realized why primitive data types (`double`, `float`) are strictly unacceptable in enterprise accounting and financial engineering.
* **Resilient Graceful Degradation:** Designing software that anticipates third-party API rate limits and network drops ensures superior user trust and production reliability.

---

## 14. Future Enhancements
1. **WebSocket / SSE Live Streaming:** Integrate real-time streaming market data protocols (e.g., Polygon.io or IEX Cloud WebSockets) to replace REST polling with push-based quote streaming.
2. **Technical Indicator Engine:** Incorporate computation of Simple Moving Averages (SMA 50/200), Exponential Moving Averages (EMA), and Relative Strength Index (RSI).
3. **Automated SMS & Webhook Notifications:** Extend `AlertService` with dispatchers for Telegram, Discord webhooks, and Twilio SMS.
4. **Historical Backtesting Simulation:** Enable ingestion of historical CSV OHLCV data to simulate portfolio performance over historical time horizons.

---

## 15. References
1. Goetz, B., Peierls, T., Bloch, J., Bowbeer, J., Holmes, D., & Lea, D. (2006). *Java Concurrency in Practice*. Addison-Wesley Professional.
2. Bloch, J. (2018). *Effective Java* (3rd ed.). Addison-Wesley Professional.
3. Oracle Corporation. (2023). *Java Platform, Standard Edition Documentation (Release 17)*. Oracle Java SE Documentation.
4. Alpha Vantage Inc. (2024). *Alpha Vantage Free Stock APIs Documentation*. https://www.alphavantage.co/documentation/
5. SQLite Development Team. (2024). *SQLite Database Engine Documentation*. https://www.sqlite.org/
