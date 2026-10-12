package prism.adapter.operation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import prism.Main;
import prism.adapter.cli.AppCommandType;
import prism.adapter.cli.input.Command;
import prism.adapter.cli.procedure.ProcedureCaller;
import prism.application.component.ColorCache;
import prism.application.component.SocketSubscriber;
import prism.application.service.CacheService;
import prism.application.service.ListeningService;
import prism.application.service.StorageService;
import prism.application.service.WriteService;
import prism.configuration.adapter.CacheAdapter;
import prism.configuration.adapter.ListenerAdapter;
import prism.configuration.adapter.ParameterAdapter;
import prism.configuration.adapter.StorageAdapter;
import prism.configuration.context.AppContext;
import prism.configuration.context.DaemonContext;
import prism.domain.model.Prism;
import prism.infrastructure.daemon.SocketServer;
import prism.infrastructure.daemon.SocketStatusType;
import prism.infrastructure.filesystem.FileDataWriter;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DaemonOperation Unit Tests")
class TestDaemonOperation {

    private static final Duration TEST_RETRY_INTERVAL = Duration.ofMillis(1);

    @TempDir
    Path tempDir;

    @BeforeEach
    @AfterEach
    void resetAndCleanup() throws Exception {
        setListeningServiceRetryInterval(TEST_RETRY_INTERVAL);
        shutdownColorCacheSchedulerIfPresent();
        cleanupSocketServer();
        cleanSocketFile();
        resetAppContext();
        SocketServer.setSocketStatusType(null);
    }

    private static void setListeningServiceRetryInterval(Duration duration) {
        try {
            Field field = ListeningService.class.getDeclaredField("RETRY_INTERVAL");
            field.setAccessible(true);
            field.set(null, duration);
        } catch (Exception ignored) {
        }
    }

    private static void resetAppContext() throws Exception {
        Field instanceField = AppContext.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);

