package prism.infrastructure.daemon;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import prism.domain.exception.SocketInterruptionException;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SocketServer Unit Tests")
class TestSocketServer {

    private SocketServer socketServer;

    @BeforeEach
    void setUp() {
        socketServer = new SocketServer();
    }

    @AfterEach
    void tearDown() {
        if (socketServer != null) {
            try {
                socketServer.interrupt();
            } catch (Exception ignored) {
            }
        }
        SocketServer.setSocketStatusType(null);
    }

    private void injectChannel(SocketServer server, ServerSocketChannel channel) {
        try {
            Field field = SocketServer.class.getDeclaredField("serverSocketChannel");
            field.setAccessible(true);
            field.set(server, channel);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("lifecycle and channel management")
    class LifecycleTests {

        @Test
        @DisplayName("should open Unix domain server socket channel and set status to ACTIVATING")
        void shouldOpenUnixDomainChannelAndSetStatusActivating() throws IOException {
            socketServer.open();

            ServerSocketChannel channel = socketServer.getServerSocketChannel();
            assertNotNull(channel);

            assertTrue(channel.isOpen());
            assertEquals(SocketStatusType.ACTIVATING, SocketServer.getSocketStatusType());
        }

        @Test
        @DisplayName("should do nothing on interrupt when serverSocketChannel is null")
        void shouldDoNothingOnInterruptWhenChannelIsNull() {
            assertNull(socketServer.getServerSocketChannel());
            socketServer.interrupt();
            assertNull(socketServer.getServerSocketChannel());
        }

        @Test
        @DisplayName("should close channel on interrupt when channel is open")
        void shouldCloseChannelOnInterruptWhenOpen() throws IOException {
            socketServer.open();
            ServerSocketChannel channel = socketServer.getServerSocketChannel();
            assertTrue(channel.isOpen());

            socketServer.interrupt();

            assertFalse(channel.isOpen());
        }

        @Test
        @DisplayName("should do nothing on interrupt when channel is already closed")
        void shouldDoNothingOnInterruptWhenAlreadyClosed() throws IOException {
            socketServer.open();
            ServerSocketChannel channel = socketServer.getServerSocketChannel();
            assertTrue(channel.isOpen());

            socketServer.interrupt();
            assertFalse(socketServer.getServerSocketChannel().isOpen());
        }

        @Test
        @DisplayName("should delegate close to interrupt")
        void shouldDelegateCloseToInterrupt() throws IOException {
            socketServer.open();
            assertTrue(socketServer.getServerSocketChannel().isOpen());

            socketServer.close();

            assertFalse(socketServer.getServerSocketChannel().isOpen());
        }

        @Test
        @DisplayName("should throw RuntimeException wrapping IOException when channel close fails")
        void shouldThrowRuntimeExceptionWhenChannelCloseThrowsIOException() throws IOException {
            ServerSocketChannel mockChannel = mock(ServerSocketChannel.class);
            when(mockChannel.isOpen()).thenReturn(true);
            doThrow(new IOException("Close failed")).when(mockChannel).close();
            injectChannel(socketServer, mockChannel);

            RuntimeException exception = assertThrows(
                    RuntimeException.class,
                    () -> socketServer.interrupt()
            );

            assertNotNull(exception.getCause());
            assertEquals("Close failed", exception.getCause().getMessage());
        }
    }

    @Nested
    @DisplayName("accept")
    class AcceptTests {

        @Mock
        private ServerSocketChannel mockChannel;

        @BeforeEach
        void setUpMock() {
            injectChannel(socketServer, mockChannel);
        }

        @Test
        @DisplayName("should return accepted SocketChannel when connection arrives")
        void shouldReturnAcceptedSocketChannel() throws IOException {
            SocketChannel mockSocketChannel = mock(SocketChannel.class);
            when(mockChannel.accept()).thenReturn(mockSocketChannel);

            SocketChannel result = socketServer.accept();

            assertSame(mockSocketChannel, result);
            verify(mockChannel).accept();
        }

        @Test
        @DisplayName("should return null and interrupt when graceful interruption occurs")
        void shouldReturnNullAndInterruptOnGracefulInterruption() throws IOException {
            SocketServer.setSocketStatusType(SocketStatusType.GRACEFUL_INTERRUPTION);
            when(mockChannel.accept()).thenThrow(new AsynchronousCloseException());
            when(mockChannel.isOpen()).thenReturn(true);

            SocketChannel result = socketServer.accept();

            assertNull(result);
            verify(mockChannel).close();
        }

        @Test
        @DisplayName("should throw SocketInterruptionException with FORCEFUL_INTERRUPTION when not graceful")
        void shouldThrowSocketInterruptionExceptionOnForcefulInterruption() throws IOException {
            SocketServer.setSocketStatusType(SocketStatusType.READY);
            when(mockChannel.accept()).thenThrow(new AsynchronousCloseException());

            SocketInterruptionException exception = assertThrows(
                    SocketInterruptionException.class,
                    () -> socketServer.accept()
            );

            assertEquals("Interrupted socket communication", exception.getMessage());
            assertEquals(SocketStatusType.FORCEFUL_INTERRUPTION, exception.getSocketStatusType());
        }

        @Test
        @DisplayName("should throw SocketInterruptionException when status is null upon AsynchronousCloseException")
        void shouldThrowSocketInterruptionExceptionWhenStatusIsNull() throws IOException {
            SocketServer.setSocketStatusType(null);
            when(mockChannel.accept()).thenThrow(new AsynchronousCloseException());

            SocketInterruptionException exception = assertThrows(
                    SocketInterruptionException.class,
                    () -> socketServer.accept()
            );

            assertEquals("Interrupted socket communication", exception.getMessage());
            assertEquals(SocketStatusType.FORCEFUL_INTERRUPTION, exception.getSocketStatusType());
        }

        @Test
        @DisplayName("should throw RuntimeException wrapping general IOException during accept")
        void shouldThrowRuntimeExceptionOnGeneralIOException() throws IOException {
            when(mockChannel.accept()).thenThrow(new IOException("Hardware fault"));

            RuntimeException exception = assertThrows(
                    RuntimeException.class,
                    () -> socketServer.accept()
            );

            assertEquals("Socket acceptance error.", exception.getMessage());
            assertNotNull(exception.getCause());
            assertEquals("Hardware fault", exception.getCause().getMessage());
        }
    }

    @Nested
    @DisplayName("listen")
    class ListenTests {

        @Mock
        private ServerSocketChannel mockChannel;

        @BeforeEach
        void setUpMock() {
            injectChannel(socketServer, mockChannel);
        }

        @Test
        @DisplayName("should throw NullPointerException when consumer is null")
        void shouldThrowNullPointerExceptionWhenConsumerIsNull() {
            assertThrows(NullPointerException.class, () -> socketServer.listen(null));
        }

        @Test
        @DisplayName("should dispatch accepted connections to consumer until graceful shutdown")
        void shouldDispatchAcceptedConnectionsToConsumer() throws IOException {
            SocketChannel conn1 = mock(SocketChannel.class);
            SocketChannel conn2 = mock(SocketChannel.class);

            AtomicInteger calls = new AtomicInteger();
            when(mockChannel.accept()).thenAnswer(_ -> {
                int count = calls.incrementAndGet();

                if (count == 1) return conn1;
                if (count == 2) return conn2;

                SocketServer.setSocketStatusType(SocketStatusType.GRACEFUL_INTERRUPTION);
                throw new AsynchronousCloseException();
            });

            @SuppressWarnings("unchecked")
            Consumer<SocketChannel> consumer = mock(Consumer.class);

            socketServer.listen(consumer);

            verify(consumer).accept(conn1);
            verify(consumer).accept(conn2);
            verify(consumer, times(2)).accept(org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("should terminate without invoking consumer when immediate graceful shutdown occurs")
        void shouldTerminateImmediatelyOnGracefulShutdown() throws IOException {
            SocketServer.setSocketStatusType(SocketStatusType.GRACEFUL_INTERRUPTION);
            when(mockChannel.accept()).thenThrow(new AsynchronousCloseException());

            @SuppressWarnings("unchecked")
            Consumer<SocketChannel> consumer = mock(Consumer.class);

            socketServer.listen(consumer);

            verify(consumer, never()).accept(org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("should propagate SocketInterruptionException thrown during listen loop")
        void shouldPropagateSocketInterruptionExceptionDuringListen() throws IOException {
            SocketServer.setSocketStatusType(SocketStatusType.READY);
            when(mockChannel.accept()).thenThrow(new AsynchronousCloseException());

            @SuppressWarnings("unchecked")
            Consumer<SocketChannel> consumer = mock(Consumer.class);

            assertThrows(
                    SocketInterruptionException.class,
                    () -> socketServer.listen(consumer)
            );

            verify(consumer, never()).accept(org.mockito.ArgumentMatchers.any());
        }
    }

    @Nested
    @DisplayName("metadata and socket path")
    class MetadataTests {

        @Test
        @DisplayName("should have owner-only socket permissions constant 'rw-------'")
        void shouldHaveCorrectSocketPermissionsConstant() {
            assertEquals("rw-------", SocketServer.SOCKET_PERMISSIONS);
        }

        @Test
        @DisplayName("should resolve socket path ending with 'prism.sock'")
        void shouldResolveSocketPathEndingWithPrismSock() {
            Path socketPath = SocketServer.getSocketPath();
            assertNotNull(socketPath);
            assertEquals("prism.sock", socketPath.getFileName().toString());
        }

        @Test
        @DisplayName("should get and set socket status type")
        void shouldGetAndSetSocketStatusType() {
            SocketServer.setSocketStatusType(SocketStatusType.READY);
            assertEquals(SocketStatusType.READY, SocketServer.getSocketStatusType());

            SocketServer.setSocketStatusType(SocketStatusType.ACTIVATING);
            assertEquals(SocketStatusType.ACTIVATING, SocketServer.getSocketStatusType());

            SocketServer.setSocketStatusType(SocketStatusType.FORCEFUL_INTERRUPTION);
            assertEquals(SocketStatusType.FORCEFUL_INTERRUPTION, SocketServer.getSocketStatusType());

            SocketServer.setSocketStatusType(null);
            assertNull(SocketServer.getSocketStatusType());
        }
    }
}