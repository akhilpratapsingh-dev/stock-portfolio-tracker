# Project Statement: Multi-Threaded Stock Portfolio Tracker with Live Alerts

## 1. Problem Statement
Retail investors and portfolio managers who track equity assets often face several critical challenges when using existing tools:
- **Stale Market Data & Rate-Limit Bottlenecks:** Free and standard financial APIs impose stringent per-minute and daily rate limits. Sequential polling of multi-stock portfolios leads to compounding network latency, outdated portfolio valuations, and frequent API request throttling.
- **Data Race Conditions & Unsafe Concurrency:** Many desktop/CLI tracker applications either block the user interface during price updates or introduce dangerous concurrency bugs (data races, dirty reads, and phantom updates) when background threads mutate portfolio values while user threads read them.
- **Floating-Point Imprecision in Financial Calculations:** Typical consumer software often relies on primitive `float` or `double` data types, causing floating-point rounding errors in profit/loss (P&L) calculations, percentage allocations, and risk computations.
- **Lack of Multi-Channel Proactive Alerting:** Investors cannot constantly monitor terminal screens; without configurable, automated threshold monitors that log alerts across persistent stores and human-readable logs, rapid market drawdowns or sudden spikes go unnoticed.

This project addresses these issues by developing a high-performance, purely terminal-executable Java application that demonstrates industrial concurrency paradigms (`ScheduledExecutorService`, worker thread pools, `ConcurrentHashMap`, fine-grained object synchronization), financial precision via `BigDecimal`, and multi-tier persistence (SQLite/MySQL) coupled with the Alpha Vantage market data API.

---

## 2. Scope of the Project

### In-Scope:
- **Real-Time Market Data Ingestion:** Integration with Alpha Vantage's `GLOBAL_QUOTE` REST endpoint via Java 11+ standard non-blocking `HttpClient` and Jackson JSON parser.
- **Non-Blocking Concurrent Architecture:** Decoupled background scheduling (`ScheduledExecutorService`) and parallel worker thread execution (`ExecutorService` fixed pool) to refresh stock quotes asynchronously without freezing user menu interactions.
- **Thread-Safe State Management:** Zero-race-condition in-memory portfolio using `ConcurrentHashMap` combined with synchronized critical sections and unmodifiable defensive snapshots for safe concurrent iteration.
- **Financial Precision Analytics:** 100% calculation of portfolio valuation, unrealized P&L, percentage returns, and asset concentration using `java.math.BigDecimal` with `RoundingMode.HALF_UP`.
- **Configurable Dynamic Alert System:** Multi-channel alerting (Console banner, File logger, and Relational Database) triggered whenever a stock's price moves beyond a user-configured percentage threshold, with duplicate suppression.
- **Portfolio Concentration Risk Analysis:** Automated portfolio weighting engine identifying concentration risks where any single equity exceeds 40% of total portfolio value.
- **Relational Persistence Layer:** JDBC Data Access Object (DAO) architecture utilizing parameterized SQL queries (`PreparedStatement`) supporting both embedded SQLite (zero-configuration default) and enterprise MySQL.
- **Offline & Fallback Mode:** Automatic graceful degradation to cached database prices when network is unavailable or API limits are reached.
- **CLI Executability & Strict Input Validation:** 100% command-line interface with regex-based validation for stock tickers, formatted company names, positive numeric quantities, and buy prices.

### Out-of-Scope:
- High-frequency algorithmic trading or direct automated order execution to brokerages.
- Graphical User Interface (GUI) or browser web UI (strictly focused on terminal CLI architecture as per system evaluation requirements).
- Multi-user authentication, JWT sessions, or remote cloud deployment infrastructure.

---

## 3. Target Users
1. **Individual Retail Investors & Traders:** Users who demand a lightweight, distraction-free, terminal-based dashboard that operates continuously in background terminals with automated alerts.
2. **Quantitative Analysts & Computer Science Students:** Practitioners studying core multi-threading models, asynchronous task scheduling, race-condition mitigation, and clean JDBC design patterns in modern Java without heavy frameworks like Spring Boot.
3. **Low-Resource & Headless Environment Operators:** Users running monitoring scripts on remote Linux VPS, Raspberry Pi, or developer workstations that require minimal CPU/RAM footprint and no graphical display server.

---

## 4. High-Level Features
- **Holding Management (CRUD):** Add, view, inspect, and remove stock holdings with live validation.
- **Parallel Multi-Stock Quotes:** Refresh quotes for multiple assets simultaneously across pooled worker threads.
- **Intelligent Price Caching & Offline Recovery:** Automatic retrieval of last-known valid prices from SQLite/MySQL if the remote API is unreachable.
- **Real-Time Threshold Alert Engine:** User-definable percentage fluctuation triggers (both global and per-symbol) with audit trails stored in `alerts_log`.
- **Concentration Risk Detector:** Real-time asset weighting warning investors of single-stock overexposure.
- **Resilient CLI Menu Interface:** Interactive Scanner console with formatted tabular reporting, ANSI styling, and robust error recovery from EOF or invalid input.
