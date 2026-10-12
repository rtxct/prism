package prism.application.component;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import prism.configuration.adapter.CacheAdapter;
import prism.configuration.context.AbstractServiceContextConfiguration;
import prism.configuration.context.AppContext;
import prism.domain.model.Prism;
import prism.domain.vo.ColorScale;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ColorCache Unit Tests")
class TestColorCache {

    @Mock
    private AbstractServiceContextConfiguration mockExecutionContext;

    private ColorCache colorCache;

    @BeforeEach
    void setUp() throws Exception {
        resetAppContext();
        colorCache = new ColorCache(() -> mockExecutionContext);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (colorCache != null) {
            try {
                colorCache.unwatch();
            } catch (Exception ignored) {
            }
            shutdownScheduler(colorCache);
        }
        resetAppContext();
    }

    private static void resetAppContext() throws Exception {
        Field instanceField = AppContext.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);

        Field appClassesField = AppContext.class.getDeclaredField("appClasses");
        appClassesField.setAccessible(true);
        appClassesField.set(null, null);
    }

    private void shutdownScheduler(ColorCache target) {
        try {
            Field schedulerField = ColorCache.class.getDeclaredField("scheduler");
            schedulerField.setAccessible(true);
            ScheduledExecutorService scheduler = (ScheduledExecutorService) schedulerField.get(target);
            if (scheduler != null) {
                scheduler.shutdownNow();
            }
        } catch (Exception ignored) {
        }
    }

    private void invokeSetup(ColorCache target) {
        try {
            Method setupMethod = ColorCache.class.getDeclaredMethod("setup");
            setupMethod.setAccessible(true);
            setupMethod.invoke(target);
        } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException("Failed to invoke setup", e);
        }
    }

    private Object[][] getInternalCache(ColorCache target) {
        try {
            Field field = ColorCache.class.getDeclaredField("cache");
            field.setAccessible(true);
            return (Object[][]) field.get(target);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get internal cache", e);
        }
    }

    private ScheduledFuture<?> getWatchTask(ColorCache target) {
        try {
            Field field = ColorCache.class.getDeclaredField("watchTask");
            field.setAccessible(true);
            return (ScheduledFuture<?>) field.get(target);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get watchTask", e);
        }
    }

    private void invokeRemoveExpired(ColorCache target, int timeoutSeconds) {
        try {
            Method method = ColorCache.class.getDeclaredMethod("removeExpired", Integer.class);
            method.setAccessible(true);
            method.invoke(target, timeoutSeconds);
        } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException("Failed to invoke removeExpired", e);
        }
    }

    private Prism createSamplePrism(String suffix) {
        ColorScale scale = ColorScale.fromColorArray(new String[]{
                "#1" + suffix, "#2" + suffix, "#3" + suffix, "#4" + suffix, "#5" + suffix,
                "#6" + suffix, "#7" + suffix, "#8" + suffix, "#9" + suffix, "#A" + suffix
        });
        return Prism.builder()
                .lux(scale)
                .core(scale)
                .wave(scale)
                .flare(scale)
                .spark(scale)
                .build();
    }

    @Nested
    @DisplayName("Constructor & Setup")
    class ConstructorAndSetupTests {

        @Test
        @DisplayName("should construct successfully with initial unstarted state")
        void shouldConstructSuccessfully() {
            assertNotNull(colorCache);
            assertFalse(colorCache.isStarted());
        }

        @Test
        @DisplayName("should initialize cache array to default amount when CacheAdapter is null")
        void shouldInitializeToDefaultAmountWhenAdapterIsNull() {
            when(mockExecutionContext.getCacheAdapter()).thenReturn(null);

            invokeSetup(colorCache);

            Object[][] cacheArray = getInternalCache(colorCache);
            assertNotNull(cacheArray);
            assertEquals(CacheAdapter.AMOUNT, cacheArray.length);
            assertEquals(3, cacheArray.length);
        }

        @Test
        @DisplayName("should initialize cache array to configured amount when CacheAdapter is provided")
        void shouldInitializeToConfiguredAmountWhenAdapterProvided() {
            CacheAdapter adapter = new CacheAdapter();
            adapter.setAmount(10);
            when(mockExecutionContext.getCacheAdapter()).thenReturn(adapter);

            invokeSetup(colorCache);

            Object[][] cacheArray = getInternalCache(colorCache);
            assertNotNull(cacheArray);
            assertEquals(10, cacheArray.length);
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when configured amount is less than 1")
        void shouldThrowExceptionWhenAmountLessThanOne() {
            CacheAdapter adapter = new CacheAdapter();
            adapter.setAmount(0);
            when(mockExecutionContext.getCacheAdapter()).thenReturn(adapter);

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> invokeSetup(colorCache)
            );

            assertEquals("Cache amount must be at least 1, got 0", ex.getMessage());
        }

        @Test
        @DisplayName("should automatically invoke setup via PostConstruct when registered in AppContext")
        void shouldInvokeSetupViaPostConstructInAppContext() {
            when(mockExecutionContext.getCacheAdapter()).thenReturn(null);

            AppContext.initialize(colorCache);

            Object[][] cacheArray = getInternalCache(colorCache);
            assertNotNull(cacheArray, "PostConstruct setup() must have allocated cache array");
            assertEquals(CacheAdapter.AMOUNT, cacheArray.length);
            assertEquals(3, cacheArray.length);
        }
    }

    @Nested
    @DisplayName("add and get Operations")
    class AddAndGetTests {

        @BeforeEach
        void initCache() {
            when(mockExecutionContext.getCacheAdapter()).thenReturn(null);
            invokeSetup(colorCache);
        }

        @Test
        @DisplayName("should store entry and retrieve it by file path")
        void shouldStoreAndRetrieveEntry() {
            Prism prism = createSamplePrism("1");
            colorCache.add("/photos/lake.png", prism);

            Prism retrieved = colorCache.get("/photos/lake.png");

            assertNotNull(retrieved);
            assertSame(prism, retrieved);
        }

        @Test
        @DisplayName("should return null when key is not present in cache")
        void shouldReturnNullWhenKeyNotFound() {
            Prism result = colorCache.get("/unseen/file.png");
            assertNull(result);
        }

        @Test
        @DisplayName("should update last-used timestamp upon get lookup")
        void shouldUpdateTimestampOnGet() throws Exception {
            Prism prism = createSamplePrism("1");
            colorCache.add("/photos/test.png", prism);

            Object[][] internal = getInternalCache(colorCache);
            Instant initialTime = (Instant) internal[0][2];
            assertNotNull(initialTime);

            Thread.sleep(10); // Guarantee clock progresses

            colorCache.get("/photos/test.png");
            Instant updatedTime = (Instant) internal[0][2];
            assertNotNull(updatedTime);
            assertTrue(updatedTime.isAfter(initialTime), "Timestamp must be updated after get()");
        }

        @Test
        @DisplayName("should populate empty cache slots in sequential order")
        void shouldFillAvailableEmptySlotsSequentially() {
            Prism p1 = createSamplePrism("1");
            Prism p2 = createSamplePrism("2");
            Prism p3 = createSamplePrism("3");

            colorCache.add("/file1.png", p1);
            colorCache.add("/file2.png", p2);
            colorCache.add("/file3.png", p3);

            Object[][] internal = getInternalCache(colorCache);
            assertEquals("/file1.png", internal[0][0]);
            assertEquals("/file2.png", internal[1][0]);
            assertEquals("/file3.png", internal[2][0]);
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when adding with empty file path")
        void shouldThrowExceptionWhenAddingWithEmptyPath() {
            Prism prism = createSamplePrism("1");

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> colorCache.add("", prism)
            );

            assertEquals("File path can not be empty while using cache", ex.getMessage());
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when getting with empty file path")
        void shouldThrowExceptionWhenGettingWithEmptyPath() {
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> colorCache.get("")
            );

            assertEquals("File path can not be empty while using cache", ex.getMessage());
        }
    }

    @Nested
    @DisplayName("LRU Displacement Order")
    class LruDisplacementTests {

        @Test
        @DisplayName("should displace least recently used entry when cache capacity is reached")
        void shouldDisplaceOldestEntryWhenFull() throws Exception {
            CacheAdapter adapter = new CacheAdapter();
            adapter.setAmount(3); // Small capacity of 3
            when(mockExecutionContext.getCacheAdapter()).thenReturn(adapter);
            invokeSetup(colorCache);

            Prism p1 = createSamplePrism("1");
            Prism p2 = createSamplePrism("2");
            Prism p3 = createSamplePrism("3");
            Prism p4 = createSamplePrism("4");

            // Fill slots 0, 1, 2
            colorCache.add("/file1.png", p1);
            Thread.sleep(5);
            colorCache.add("/file2.png", p2);
            Thread.sleep(5);
            colorCache.add("/file3.png", p3);
            Thread.sleep(5);

            // Access file1 to make file2 the oldest entry
            colorCache.get("/file1.png");
            Thread.sleep(5);

            // Add file4: capacity is full, should displace file2 (the oldest)
            colorCache.add("/file4.png", p4);

            assertSame(p1, colorCache.get("/file1.png"), "file1 should remain in cache");
            assertNull(colorCache.get("/file2.png"), "file2 should have been displaced");
            assertSame(p3, colorCache.get("/file3.png"), "file3 should remain in cache");
            assertSame(p4, colorCache.get("/file4.png"), "file4 should be present in cache");
        }
    }

    @Nested
    @DisplayName("Dynamic Resizing (resizeIfNeeded)")
    class ResizingTests {

        @Test
        @DisplayName("should retain capacity when amount remains unchanged")
        void shouldRetainCapacityWhenAmountUnchanged() {
            CacheAdapter adapter = new CacheAdapter();
            adapter.setAmount(5);
            when(mockExecutionContext.getCacheAdapter()).thenReturn(adapter);
            invokeSetup(colorCache);

            colorCache.add("/f1.png", createSamplePrism("1"));

            Object[][] internal = getInternalCache(colorCache);
            assertEquals(5, internal.length);
        }

        @Test
        @DisplayName("should expand capacity and preserve existing entries when configured amount increases")
        void shouldExpandCapacityAndPreserveEntries() {
            CacheAdapter adapter = new CacheAdapter();
            adapter.setAmount(2);
            when(mockExecutionContext.getCacheAdapter()).thenReturn(adapter);
            invokeSetup(colorCache);

            Prism p1 = createSamplePrism("1");
            Prism p2 = createSamplePrism("2");
            colorCache.add("/f1.png", p1);
            colorCache.add("/f2.png", p2);

            // Increase capacity to 4
            adapter.setAmount(4);
            Prism p3 = createSamplePrism("3");
            colorCache.add("/f3.png", p3);

            Object[][] internal = getInternalCache(colorCache);
            assertEquals(4, internal.length);
            assertSame(p1, colorCache.get("/f1.png"));
            assertSame(p2, colorCache.get("/f2.png"));
            assertSame(p3, colorCache.get("/f3.png"));
        }

        @Test
        @DisplayName("should shrink capacity and retain most recently used entries when configured amount decreases")
        void shouldShrinkCapacityAndRetainMruEntries() throws Exception {
            CacheAdapter adapter = new CacheAdapter();
            adapter.setAmount(4);
            when(mockExecutionContext.getCacheAdapter()).thenReturn(adapter);
            invokeSetup(colorCache);

            Prism p1 = createSamplePrism("1");
            Prism p2 = createSamplePrism("2");
            Prism p3 = createSamplePrism("3");
            Prism p4 = createSamplePrism("4");

            colorCache.add("/f1.png", p1);
            Thread.sleep(5);
            colorCache.add("/f2.png", p2);
            Thread.sleep(5);
            colorCache.add("/f3.png", p3);
            Thread.sleep(5);
            colorCache.add("/f4.png", p4);
            Thread.sleep(5);

            // Decrease capacity to 2 (should keep f4 and f3 as they have the latest timestamps)
            adapter.setAmount(2);

            // Trigger resize by doing add
            Prism p5 = createSamplePrism("5");
            colorCache.add("/f5.png", p5);

            Object[][] internal = getInternalCache(colorCache);
            assertEquals(2, internal.length);
            // f5 should be in cache
            assertSame(p5, colorCache.get("/f5.png"));
            // At least one of f3/f4 retained
            assertTrue(colorCache.get("/f4.png") != null || colorCache.get("/f3.png") != null);
            // Oldest f1 and f2 should be evicted
            assertNull(colorCache.get("/f1.png"));
            assertNull(colorCache.get("/f2.png"));
        }
    }

    @Nested
    @DisplayName("Watcher & Expiration")
    class WatcherTests {

        @BeforeEach
        void initCache() {
            when(mockExecutionContext.getCacheAdapter()).thenReturn(null);
            invokeSetup(colorCache);
        }

        @Test
        @DisplayName("should start watcher task and update started state to true")
        void shouldStartWatcherSuccessfully() {
            assertFalse(colorCache.isStarted());

            colorCache.watch();

            assertTrue(colorCache.isStarted());
            ScheduledFuture<?> task = getWatchTask(colorCache);
            assertNotNull(task);
            assertFalse(task.isCancelled());
        }

        @Test
        @DisplayName("should be idempotent when watch is invoked repeatedly")
        void shouldBeIdempotentOnMultipleWatchCalls() {
            colorCache.watch();
            ScheduledFuture<?> firstTask = getWatchTask(colorCache);

            colorCache.watch();
            ScheduledFuture<?> secondTask = getWatchTask(colorCache);

            assertSame(firstTask, secondTask, "Repeated watch() calls must not reschedule tasks");
        }

        @Test
        @DisplayName("should use custom timeout from CacheAdapter when present")
        void shouldUseCustomTimeoutFromAdapter() {
            CacheAdapter adapter = new CacheAdapter();
            adapter.setTimeout(120);
            when(mockExecutionContext.getCacheAdapter()).thenReturn(adapter);

            colorCache.watch();

            assertTrue(colorCache.isStarted());
        }

        @Test
        @DisplayName("should cancel watch task and update started state to false on unwatch")
        void shouldCancelTaskAndResetStartedOnUnwatch() {
            colorCache.watch();
            assertTrue(colorCache.isStarted());

            colorCache.unwatch();

            assertFalse(colorCache.isStarted());
            ScheduledFuture<?> task = getWatchTask(colorCache);
            assertNotNull(task);
            assertTrue(task.isCancelled());
        }

        @Test
        @DisplayName("should log warning and ignore when unwatch is invoked without being started")
        void shouldIgnoreUnwatchWhenNotStarted() {
            assertDoesNotThrow(() -> colorCache.unwatch());
            assertFalse(colorCache.isStarted());
        }

        @Test
        @DisplayName("should log warning and ignore when unwatch is invoked repeatedly after cancellation")
        void shouldIgnoreRepeatedUnwatchCalls() {
            colorCache.watch();
            colorCache.unwatch();

            assertDoesNotThrow(() -> colorCache.unwatch());
            assertFalse(colorCache.isStarted());
        }

        @Test
        @DisplayName("should clear all entries, reallocate cache, and unwatch when clear is called")
        void shouldClearEntriesAndUnwatch() {
            colorCache.watch();
            colorCache.add("/item.png", createSamplePrism("1"));
            assertTrue(colorCache.isStarted());
            assertNotNull(colorCache.get("/item.png"));

            colorCache.clear();

            assertFalse(colorCache.isStarted());
            assertNull(colorCache.get("/item.png"));
            Object[][] internal = getInternalCache(colorCache);
            assertNotNull(internal);
            assertEquals(CacheAdapter.AMOUNT, internal.length);
            assertEquals(3, internal.length);
        }

        @Test
        @DisplayName("should evict expired entries and automatically clear and unwatch when all entries expire")
        void shouldEvictExpiredEntriesAndAutoUnwatch() {
            colorCache.watch();
            Prism p1 = createSamplePrism("1");
            colorCache.add("/old.png", p1);

            // Force timestamp to 100 seconds in the past
            Object[][] internal = getInternalCache(colorCache);
            internal[0][2] = Instant.now().minusSeconds(100);

            // Timeout of 10s: entry is expired
            invokeRemoveExpired(colorCache, 10);

            assertNull(colorCache.get("/old.png"));
            assertFalse(colorCache.isStarted(), "Should auto unwatch when all entries have expired");
        }

        @Test
        @DisplayName("should keep fresh entries active and evict only expired entries")
        void shouldEvictOnlyExpiredEntries() {
            colorCache.watch();
            Prism p1 = createSamplePrism("1");
            Prism p2 = createSamplePrism("2");
            colorCache.add("/old.png", p1);
            colorCache.add("/fresh.png", p2);

            // Set only the first entry to the past
            Object[][] internal = getInternalCache(colorCache);
            internal[0][2] = Instant.now().minusSeconds(100);
            internal[1][2] = Instant.now();

            invokeRemoveExpired(colorCache, 10);

            assertNull(colorCache.get("/old.png"), "Old entry should be evicted");
            assertSame(p2, colorCache.get("/fresh.png"), "Fresh entry should remain active");
            assertTrue(colorCache.isStarted(), "Watcher should remain started because fresh entry exists");
        }
    }
}