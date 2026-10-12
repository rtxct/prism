package prism.application.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import prism.application.component.ColorCache;
import prism.configuration.context.AbstractServiceContextConfiguration;
import prism.configuration.context.AppContext;
import prism.domain.model.Prism;
import prism.domain.vo.ColorScale;

import java.lang.reflect.Field;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CacheService Unit Tests")
class TestCacheService {

    @Mock
    private ColorCache mockColorCache;

    @Mock
    private Prism mockPrism;

    @Mock
    private AbstractServiceContextConfiguration mockExecutionContext;

    private static final String SAMPLE_FILE_PATH = "/home/user/wallpapers/sunset.png";

    @BeforeEach
    void setUp() throws Exception {
        resetAppContext();
        Field execField = ColorCache.class.getDeclaredField("appExecutionContext");
        execField.setAccessible(true);
        execField.set(mockColorCache, (Supplier<?>) () -> mockExecutionContext);
    }

    @AfterEach
    void tearDown() throws Exception {
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

    private CacheService createInitializedService() {
        AppContext.initialize(mockColorCache);
        return new CacheService();
    }

    private Prism createSamplePrism() {
        ColorScale scale = ColorScale.fromColorArray(new String[]{
                "#111111", "#222222", "#333333", "#444444", "#555555",
                "#666666", "#777777", "#888888", "#999999", "#AAAAAA"
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
    @DisplayName("Constructor & AppContext Integration")
    class ConstructorAndContextTests {

        @Test
        @DisplayName("should construct successfully when AppContext is initialized")
        void shouldConstructSuccessfullyWhenContextInitialized() {
            CacheService service = createInitializedService();
            assertNotNull(service);
        }

        @Test
        @DisplayName("should not evaluate AppContext dependency during construction")
        void shouldNotEvaluateAppContextDuringConstruction() {
            // AppContext uninitialized, constructor still succeeds due to lazy supplier
            CacheService service = new CacheService();
            assertNotNull(service);
        }

        @Test
        @DisplayName("should throw IllegalStateException when add is invoked without AppContext initialization")
        void shouldThrowExceptionWhenAddCalledWithoutAppContext() {
            CacheService service = new CacheService();

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> service.add(SAMPLE_FILE_PATH, mockPrism)
            );

            assertEquals("App context has not been initialized", ex.getMessage());
        }

        @Test
        @DisplayName("should throw IllegalStateException when get is invoked without AppContext initialization")
        void shouldThrowExceptionWhenGetCalledWithoutAppContext() {
            CacheService service = new CacheService();

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> service.get(SAMPLE_FILE_PATH)
            );

            assertEquals("App context has not been initialized", ex.getMessage());
        }

        @Test
        @DisplayName("should throw IllegalStateException on service call when ColorCache is not registered")
        void shouldThrowExceptionWhenColorCacheNotRegistered() {
            AppContext.initialize(new Object());
            CacheService service = new CacheService();

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> service.get(SAMPLE_FILE_PATH)
            );

            assertTrue(ex.getMessage().contains("Class not registered in context: " + ColorCache.class.getName()));
        }
    }

    @Nested
    @DisplayName("add")
    class AddTests {

        @Test
        @DisplayName("should start cache watcher and add entry when watcher is not started")
        void shouldStartWatcherAndAddEntryWhenNotStarted() {
            CacheService service = createInitializedService();
            when(mockColorCache.isStarted()).thenReturn(false);

            service.add(SAMPLE_FILE_PATH, mockPrism);

            verify(mockColorCache, times(1)).isStarted();
            verify(mockColorCache, times(1)).watch();
            verify(mockColorCache, times(1)).add(SAMPLE_FILE_PATH, mockPrism);
        }

        @Test
        @DisplayName("should not start cache watcher when watcher is already running")
        void shouldNotStartWatcherWhenAlreadyStarted() {
            CacheService service = createInitializedService();
            when(mockColorCache.isStarted()).thenReturn(true);

            service.add(SAMPLE_FILE_PATH, mockPrism);

            verify(mockColorCache, times(1)).isStarted();
            verify(mockColorCache, never()).watch();
            verify(mockColorCache, times(1)).add(SAMPLE_FILE_PATH, mockPrism);
        }

        @Test
        @DisplayName("should pass exact file path and Prism instance references to cache")
        void shouldPassExactArgumentsToCache() {
            CacheService service = createInitializedService();
            when(mockColorCache.isStarted()).thenReturn(true);
            String customPath = "/opt/images/mountains.jpg";

            service.add(customPath, mockPrism);

            verify(mockColorCache, times(1)).add(customPath, mockPrism);
        }

        @Test
        @DisplayName("should propagate exception when cache watch fails and abort add")
        void shouldPropagateExceptionWhenWatchFails() {
            CacheService service = createInitializedService();
            when(mockColorCache.isStarted()).thenReturn(false);
            doThrow(new IllegalStateException("Scheduler failed to start"))
                    .when(mockColorCache).watch();

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> service.add(SAMPLE_FILE_PATH, mockPrism)
            );

            assertEquals("Scheduler failed to start", ex.getMessage());
            verify(mockColorCache, never()).add(any(), any());
        }

        @Test
        @DisplayName("should propagate exception when cache add fails")
        void shouldPropagateExceptionWhenCacheAddFails() {
            CacheService service = createInitializedService();
            when(mockColorCache.isStarted()).thenReturn(true);
            doThrow(new IllegalArgumentException("File path can not be empty while using cache"))
                    .when(mockColorCache).add(any(), any());

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> service.add("", mockPrism)
            );

            assertEquals("File path can not be empty while using cache", ex.getMessage());
        }
    }

    @Nested
    @DisplayName("get")
    class GetTests {

        @Test
        @DisplayName("should return cached Prism instance when file path exists in cache")
        void shouldReturnCachedPrismWhenExists() {
            CacheService service = createInitializedService();
            when(mockColorCache.get(SAMPLE_FILE_PATH)).thenReturn(mockPrism);

            Prism result = service.get(SAMPLE_FILE_PATH);

            assertNotNull(result);
            assertSame(mockPrism, result);
            verify(mockColorCache, times(1)).get(SAMPLE_FILE_PATH);
        }

        @Test
        @DisplayName("should return null when file path is not found in cache")
        void shouldReturnNullWhenNotFound() {
            CacheService service = createInitializedService();
            when(mockColorCache.get("/unseen/image.png")).thenReturn(null);

            Prism result = service.get("/unseen/image.png");

            assertNull(result);
            verify(mockColorCache, times(1)).get("/unseen/image.png");
        }

        @Test
        @DisplayName("should pass exact file path key to cache lookup")
        void shouldPassExactPathToCacheGet() {
            CacheService service = createInitializedService();
            String customKey = "/var/art/neon.webp";
            when(mockColorCache.get(customKey)).thenReturn(null);

            service.get(customKey);

            verify(mockColorCache, times(1)).get(customKey);
        }

        @Test
        @DisplayName("should not interact with watcher during get lookup")
        void shouldNotInteractWithWatcherDuringGet() {
            CacheService service = createInitializedService();
            when(mockColorCache.get(SAMPLE_FILE_PATH)).thenReturn(mockPrism);

            service.get(SAMPLE_FILE_PATH);

            verify(mockColorCache, never()).isStarted();
            verify(mockColorCache, never()).watch();
        }

        @Test
        @DisplayName("should propagate exception when cache get throws exception")
        void shouldPropagateExceptionWhenCacheGetFails() {
            CacheService service = createInitializedService();
            when(mockColorCache.get(""))
                    .thenThrow(new IllegalArgumentException("File path can not be empty while using cache"));

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> service.get("")
            );

            assertEquals("File path can not be empty while using cache", ex.getMessage());
        }
    }

    @Nested
    @DisplayName("End-to-End Integration with Real ColorCache")
    class EndToEndIntegrationTests {

        @Test
        @DisplayName("should automatically start watcher and store entry on first add")
        void shouldStartWatcherAndStoreEntryOnFirstAdd() {
            when(mockExecutionContext.getCacheAdapter()).thenReturn(null);
            ColorCache realCache = new ColorCache(() -> mockExecutionContext);
            AppContext.initialize(realCache);

            CacheService service = new CacheService();
            assertFalse(realCache.isStarted(), "Watcher should initially not be started");

            Prism samplePrism = createSamplePrism();
            service.add(SAMPLE_FILE_PATH, samplePrism);

            assertTrue(realCache.isStarted(), "Watcher should be started after first add");
            Prism retrieved = service.get(SAMPLE_FILE_PATH);
            assertNotNull(retrieved);
            assertSame(samplePrism, retrieved);

            realCache.unwatch();
        }

        @Test
        @DisplayName("should return stored Prism and return null for non-existent key")
        void shouldRetrieveStoredPrismAndReturnNullForMiss() {
            when(mockExecutionContext.getCacheAdapter()).thenReturn(null);
            ColorCache realCache = new ColorCache(() -> mockExecutionContext);
            AppContext.initialize(realCache);

            CacheService service = new CacheService();
            Prism samplePrism = createSamplePrism();

            service.add("/path/a.png", samplePrism);

            assertSame(samplePrism, service.get("/path/a.png"));
            assertNull(service.get("/path/missing.png"));

            realCache.unwatch();
        }

        @Test
        @DisplayName("should store multiple distinct entries and retrieve each successfully")
        void shouldStoreMultipleDistinctEntries() {
            when(mockExecutionContext.getCacheAdapter()).thenReturn(null);
            ColorCache realCache = new ColorCache(() -> mockExecutionContext);
            AppContext.initialize(realCache);

            CacheService service = new CacheService();
            Prism prism1 = createSamplePrism();
            Prism prism2 = createSamplePrism();

            service.add("/path/first.png", prism1);
            service.add("/path/second.png", prism2);

            assertSame(prism1, service.get("/path/first.png"));
            assertSame(prism2, service.get("/path/second.png"));

            realCache.unwatch();
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when adding with empty file path")
        void shouldThrowExceptionWhenAddingWithEmptyPath() {
            when(mockExecutionContext.getCacheAdapter()).thenReturn(null);
            ColorCache realCache = new ColorCache(() -> mockExecutionContext);
            AppContext.initialize(realCache);

            CacheService service = new CacheService();
            Prism samplePrism = createSamplePrism();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> service.add("", samplePrism)
            );

            assertEquals("File path can not be empty while using cache", ex.getMessage());
            realCache.unwatch();
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when getting with empty file path")
        void shouldThrowExceptionWhenGettingWithEmptyPath() {
            when(mockExecutionContext.getCacheAdapter()).thenReturn(null);
            ColorCache realCache = new ColorCache(() -> mockExecutionContext);
            AppContext.initialize(realCache);

            CacheService service = new CacheService();

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> service.get("")
            );

            assertEquals("File path can not be empty while using cache", ex.getMessage());
            realCache.unwatch();
        }
    }
}