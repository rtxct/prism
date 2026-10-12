package prism.application.component;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import prism.configuration.adapter.ListenerAdapter;
import prism.configuration.context.AbstractServiceContextConfiguration;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SocketSubscriber Unit Tests")
class TestSocketSubscriber {

    @Mock
    private AbstractServiceContextConfiguration mockExecutionContext;

    private ServerSocketChannel serverSocketChannel;
    private SocketSubscriber subscriber;

    @BeforeEach
    void setUp() {
        subscriber = new SocketSubscriber(() -> mockExecutionContext);
    }

    @AfterEach
    void tearDown() {
        if (subscriber != null) {
            try {
                subscriber.close();
            } catch (Exception ignored) {
            }
        }
        if (serverSocketChannel != null) {
            try {
                serverSocketChannel.close();
            } catch (Exception ignored) {
            }
        }
    }

    private void injectChannel(SocketSubscriber target, SocketChannel channel) {
        try {
            Field field = SocketSubscriber.class.getDeclaredField("socketChannel");
            field.setAccessible(true);
            field.set(target, channel);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject socketChannel", e);
        }
    }

    private SocketChannel getInjectedChannel(SocketSubscriber target) {
        try {
            Field field = SocketSubscriber.class.getDeclaredField("socketChannel");
            field.setAccessible(true);
            return (SocketChannel) field.get(target);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read socketChannel", e);
        }
    }

    private ServerSocketChannel bindServer(Path socketPath) throws IOException {
        Files.deleteIfExists(socketPath);
        serverSocketChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        serverSocketChannel.bind(UnixDomainSocketAddress.of(socketPath));
        return serverSocketChannel;
    }

    private void configureAdapter(Path socketPath) {
        ListenerAdapter adapter = new ListenerAdapter();
        adapter.setDirectory(socketPath.getParent().toString());
        adapter.setFile(socketPath.getFileName().toString());
        when(mockExecutionContext.getListenerAdapter()).thenReturn(adapter);
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("should construct successfully with non-null execution context supplier")
        void shouldConstructSuccessfullyWithSupplier() {
            assertNotNull(subscriber);
        }

        @Test
        @DisplayName("should initialize default ObjectMapper instance on construction")
        void shouldInitializeObjectMapper() {
            assertNotNull(subscriber.mapper);
        }

        @Test
        @DisplayName("should start with null socketChannel prior to subscription")
        void shouldStartWithNullSocketChannel() {
            assertNull(getInjectedChannel(subscriber));
        }
    }

    @Nested
    @DisplayName("subscribe")
    class SubscribeTests {

        @Test
        @DisplayName("should connect successfully when server is listening at configured socket path")
        void shouldConnectSuccessfullyToConfiguredPath(@TempDir Path tempDir) throws Exception {
            Path socketPath = tempDir.resolve("active.sock");
            try (ServerSocketChannel _ = bindServer(socketPath)) {
                configureAdapter(socketPath);

                subscriber.subscribe();

                SocketChannel channel = getInjectedChannel(subscriber);
                assertNotNull(channel, "socketChannel must be initialized");
                assertTrue(channel.isOpen(), "socketChannel must be open");
                assertTrue(channel.isConnected(), "socketChannel must be connected");
            }
        }

        @Test
        @DisplayName("should use default ListenerAdapter directory and file when adapter is null")
        void shouldFallBackToDefaultAdapterConstantsWhenAdapterIsNull() {
            when(mockExecutionContext.getListenerAdapter()).thenReturn(null);

            // Without a server listening at default path (~/.cache/prism/socket.sock), connect fails with IOException
            assertThrows(IOException.class, subscriber::subscribe);
        }

        @Test
        @DisplayName("should throw IOException when no server is listening at configured socket path")
        void shouldThrowIOExceptionWhenNoServerListening(@TempDir Path tempDir) {
            Path nonExistentSocket = tempDir.resolve("non_existent.sock");
            configureAdapter(nonExistentSocket);

            assertThrows(IOException.class, subscriber::subscribe);
        }
    }

    @Nested
    @DisplayName("unsubscribe and close")
    class UnsubscribeAndCloseTests {

        @Test
        @DisplayName("should be a no-op when unsubscribe is called with null socketChannel")
        void shouldBeNoOpWhenChannelIsNull() {
            assertDoesNotThrow(() -> subscriber.unsubscribe());
            assertNull(getInjectedChannel(subscriber));
        }

