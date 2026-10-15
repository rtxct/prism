package prism.adapter.operation;

import lombok.extern.java.Log;
import org.jspecify.annotations.NonNull;
import prism.Main;
import prism.adapter.cli.input.Command;
import prism.adapter.cli.procedure.ProcedureCaller;
import prism.application.component.ColorCache;
import prism.application.component.SocketSubscriber;
import prism.application.service.CacheService;
import prism.application.service.ListeningService;
import prism.application.service.StorageService;
import prism.application.service.WriteService;
import prism.configuration.AppStartup;
import prism.configuration.adapter.ParameterAdapter;
import prism.configuration.context.AbstractServiceContextConfiguration;
import prism.configuration.context.AppContext;
import prism.configuration.context.DaemonContext;
import prism.domain.model.Prism;
import prism.domain.vo.ColorCluster;
import prism.infrastructure.daemon.SocketServer;
import prism.infrastructure.daemon.SocketStatusType;
import prism.infrastructure.filesystem.FileDataWriter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Operation that serves as the entry point for starting the long-running background daemon
 * process.
 * <p>
 * Extends {@link AppStartup} to bootstrap the core application components, specifically
 * providing a {@link DaemonContext} as the execution context.
 * <p>
 * The daemon runs two virtual threads:
 * <ul>
 *   <li><b>Socket thread</b> (always active) – accepts user calls on the daemon socket,
 *   updates the configuration and applies the given image argument (write, cache, store).</li>
 *   <li><b>Listener thread</b> (optional) – subscribes to the external IPC socket while a
 *   listener configuration is active, applying every listened image (write, cache).</li>
 * </ul>
 * All configuration updates and prism processing are serialized through a single lock, so
 * both threads never interleave.
 * <p>
 * Call protocol: each connection carries a single UTF-8 line with the raw CLI arguments
 * (without the program name), separated by whitespace. The daemon never answers; results
 * and errors are only logged.
 */
@Log
public class DaemonOperation extends AppStartup {

    /**
     * The parsed CLI commands initializing this daemon operation.
     */
    private final Command[] commands;

    /**
     * The underlying context holding application state and configuration.
     */
    private final AppContext appContext;

    /**
     * Serializes configuration updates and prism processing between both threads.
     */
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Guards {@link #shutdown()} so it runs once, either from the main thread or the
     * shutdown hook.
     */
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    /**
     * Reference to the background thread responsible for listening to external IPC signals.
     */
    private volatile Thread listenerThread;

    /**
     * Constructs a new daemon operation and bootstraps the application context.
     *
     * @param commands the array of parsed CLI commands used to configure the daemon
     */
    private DaemonOperation(Command[] commands) {
        Supplier<? extends AbstractServiceContextConfiguration>
                daemonContext = AppContext.getClassLazy(DaemonContext.class);

        super(
                daemonContext
        );

        this.commands = commands;
        this.appContext = super.getAppContext();
    }

    /**
     * Starts the background daemon process with the specified commands.
     *
     * @param commands the configuration commands
     */
    public static void start(Command[] commands) {
        new DaemonOperation(commands).startDaemon();
    }

    /**
     * Starts Daemon process.
     * <p>
     * Binds the daemon socket, applies the startup commands, then blocks the calling thread
     * until the socket thread ends.
     *
     * @throws IllegalStateException if the socket can not be opened, another daemon is
     *                               already running, or the socket thread stops unexpectedly
     */
    private void startDaemon() {
        SocketServer socketServer =
                this.appContext.getClass(SocketServer.class);

        this.openServer(socketServer);

        this.registerSignalHandlers();

        try {
            this.applyCommands(this.commands);

            Thread socketThread = Thread.ofVirtual()
                    .name("prism-socket")
                    .start(() -> this.serve(socketServer));

            socketThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Daemon was interrupted " +
                    "while waiting for the socket thread", e);
        } finally {
            this.shutdown();
        }

        if (!SocketStatusType.GRACEFUL_INTERRUPTION.equals(SocketServer.getSocketStatusType())) {
            throw new IllegalStateException("Daemon socket stopped " +
                    "unexpectedly with status %s".formatted(SocketServer.getSocketStatusType()));
        }

        log.info("Prism daemon stopped");
    }