        Field appClassesField = AppContext.class.getDeclaredField("appClasses");
        appClassesField.setAccessible(true);
        appClassesField.set(null, null);
    }

    private static void shutdownColorCacheSchedulerIfPresent() {
        try {
            Field instanceField = AppContext.class.getDeclaredField("instance");
            instanceField.setAccessible(true);
            AppContext context = (AppContext) instanceField.get(null);

            if (context != null) {
                ColorCache cache = context.getClass(ColorCache.class);
                Field schedulerField = ColorCache.class.getDeclaredField("scheduler");

                schedulerField.setAccessible(true);
                ScheduledExecutorService scheduler = (ScheduledExecutorService) schedulerField.get(cache);
                if (scheduler != null) {
                    scheduler.shutdownNow();
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void cleanupSocketServer() {
        try {
            Field instanceField = AppContext.class.getDeclaredField("instance");
            instanceField.setAccessible(true);
            AppContext context = (AppContext) instanceField.get(null);
            if (context != null) {
                try (SocketServer server = context.getClass(SocketServer.class)) {
                    server.interrupt();
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void cleanSocketFile() {
        try {
            Files.deleteIfExists(SocketServer.getSocketPath());
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private static void injectAppContextBean(Class<?> clazz, Object bean) throws Exception {
        Field appClassesField = AppContext.class.getDeclaredField("appClasses");
        appClassesField.setAccessible(true);
        Map<Class<?>, Object> map = (Map<Class<?>, Object>) appClassesField.get(null);
        if (map != null) {
            map.put(clazz, bean);
        }
    }

    private DaemonOperation createDaemonOperation(Command... commands) throws Exception {
        if (commands.length == 0) {
            Command defaultCmd = new Command();
            defaultCmd.setCommand(AppCommandType.WRITE);
            commands = new Command[]{defaultCmd};
        }
        Constructor<DaemonOperation> constructor = DaemonOperation.class.getDeclaredConstructor(Command[].class);
        constructor.setAccessible(true);
        return constructor.newInstance((Object) commands);
    }

    private Path createTestImage(String filename, Color... colors) throws IOException {
        int width = 10;
        int height = 10;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        int colorCount = colors.length;
        int sliceWidth = Math.max(1, width / colorCount);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int colorIndex = Math.min(x / sliceWidth, colorCount - 1);
                image.setRGB(x, y, colors[colorIndex].getRGB());
            }
        }

        Path imagePath = tempDir.resolve(filename);
        ImageIO.write(image, "png", imagePath.toFile());
        return imagePath;
    }

    @Nested
    @DisplayName("Constructor and AppStartup Bootstrap Tests")
    class ConstructorAndAppStartupBootstrapTests {

        @Test
        @DisplayName("private constructor initializes commands field and bootstraps AppContext")
        void constructor_initializesCommandsAndBootstrapsAppContext() throws Exception {
            Command[] commands = new Command[]{new Command()};
            DaemonOperation operation = createDaemonOperation(commands);

            assertNotNull(operation.getAppContext(), "AppContext should be bootstrapped and accessible");
            assertSame(AppContext.instance(), operation.getAppContext());

            Field commandsField = DaemonOperation.class.getDeclaredField("commands");
            commandsField.setAccessible(true);
            Command[] actualCommands = (Command[]) commandsField.get(operation);
            assertSame(commands, actualCommands);
        }

        @Test
        @DisplayName("constructor registers all required services and components in AppContext")
        void constructor_registersAllRequiredServicesInDaemonContext() throws Exception {
            DaemonOperation operation = createDaemonOperation();
            AppContext context = operation.getAppContext();

            assertNotNull(context.getClass(DaemonContext.class), "DaemonContext should be registered");
            assertNotNull(context.getClass(SocketServer.class), "SocketServer should be registered");
            assertNotNull(context.getClass(WriteService.class), "WriteService should be registered");
            assertNotNull(context.getClass(CacheService.class), "CacheService should be registered");
            assertNotNull(context.getClass(ListeningService.class), "ListeningService should be registered");
            assertNotNull(context.getClass(StorageService.class), "StorageService should be registered");
            assertNotNull(context.getClass(FileDataWriter.class), "FileDataWriter should be registered");
            assertNotNull(context.getClass(ColorCache.class), "ColorCache should be registered");
            assertNotNull(context.getClass(SocketSubscriber.class), "SocketSubscriber should be registered");
        }
    }

    @Nested
    @DisplayName("Server Lifecycle and Startup Tests")
    class ServerLifecycleAndStartupTests {

        @Test
        @DisplayName("start with stop command initializes daemon and gracefully shuts down immediately")
        void start_withStopCommand_initializesAndShutsDownGracefully() {
            Command[] stopCommands = ProcedureCaller.assertStopCall("stop");
            assertNotNull(stopCommands);

            assertDoesNotThrow(() -> DaemonOperation.start(stopCommands));

            assertEquals(SocketStatusType.GRACEFUL_INTERRUPTION, SocketServer.getSocketStatusType());
            assertFalse(Files.exists(SocketServer.getSocketPath()), "Socket file should be removed on shutdown");
        }

        @Test
        @DisplayName("openServer creates socket file with owner-only POSIX permissions and sets status READY")
        void openServer_createsSocketFileWithPosixPermissionsAndSetsReadyStatus() throws Exception {
            DaemonOperation operation = createDaemonOperation();
            SocketServer server = operation.getAppContext().getClass(SocketServer.class);

            Method openServerMethod = DaemonOperation.class.getDeclaredMethod("openServer", SocketServer.class);
            openServerMethod.setAccessible(true);

            openServerMethod.invoke(operation, server);

            Path socketPath = SocketServer.getSocketPath();
            assertTrue(Files.exists(socketPath), "Socket file should exist after openServer");
            assertEquals(SocketStatusType.READY, SocketServer.getSocketStatusType());

            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(socketPath);
            assertEquals("rw-------", PosixFilePermissions.toString(permissions));

            Method shutdownMethod = DaemonOperation.class.getDeclaredMethod("shutdown");
            shutdownMethod.setAccessible(true);
            shutdownMethod.invoke(operation);
        }

        @Test
        @DisplayName("openServer interrupts server and throws IllegalStateException on IOException")
        void openServer_whenIOExceptionOccurs_interruptsServerAndThrowsIllegalStateException() throws Exception {
            DaemonOperation operation = createDaemonOperation();
            SocketServer mockServer = mock(SocketServer.class);
            doThrow(new IOException("Simulated channel open failure")).when(mockServer).open();

            Method openServerMethod = DaemonOperation.class.getDeclaredMethod("openServer", SocketServer.class);
            openServerMethod.setAccessible(true);

            InvocationTargetException ite = assertThrows(
                    InvocationTargetException.class,
                    () -> openServerMethod.invoke(operation, mockServer)
            );

            assertInstanceOf(IllegalStateException.class, ite.getCause());
            assertTrue(ite.getCause().getMessage().contains("Could not open daemon socket"));
            verify(mockServer).interrupt();
        }

        @Test
        @DisplayName("assertNoRunningDaemon proceeds normally when socket file does not exist")
        void assertNoRunningDaemon_whenNoSocketFile_proceedsNormally() throws Exception {
            cleanSocketFile();
            DaemonOperation operation = createDaemonOperation();

            Method assertMethod = DaemonOperation.class.getDeclaredMethod("assertNoRunningDaemon");
            assertMethod.setAccessible(true);

            assertDoesNotThrow(() -> assertMethod.invoke(operation));
        }

        @Test
        @DisplayName("assertNoRunningDaemon replaces stale socket file when connection is refused")
        void assertNoRunningDaemon_whenStaleSocketFilePresent_cleansUpAndProceeds() throws Exception {
            Path socketPath = SocketServer.getSocketPath();
            cleanSocketFile();
            Files.createFile(socketPath);

            DaemonOperation operation = createDaemonOperation();
            Method assertMethod = DaemonOperation.class.getDeclaredMethod("assertNoRunningDaemon");
            assertMethod.setAccessible(true);

            assertDoesNotThrow(() -> assertMethod.invoke(operation));
        }

        @Test
        @DisplayName("assertNoRunningDaemon throws IllegalStateException when active daemon is responding")
        void assertNoRunningDaemon_whenActiveDaemonPresent_throwsIllegalStateException() throws Exception {
            Path socketPath = SocketServer.getSocketPath();
            cleanSocketFile();

            try (ServerSocketChannel activeServer = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
                activeServer.bind(UnixDomainSocketAddress.of(socketPath));

                DaemonOperation operation = createDaemonOperation();
                Method assertMethod = DaemonOperation.class.getDeclaredMethod("assertNoRunningDaemon");
                assertMethod.setAccessible(true);

                InvocationTargetException ite = assertThrows(
                        InvocationTargetException.class,
                        () -> assertMethod.invoke(operation)
                );

                assertInstanceOf(IllegalStateException.class, ite.getCause());
                assertTrue(ite.getCause().getMessage().contains("Another Prism daemon is already running"));
            }
        }

        @Test
        @DisplayName("registerSignalHandlers executes without exception")
        void registerSignalHandlers_executesWithoutException() throws Exception {
            DaemonOperation operation = createDaemonOperation();
            Method registerMethod = DaemonOperation.class.getDeclaredMethod("registerSignalHandlers");
            registerMethod.setAccessible(true);

            assertDoesNotThrow(() -> registerMethod.invoke(operation));
        }

        @Test
        @DisplayName("startDaemon throws IllegalStateException when socket terminates with forceful interruption")
        void startDaemon_whenSocketStopsWithForcefulInterruption_throwsIllegalStateException() throws Exception {
            DaemonOperation operation = createDaemonOperation();

            SocketServer mockServer = mock(SocketServer.class);
            ServerSocketChannel channel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            when(mockServer.getServerSocketChannel()).thenReturn(channel);
            when(mockServer.accept()).thenThrow(new RuntimeException("Simulated socket crash during serve"));
            injectAppContextBean(SocketServer.class, mockServer);

            Method startDaemonMethod = DaemonOperation.class.getDeclaredMethod("startDaemon");
            startDaemonMethod.setAccessible(true);

            InvocationTargetException ite = assertThrows(
                    InvocationTargetException.class,
                    () -> startDaemonMethod.invoke(operation)
            );

            assertInstanceOf(IllegalStateException.class, ite.getCause());
            assertTrue(ite.getCause().getMessage().contains("Daemon socket stopped unexpectedly with status FORCEFUL_INTERRUPTION"));
            channel.close();
        }

        @Test
        @DisplayName("startDaemon throws IllegalStateException when thread is interrupted waiting for socket thread")
        void startDaemon_whenInterrupted_throwsIllegalStateException() throws Exception {
            DaemonOperation operation = createDaemonOperation();

            SocketServer mockServer = mock(SocketServer.class);
            ServerSocketChannel channel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            when(mockServer.getServerSocketChannel()).thenReturn(channel);
            CountDownLatch acceptCalled = new CountDownLatch(1);
            when(mockServer.accept()).thenAnswer(_ -> {
                acceptCalled.countDown();
                Thread.sleep(10_000);
                return null;
            });
            injectAppContextBean(SocketServer.class, mockServer);

            Method startDaemonMethod = DaemonOperation.class.getDeclaredMethod("startDaemon");
            startDaemonMethod.setAccessible(true);

            AtomicBoolean interruptedCaught = new AtomicBoolean(false);

            Thread runnerThread = Thread.ofVirtual().start(() -> {
                try {
                    startDaemonMethod.invoke(operation);
                } catch (InvocationTargetException ite) {
                    if (ite.getCause() instanceof IllegalStateException ise
                            && ise.getMessage().contains("Daemon was interrupted")) {
                        interruptedCaught.set(true);
                    }
                } catch (Exception ignored) {
                }
            });

            assertTrue(acceptCalled.await(2, TimeUnit.SECONDS));
            runnerThread.interrupt();
            runnerThread.join(2000);

            assertTrue(interruptedCaught.get(), "Should catch IllegalStateException wrapping InterruptedException");
            channel.close();
        }
    }

    @Nested
    @DisplayName("Serve and Connection Handling Tests")
    class ServeAndConnectionHandlingTests {

        @Test
        @DisplayName("serve terminates immediately when status is already GRACEFUL_INTERRUPTION")
        void serve_whenStatusAlreadyGracefulInterruption_terminatesImmediately() throws Exception {
            DaemonOperation operation = createDaemonOperation();
            try (SocketServer mockServer = mock(SocketServer.class)) {

                SocketServer.setSocketStatusType(SocketStatusType.GRACEFUL_INTERRUPTION);

                Method serveMethod = DaemonOperation.class.getDeclaredMethod("serve", SocketServer.class);
                serveMethod.setAccessible(true);

                assertDoesNotThrow(() -> serveMethod.invoke(operation, mockServer));
            }
            assertEquals(SocketStatusType.GRACEFUL_INTERRUPTION, SocketServer.getSocketStatusType());
        }

        @Test
        @DisplayName("serve breaks accept loop when accept returns null")
        void serve_whenAcceptReturnsNull_breaksLoop() throws Exception {
            DaemonOperation operation = createDaemonOperation();
            try (SocketServer mockServer = mock(SocketServer.class)) {
                when(mockServer.accept()).thenReturn(null);

                SocketServer.setSocketStatusType(SocketStatusType.READY);

                Method serveMethod = DaemonOperation.class.getDeclaredMethod("serve", SocketServer.class);
                serveMethod.setAccessible(true);

                assertDoesNotThrow(() -> serveMethod.invoke(operation, mockServer));
            }
        }

        @Test
        @DisplayName("serve sets status to FORCEFUL_INTERRUPTION and logs when accept throws unexpected RuntimeException")
        void serve_whenAcceptThrowsRuntimeException_setsForcefulInterruptionAndLogs() throws Exception {
            DaemonOperation operation = createDaemonOperation();
            try (SocketServer mockServer = mock(SocketServer.class)) {
                when(mockServer.accept()).thenThrow(new RuntimeException("Simulated socket failure"));

                SocketServer.setSocketStatusType(SocketStatusType.READY);

                Method serveMethod = DaemonOperation.class.getDeclaredMethod("serve", SocketServer.class);
                serveMethod.setAccessible(true);

                assertDoesNotThrow(() -> serveMethod.invoke(operation, mockServer));
            }
            assertEquals(SocketStatusType.FORCEFUL_INTERRUPTION, SocketServer.getSocketStatusType());
        }

        @Test
        @DisplayName("serve returns cleanly when accept throws RuntimeException but status is GRACEFUL_INTERRUPTION")
        void serve_whenAcceptThrowsRuntimeExceptionAndStatusIsGracefulInterruption_returnsCleanly() throws Exception {
            DaemonOperation operation = createDaemonOperation();
            try (SocketServer mockServer = mock(SocketServer.class)) {
                when(mockServer.accept()).thenAnswer(_ -> {
                    SocketServer.setSocketStatusType(SocketStatusType.GRACEFUL_INTERRUPTION);
                    throw new RuntimeException("Channel closed during shutdown");
                });

                SocketServer.setSocketStatusType(SocketStatusType.READY);

                Method serveMethod = DaemonOperation.class.getDeclaredMethod("serve", SocketServer.class);
                serveMethod.setAccessible(true);

                assertDoesNotThrow(() -> serveMethod.invoke(operation, mockServer));
            }
            assertEquals(SocketStatusType.GRACEFUL_INTERRUPTION, SocketServer.getSocketStatusType());
        }

        @Test
        @DisplayName("handleCall ignores blank or null line silently")
        void handleCall_withBlankOrNullLine_returnsSilently() throws Exception {
            DaemonOperation operation = createDaemonOperation();
            Path tempSock = tempDir.resolve("blank_line.sock");

            try (ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
                server.bind(UnixDomainSocketAddress.of(tempSock));
                try (SocketChannel client = SocketChannel.open(UnixDomainSocketAddress.of(tempSock));
                     SocketChannel accepted = server.accept()) {

                    client.write(StandardCharsets.UTF_8.encode("\n"));

                    Method handleCallMethod = DaemonOperation.class.getDeclaredMethod("handleCall", SocketChannel.class);
                    handleCallMethod.setAccessible(true);

                    assertDoesNotThrow(() -> handleCallMethod.invoke(operation, accepted));
                }
            }
        }

        @Test
        @DisplayName("handleCall parses valid command line and writes spectrum")
        void handleCall_withValidCommand_executesCallAndUpdatesConfiguration() throws Exception {
            Path imagePath = createTestImage("call_sample.png", new Color(0x3B, 0x82, 0xF6));
            Path outputDir = tempDir.resolve("call_out");
            Files.createDirectories(outputDir);

            DaemonOperation operation = createDaemonOperation();
            DaemonContext context = operation.getAppContext().getClass(DaemonContext.class);
            assert context.getWriterAdapter() != null;
            context.getWriterAdapter().setDirectory(outputDir.toString());
            context.getWriterAdapter().setFile("call_spectrum.prism");

            Path tempSock = tempDir.resolve("valid_call.sock");
            try (ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
                server.bind(UnixDomainSocketAddress.of(tempSock));
                try (SocketChannel client = SocketChannel.open(UnixDomainSocketAddress.of(tempSock));
                     SocketChannel accepted = server.accept()) {

                    String line = "-p -f " + imagePath + "\n";
                    client.write(StandardCharsets.UTF_8.encode(line));

                    Method handleCallMethod = DaemonOperation.class.getDeclaredMethod("handleCall", SocketChannel.class);
                    handleCallMethod.setAccessible(true);

                    assertDoesNotThrow(() -> handleCallMethod.invoke(operation, accepted));
                }
            }

            Path outputFile = outputDir.resolve("call_spectrum.prism");
            assertTrue(Files.exists(outputFile), "Output spectrum file should be generated by handleCall");
        }

        @Test
        @DisplayName("handleCall catches and logs invalid command line without propagating exception")
        void handleCall_withMalformedCommand_catchesAndLogsWithoutThrowing() throws Exception {
            DaemonOperation operation = createDaemonOperation();
            Path tempSock = tempDir.resolve("invalid_call.sock");

            try (ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
                server.bind(UnixDomainSocketAddress.of(tempSock));
                try (SocketChannel client = SocketChannel.open(UnixDomainSocketAddress.of(tempSock));
                     SocketChannel accepted = server.accept()) {

                    client.write(StandardCharsets.UTF_8.encode("--unsupported-flag-that-fails\n"));

                    Method handleCallMethod = DaemonOperation.class.getDeclaredMethod("handleCall", SocketChannel.class);
                    handleCallMethod.setAccessible(true);

                    assertDoesNotThrow(() -> handleCallMethod.invoke(operation, accepted));
                }
            }
        }
    }

    @Nested
    @DisplayName("Argument and Prism Processing Tests")
    class ArgumentAndPrismProcessingTests {

        @Test
        @DisplayName("writePrism throws IllegalArgumentException when image path is relative")
        void writePrism_whenPathNotAbsolute_throwsIllegalArgumentException() throws Exception {
            DaemonOperation operation = createDaemonOperation();
            DaemonContext context = operation.getAppContext().getClass(DaemonContext.class);

            Method writePrismMethod = DaemonOperation.class.getDeclaredMethod("writePrism", DaemonContext.class, String.class);
            writePrismMethod.setAccessible(true);

            InvocationTargetException ite = assertThrows(
                    InvocationTargetException.class,
                    () -> writePrismMethod.invoke(operation, context, "relative/path/image.png")
            );

            assertInstanceOf(IllegalArgumentException.class, ite.getCause());
            assertTrue(ite.getCause().getMessage().contains("must be absolute"));
        }

        @Test
        @DisplayName("writePrism extracts image palette and writes spectrum file")
        void writePrism_withValidImage_extractsAndWritesSpectrumFile() throws Exception {
            Path imagePath = createTestImage("prism_test.png", new Color(0xEF, 0x44, 0x44));
            Path outputDir = tempDir.resolve("out_prism");
            Files.createDirectories(outputDir);

            DaemonOperation operation = createDaemonOperation();
            DaemonContext context = operation.getAppContext().getClass(DaemonContext.class);
            assert context.getWriterAdapter() != null;
            context.getWriterAdapter().setDirectory(outputDir.toString());
            context.getWriterAdapter().setFile("written.prism");

            Method writePrismMethod = DaemonOperation.class.getDeclaredMethod("writePrism", DaemonContext.class, String.class);
            writePrismMethod.setAccessible(true);

            writePrismMethod.invoke(operation, context, imagePath.toString());

            Path outputFile = outputDir.resolve("written.prism");
            assertTrue(Files.exists(outputFile));
            String content = Files.readString(outputFile);
            assertTrue(content.contains("core100::"));
        }

        @Test
        @DisplayName("writePrism throws IllegalStateException when WriteService cannot read image colors")
        void writePrism_whenImageColorsNull_throwsIllegalStateException() throws Exception {
            Path imagePath = createTestImage("null_colors.png", Color.BLACK);

            DaemonOperation operation = createDaemonOperation();
            DaemonContext context = operation.getAppContext().getClass(DaemonContext.class);

            WriteService mockWriteService = mock(WriteService.class);
            when(mockWriteService.processImage(imagePath.toString())).thenReturn(null);
            injectAppContextBean(WriteService.class, mockWriteService);

            Method writePrismMethod = DaemonOperation.class.getDeclaredMethod("writePrism", DaemonContext.class, String.class);
            writePrismMethod.setAccessible(true);

            InvocationTargetException ite = assertThrows(
                    InvocationTargetException.class,
                    () -> writePrismMethod.invoke(operation, context, imagePath.toString())
            );

            assertInstanceOf(IllegalStateException.class, ite.getCause());
            assertTrue(ite.getCause().getMessage().contains("Could not read colors from image"));
        }

        @Test
        @DisplayName("writePrism with active cache stores newly generated prism and retrieves it on second call")
        void writePrism_withActiveCache_storesInCacheAndRetrievesOnSubsequentCall() throws Exception {
            Path imagePath = createTestImage("cached_sample.png", new Color(0x10, 0xB9, 0x81));
            Path outputDir = tempDir.resolve("out_cache");
            Files.createDirectories(outputDir);

            DaemonOperation operation = createDaemonOperation();
            DaemonContext context = operation.getAppContext().getClass(DaemonContext.class);
            assert context.getWriterAdapter() != null;
            context.getWriterAdapter().setDirectory(outputDir.toString());
            context.getWriterAdapter().setFile("cached.prism");
            context.setCacheAdapter(new CacheAdapter());

            CacheService cacheService = operation.getAppContext().getClass(CacheService.class);
            assertNull(cacheService.get(imagePath.toString()), "Cache should be empty initially");

            Method writePrismMethod = DaemonOperation.class.getDeclaredMethod("writePrism", DaemonContext.class, String.class);
            writePrismMethod.setAccessible(true);

            // First call: cache miss, processes and caches
            writePrismMethod.invoke(operation, context, imagePath.toString());
            Prism cachedPrism = cacheService.get(imagePath.toString());
            assertNotNull(cachedPrism, "Prism should be populated in cache after first write");

            // Second call: cache hit, reads directly from cache
            writePrismMethod.invoke(operation, context, imagePath.toString());
            assertSame(cachedPrism, cacheService.get(imagePath.toString()));
        }

        @Test
        @DisplayName("applyArgument with storage adapter persists image path via StorageService")
        void applyArgument_whenParameterAdapterPresentWithStorage_writesPrismAndStores() throws Exception {
            Path imagePath = createTestImage("stored_sample.png", new Color(0xF5, 0x9E, 0x0B));
            Path outputDir = tempDir.resolve("out_storage");
            Files.createDirectories(outputDir);

            DaemonOperation operation = createDaemonOperation();
            DaemonContext context = operation.getAppContext().getClass(DaemonContext.class);
            assert context.getWriterAdapter() != null;
            context.getWriterAdapter().setDirectory(outputDir.toString());
            context.getWriterAdapter().setFile("stored.prism");

            StorageAdapter storageAdapter = new StorageAdapter();
            storageAdapter.setDirectory(outputDir.toString());
            storageAdapter.setFile("storage_history.json");
            context.setStorageAdapter(storageAdapter);

            ParameterAdapter paramAdapter = new ParameterAdapter();
            paramAdapter.setFile(imagePath.toString());
            context.setParameterAdapter(paramAdapter);

            Method applyArgumentMethod = DaemonOperation.class.getDeclaredMethod("applyArgument", DaemonContext.class);
            applyArgumentMethod.setAccessible(true);

            applyArgumentMethod.invoke(operation, context);

            assertTrue(Files.exists(outputDir.resolve("stored.prism")));
            assertTrue(Files.exists(outputDir.resolve("storage_history.json")));
        }

        @Test
        @DisplayName("applyArgument when parameter file is null or blank logs warning and does not crash")
        void applyArgument_whenParameterAdapterHasNullOrBlankFile_logsWarningAndNoOps() throws Exception {
            DaemonOperation operation = createDaemonOperation();
            DaemonContext context = operation.getAppContext().getClass(DaemonContext.class);

            ParameterAdapter paramAdapter = new ParameterAdapter();
            paramAdapter.setFile("");
            context.setParameterAdapter(paramAdapter);

            Method applyArgumentMethod = DaemonOperation.class.getDeclaredMethod("applyArgument", DaemonContext.class);
            applyArgumentMethod.setAccessible(true);

            assertDoesNotThrow(() -> applyArgumentMethod.invoke(operation, context));
        }

        @Test
        @DisplayName("applyArgument returns silently when ParameterAdapter is null")
        void applyArgument_whenParameterAdapterNull_returnsSilently() throws Exception {
            DaemonOperation operation = createDaemonOperation();
            DaemonContext context = operation.getAppContext().getClass(DaemonContext.class);
            assertNull(context.getParameterAdapter());

            Method applyArgumentMethod = DaemonOperation.class.getDeclaredMethod("applyArgument", DaemonContext.class);
            applyArgumentMethod.setAccessible(true);

            assertDoesNotThrow(() -> applyArgumentMethod.invoke(operation, context));
        }

        @Test
        @DisplayName("applyCommands triggers shutdown immediately when command is STOP_CMD")
        void applyCommands_withStopCommand_triggersShutdown() throws Exception {
            Command stopCmd = new Command();
            stopCmd.add(Main.STOP_CMD);

            DaemonOperation operation = createDaemonOperation(stopCmd);

            Method applyCommandsMethod = DaemonOperation.class.getDeclaredMethod("applyCommands", Command[].class);
            applyCommandsMethod.setAccessible(true);

            applyCommandsMethod.invoke(operation, (Object) new Command[]{stopCmd});

            Field stoppedField = DaemonOperation.class.getDeclaredField("stopped");
            stoppedField.setAccessible(true);
            AtomicBoolean stopped = (AtomicBoolean) stoppedField.get(operation);
            assertTrue(stopped.get(), "Shutdown should be invoked when stop command received");
        }
    }

    @Nested
    @DisplayName("Listener and Cache Reconciliation Tests")
    class ListenerAndCacheReconciliationTests {

        @Test
        @DisplayName("reconcileCache clears ColorCache when CacheAdapter is absent")
        void reconcileCache_whenCacheAdapterNull_clearsColorCache() throws Exception {
            DaemonOperation operation = createDaemonOperation();
            DaemonContext context = operation.getAppContext().getClass(DaemonContext.class);
            context.setCacheAdapter(null);

            ColorCache cache = operation.getAppContext().getClass(ColorCache.class);
            Prism dummyPrism = mock(Prism.class);
            cache.add("/dummy/path.png", dummyPrism);
            assertNotNull(cache.get("/dummy/path.png"));

            Method reconcileCacheMethod = DaemonOperation.class.getDeclaredMethod("reconcileCache", DaemonContext.class);
            reconcileCacheMethod.setAccessible(true);

            reconcileCacheMethod.invoke(operation, context);

            assertNull(cache.get("/dummy/path.png"), "Cache should be cleared when CacheAdapter is null");
        }

        @Test
        @DisplayName("reconcileCache preserves ColorCache entries when CacheAdapter is active")
        void reconcileCache_whenCacheAdapterPresent_preservesColorCache() throws Exception {
            DaemonOperation operation = createDaemonOperation();
            DaemonContext context = operation.getAppContext().getClass(DaemonContext.class);
            context.setCacheAdapter(new CacheAdapter());

            ColorCache cache = operation.getAppContext().getClass(ColorCache.class);
            Prism dummyPrism = mock(Prism.class);
            cache.add("/dummy/path2.png", dummyPrism);

            Method reconcileCacheMethod = DaemonOperation.class.getDeclaredMethod("reconcileCache", DaemonContext.class);
            reconcileCacheMethod.setAccessible(true);

            reconcileCacheMethod.invoke(operation, context);

            assertNotNull(cache.get("/dummy/path2.png"), "Cache entries should remain intact when CacheAdapter is active");
        }

        @Test
        @DisplayName("reconcileListener starts listener thread when ListenerAdapter is active and thread is not running")
        void reconcileListener_whenListenerAdapterActiveAndNotRunning_startsListenerThread() throws Exception {
            DaemonOperation operation = createDaemonOperation();
            DaemonContext context = operation.getAppContext().getClass(DaemonContext.class);
            context.setListenerAdapter(new ListenerAdapter());

            ListeningService mockListeningService = mock(ListeningService.class);
            CountDownLatch startedLatch = new CountDownLatch(1);
            CountDownLatch blockLatch = new CountDownLatch(1);
            doAnswer(_ -> {
                startedLatch.countDown();

                //noinspection ResultOfMethodCallIgnored
                blockLatch.await(2, TimeUnit.SECONDS);
                return null;
            }).when(mockListeningService).listen(any());
            injectAppContextBean(ListeningService.class, mockListeningService);

            Method reconcileListenerMethod = DaemonOperation.class.getDeclaredMethod("reconcileListener", DaemonContext.class, Command[].class);
            reconcileListenerMethod.setAccessible(true);

            reconcileListenerMethod.invoke(operation, context, (Object) new Command[]{});

            Field threadField = DaemonOperation.class.getDeclaredField("listenerThread");
            threadField.setAccessible(true);
            Thread listenerThread = (Thread) threadField.get(operation);

            assertNotNull(listenerThread);
            assertTrue(startedLatch.await(2, TimeUnit.SECONDS), "listen should be called by listenerThread");
            assertTrue(listenerThread.isAlive());

            // Cleanup listener
            blockLatch.countDown();
            Method stopListenerMethod = DaemonOperation.class.getDeclaredMethod("stopListener");
            stopListenerMethod.setAccessible(true);
            stopListenerMethod.invoke(operation);
        }

        @Test
        @DisplayName("reconcileListener stops listener thread when ListenerAdapter is null and thread is alive")
        void reconcileListener_whenListenerNotActiveAndRunning_stopsListenerThread() throws Exception {
            DaemonOperation operation = createDaemonOperation();
            DaemonContext context = operation.getAppContext().getClass(DaemonContext.class);

            SocketSubscriber mockSubscriber = mock(SocketSubscriber.class);
            injectAppContextBean(SocketSubscriber.class, mockSubscriber);

            CountDownLatch latch = new CountDownLatch(1);
            Thread testThread = Thread.ofVirtual().start(() -> {
                try {
                    //noinspection ResultOfMethodCallIgnored
                    latch.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                }
            });

            Field threadField = DaemonOperation.class.getDeclaredField("listenerThread");
            threadField.setAccessible(true);
            threadField.set(operation, testThread);

            context.setListenerAdapter(null);

            Method reconcileListenerMethod = DaemonOperation.class.getDeclaredMethod("reconcileListener", DaemonContext.class, Command[].class);
            reconcileListenerMethod.setAccessible(true);

            reconcileListenerMethod.invoke(operation, context, (Object) new Command[]{});

            verify(mockSubscriber).close();
            latch.countDown();
        }

        @Test
        @DisplayName("reconcileListener restarts listener when LISTEN command is present in callCommands")
        void reconcileListener_whenListenCommandInCallCommandsAndAlreadyRunning_restartsListener() throws Exception {
            DaemonOperation operation = createDaemonOperation();
            DaemonContext context = operation.getAppContext().getClass(DaemonContext.class);
            context.setListenerAdapter(new ListenerAdapter());

            SocketSubscriber mockSubscriber = mock(SocketSubscriber.class);
            injectAppContextBean(SocketSubscriber.class, mockSubscriber);

            ListeningService mockListeningService = mock(ListeningService.class);
            CountDownLatch startedLatch = new CountDownLatch(1);
            CountDownLatch latch = new CountDownLatch(1);
            doAnswer(_ -> {
                startedLatch.countDown();
                //noinspection ResultOfMethodCallIgnored
                latch.await(2, TimeUnit.SECONDS);
                return null;
            }).when(mockListeningService).listen(any());
            injectAppContextBean(ListeningService.class, mockListeningService);

            Thread existingThread = Thread.ofVirtual().start(() -> {
                try {
                    //noinspection ResultOfMethodCallIgnored
                    latch.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                }
            });
            Field threadField = DaemonOperation.class.getDeclaredField("listenerThread");
            threadField.setAccessible(true);
            threadField.set(operation, existingThread);

            Command listenCmd = new Command();
            listenCmd.setCommand(AppCommandType.LISTEN);

            Method reconcileListenerMethod = DaemonOperation.class.getDeclaredMethod("reconcileListener", DaemonContext.class, Command[].class);
            reconcileListenerMethod.setAccessible(true);

            reconcileListenerMethod.invoke(operation, context, (Object) new Command[]{listenCmd});

            verify(mockSubscriber).close();

            Thread newThread = (Thread) threadField.get(operation);
            assertNotNull(newThread);
            assertTrue(startedLatch.await(2, TimeUnit.SECONDS), "New listener should be started");
            latch.countDown();
        }

        @Test
        @DisplayName("onListened returns early without processing when ListenerAdapter is null")
        void onListened_whenListenerAdapterNull_returnsEarlyWithoutProcessing() throws Exception {
            Path imagePath = createTestImage("listened_null.png", Color.BLUE);
            Path outputDir = tempDir.resolve("out_listened_null");
            Files.createDirectories(outputDir);

            DaemonOperation operation = createDaemonOperation();
            DaemonContext context = operation.getAppContext().getClass(DaemonContext.class);
            assert context.getWriterAdapter() != null;
            context.getWriterAdapter().setDirectory(outputDir.toString());
            context.getWriterAdapter().setFile("listened_null.prism");
            context.setListenerAdapter(null);

            Method onListenedMethod = DaemonOperation.class.getDeclaredMethod("onListened", String.class);
            onListenedMethod.setAccessible(true);

            assertDoesNotThrow(() -> onListenedMethod.invoke(operation, imagePath.toString()));

            assertFalse(Files.exists(outputDir.resolve("listened_null.prism")));
        }

        @Test
        @DisplayName("onListened processes image and writes spectrum when ListenerAdapter is active")
        void onListened_whenListenerActive_processesAndWritesSpectrum() throws Exception {
            Path imagePath = createTestImage("listened_active.png", new Color(0x63, 0x66, 0xF1));
            Path outputDir = tempDir.resolve("out_listened_active");
            Files.createDirectories(outputDir);

            DaemonOperation operation = createDaemonOperation();
            DaemonContext context = operation.getAppContext().getClass(DaemonContext.class);
            assert context.getWriterAdapter() != null;
            context.getWriterAdapter().setDirectory(outputDir.toString());
            context.getWriterAdapter().setFile("listened_active.prism");
            context.setListenerAdapter(new ListenerAdapter());

            Method onListenedMethod = DaemonOperation.class.getDeclaredMethod("onListened", String.class);
            onListenedMethod.setAccessible(true);

            assertDoesNotThrow(() -> onListenedMethod.invoke(operation, imagePath.toString()));

            assertTrue(Files.exists(outputDir.resolve("listened_active.prism")));
        }

        @Test
        @DisplayName("onListened catches and logs writePrism errors without throwing")
        void onListened_whenWritePrismThrowsException_logsSevereWithoutPropagating() throws Exception {
            DaemonOperation operation = createDaemonOperation();
            DaemonContext context = operation.getAppContext().getClass(DaemonContext.class);
            context.setListenerAdapter(new ListenerAdapter());

            Method onListenedMethod = DaemonOperation.class.getDeclaredMethod("onListened", String.class);
            onListenedMethod.setAccessible(true);

            assertDoesNotThrow(() -> onListenedMethod.invoke(operation, "relative/image.png"));
        }

        @Test
        @DisplayName("stopListener returns silently when listener thread is null or not alive")
        void stopListener_whenThreadNotAliveOrNull_returnsSilently() throws Exception {
            DaemonOperation operation = createDaemonOperation();

            Method stopListenerMethod = DaemonOperation.class.getDeclaredMethod("stopListener");
            stopListenerMethod.setAccessible(true);

            assertDoesNotThrow(() -> stopListenerMethod.invoke(operation));
        }
    }

    @Nested
    @DisplayName("Graceful Shutdown Tests")
    class GracefulShutdownTests {

        @Test
        @DisplayName("shutdown runs only once due to atomic stopped guard")
        void shutdown_isIdempotent_executesOnlyOnce() throws Exception {
            DaemonOperation operation = createDaemonOperation();

            Method shutdownMethod = DaemonOperation.class.getDeclaredMethod("shutdown");
            shutdownMethod.setAccessible(true);

            shutdownMethod.invoke(operation);

            Field stoppedField = DaemonOperation.class.getDeclaredField("stopped");
            stoppedField.setAccessible(true);
            AtomicBoolean stopped = (AtomicBoolean) stoppedField.get(operation);
            assertTrue(stopped.get());

            // Second invocation should safely return without exception
            assertDoesNotThrow(() -> shutdownMethod.invoke(operation));
        }

        @Test
        @DisplayName("shutdown preserves FORCEFUL_INTERRUPTION status without overwriting to graceful")
        void shutdown_whenStatusIsForcefulInterruption_preservesForcefulStatus() throws Exception {
            DaemonOperation operation = createDaemonOperation();
            SocketServer.setSocketStatusType(SocketStatusType.FORCEFUL_INTERRUPTION);

            Method shutdownMethod = DaemonOperation.class.getDeclaredMethod("shutdown");
            shutdownMethod.setAccessible(true);

            shutdownMethod.invoke(operation);

            assertEquals(SocketStatusType.FORCEFUL_INTERRUPTION, SocketServer.getSocketStatusType());
        }

        @Test
        @DisplayName("shutdown unwatches ColorCache and deletes socket file")
        void shutdown_unwatchesColorCacheAndDeletesSocketFile() throws Exception {
            DaemonOperation operation = createDaemonOperation();

            Path socketPath = SocketServer.getSocketPath();
            cleanSocketFile();
            Files.createFile(socketPath);
            assertTrue(Files.exists(socketPath));

            Method shutdownMethod = DaemonOperation.class.getDeclaredMethod("shutdown");
            shutdownMethod.setAccessible(true);

            shutdownMethod.invoke(operation);

            assertFalse(Files.exists(socketPath), "Socket file should be deleted on shutdown");
            assertEquals(SocketStatusType.GRACEFUL_INTERRUPTION, SocketServer.getSocketStatusType());
        }
    }
}