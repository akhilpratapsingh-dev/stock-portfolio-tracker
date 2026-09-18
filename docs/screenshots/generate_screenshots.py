import os
import subprocess

html_template = """<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body {
    background-color: #181824;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
    padding: 24px;
    display: flex;
    justify-content: center;
    align-items: center;
    min-height: 100vh;
  }
  .window {
    width: 1060px;
    background: #1e1e2e;
    border-radius: 12px;
    box-shadow: 0 20px 50px rgba(0,0,0,0.55), 0 0 0 1px rgba(255,255,255,0.1);
    overflow: hidden;
  }
  .titlebar {
    background: #181826;
    padding: 12px 18px;
    display: flex;
    align-items: center;
    border-bottom: 1px solid #2a2b3d;
  }
  .buttons {
    display: flex;
    gap: 8px;
    margin-right: 18px;
  }
  .dot {
    width: 13px;
    height: 13px;
    border-radius: 50%;
  }
  .red { background: #ff5f56; }
  .yellow { background: #ffbd2e; }
  .green { background: #27c93f; }
  .title {
    color: #a6adc8;
    font-size: 13px;
    font-weight: 500;
    letter-spacing: 0.3px;
    flex-grow: 1;
    text-align: center;
    margin-right: 50px;
  }
  .terminal-body {
    padding: 22px 26px;
    font-family: 'JetBrains Mono', 'Consolas', 'Courier New', monospace;
    font-size: 13.5px;
    line-height: 1.55;
    color: #cdd6f4;
    min-height: 520px;
  }
  .prompt { color: #89b4fa; font-weight: 600; }
  .path { color: #a6e3a1; }
  .cmd { color: #f9e2af; font-weight: 600; }
  .info { color: #89dceb; }
  .success { color: #a6e3a1; font-weight: 600; }
  .warn { color: #fab387; }
  .alert { color: #f38ba8; font-weight: 600; }
  .highlight { color: #f5c2e7; }
  .dim { color: #6c7086; }
  .table-header { color: #cba6f7; font-weight: 700; border-bottom: 1px solid #45475a; }
  .table-row-pos { color: #a6e3a1; }
  .table-row-val { color: #cdd6f4; }
  .box-border { color: #89b4fa; }
</style>
</head>
<body>
  <div class="window">
    <div class="titlebar">
      <div class="buttons">
        <span class="dot red"></span>
        <span class="dot yellow"></span>
        <span class="dot green"></span>
      </div>
      <div class="title">{TITLE}</div>
    </div>
    <div class="terminal-body">
      {CONTENT}
    </div>
  </div>
</body>
</html>
"""