    /**
     * Registers system signal handlers (e.g., SIGTERM, SIGINT) to gracefully shut down the daemon.
     * Uses reflection to interact with `sun.misc.Signal` to avoid compile-time dependencies,
     * falling back to a standard JVM shutdown hook if native registration fails.
     */
    private void registerSignalHandlers() {
        try {
            Class<?> signalClass = Class.forName("sun.misc.Signal");
            Class<?> signalHandlerClass = Class.forName("sun.misc.SignalHandler");

            Object handler = Proxy.newProxyInstance(
                    DaemonOperation.class.getClassLoader(),
                    new Class<?>[]{signalHandlerClass},
                    (Object _, Method _, Object[] _) -> {
                        this.shutdown();
                        return null;
                    }
            );

            for (String sig : new String[]{"TERM", "INT"}) {
                Object signal = signalClass.getConstructor(String.class).newInstance(sig);
                signalClass.getMethod("handle", signalClass, signalHandlerClass).invoke(null, signal, handler);
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Could not register native " +
                    "signal handlers. Falling back to JVM shutdown hook.", e);

            Runtime.getRuntime().addShutdownHook(
                    Thread.ofPlatform().name("prism-shutdown").unstarted(this::shutdown));
        }
    }

    /**
     * Opens and binds the daemon socket, restricting it to the current user, and marks the
     * server as {@link SocketStatusType#READY}.
     *
     * @param socketServer the server to open
     * @throws IllegalStateException if another daemon is running or the socket can not be
     *                               bound
     */
    @SuppressWarnings("resource")
    private void openServer(@NonNull SocketServer socketServer) {
        this.assertNoRunningDaemon();

        Path socketPath = SocketServer.getSocketPath();

        try {
            Files.deleteIfExists(socketPath);
            socketServer.open();

            socketServer.getServerSocketChannel()
                    .bind(UnixDomainSocketAddress.of(socketPath));

            Files.setPosixFilePermissions(socketPath,
                    PosixFilePermissions.fromString(SocketServer.SOCKET_PERMISSIONS));
        } catch (IOException e) {
            socketServer.interrupt();
            throw new IllegalStateException("Could not open daemon " +
                    "socket at %s: %s".formatted(socketPath, e.getMessage()), e);
        }

        SocketServer.setSocketStatusType(SocketStatusType.READY);
        log.info("Prism daemon listening for calls at %s".formatted(socketPath));
    }

    /**
     * Fails if a daemon is already answering on the socket path. A socket file that refuses
     * connections is a leftover from a previous run, and is replaced on bind.
     *
     * @throws IllegalStateException if another daemon is already running
     */
    private void assertNoRunningDaemon() {
        Path socketPath = SocketServer.getSocketPath();

        if (!Files.exists(socketPath)) {
            return;
        }

        try (SocketChannel probe = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            probe.connect(UnixDomainSocketAddress.of(socketPath));
        } catch (IOException e) {
            log.info("Replacing stale daemon socket at %s".formatted(socketPath));
            return;
        }

        throw new IllegalStateException(("Another Prism daemon is " +
                "already running at %s").formatted(socketPath));
    }

    /**
     * Socket thread loop. Accepts user calls one at a time until the server is no longer
     * {@link SocketStatusType#READY}.
     * <p>
     * {@link SocketServer#listen} is not used because it keeps generating {@code accept()}
     * calls after a graceful interruption, ending with an exception on the closed channel.
     *
     * @param socketServer the opened server
     */
    private void serve(@NonNull SocketServer socketServer) {
        try {
            while (SocketStatusType.READY.equals(SocketServer.getSocketStatusType())) {
                SocketChannel client = socketServer.accept();

                if (Objects.isNull(client)) {
                    break;
                }

                Thread.ofVirtual().name("prism-client").start(() -> this.handleCall(client));
            }
        } catch (RuntimeException e) {
            if (SocketStatusType.GRACEFUL_INTERRUPTION.equals(SocketServer.getSocketStatusType())) {
                return;
            }

            SocketServer.setSocketStatusType(SocketStatusType.FORCEFUL_INTERRUPTION);
            log.log(Level.SEVERE, "Daemon socket stopped unexpectedly", e);
        }
    }

