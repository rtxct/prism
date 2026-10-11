package prism.infrastructure.daemon;

import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import prism.domain.exception.SocketInterruptionException;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * A Unix-domain socket server that listens for incoming connections and dispatches them to
 * a consumer callback.
 *
 * <p>Implements {@link AutoCloseable} so it can be used in try-with-resources
 * blocks; closing the server triggers an {@linkplain #interrupt() interrupt}.
 *
 * @see SocketStatusType
 */
public class SocketServer implements AutoCloseable {

    /**
     * Owner-only permissions applied to the daemon socket, so only the user can call it.
     */
    public static final String SOCKET_PERMISSIONS = "rw-------";

    /**
     * File name of the daemon socket.
     */
    private static final String SOCKET_FILE = "prism.sock";

    /**
     * Environment variable holding the user runtime directory (set by systemd).
     */
    private static final String RUNTIME_DIR_ENV = "XDG_RUNTIME_DIR";

    /**
     * Directory used for the daemon socket when {@value #RUNTIME_DIR_ENV} is not set.
     */
    private static final String FALLBACK_DIR = "/tmp";

    /**
     * The current lifecycle state of the daemon socket server.
     */
    @Getter
    @Setter
    private static volatile SocketStatusType socketStatusType;

    /**
     * The underlying Unix-domain server socket channel.
     */
    @Getter
    private ServerSocketChannel serverSocketChannel;

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to {@link #interrupt()} to close the underlying channel.
     */
    @Override
    public void close() {
        this.interrupt();
    }

    /**
     * Opens a new Unix-domain {@link ServerSocketChannel} and sets the server status to
     * {@link SocketStatusType#ACTIVATING}.
     *
     * @throws IOException if the channel cannot be opened
     */
    public void open() throws IOException {
        this.serverSocketChannel =
                ServerSocketChannel.open(StandardProtocolFamily.UNIX);

        socketStatusType = SocketStatusType.ACTIVATING;
    }

    /**
     * Closes the underlying server socket channel if it is currently open.
     * <p>
     * This is a no-op if the channel has not been initialized.
     *
     * @throws RuntimeException wrapping an {@link IOException} if the channel cannot be
     *                          closed
     */
    public void interrupt() {
        if (Objects.isNull(this.serverSocketChannel)) {
            return;
        }

        try {
            if (this.serverSocketChannel.isOpen()) {
                this.serverSocketChannel.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Blocks the calling thread and continuously accepts incoming connections, passing each
     * {@link SocketChannel} to the given consumer.
     * <p>
     * {@link SocketChannel} call acceptances are synchronous, this means that a
     * {@link #accept()} call will hold the thread until a request is given.
     * <p>
     * This method runs an endless loop and only returns when the channel is closed (e.g.
     * via {@link #interrupt()}).
     *
     * @param consumer the callback invoked for each accepted connection
     */
    public void listen(@NonNull Consumer<@Nullable SocketChannel> consumer) {
        Stream.generate(this::accept)
                .takeWhile(Objects::nonNull)
                .forEach(consumer);
    }

    /**
     * Accepts a single incoming connection on the server socket channel.
     * <p>
     * If the channel is closed due to a
     * {@linkplain SocketStatusType#GRACEFUL_INTERRUPTION graceful interruption}, the server
     * is interrupted and {@code null} is returned. For any other asynchronous close, a
     * {@link SocketInterruptionException} is thrown with a
     * {@linkplain SocketStatusType#FORCEFUL_INTERRUPTION forceful} status.
     *
     * @return the accepted {@link SocketChannel}, or {@code null} on graceful shutdown
     * @throws SocketInterruptionException if the channel is closed unexpectedly
     * @throws RuntimeException            if a general I/O error occurs during acceptance
     */
    public @Nullable SocketChannel accept() {
        try {
            return this.serverSocketChannel.accept();
        } catch (AsynchronousCloseException e) {
            if (Objects.equals(SocketStatusType.GRACEFUL_INTERRUPTION, socketStatusType)) {
                this.interrupt();
                return null;
            }

            throw new SocketInterruptionException(
                    "Interrupted socket communication", SocketStatusType.FORCEFUL_INTERRUPTION);
        } catch (IOException e) {
            throw new RuntimeException("Socket acceptance error.", e);
        }
    }

    /**
     * Resolves the daemon socket path: {@code $XDG_RUNTIME_DIR/prism.sock}, falling back to
     * {@code /tmp/prism.sock}. Clients must use this same path to reach the daemon.
     *
     * @return the daemon socket path
     */
    public static @NonNull Path getSocketPath() {
        String runtimeDir = System.getenv(RUNTIME_DIR_ENV);

        String dir = Objects.isNull(runtimeDir) || runtimeDir.isBlank()
                ? FALLBACK_DIR
                : runtimeDir;

        return Path.of(dir, SOCKET_FILE);
    }
}