package prism.application.service;

import lombok.extern.java.Log;
import prism.application.component.SocketSubscriber;
import prism.configuration.context.AppContext;

import java.io.IOException;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Application service responsible for managing socket connections and
 * listening for incoming events via a {@link SocketSubscriber}.
 */
@Log
public class ListeningService {

    private static final int MAX_ATTEMPTS = 3;
    static Duration RETRY_INTERVAL = Duration.ofSeconds(5);

    private final Supplier<SocketSubscriber> socketSubscriber;

    /**
     * Constructs a new {@code ListeningService}, initializing its
     * {@link SocketSubscriber} dependency lazily via the global {@link AppContext}.
     */
    public ListeningService() {
        this.socketSubscriber =
                AppContext.getClassLazy(SocketSubscriber.class);
    }

    /**
     * Attempts to subscribe to the socket connection, retrying up to a maximum
     * number of attempts if the connection fails.
     *
     * @throws RuntimeException if the maximum number of attempts is reached
     *                          without successfully connecting, or if interrupted
     */
    public void subscribeWithRetry() {
        for (int i = 1; i <= MAX_ATTEMPTS; i++) {
            try {
                this.subscribe();
                return;
            } catch (RuntimeException e) {
                log.log(Level.SEVERE, ("Connection (%s) could " +
                        "not be stabilished while subscribing to socket").formatted(i), e);

                try {
                    Thread.sleep(RETRY_INTERVAL.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry", ie);
                }
            }
        }

        throw new RuntimeException(
                "Listening to socket will not be possible");
    }

    /**
     * Subscribes to the socket connection.
     *
     * @throws RuntimeException if an I/O error occurs while subscribing
     */
    public void subscribe() {
        try {
            this.socketSubscriber.get().subscribe();
        } catch (IOException e) {
            throw new RuntimeException("Error " +
                    "while trying to subscribe to socket", e);
        }
    }

    /**
     * Listens for incoming messages on the socket and processes them using
     * the provided consumer. Ensures the socket is properly closed after listening.
     *
     * @param consumer the consumer to handle incoming messages
     */
    public void listen(Consumer<String> consumer) {
        try {
            this.socketSubscriber
                    .get().listen(consumer);
        } finally {
            this.socketSubscriber.get().close();
        }
    }
}