    /**
     * Reads a single user call from the connection and applies it. Errors are logged and
     * never reach the caller, keeping the socket thread alive.
     *
     * @param client the accepted connection, closed after reading
     */
    private void handleCall(@NonNull SocketChannel client) {
        try (client; BufferedReader reader = new BufferedReader(new InputStreamReader(
                Channels.newInputStream(client), StandardCharsets.UTF_8))) {

            String line = reader.readLine();

            if (Objects.isNull(line) || line.isBlank()) {
                return;
            }

            log.info("Received call: %s".formatted(line));

            Command[] callCommands =
                    ProcedureCaller.assertAndGetDaemonCall(prism.utils.ArrayUtils.splitCommand(line));

            this.applyCommands(callCommands);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Could not apply call: %s".formatted(e.getMessage()), e);
        }
    }

    /**
     * Updates the daemon configuration with the given commands, starts or stops the
     * optional services accordingly, and applies the image argument if one was given.
     *
     * @param callCommands the parsed commands
     * @throws IllegalArgumentException if the commands hold an invalid configuration
     */
    private void applyCommands(@NonNull Command[] callCommands) {
        if (callCommands.length > 0 && Objects.isNull(callCommands[0].getCommand())) {
            if (Objects.equals(callCommands[0].getSubCommands()[0], Main.STOP_CMD)) {
                this.shutdown();
                return;
            }
        }

        DaemonContext context =
                this.appContext.getClass(DaemonContext.class);

        this.lock.lock();
        try {
            context.updateConfiguration(callCommands);

            this.reconcileListener(context, callCommands);
            this.reconcileCache(context);

            this.applyArgument(context);
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * Writes the prism of the image argument (consuming it), caching and storing it when
     * those configurations are active. Must be called while holding {@link #lock}.
     *
     * @param context the daemon context
     */
    private void applyArgument(@NonNull DaemonContext context) {
        ParameterAdapter parameterAdapter = context.getParameterAdapter();

        if (Objects.isNull(parameterAdapter)) {
            return;
        }

        String imagePath = parameterAdapter.getFile();

        if (Objects.isNull(imagePath) || imagePath.isBlank()) {
            log.warning("Argument given without a file, nothing to apply");
            return;
        }

        try {
            this.writePrism(context, imagePath);

            if (Objects.nonNull(context.getStorageAdapter())) {
                this.appContext.getClass(StorageService.class).store(imagePath);
            }

            log.info("Prism applied for %s".formatted(imagePath));
        } catch (Exception e) {
            log.log(Level.SEVERE, "Could not apply prism for %s: %s"
                    .formatted(imagePath, e.getMessage()), e);
        }
    }

    /**
     * Builds (or fetches from cache) the prism of an image and writes it, caching newly
     * built prisms when the cache configuration is active. Must be called while holding
     * {@link #lock}.
     *
     * @param context   the daemon context
     * @param imagePath absolute path to the image
     * @throws IllegalArgumentException if the path is not absolute
     * @throws IllegalStateException    if the image colors can not be read
     */
    private void writePrism(@NonNull DaemonContext context, @NonNull String imagePath) {
        if (!Path.of(imagePath).isAbsolute()) {
            throw new IllegalArgumentException(("Image path %s must be absolute, the " +
                    "daemon does not share the caller's working directory").formatted(imagePath));
        }

        CacheService cacheService =
                this.appContext.getClass(CacheService.class);

        boolean cacheActive = Objects.nonNull(context.getCacheAdapter());

        Prism prism = cacheActive
                ? cacheService.get(imagePath)
                : null;

        boolean cached = Objects.nonNull(prism);

        if (!cached) {
            WriteService writeService =
                    this.appContext.getClass(WriteService.class);

            List<ColorCluster> imageColors = writeService.processImage(imagePath);

            if (Objects.isNull(imageColors)) {
                throw new IllegalStateException(
                        "Could not read colors from image %s".formatted(imagePath));
            }

            prism = writeService.getPrism(imageColors);
        }

        this.appContext.getClass(
                FileDataWriter.class).writeSpectrum(prism);

        if (cacheActive && !cached) {
            cacheService.add(imagePath, prism);
        }
    }

    /**
     * Starts the listener thread when a listener configuration is active and it is not
     * running, or stops it when the configuration was interrupted. Must be called while
     * holding {@link #lock}.
     *
     * @param context the daemon context
     */
    private void reconcileListener(@NonNull DaemonContext context, @NonNull Command[] callCommands) {
        boolean active = Objects.nonNull(context.getListenerAdapter());
        boolean running = Objects.nonNull(this.listenerThread) && this.listenerThread.isAlive();

        boolean changed = java.util.Arrays.stream(callCommands)
                .anyMatch(c -> prism.adapter.cli.AppCommandType.LISTEN.equals(c.getCommand()));

        if (changed && running) {
            this.stopListener();
            running = false;
        }

        if (active && !running) {
            this.listenerThread = Thread.ofVirtual()
                    .name("prism-listener")
                    .start(this::listen);
            return;
        }

        if (!active && running) {
            this.stopListener();
        }
    }

    /**
     * Clears cached prisms when the cache configuration was interrupted. Must be called
     * while holding {@link #lock}.
     *
     * @param context the daemon context
     */
    private void reconcileCache(@NonNull DaemonContext context) {
        if (Objects.isNull(context.getCacheAdapter())) {
            this.appContext.getClass(ColorCache.class).clear();
        }
    }

    /**
     * Listener thread body. Subscribes to the external socket and applies every listened
     * image until the connection ends or the listener is stopped. A failed subscription
     * ends the thread; calling the listener command again retries it.
     */
    private void listen() {
        ListeningService listeningService =
                this.appContext.getClass(ListeningService.class);

        try {
            listeningService.subscribeWithRetry();
            log.info("Listener subscribed");

            listeningService.listen(this::onListened);
            log.info("Listener connection ended");
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, ("Listener stopped: %s. Call the " +
                    "listener command again to retry").formatted(e.getMessage()), e);
        }
    }

    /**
     * Applies a listened image (write and cache, never store). Errors are logged so they
     * don't end the listener loop.
     *
     * @param imagePath the image path extracted from the listened command
     */
    private void onListened(@NonNull String imagePath) {
        DaemonContext context =
                this.appContext.getClass(DaemonContext.class);

        this.lock.lock();
        try {
            // Listener may have been interrupted while this thread waited for the lock
            if (Objects.isNull(context.getListenerAdapter())) {
                return;
            }

            this.writePrism(context, imagePath);
            log.info("Listened prism applied for %s".formatted(imagePath));
        } catch (Exception e) {
            log.log(Level.SEVERE, "Could not apply listened prism for %s: %s"
                    .formatted(imagePath, e.getMessage()), e);
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * Closes the external socket subscription, which ends the listener thread's blocking
     * read. Doesn't wait for it, as it may be waiting for {@link #lock}.
     */
    private void stopListener() {
        Thread thread = this.listenerThread;

        if (Objects.isNull(thread) || !thread.isAlive()) {
            return;
        }

        this.appContext.getClass(SocketSubscriber.class).close();
        log.info("Listener stopped");
    }

    /**
     * Gracefully stops the daemon: stops accepting calls, waits for in-flight processing,
     * stops the listener and cache watcher, and removes the socket file. Runs once.
     */
    @SuppressWarnings("resource")
    private void shutdown() {
        if (!this.stopped.compareAndSet(false, true)) {
            return;
        }

        log.info("Stopping Prism daemon");

        if (!SocketStatusType.FORCEFUL_INTERRUPTION.equals(SocketServer.getSocketStatusType())) {
            SocketServer.setSocketStatusType(SocketStatusType.GRACEFUL_INTERRUPTION);
        }

        try {
            this.appContext.getClass(SocketServer.class).interrupt();
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "Could not close daemon socket", e);
        }

        this.lock.lock();
        try {
            this.stopListener();

            ColorCache colorCache =
                    this.appContext.getClass(ColorCache.class);

            if (colorCache.isStarted()) {
                colorCache.unwatch();
            }
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "Could not stop daemon services", e);
        } finally {
            this.lock.unlock();
        }

        Path socketPath = SocketServer.getSocketPath();

        try {
            Files.deleteIfExists(socketPath);
        } catch (IOException e) {
            log.log(Level.WARNING, "Could not remove daemon " +
                    "socket file %s".formatted(socketPath), e);
        }
    }
}