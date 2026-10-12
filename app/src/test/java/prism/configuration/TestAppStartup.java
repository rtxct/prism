package prism.configuration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import prism.application.component.ColorCache;
import prism.application.component.ImageReader;
import prism.application.component.SocketSubscriber;
import prism.application.component.color.ColorWriter;
import prism.application.component.color.RoleSelector;
import prism.application.service.CacheService;
import prism.application.service.ListeningService;
import prism.application.service.StorageService;
import prism.application.service.WriteService;
import prism.configuration.adapter.CacheAdapter;
import prism.configuration.context.AbstractServiceContextConfiguration;
import prism.configuration.context.AppContext;
import prism.configuration.context.DaemonContext;
import prism.infrastructure.daemon.SocketServer;
import prism.infrastructure.filesystem.FileDataWriter;
import prism.infrastructure.filesystem.FileImageReader;

import java.lang.reflect.Field;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AppStartup Unit Tests")
class TestAppStartup {

    @Mock
    private AbstractServiceContextConfiguration mockContext;

    @Mock
    private Supplier<AbstractServiceContextConfiguration> mockSupplier;

    private static class TestableAppStartup extends AppStartup {
        TestableAppStartup(Supplier<? extends AbstractServiceContextConfiguration> supplier) {
            super(supplier);
        }
    }

    @BeforeEach
    @AfterEach
    void resetAppContextSingleton() throws Exception {
        Field instanceField = AppContext.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);