        @Test
        @DisplayName("should be a no-op when unsubscribe is called on an already closed channel")
        void shouldBeNoOpWhenChannelAlreadyClosed() {
            SocketChannel mockChannel = mock(SocketChannel.class);
            when(mockChannel.isOpen()).thenReturn(false);
            injectChannel(subscriber, mockChannel);

            assertDoesNotThrow(() -> subscriber.unsubscribe());
        }

        @Test
        @DisplayName("should close open socket channel and reset socketChannel field to null")
        void shouldCloseChannelAndResetFieldToNull(@TempDir Path tempDir) throws Exception {
            Path socketPath = tempDir.resolve("close_test.sock");
            try (ServerSocketChannel _ = bindServer(socketPath)) {
                configureAdapter(socketPath);

                subscriber.subscribe();
                SocketChannel channel = getInjectedChannel(subscriber);
                assertTrue(channel.isOpen());

                subscriber.unsubscribe();

                assertFalse(channel.isOpen(), "Channel must be closed after unsubscribe");
                assertNull(getInjectedChannel(subscriber), "socketChannel field must be reset to null");
            }
        }

        @Test
        @DisplayName("should wrap IOException in RuntimeException when socket channel close fails")
        void shouldWrapIOExceptionWhenChannelCloseFails() throws Exception {
            SocketChannel mockChannel = mock(SocketChannel.class);
            when(mockChannel.isOpen()).thenReturn(true);
            doThrow(new IOException("Network interface failure")).when(mockChannel).close();
            injectChannel(subscriber, mockChannel);

            RuntimeException ex = assertThrows(
                    RuntimeException.class,
                    subscriber::unsubscribe
            );

            assertEquals("An error was thrown while trying to end connection", ex.getMessage());
            assertInstanceOf(IOException.class, ex.getCause());
        }

        @Test
        @DisplayName("should ensure socketChannel field is reset to null even if close throws exception")
        void shouldResetFieldToNullEvenWhenCloseThrows() throws Exception {
            SocketChannel mockChannel = mock(SocketChannel.class);
            when(mockChannel.isOpen()).thenReturn(true);
            doThrow(new IOException("Close failure")).when(mockChannel).close();
            injectChannel(subscriber, mockChannel);

            assertThrows(RuntimeException.class, subscriber::unsubscribe);
            assertNull(getInjectedChannel(subscriber), "socketChannel field must be null after finally block");
        }

        @Test
        @DisplayName("should delegate AutoCloseable close method directly to unsubscribe")
        void shouldDelegateCloseToUnsubscribe(@TempDir Path tempDir) throws Exception {
            Path socketPath = tempDir.resolve("autoclose.sock");
            try (ServerSocketChannel _ = bindServer(socketPath)) {
                configureAdapter(socketPath);

                subscriber.subscribe();
                SocketChannel channel = getInjectedChannel(subscriber);
                assertTrue(channel.isOpen());

                subscriber.close();

                assertFalse(channel.isOpen());
                assertNull(getInjectedChannel(subscriber));
            }
        }
    }

    @Nested
    @DisplayName("listen")
    class ListenTests {

        @Test
        @DisplayName("should throw IllegalStateException when listen is called before subscribing")
        @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
        void shouldThrowExceptionWhenListeningBeforeSubscribing() {
            List<String> received = new ArrayList<>();

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> subscriber.listen(received::add)
            );

            assertEquals("Cannot listen before subscribing", ex.getMessage());
        }

        @Test
        @DisplayName("should receive, parse valid Listened JSON message, and dispatch extracted file to consumer")
        void shouldReceiveAndDispatchExtractedFile(@TempDir Path tempDir) throws Exception {
            Path socketPath = tempDir.resolve("stream.sock");
            try (ServerSocketChannel server = bindServer(socketPath)) {
                configureAdapter(socketPath);

                subscriber.subscribe();

                try (SocketChannel serverClient = server.accept()) {
                    String jsonMessage = "{\"sender\":\"cli\",\"command\":\"set-wallpaper /home/user/nature.png\"}\n";
                    serverClient.write(StandardCharsets.UTF_8.encode(jsonMessage));
                }

                List<String> received = new ArrayList<>();
                subscriber.listen(received::add);

                assertEquals(1, received.size());
                assertEquals("/home/user/nature.png", received.getFirst());
            }
        }

