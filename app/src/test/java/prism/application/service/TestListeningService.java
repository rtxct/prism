package prism.application.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import prism.application.component.SocketSubscriber;
import prism.configuration.context.AppContext;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ListeningService Unit Tests")
class TestListeningService {

    @Mock
    private SocketSubscriber mockSocketSubscriber;

    @Mock
    private Consumer<String> mockConsumer;

    private static final Duration ORIGINAL_RETRY_INTERVAL = Duration.ofSeconds(5);
    private static final Duration TEST_RETRY_INTERVAL = Duration.ofMillis(1);

    @BeforeEach
    void setUp() throws Exception {
        resetAppContext();
        ListeningService.RETRY_INTERVAL = TEST_RETRY_INTERVAL;
    }

    @AfterEach
    void tearDown() throws Exception {
        resetAppContext();
        ListeningService.RETRY_INTERVAL = ORIGINAL_RETRY_INTERVAL;

        // Clear interrupted status if left set by interruption test
        Thread.interrupted();
    }

    private static void resetAppContext() throws Exception {
        Field instanceField = AppContext.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);

        Field appClassesField = AppContext.class.getDeclaredField("appClasses");
        appClassesField.setAccessible(true);
        appClassesField.set(null, null);
    }

    private ListeningService createInitializedService() {
        AppContext.initialize(mockSocketSubscriber);
        return new ListeningService();
    }

    @Nested
    @DisplayName("Constructor & AppContext Integration")
    class ConstructorAndContextTests {

        @Test
        @DisplayName("should construct successfully when AppContext has been initialized")
        void shouldConstructSuccessfullyWhenContextInitialized() {
            ListeningService service = createInitializedService();
            assertNotNull(service);
        }

        @Test
        @DisplayName("should throw IllegalStateException when service is invoked without AppContext initialization")
        void shouldThrowExceptionWhenInvokedWithoutAppContext() {
            ListeningService service = new ListeningService();

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    service::subscribe
            );

            assertEquals("App context has not been initialized", ex.getMessage());
        }

        @Test
        @DisplayName("should throw IllegalStateException on service call when SocketSubscriber is not registered")
        void shouldThrowExceptionWhenSocketSubscriberNotRegistered() {
            AppContext.initialize(new Object()); // Initialize without SocketSubscriber
            ListeningService service = new ListeningService();

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    service::subscribe
            );

            assertTrue(ex.getMessage().contains("Class not registered in context: " + SocketSubscriber.class.getName()));
        }
    }

    @Nested
    @DisplayName("subscribe")
    class SubscribeTests {

        @Test
        @DisplayName("should invoke subscribe on SocketSubscriber successfully")
        void shouldSuccessfullySubscribe() throws Exception {
            ListeningService service = createInitializedService();

            service.subscribe();

            verify(mockSocketSubscriber).subscribe();
        }

        @Test
        @DisplayName("should wrap and throw RuntimeException when SocketSubscriber throws IOException")
        void shouldWrapIOExceptionInRuntimeException() throws Exception {
            ListeningService service = createInitializedService();
            IOException ioException = new IOException("Connection refused");
            doThrow(ioException).when(mockSocketSubscriber).subscribe();

            RuntimeException ex = assertThrows(
                    RuntimeException.class,
                    service::subscribe
            );

            assertEquals("Error while trying to subscribe to socket", ex.getMessage());
            assertSame(ioException, ex.getCause());
        }

        @Test
        @DisplayName("should propagate unchecked RuntimeException directly without rewrapping")
        void shouldPropagateDirectRuntimeException() throws Exception {
            ListeningService service = createInitializedService();
            IllegalStateException runtimeException = new IllegalStateException("Channel not ready");
            doThrow(runtimeException).when(mockSocketSubscriber).subscribe();

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    service::subscribe
            );

            assertSame(runtimeException, ex);
        }
    }

    @Nested
    @DisplayName("listen")
    class ListenTests {

        @Test
        @DisplayName("should pass consumer to SocketSubscriber listen method")
        void shouldPassConsumerToSocketSubscriber() {
            ListeningService service = createInitializedService();

            service.listen(mockConsumer);

            verify(mockSocketSubscriber).listen(mockConsumer);
        }

        @Test
        @DisplayName("should always invoke close on SocketSubscriber after normal listen completion")
        void shouldCloseSocketSubscriberAfterNormalListen() {
            ListeningService service = createInitializedService();

            service.listen(mockConsumer);

            verify(mockSocketSubscriber).close();
        }

        @Test
        @DisplayName("should invoke close on SocketSubscriber in finally block even when listen throws exception")
        void shouldCloseSocketSubscriberWhenListenFails() {
            ListeningService service = createInitializedService();
            doThrow(new RuntimeException("Streaming error")).when(mockSocketSubscriber).listen(mockConsumer);

            RuntimeException ex = assertThrows(
                    RuntimeException.class,
                    () -> service.listen(mockConsumer)
            );

            assertEquals("Streaming error", ex.getMessage());
            verify(mockSocketSubscriber).close();
        }

        @Test
        @DisplayName("should propagate exception when close fails during cleanup")
        void shouldPropagateExceptionWhenCloseFails() {
            ListeningService service = createInitializedService();
            doThrow(new RuntimeException("Close failure")).when(mockSocketSubscriber).close();

            RuntimeException ex = assertThrows(
                    RuntimeException.class,
                    () -> service.listen(mockConsumer)
            );

            assertEquals("Close failure", ex.getMessage());
            verify(mockSocketSubscriber).close();
        }
    }

    @Nested
    @DisplayName("subscribeWithRetry")
    class SubscribeWithRetryTests {

        @Test
        @DisplayName("should succeed on first attempt without sleeping or retrying")
        void shouldSucceedOnFirstAttempt() throws Exception {
            ListeningService service = createInitializedService();

            service.subscribeWithRetry();

            verify(mockSocketSubscriber, times(1)).subscribe();
        }

        @Test
        @DisplayName("should retry and succeed on second attempt after one initial failure")
        void shouldRetryAndSucceedOnSecondAttempt() throws Exception {
            ListeningService service = createInitializedService();
            doThrow(new IOException("First attempt dropped"))
                    .doNothing()
                    .when(mockSocketSubscriber).subscribe();

            service.subscribeWithRetry();

            verify(mockSocketSubscriber, times(2)).subscribe();
        }

        @Test
        @DisplayName("should retry and succeed on third attempt after two consecutive failures")
        void shouldRetryAndSucceedOnThirdAttempt() throws Exception {
            ListeningService service = createInitializedService();
            doThrow(new IOException("Attempt 1 dropped"))
                    .doThrow(new IOException("Attempt 2 dropped"))
                    .doNothing()
                    .when(mockSocketSubscriber).subscribe();

            service.subscribeWithRetry();

            verify(mockSocketSubscriber, times(3)).subscribe();
        }

        @Test
        @DisplayName("should throw RuntimeException after exhausting all three maximum attempts")
        void shouldThrowExceptionWhenAllThreeAttemptsFail() throws Exception {
            ListeningService service = createInitializedService();
            doThrow(new IOException("Connection persistently refused"))
                    .when(mockSocketSubscriber).subscribe();

            RuntimeException ex = assertThrows(
                    RuntimeException.class,
                    service::subscribeWithRetry
            );

            assertEquals("Listening to socket will not be possible", ex.getMessage());
            verify(mockSocketSubscriber, times(3)).subscribe();
        }

        @Test
        @DisplayName("should handle InterruptedException during retry sleep and restore thread interrupt status")
        void shouldHandleInterruptedExceptionDuringRetrySleep() throws Exception {
            ListeningService service = createInitializedService();
            doThrow(new IOException("Initial disconnect")).when(mockSocketSubscriber).subscribe();

            // Pre-interrupt the current thread so Thread.sleep immediately throws InterruptedException
            Thread.currentThread().interrupt();

            RuntimeException ex = assertThrows(
                    RuntimeException.class,
                    service::subscribeWithRetry
            );

            assertEquals("Interrupted during retry", ex.getMessage());
            assertInstanceOf(InterruptedException.class, ex.getCause());
            assertTrue(Thread.currentThread().isInterrupted(), "Thread interrupt flag should remain set");
        }
    }

    private static void assertSame(Object expected, Object actual) {
        org.junit.jupiter.api.Assertions.assertSame(expected, actual);
    }
}