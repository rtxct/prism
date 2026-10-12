package prism.configuration.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import prism.configuration.annotations.PostConstruct;

import java.lang.reflect.Field;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AppContext Unit Tests")
class TestAppContext {

    @BeforeEach
    @AfterEach
    void resetContextSingleton() throws Exception {
        Field instanceField = AppContext.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);

        Field appClassesField = AppContext.class.getDeclaredField("appClasses");
        appClassesField.setAccessible(true);
        appClassesField.set(null, null);
    }

    static class SampleComponentA {
        boolean postConstructCalled = false;

        @PostConstruct
        void init() {
            postConstructCalled = true;
        }
    }

    static class SampleComponentB {
    }

    static class FailingPostConstructComponent {
        @PostConstruct
        void fail() {
            throw new IllegalStateException("Simulated init failure");
        }
    }

    @Nested
    @DisplayName("initialize")
    class InitializeTests {

        @Test
        @DisplayName("should initialize context singleton with provided component instances")
        void shouldInitializeWithGivenObjects() {
            SampleComponentA a = new SampleComponentA();
            SampleComponentB b = new SampleComponentB();

            AppContext context = AppContext.initialize(a, b);

            assertNotNull(context);
            assertSame(context, AppContext.instance());
            assertSame(a, context.getClass(SampleComponentA.class));
            assertSame(b, context.getClass(SampleComponentB.class));
        }

        @Test
        @DisplayName("should throw IllegalStateException when already initialized")
        void shouldThrowExceptionWhenAlreadyInitialized() {
            AppContext.initialize(new SampleComponentA());

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> AppContext.initialize(new SampleComponentB())
            );

            assertEquals("App context already initialized", ex.getMessage());
        }

        @Test
        @DisplayName("should invoke @PostConstruct annotated methods on registered components")
        void shouldInvokePostConstructOnComponents() {
            SampleComponentA a = new SampleComponentA();

            AppContext.initialize(a);

            assertTrue(a.postConstructCalled, "@PostConstruct method should have been invoked");
        }

        @Test
        @DisplayName("should wrap and throw RuntimeException when @PostConstruct invocation fails")
        void shouldWrapAndThrowRuntimeExceptionWhenPostConstructFails() {
            FailingPostConstructComponent failing = new FailingPostConstructComponent();

            RuntimeException ex = assertThrows(
                    RuntimeException.class,
                    () -> AppContext.initialize(failing)
            );

            assertTrue(ex.getMessage().contains("App context could not be initialized while applying post processing methods."));
            assertInstanceOf(java.lang.reflect.InvocationTargetException.class, ex.getCause());
            assertInstanceOf(IllegalStateException.class, ex.getCause().getCause());
        }
    }

    @Nested
    @DisplayName("instance")
    class InstanceTests {

        @Test
        @DisplayName("should return initialized singleton instance")
        void shouldReturnInitializedInstance() {
            AppContext context = AppContext.initialize(new SampleComponentA());
            assertSame(context, AppContext.instance());
        }

        @Test
        @DisplayName("should throw IllegalStateException when instance is requested before initialization")
        void shouldThrowExceptionWhenNotInitialized() {
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    AppContext::instance
            );

            assertEquals("App context has not been initialized", ex.getMessage());
        }
    }

    @Nested
    @DisplayName("getClass")
    class GetClassTests {

        @Test
        @DisplayName("should retrieve registered component instance by its class")
        void shouldRetrieveRegisteredComponent() {
            SampleComponentA a = new SampleComponentA();
            AppContext context = AppContext.initialize(a);

            SampleComponentA retrieved = context.getClass(SampleComponentA.class);

            assertSame(a, retrieved);
        }

        @Test
        @DisplayName("should throw IllegalStateException when component class is not registered")
        void shouldThrowExceptionWhenComponentNotRegistered() {
            AppContext context = AppContext.initialize(new SampleComponentA());

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> context.getClass(SampleComponentB.class)
            );

            assertTrue(ex.getMessage().contains("Class not registered in context: " + SampleComponentB.class.getName()));
        }

        @Test
        @DisplayName("should throw NullPointerException when clazz argument is null")
        void shouldThrowExceptionWhenClazzIsNull() {
            AppContext context = AppContext.initialize(new SampleComponentA());

            assertThrows(NullPointerException.class, () -> context.getClass(null));
        }
    }

    @Nested
    @DisplayName("getClassLazy")
    class GetClassLazyTests {

        @Test
        @DisplayName("should return supplier that lazily retrieves registered component")
        void shouldReturnSupplierForLazyRetrieval() {
            SampleComponentA a = new SampleComponentA();
            AppContext.initialize(a);

            Supplier<SampleComponentA> supplier = AppContext.getClassLazy(SampleComponentA.class);

            assertNotNull(supplier);
            assertSame(a, supplier.get());
        }

        @Test
        @DisplayName("should throw IllegalStateException when lazy supplier is evaluated before initialization")
        void shouldThrowExceptionWhenSupplierEvaluatedBeforeInitialization() {
            Supplier<SampleComponentA> supplier = AppContext.getClassLazy(SampleComponentA.class);

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    supplier::get
            );

            assertEquals("App context has not been initialized", ex.getMessage());
        }

        @Test
        @DisplayName("should throw IllegalStateException when lazy supplier is evaluated for unregistered component")
        void shouldThrowExceptionWhenSupplierEvaluatedForUnregisteredComponent() {
            AppContext.initialize(new SampleComponentA());

            Supplier<SampleComponentB> supplier = AppContext.getClassLazy(SampleComponentB.class);

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    supplier::get
            );

            assertTrue(ex.getMessage().contains("Class not registered in context: " + SampleComponentB.class.getName()));
        }
    }

    @Nested
    @DisplayName("setClass")
    class SetClassTests {

        @Test
        @DisplayName("should dynamically register new component instance into context")
        void shouldDynamicallyRegisterComponent() {
            AppContext context = AppContext.initialize(new SampleComponentA());

            SampleComponentB b = new SampleComponentB();
            context.setClass(b);

            assertSame(b, context.getClass(SampleComponentB.class));
        }

        @Test
        @DisplayName("should overwrite existing registered component of same class")
        void shouldOverwriteExistingComponent() {
            SampleComponentA initial = new SampleComponentA();
            AppContext context = AppContext.initialize(initial);

            SampleComponentA replacement = new SampleComponentA();
            context.setClass(replacement);

            assertSame(replacement, context.getClass(SampleComponentA.class));
        }

        @Test
        @DisplayName("should throw NullPointerException when argument is null")
        void shouldThrowExceptionWhenObjectIsNull() {
            AppContext context = AppContext.initialize(new SampleComponentA());

            assertThrows(NullPointerException.class, () -> context.setClass(null));
        }
    }
}