        @Test
        @DisplayName("should process multiple consecutive valid messages in order")
        void shouldProcessMultipleConsecutiveMessages(@TempDir Path tempDir) throws Exception {
            Path socketPath = tempDir.resolve("multi.sock");
            try (ServerSocketChannel server = bindServer(socketPath)) {
                configureAdapter(socketPath);

                subscriber.subscribe();

                try (SocketChannel serverClient = server.accept()) {
                    String payload = """
                            {"sender":"user1","command":"process /images/a.webp"}
                            {"sender":"user2","command":"process /images/b.jpg"}
                            {"sender":"user3","command":"process /images/c.jpeg"}
                            """;
                    serverClient.write(StandardCharsets.UTF_8.encode(payload));
                }

                List<String> received = new ArrayList<>();
                subscriber.listen(received::add);

                assertEquals(3, received.size());
                assertEquals("/images/a.webp", received.get(0));
                assertEquals("/images/b.jpg", received.get(1));
                assertEquals("/images/c.jpeg", received.get(2));
            }
        }

        @Test
        @DisplayName("should ignore messages where command has no supported image extension")
        void shouldIgnoreMessagesWithoutImageExtension(@TempDir Path tempDir) throws Exception {
            Path socketPath = tempDir.resolve("filter.sock");
            try (ServerSocketChannel server = bindServer(socketPath)) {
                configureAdapter(socketPath);

                subscriber.subscribe();

                try (SocketChannel serverClient = server.accept()) {
                    String payload = """
                            {"sender":"daemon","command":"ping"}
                            {"sender":"daemon","command":"echo hello world"}
                            {"sender":"daemon","command":"open document.pdf"}
                            {"sender":"daemon","command":"update /assets/valid.png"}
                            """;
                    serverClient.write(StandardCharsets.UTF_8.encode(payload));
                }

                List<String> received = new ArrayList<>();
                subscriber.listen(received::add);

                assertEquals(1, received.size(), "Only message with valid image extension should be accepted");
                assertEquals("/assets/valid.png", received.getFirst());
            }
        }

        @Test
        @DisplayName("should ignore malformed or unparseable JSON messages and continue processing")
        void shouldIgnoreMalformedJsonLines(@TempDir Path tempDir) throws Exception {
            Path socketPath = tempDir.resolve("corrupt.sock");
            try (ServerSocketChannel server = bindServer(socketPath)) {
                configureAdapter(socketPath);

                subscriber.subscribe();

                try (SocketChannel serverClient = server.accept()) {
                    String payload = """
                            INVALID_RAW_STRING_NOT_JSON
                            {"broken_json_missing_brace":
                            {"sender":"cli","command":"render /wallpapers/galaxy.png"}
                            """;
                    serverClient.write(StandardCharsets.UTF_8.encode(payload));
                }

                List<String> received = new ArrayList<>();
                subscriber.listen(received::add);

                assertEquals(1, received.size());
                assertEquals("/wallpapers/galaxy.png", received.getFirst());
            }
        }

        @Test
        @DisplayName("should terminate loop cleanly upon reaching EOF when server disconnects")
        void shouldTerminateCleanlyOnEof(@TempDir Path tempDir) throws Exception {
            Path socketPath = tempDir.resolve("eof.sock");
            try (ServerSocketChannel server = bindServer(socketPath)) {
                configureAdapter(socketPath);

                subscriber.subscribe();

                //noinspection EmptyTryBlock
                try (SocketChannel _ = server.accept()) {
                    // Open and immediately close to signal EOF
                }

                List<String> received = new ArrayList<>();
                assertDoesNotThrow(() -> subscriber.listen(received::add));
                assertTrue(received.isEmpty());
            }
        }

        @Test
        @DisplayName("should catch ClosedChannelException gracefully without rethrowing")
        void shouldCatchClosedChannelExceptionGracefully() throws Exception {
            SocketChannel mockChannel = mock(SocketChannel.class);
            when(mockChannel.blockingLock()).thenReturn(new Object());
            when(mockChannel.isBlocking()).thenReturn(true);
            when(mockChannel.read(any(ByteBuffer.class)))
                    .thenThrow(new ClosedChannelException());
            injectChannel(subscriber, mockChannel);

            assertDoesNotThrow(() -> subscriber.listen(_ -> {
            }));
        }

        @Test
        @DisplayName("should catch IOException during stream read gracefully without rethrowing")
        void shouldCatchIOExceptionGracefully() throws Exception {
            SocketChannel mockChannel = mock(SocketChannel.class);
            when(mockChannel.blockingLock()).thenReturn(new Object());
            when(mockChannel.isBlocking()).thenReturn(true);
            when(mockChannel.read(any(ByteBuffer.class)))
                    .thenThrow(new IOException("Connection reset by peer"));
            injectChannel(subscriber, mockChannel);

            assertDoesNotThrow(() -> subscriber.listen(_ -> {
            }));
        }
    }
}