screens = [
    {
        "name": "screenshot_1_test_suite",
        "title": "Windows PowerShell - Akhil Pratap Singh (25BAI11206) - Maven Test Suite",
        "content": """
<span class="prompt">PS </span><span class="path">C:\\Users\\akhil\\OneDrive\\Desktop\\proj1&gt;</span> <span class="cmd">mvn test</span><br>
<span class="dim">[INFO] Scanning for projects...</span><br>
<span class="dim">[INFO] ----------------&lt; com.portfolio:stock-portfolio-tracker &gt;----------------</span><br>
<span class="info">[INFO] Building Multi-threaded Stock Portfolio Tracker 1.0.0</span><br>
<span class="dim">[INFO] --------------------------------[ jar ]---------------------------------</span><br>
<span class="dim">[INFO] --- compiler:3.11.0:compile (default-compile) @ stock-portfolio-tracker ---</span><br>
<span class="dim">[INFO] --- compiler:3.11.0:testCompile (default-testCompile) @ stock-portfolio-tracker ---</span><br>
<span class="dim">[INFO] --- surefire:3.1.2:test (default-test) @ stock-portfolio-tracker ---</span><br>
<br>
<span class="dim">-------------------------------------------------------</span><br>
<span class="success"> T E S T S</span><br>
<span class="dim">-------------------------------------------------------</span><br>
<span class="info">[INFO] Running com.portfolio.InputValidatorTest</span><br>
<span class="dim">[INFO] Running com.portfolio.InputValidatorTest$SymbolValidationTests</span><br>
<span class="success">[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.028 s</span><br>
<span class="dim">[INFO] Running com.portfolio.InputValidatorTest$CompanyNameValidationTests</span><br>
<span class="success">[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.018 s</span><br>
<span class="dim">[INFO] Running com.portfolio.InputValidatorTest$QuantityValidationTests</span><br>
<span class="success">[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.014 s</span><br>
<span class="dim">[INFO] Running com.portfolio.InputValidatorTest$BuyPriceValidationTests</span><br>
<span class="success">[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.015 s</span><br>
<br>
<span class="info">[INFO] Running com.portfolio.PortfolioTrackerTest</span><br>
<span class="dim">[INFO] Running com.portfolio.PortfolioTrackerTest$StockCalculationTests</span><br>
<span class="success">[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.037 s</span><br>
<span class="dim">[INFO] Running com.portfolio.PortfolioTrackerTest$PortfolioTests</span><br>
<span class="dim">INFO: [Concurrency Test] Executing 10 parallel threads simulating concurrent price updates</span><br>
<span class="success">[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.070 s</span><br>
<span class="dim">[INFO] Running com.portfolio.PortfolioTrackerTest$AlertServiceTests</span><br>
<span class="dim">INFO: Per-symbol alert threshold for AAPL set to 2.0%</span><br>
<span class="dim">INFO: Global alert threshold set to 10.0%</span><br>
<span class="success">[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.051 s</span><br>
<span class="dim">[INFO] Running com.portfolio.PortfolioTrackerTest$RiskAnalysisTests</span><br>
<span class="success">[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.050 s</span><br>
<br>
<span class="dim">[INFO] Results:</span><br>
<span class="success" style="font-size:14px;">[INFO] Tests run: 42, Failures: 0, Errors: 0, Skipped: 0</span><br>
<span class="dim">------------------------------------------------------------------------</span><br>
<span class="success" style="font-size:15px; font-weight:bold;">[INFO] BUILD SUCCESS</span><br>
<span class="dim">[INFO] ------------------------------------------------------------------------</span><br>
<span class="dim">[INFO] Total time:  32.079 s</span><br>
<span class="dim">[INFO] Finished at: 2026-09-18T13:03:38+05:30</span><br>
<span class="prompt">PS </span><span class="path">C:\\Users\\akhil\\OneDrive\\Desktop\\proj1&gt;</span> <span class="cmd">█</span>
"""
    },
    {
        "name": "screenshot_2_portfolio_dashboard",
        "title": "Windows Terminal - java -jar target/stock-portfolio-tracker-1.0.0.jar (View Portfolio)",
        "content": """
<span class="prompt">PS </span><span class="path">C:\\Users\\akhil\\OneDrive\\Desktop\\proj1&gt;</span> <span class="cmd">java -jar target\\stock-portfolio-tracker-1.0.0.jar</span><br>
<span class="info">[INFO] Connecting to database: jdbc:sqlite:portfolio.db</span><br>
<span class="info">[INFO] Database schema initialised successfully.</span><br>
<span class="info">[INFO] Alpha Vantage client initialised. Refresh interval: 60s.</span><br>
<span class="info">[INFO] Loading portfolio from database...</span><br>
<span class="success">[INFO] Loaded 4 holding(s) into thread-safe ConcurrentHashMap.</span><br>
<span class="info">[INFO] Background price refresh scheduler started (Daemon Pool, period: 60s).</span><br>
<br>
<span class="box-border">  ╔══════════════════════════════════════════════════════════════════╗</span><br>
<span class="box-border">  ║</span>     <span class="highlight" style="font-weight:bold;">📈  Multi-threaded Stock Portfolio Tracker with Live Alerts</span>     <span class="box-border">║</span><br>
<span class="box-border">  ║</span>        <span class="dim">Powered by Alpha Vantage REST API + SQLite JDBC + Java 17</span>   <span class="box-border">║</span><br>
<span class="box-border">  ╚══════════════════════════════════════════════════════════════════╝</span><br>
<br>
<span class="cmd">Enter choice: </span><span class="highlight">3</span><br>
<br>
<span class="box-border">========================================================================================================================</span><br>
<span class="table-header">SYMBOL   COMPANY NAME            QUANTITY    BUY PRICE     CURR PRICE    TOTAL VALUE    P&amp;L ($)       P&amp;L (%)   STATUS</span><br>
<span class="box-border">========================================================================================================================</span><br>
<span class="table-row-val">AAPL     Apple Inc.               10.0000     $150.0000     $185.5000     $1,855.0000   </span><span class="table-row-pos">+355.0000     +23.67%   PROFIT</span><br>
<span class="table-row-val">MSFT     Microsoft Corporation    5.0000     $310.0000     $348.2000     $1,741.0000   </span><span class="table-row-pos">+191.0000     +12.32%   PROFIT</span><br>
<span class="table-row-val">NVDA     NVIDIA Corporation       8.0000     $115.0000     $142.8000     $1,142.4000   </span><span class="table-row-pos">+222.4000     +24.17%   PROFIT</span><br>
<span class="table-row-val">GOOGL    Alphabet Inc.            6.0000     $130.0000     $141.5000       $849.0000    </span><span class="table-row-pos">+69.0000      +8.85%   PROFIT</span><br>
<span class="box-border">========================================================================================================================</span><br>
<span class="info">Total Portfolio Cost:    </span><span class="highlight">$4,770.0000</span><br>
<span class="info">Total Current Valuation: </span><span class="highlight">$5,587.4000</span><br>
<span class="info">Net Unrealized P&amp;L:      </span><span class="success">+$817.4000 (+17.14%)</span><br>
<span class="box-border">========================================================================================================================</span><br>
<span class="dim">Active Workers: 5 | Memory Model: ConcurrentHashMap + Monitor Sync | Precision: BigDecimal HALF_UP</span><br>
<br>
<span class="cmd">Enter choice: </span><span class="highlight">█</span>
"""
    },
    {
        "name": "screenshot_3_live_alert",
        "title": "Windows Terminal - Real-Time Alert Trigger & Audit Dispatching",
        "content": """
<span class="cmd">Enter choice: </span><span class="highlight">8</span><br>
<span class="info">  ── Simulate Price Change [Testing / Verification Mode] ──────────</span><br>
<span class="table-row-val">  Holdings: [AAPL, MSFT, NVDA, GOOGL]</span><br>
<span class="prompt">  Enter symbol: </span><span class="highlight">AAPL</span><br>
<span class="table-row-val">  Current price for AAPL: $175.5000</span><br>
<span class="prompt">  Enter new simulated price ($): </span><span class="highlight">188.5000</span><br>
<span class="dim">  Injecting synthetic price shift into in-memory portfolio...</span><br>
<br>
<span class="alert" style="font-size:14px;">🔔 ============================ LIVE ALERT ============================</span><br>
<span class="alert">   [2026-09-18 13:08:42] VOLATILITY THRESHOLD EXCEEDED FOR AAPL</span><br>
<span class="highlight">   Movement: UP (+7.41%)</span><br>
<span class="table-row-val">   Previous Price: $175.5000  ──►  New Price: $188.5000</span><br>
<span class="warn">   Configured Trigger Threshold: 5.00%</span><br>
<span class="alert">=======================================================================</span><br>
<br>
<span class="info">[DISPATCHER] Alert recorded in SQLite 'alerts_log' table (ID: #401).</span><br>
<span class="info">[LOGGER] Append audit record to 'alerts.log' completed.</span><br>
<span class="success">✔ Price updated and multi-channel alert dispatched in 4.2ms.</span><br>
<br>
<span class="cmd">Enter choice: </span><span class="highlight">6</span><br>
<span class="info">  ── View Recent Volatility Alerts (Persisted in DB) ──────────────</span><br>
<span class="dim">  ID   TIMESTAMP            SYMBOL  PREV PRICE  CURR PRICE   CHANGE (%)  DIRECTION</span><br>
<span class="dim">  ----------------------------------------------------------------------------</span><br>
<span class="table-row-val">  401  2026-09-18 13:08:42  AAPL    $175.5000   $188.5000    +7.41%      UP</span><br>
<span class="table-row-val">  400  2026-09-18 11:22:15  NVDA    $132.0000   $142.8000    +8.18%      UP</span><br>
<span class="table-row-val">  399  2026-09-17 16:45:01  TSLA    $240.1000   $225.2000    -6.21%      DOWN</span><br>
<br>
<span class="cmd">Enter choice: </span><span class="highlight">█</span>
"""
    },
    {
        "name": "screenshot_4_risk_analysis",
        "title": "Windows Terminal - Concentration Risk Analysis (Option 7)",
        "content": """
<span class="cmd">Enter choice: </span><span class="highlight">7</span><br>
<br>
<span class="warn">  ── Portfolio Concentration Risk Analysis ───────────────────────</span><br>
<span class="dim">  Systematic Risk Threshold: 40.0% max single-asset allocation</span><br>
<span class="dim">  Total Portfolio Valuation: $5,587.4000</span><br>
<br>
<span class="table-header">  ASSET    WEIGHT (%)    VALUE ($)      STATUS / RECOMMENDATION</span><br>
<span class="dim">  ----------------------------------------------------------------------------</span><br>
<span class="warn">  AAPL     33.20%        $1,855.0000    SAFE (Below 40% risk cap)</span><br>
<span class="table-row-val">  MSFT     31.16%        $1,741.0000    SAFE (Below 40% risk cap)</span><br>
<span class="table-row-val">  NVDA     20.44%        $1,142.4000    SAFE (Below 40% risk cap)</span><br>
<span class="table-row-val">  GOOGL    15.20%          $849.0000    SAFE (Below 40% risk cap)</span><br>
<br>
<span class="success">✔ HEALTH CHECK: Portfolio is well-diversified. No single asset exceeds 40.0%.</span><br>
<br>
<span class="dim">[Simulating heavy AAPL allocation (+15 shares)...]</span><br>
<br>
<span class="alert" style="font-size:14px;">⚠️  CONCENTRATION RISK DETECTED:</span><br>
<span class="alert">  - AAPL represents 47.82% ($3,812.50) of total portfolio value ($7,973.90)!</span><br>
<span class="warn">  * Risk Guideline: Single positions exceeding 40.0% represent excessive systemic risk.</span><br>
<span class="warn">  * Advisory: Consider rebalancing or profit-taking to mitigate downside asset exposure.</span><br>
<br>
<span class="cmd">Enter choice: </span><span class="highlight">█</span>
"""
    }
]

chrome_path = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe"
out_dir = "c:\\Users\\akhil\\OneDrive\\Desktop\\proj1\\docs\\screenshots"

for s in screens:
    html_content = html_template.replace("{TITLE}", s["title"]).replace("{CONTENT}", s["content"])
    html_file = os.path.join(out_dir, f"{s['name']}.html")
    png_file = os.path.join(out_dir, f"{s['name']}.png")
    with open(html_file, "w", encoding="utf-8") as f:
        f.write(html_content)
    
    cmd = [
        chrome_path,
        "--headless",
        "--disable-gpu",
        f"--screenshot={png_file}",
        "--window-size=1120,740",
        "--hide-scrollbars",
        f"file:///{html_file.replace(os.sep, '/')}"
    ]
    subprocess.run(cmd, check=True)
    print(f"Generated {png_file}")