        Field appClassesField = AppContext.class.getDeclaredField("appClasses");
        appClassesField.setAccessible(true);
        appClassesField.set(null, null);
    }

    @Nested
    @DisplayName("Lifecycle & Bootstrapping")
    class LifecycleAndBootstrapTests {

        @Test
        @DisplayName("should initialize AppContext during construction")
        void shouldInitializeAppContextDuringConstruction() {
            when(mockSupplier.get()).thenReturn(mockContext);

            AppStartup startup = new TestableAppStartup(mockSupplier);

            assertNotNull(startup.getAppContext());
        }

        @Test
        @DisplayName("should match global AppContext.instance()")
        void shouldMatchGlobalAppContextInstance() {
            when(mockSupplier.get()).thenReturn(mockContext);

            AppStartup startup = new TestableAppStartup(mockSupplier);

            assertSame(AppContext.instance(), startup.getAppContext());
        }

        @Test
        @DisplayName("should fail bootstrap with RuntimeException wrapping NullPointerException when supplier is null")
        void shouldThrowExceptionWhenSupplierIsNull() {
            RuntimeException exception = assertThrows(
                    RuntimeException.class,
                    () -> new TestableAppStartup(null)
            );

            assertTrue(exception.getMessage().contains("App context could not be initialized"));
            assertInstanceOf(java.lang.reflect.InvocationTargetException.class, exception.getCause());
            assertInstanceOf(NullPointerException.class, exception.getCause().getCause());
        }

        @Test
        @DisplayName("should throw IllegalStateException when AppContext is already initialized")
        void shouldThrowExceptionWhenAlreadyInitialized() {
            when(mockSupplier.get()).thenReturn(mockContext);

            // First startup succeeds
            new TestableAppStartup(mockSupplier);

            // Second startup should fail because AppContext singleton is already active
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> new TestableAppStartup(mockSupplier)
            );

            assertEquals("App context already initialized", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("Component Registration")
    class ComponentRegistrationTests {

        private AppStartup startup;

        @BeforeEach
        void setUp() {
            when(mockSupplier.get()).thenReturn(mockContext);
            startup = new TestableAppStartup(mockSupplier);
        }

        @Test
        @DisplayName("should register ColorCache component")
        void shouldRegisterColorCache() {
            ColorCache component = startup.getAppContext().getClass(ColorCache.class);
            assertNotNull(component);
            assertInstanceOf(ColorCache.class, component);
        }

        @Test
        @DisplayName("should register RoleSelector component")
        void shouldRegisterRoleSelector() {
            RoleSelector component = startup.getAppContext().getClass(RoleSelector.class);
            assertNotNull(component);
            assertInstanceOf(RoleSelector.class, component);
        }

        @Test
        @DisplayName("should register SocketSubscriber component")
        void shouldRegisterSocketSubscriber() {
            SocketSubscriber component = startup.getAppContext().getClass(SocketSubscriber.class);
            assertNotNull(component);
            assertInstanceOf(SocketSubscriber.class, component);
        }

        @Test
        @DisplayName("should register ColorWriter component")
        void shouldRegisterColorWriter() {
            ColorWriter component = startup.getAppContext().getClass(ColorWriter.class);
            assertNotNull(component);
            assertInstanceOf(ColorWriter.class, component);
        }

        @Test
        @DisplayName("should register ImageReader component")
        void shouldRegisterImageReader() {
            ImageReader component = startup.getAppContext().getClass(ImageReader.class);
            assertNotNull(component);
            assertInstanceOf(ImageReader.class, component);
        }

        @Test
        @DisplayName("should register WriteService component")
        void shouldRegisterWriteService() {
            WriteService component = startup.getAppContext().getClass(WriteService.class);
            assertNotNull(component);
            assertInstanceOf(WriteService.class, component);
        }

        @Test
        @DisplayName("should register StorageService component")
        void shouldRegisterStorageService() {
            StorageService component = startup.getAppContext().getClass(StorageService.class);
            assertNotNull(component);
            assertInstanceOf(StorageService.class, component);
        }

        @Test
        @DisplayName("should register ListeningService component")
        void shouldRegisterListeningService() {
            ListeningService component = startup.getAppContext().getClass(ListeningService.class);
            assertNotNull(component);
            assertInstanceOf(ListeningService.class, component);
        }

        @Test
        @DisplayName("should register CacheService component")
        void shouldRegisterCacheService() {
            CacheService component = startup.getAppContext().getClass(CacheService.class);
            assertNotNull(component);
            assertInstanceOf(CacheService.class, component);
        }

        @Test
        @DisplayName("should register DaemonContext component")
        void shouldRegisterDaemonContext() {
            DaemonContext component = startup.getAppContext().getClass(DaemonContext.class);
            assertNotNull(component);
            assertInstanceOf(DaemonContext.class, component);
        }

        @Test
        @DisplayName("should register SocketServer component")
        void shouldRegisterSocketServer() {
            SocketServer component = startup.getAppContext().getClass(SocketServer.class);
            assertNotNull(component);
            assertInstanceOf(SocketServer.class, component);
        }

        @Test
        @DisplayName("should register FileImageReader component")
        void shouldRegisterFileImageReader() {
            FileImageReader component = startup.getAppContext().getClass(FileImageReader.class);
            assertNotNull(component);
            assertInstanceOf(FileImageReader.class, component);
        }

        @Test
        @DisplayName("should register FileDataWriter component")
        void shouldRegisterFileDataWriter() {
            FileDataWriter component = startup.getAppContext().getClass(FileDataWriter.class);
            assertNotNull(component);
            assertInstanceOf(FileDataWriter.class, component);
        }
    }

    @Nested
    @DisplayName("Execution Context Supplier Delegation")
    class ExecutionContextDelegationTests {

        @Test
        @DisplayName("should invoke execution context supplier during ColorCache PostConstruct")
        void shouldInvokeSupplierDuringPostConstruct() {
            when(mockSupplier.get()).thenReturn(mockContext);

            new TestableAppStartup(mockSupplier);

            verify(mockSupplier).get();
        }

        @Test
        @DisplayName("should configure ColorCache with custom amount from CacheAdapter")
        void shouldConfigureColorCacheWithCustomAmount() {
            CacheAdapter customAdapter = new CacheAdapter();
            customAdapter.setAmount(7);
            when(mockContext.getCacheAdapter()).thenReturn(customAdapter);
            when(mockSupplier.get()).thenReturn(mockContext);

            AppStartup startup = new TestableAppStartup(mockSupplier);

            ColorCache cache = startup.getAppContext().getClass(ColorCache.class);
            assertNotNull(cache);
        }

        @Test
        @DisplayName("should fail bootstrap with RuntimeException when PostConstruct encounters invalid configuration")
        void shouldFailBootstrapWhenPostConstructFails() {
            CacheAdapter invalidAdapter = new CacheAdapter();
            invalidAdapter.setAmount(0); // Cache amount must be at least 1
            when(mockContext.getCacheAdapter()).thenReturn(invalidAdapter);
            when(mockSupplier.get()).thenReturn(mockContext);

            RuntimeException exception = assertThrows(
                    RuntimeException.class,
                    () -> new TestableAppStartup(mockSupplier)
            );

            assertTrue(exception.getMessage().contains("App context could not be initialized"));
            assertInstanceOf(java.lang.reflect.InvocationTargetException.class, exception.getCause());
            assertInstanceOf(IllegalArgumentException.class, exception.getCause().getCause());
        }
    }
}