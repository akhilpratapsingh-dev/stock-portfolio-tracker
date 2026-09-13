package com.portfolio.concurrency;

import com.portfolio.model.Portfolio;
import com.portfolio.service.PortfolioService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Background scheduler that periodically refreshes all stock prices in the portfolio.
 *
 * <h2>Concurrency Design</h2>
 *
 * <p>Two executors are used intentionally:</p>
 *
 * <ol>
 *   <li><b>{@link ScheduledExecutorService} (1 thread)</b> — fires a single
 *       "refresh cycle" task every {@code intervalSeconds}. This thread never
 *       blocks on network I/O; it only submits work to the thread pool below.</li>
 *
 *   <li><b>{@link ExecutorService} fixed thread pool ({@code FETCH_THREADS} threads)</b>
 *       — each symbol is submitted as a separate {@link Callable} task so that
 *       N stock prices are fetched in parallel rather than sequentially. For a
 *       portfolio of 10 stocks, this reduces refresh time from ~10 × 5 s to
 *       ~1 × 5 s (assuming a 5-second fetch per stock).</li>
 * </ol>
 *
 * <p>Both executors are created once in the constructor and reused across every
 * refresh cycle. No new executor is created per cycle.</p>
 *
 * <h2>Thread Safety</h2>
 *
 * <p>The refresh task reads symbols from {@link Portfolio#getSymbols()}, which
 * returns an immutable snapshot. Each per-symbol task calls
 * {@link PortfolioService#refreshPrice(String)}, which itself uses the
 * Portfolio's synchronized methods for all reads and writes. Therefore, the
 * main menu thread and the refresh thread cannot see a partially updated
 * portfolio.</p>
 *
 * <h2>Shutdown</h2>
 *
 * <p>Call {@link #stop()} from the main thread when the user exits. This
 * cancels the scheduled task, shuts down both executors, and waits up to 10
 * seconds for running price-fetch tasks to complete gracefully.</p>
 */
public class PriceRefreshScheduler {

    private static final Logger LOGGER = Logger.getLogger(PriceRefreshScheduler.class.getName());

    /** Maximum parallel price-fetch threads. Keeps below free-tier rate limits. */
    private static final int FETCH_THREADS = 5;

    /** How long to wait for in-progress fetches during shutdown. */
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 10;

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final PortfolioService portfolioService;
    private final Portfolio portfolio;
    private final long intervalSeconds;

    /**
     * Single-thread scheduler — triggers the refresh cycle at fixed intervals.
     * Created once, shared across all cycles.
     */
    private final ScheduledExecutorService scheduler;

    /**
     * Fixed thread pool — fetches individual stock prices in parallel.
     * Created once, shared across all cycles.
     */
    private final ExecutorService fetchPool;

    /** Reference to the scheduled cycle task, kept for cancellation on shutdown. */
    private volatile ScheduledFuture<?> scheduledTask;

    /** Whether the scheduler has been started. */
    private volatile boolean running = false;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    /**
     * Creates the scheduler. Call {@link #start()} to begin periodic execution.
     *
     * @param portfolioService service that performs each symbol's price refresh
     * @param portfolio        the in-memory portfolio (used to read current symbols)
     * @param intervalSeconds  how often to run a full refresh cycle, in seconds
     */
    public PriceRefreshScheduler(PortfolioService portfolioService,
                                 Portfolio portfolio,
                                 long intervalSeconds) {
        this.portfolioService = portfolioService;
        this.portfolio        = portfolio;
        this.intervalSeconds  = intervalSeconds;

        // Both executors created once — never recreated per cycle
        this.scheduler  = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "price-refresh-scheduler");
            t.setDaemon(true);
            return t;
        });
        this.fetchPool  = Executors.newFixedThreadPool(FETCH_THREADS, r -> {
            Thread t = new Thread(r, "price-fetch-" + System.nanoTime());
            t.setDaemon(true);
            return t;
        });
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    /**
     * Starts the background refresh scheduler.
     * The first refresh runs after one full interval has elapsed.
     *
     * @throws IllegalStateException if already started
     */
    public synchronized void start() {
        if (running) {
            throw new IllegalStateException("PriceRefreshScheduler is already running.");
        }
        scheduledTask = scheduler.scheduleAtFixedRate(
                this::runRefreshCycle,
                intervalSeconds,     // initial delay — wait one interval before first run
                intervalSeconds,     // period
                TimeUnit.SECONDS
        );
        running = true;
        LOGGER.info("Price refresh scheduler started. Interval: " + intervalSeconds + "s, "
                    + "parallel fetch threads: " + FETCH_THREADS);
    }

    /**
     * Stops the scheduler and shuts down both thread pools gracefully.
     *
     * <p>In-progress fetch tasks are given up to {@value SHUTDOWN_TIMEOUT_SECONDS}
     * seconds to complete before being force-cancelled.</p>
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;

        // Cancel the scheduled trigger (do not interrupt the running task if any)
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
        }

        // Shutdown both executors
        shutdownExecutor("scheduler",  scheduler);
        shutdownExecutor("fetch-pool", fetchPool);

        LOGGER.info("Price refresh scheduler stopped.");
    }

    /** @return {@code true} if the scheduler is currently running */
    public boolean isRunning() {
        return running;
    }

    /**
     * Triggers a single refresh cycle immediately, bypassing the schedule.
     * Useful for the "Refresh Prices Now" menu option.
     */
    public void refreshNow() {
        LOGGER.info("Manual price refresh triggered.");
        // Submit as a task on the fetch pool to keep it off the main thread
        fetchPool.submit(this::runRefreshCycle);
    }

    // -----------------------------------------------------------------------
    // Core refresh logic
    // -----------------------------------------------------------------------

    /**
     * A single refresh cycle: fetches all portfolio symbols in parallel.
     *
     * <p>This method is called by the {@link ScheduledExecutorService} thread.
     * It submits one task per symbol to the fetch pool, then waits for all
     * tasks to complete (with a per-symbol timeout). Symbols added or removed
     * between cycles are handled correctly because
     * {@link Portfolio#getSymbols()} returns a snapshot.</p>
     */
    private void runRefreshCycle() {
        if (portfolio.isEmpty()) {
            LOGGER.fine("Refresh cycle: portfolio is empty, nothing to refresh.");
            return;
        }

        Collection<String> symbols = portfolio.getSymbols(); // Immutable snapshot
        LOGGER.info("Starting price refresh cycle for " + symbols.size() + " symbol(s).");

        // Submit one Callable per symbol to the fixed thread pool
        List<Future<?>> futures = new ArrayList<>(symbols.size());
        for (String symbol : symbols) {
            Future<?> future = fetchPool.submit(() -> {
                try {
                    portfolioService.refreshPrice(symbol);
                } catch (Exception e) {
                    // Never let an exception kill the fetch thread
                    LOGGER.log(Level.WARNING, "Unhandled error refreshing price for " + symbol, e);
                }
            });
            futures.add(future);
        }

        // Wait for all fetches to complete (or timeout individually)
        int completed = 0;
        int failed = 0;
        for (Future<?> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS); // 30s per symbol max
                completed++;
            } catch (TimeoutException e) {
                future.cancel(true);
                LOGGER.warning("Price fetch timed out for one symbol; task cancelled.");
                failed++;
            } catch (ExecutionException e) {
                LOGGER.log(Level.WARNING, "Price fetch task raised an exception", e.getCause());
                failed++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warning("Refresh cycle interrupted; stopping mid-cycle.");
                return;
            }
        }

        LOGGER.info("Refresh cycle complete. Completed: " + completed + ", Failed: " + failed);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Shuts down an executor, waiting for in-progress tasks, then force-stopping.
     *
     * @param name     human-readable name for logging
     * @param executor the executor to shut down
     */
    private void shutdownExecutor(String name, ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                LOGGER.warning(name + " did not terminate in " + SHUTDOWN_TIMEOUT_SECONDS
                               + "s; forcing shutdown.");
                executor.shutdownNow();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    LOGGER.severe(name + " could not be terminated.");
                }
            } else {
                LOGGER.info(name + " shut down cleanly.");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
