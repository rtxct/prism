package prism.application.component;

import lombok.Getter;
import lombok.extern.java.Log;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import prism.configuration.adapter.CacheAdapter;
import prism.configuration.annotations.PostConstruct;
import prism.configuration.context.AbstractServiceContextConfiguration;
import prism.domain.model.Prism;

import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * A size-capped, optionally time-expiring cache for storing generated {@link Prism} color palettes.
 * <p>
 * The cache maintains a fixed capacity, displacing the least recently used entries
 * when full. It also supports an optional watcher task that clears expired entries periodically.
 */
@Log
public class ColorCache {

    private static final int WATCHER_WAIT_TIME = 5_000;

    private static final int TOTAL_PROPERTIES = 3;

    private final Supplier<? extends AbstractServiceContextConfiguration> appExecutionContext;

    private final ScheduledExecutorService scheduler;

    private Object[][] cache;

    private ScheduledFuture<?> watchTask;

    @Getter
    private boolean started;

    /**
     * Constructs a new {@code ColorCache} using the provided application context.
     * Initializes a single-threaded virtual thread executor for the watcher task.
     *
     * @param appExecutionContext a supplier providing the application configuration context
     */
    public ColorCache(@NonNull Supplier<? extends AbstractServiceContextConfiguration> appExecutionContext) {
        this.appExecutionContext = appExecutionContext;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().factory()
        );
    }

    /**
     * Initializes the underlying cache array based on the configured maximum amount.
     * Automatically invoked after dependency injection via {@link PostConstruct}.
     */
    @PostConstruct
    @SuppressWarnings("unused")
    private void setup() {
        this.cache = new Object[
                this.getConfiguredAmount()][TOTAL_PROPERTIES];
    }

    /**
     * Resizes the cache to the currently configured amount, keeping as many existing
     * entries as fit. Needed because the configuration can change after the initial
     * {@link CacheAdapter#AMOUNT}.
     */
    private void resizeIfNeeded() {
        int amount = this.getConfiguredAmount();

        if (this.cache.length == amount) {
            return;
        }

        Object[][] resized = new Object[amount][TOTAL_PROPERTIES];

        if (amount >= this.cache.length) {
            System.arraycopy(this.cache, 0, resized, 0, this.cache.length);
        } else {
            Object[][] sorted = this.cache.clone();

            Arrays.sort(sorted, Comparator.comparing(
                    row -> (Instant) row[2],
                    Comparator.nullsLast(Comparator.reverseOrder())
            ));

            System.arraycopy(sorted, 0, resized, 0, amount);
        }

        this.cache = resized;
    }

    /**
     * Retrieves the configured maximum cache amount from the application configuration.
     * Falls back to a default value if not explicitly set.
     *
     * @return the maximum number of items the cache can hold
     * @throws IllegalArgumentException if the configured amount is less than 1
     */
    private int getConfiguredAmount() {
        CacheAdapter cacheAdapter =
                this.appExecutionContext.get().getCacheAdapter();

        Integer amount = CacheAdapter.AMOUNT;

        if (Objects.nonNull(cacheAdapter)) {
            amount = cacheAdapter.getAmount();
        }

        if (amount < 1) {
            throw new IllegalArgumentException(
                    "Cache amount must be at least 1, got %d".formatted(amount));
        }

        return amount;
    }

    /**
     * Starts the periodic watcher task that removes expired entries from the cache.
     * The expiration timeout is determined by the cache configuration.
     * If the watcher is already running, this method does nothing.
     */
    public void watch() {
        if (this.started) {
            return;
        }

        CacheAdapter cacheAdapter =
                this.appExecutionContext.get().getCacheAdapter();

        Integer keepAlive = CacheAdapter.KEEP_ALIVE;

        if (Objects.nonNull(cacheAdapter)) {
            keepAlive = cacheAdapter.getTimeout();
        }

        final Integer timeout = keepAlive;

        this.started = true;

        this.watchTask = this.scheduler.scheduleAtFixedRate(
                () -> this.removeExpired(timeout), 0, WATCHER_WAIT_TIME, TimeUnit.MILLISECONDS);
    }

    /**
     * Stops the periodic watcher task, preventing further removal of expired entries.
     * Logs a warning if the watcher is not currently running.
     */
    public synchronized void unwatch() {
        if (this.watchTask == null || this.watchTask.isCancelled()) {
            log.warning("Trying to stop cache watcher " +
                    "while watcher is not started. Request will be ignored");
            return;
        }

        this.started = false;

        this.watchTask.cancel(true);
    }

    /**
     * Periodically invoked by the watcher task to clean up cache entries
     * that have exceeded their time-to-live.
     *
     * @param timeout the time-to-live in seconds for cache entries
     */
    private synchronized void removeExpired(@NonNull Integer timeout) {
        Instant now = Instant.now();

        for (Object[] cache : this.cache) {
            if (cache[2] == null) {
                continue;
            }

            Instant expiration =
                    ((Instant) cache[2]).plusSeconds(timeout);

            if (!expiration.isAfter(now)) {
                Arrays.fill(cache, null);
            }
        }

        boolean hasAny = false;

        for (Object[] cache : this.cache) {
            if (cache[2] != null) {
                hasAny = true;
                break;
            }
        }

        if (!hasAny) {
            this.clear();
        }
    }

    /**
     * Adds or updates a {@link Prism} entry in the cache for the given file path.
     * If the cache is full, displaces the least recently used entry.
     *
     * @param filePath the path to the image file, used as the cache key
     * @param prism the generated color spectrum to cache
     * @throws IllegalArgumentException if the provided file path is empty
     */
    public synchronized void add(@NonNull String filePath, @NonNull Prism prism) {
        if (filePath.isEmpty()) {
            throw new IllegalArgumentException(
                    "File path can not be empty while using cache"
            );
        }

        this.resizeIfNeeded();

        int index = this.lastUsed(this.cache);

        this.cache[index] =
                new Object[]{filePath, prism, Instant.now()};
    }

    /**
     * Retrieves a cached {@link Prism} color spectrum for the given file path,
     * updating its last-used timestamp to prevent it from being displaced.
     *
     * @param filePath the path to the image file
     * @return the cached {@link Prism}, or {@code null} if not found in the cache
     * @throws IllegalArgumentException if the provided file path is empty
     */
    public synchronized @Nullable Prism get(@NonNull String filePath) {
        if (filePath.isEmpty()) {
            throw new IllegalArgumentException(
                    "File path can not be empty while using cache");
        }

        for (int i = 0; i < this.cache.length; i++) {
            if (this.cache[i][0] != null && this.cache[i][0].equals(filePath)) {
                this.cache[i][2] = Instant.now();
                return (Prism) this.cache[i][1];
            }
        }

        return null;
    }

    /**
     * Clears all entries from the cache, resetting it to an empty state while retaining its
     * configured capacity, and {@link #unwatch()} the active process.
     */
    public synchronized void clear() {
        this.cache = new Object[
                this.getConfiguredAmount()][TOTAL_PROPERTIES];

        this.unwatch();
    }

    /**
     * Finds the index of the least recently used entry in the cache, or the
     * first available empty slot if the cache is not full.
     *
     * @param array the 2D array representing the cache
     * @return the index of the least recently used or empty slot
     */
    private int lastUsed(@NonNull Object[][] array) {
        int lastUsedIndex = 0;

        for (int i = 0; i < array.length; i++) {
            if (array[i][2] == null) {
                return i;
            }

            if (array[i][2] instanceof Instant current
                    && array[lastUsedIndex][2] instanceof Instant oldest
                    && current.isBefore(oldest)) {

                lastUsedIndex = i;
            }
        }

        return lastUsedIndex;
    }
}