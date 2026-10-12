package prism.configuration.context;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import prism.adapter.cli.AppCommandType;
import prism.adapter.cli.input.Command;
import prism.configuration.adapter.CacheAdapter;
import prism.configuration.adapter.ListenerAdapter;
import prism.configuration.adapter.ParameterAdapter;
import prism.configuration.adapter.StorageAdapter;
import prism.configuration.adapter.WriterAdapter;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DaemonContext Unit Tests")
class TestDaemonContext {

    private DaemonContext context;

    @BeforeEach
    void setUp() {
        context = new DaemonContext();
    }

    private Command createCommand(AppCommandType type, String... subCommands) {
        Command cmd = new Command();
        cmd.setCommand(type);
        for (String sub : subCommands) {
            cmd.add(sub);
        }
        return cmd;
    }

    @Nested
    @DisplayName("Defaults & Adapters")
    class DefaultsAndAdaptersTests {

        @Test
        @DisplayName("should have non-null default WriterAdapter upon instantiation")
        void shouldHaveDefaultWriterAdapter() {
            assertNotNull(context.getWriterAdapter());
            assertEquals("palette.spec", context.getWriterAdapter().getFile());
            assertEquals("rgb", context.getWriterAdapter().getType());
        }

        @Test
        @DisplayName("should have null adapters initially for cache, storage, listener, and parameter")
        void shouldHaveNullOptionalAdaptersInitially() {
            assertNull(context.getCacheAdapter());
            assertNull(context.getStorageAdapter());
            assertNull(context.getListenerAdapter());
            assertNull(context.getParameterAdapter());
        }

        @Test
        @DisplayName("should consume ParameterAdapter on getParameterAdapter, returning null on subsequent calls")
        void shouldConsumeParameterAdapterOnGet() {
            ParameterAdapter adapter = new ParameterAdapter();
            adapter.setFile("input.png");
            context.setParameterAdapter(adapter);

            ParameterAdapter first = context.getParameterAdapter();
            ParameterAdapter second = context.getParameterAdapter();

            assertSame(adapter, first);
            assertNull(second, "Parameter adapter must be consumed on first read");
        }

        @Test
        @DisplayName("should allow explicitly setting custom adapters")
        void shouldAllowSettingCustomAdapters() {
            CacheAdapter cache = new CacheAdapter();
            StorageAdapter storage = new StorageAdapter();
            ListenerAdapter listener = new ListenerAdapter();

            context.setCacheAdapter(cache);
            context.setStorageAdapter(storage);
            context.setListenerAdapter(listener);

            assertSame(cache, context.getCacheAdapter());
            assertSame(storage, context.getStorageAdapter());
            assertSame(listener, context.getListenerAdapter());
        }
    }

    @Nested
    @DisplayName("updateConfiguration with WRITE command")
    class WriteCommandTests {

        @Test
        @DisplayName("should configure writer file, directory, and type")
        void shouldConfigureWriterAdapter(@TempDir Path tempDir) {
            Command cmd = createCommand(AppCommandType.WRITE,
                    "-f", "custom.spec",
                    "-d", tempDir.toString(),
                    "-t", "hex");

            context.updateConfiguration(new Command[]{cmd});

            WriterAdapter adapter = context.getWriterAdapter();

            assert adapter != null;
            assertEquals("custom.spec", adapter.getFile());

            assertEquals(tempDir.toString(), adapter.getDirectory());
            assertEquals("hex", adapter.getType());
        }

        @Test
        @DisplayName("should reset WriterAdapter to default on reset sub-command")
        void shouldResetWriterAdapterOnReset() {
            Command setup = createCommand(AppCommandType.WRITE, "-f", "custom.spec", "-t", "hex");
            context.updateConfiguration(new Command[]{setup});

            Command reset = createCommand(AppCommandType.WRITE, "reset");
            context.updateConfiguration(new Command[]{reset});

            assert context.getWriterAdapter() != null;
            assertEquals("palette.spec", context.getWriterAdapter().getFile());

            assertEquals("rgb", context.getWriterAdapter().getType());
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when writer directory is invalid")
        void shouldThrowExceptionWhenDirectoryIsInvalid() {
            Command cmd = createCommand(AppCommandType.WRITE, "-d", "/non/existent/path/99999");

            assertThrows(IllegalArgumentException.class, () -> context.updateConfiguration(new Command[]{cmd}));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when writer type is invalid")
        void shouldThrowExceptionWhenTypeIsInvalid() {
            Command cmd = createCommand(AppCommandType.WRITE, "-t", "invalid_type");

            assertThrows(IllegalArgumentException.class, () -> context.updateConfiguration(new Command[]{cmd}));
        }
    }

    @Nested
    @DisplayName("updateConfiguration with CACHE command")
    class CacheCommandTests {

        @Test
        @DisplayName("should configure cache amount and keep-alive duration")
        void shouldConfigureCacheAdapter() {
            Command cmd = createCommand(AppCommandType.CACHE,
                    "-a", "15",
                    "-k", "120");

            context.updateConfiguration(new Command[]{cmd});

            CacheAdapter adapter = context.getCacheAdapter();
            assertNotNull(adapter);
            assertEquals(15, adapter.getAmount());
            assertEquals(120, adapter.getTimeout());
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when amount is not a valid number")
        void shouldThrowExceptionWhenAmountNotNumber() {
            Command cmd = createCommand(AppCommandType.CACHE, "-a", "not_a_number");

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> context.updateConfiguration(new Command[]{cmd})
            );

            assertTrue(ex.getMessage().contains("is not a valid amount."));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when keep-alive is not a valid number")
        void shouldThrowExceptionWhenKeepAliveNotNumber() {
            Command cmd = createCommand(AppCommandType.CACHE, "-k", "invalid_timeout");

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> context.updateConfiguration(new Command[]{cmd})
            );

            assertTrue(ex.getMessage().contains("is not a valid timeout."));
        }

        @Test
        @DisplayName("should nullify CacheAdapter when interrupt sub-command is encountered")
        void shouldNullifyCacheAdapterOnInterrupt() {
            Command setup = createCommand(AppCommandType.CACHE, "-a", "10");
            context.updateConfiguration(new Command[]{setup});
            assertNotNull(context.getCacheAdapter());

            Command interrupt = createCommand(AppCommandType.CACHE, "interrupt");
            context.updateConfiguration(new Command[]{interrupt});

            assertNull(context.getCacheAdapter());
        }

        @Test
        @DisplayName("should reset CacheAdapter to defaults when reset sub-command is encountered")
        void shouldResetCacheAdapterOnReset() {
            Command setup = createCommand(AppCommandType.CACHE, "-a", "50", "-k", "500");
            context.updateConfiguration(new Command[]{setup});

            Command reset = createCommand(AppCommandType.CACHE, "reset");
            context.updateConfiguration(new Command[]{reset});

            CacheAdapter adapter = context.getCacheAdapter();
            assertNotNull(adapter);
            assertEquals(CacheAdapter.AMOUNT, adapter.getAmount());
            assertEquals(CacheAdapter.KEEP_ALIVE, adapter.getTimeout());
        }
    }

    @Nested
    @DisplayName("updateConfiguration with STORE command")
    class StoreCommandTests {

        @Test
        @DisplayName("should configure storage file and directory")
        void shouldConfigureStorageAdapter(@TempDir Path tempDir) {
            Command cmd = createCommand(AppCommandType.STORE,
                    "-f", "my-storage.json",
                    "-d", tempDir.toString());

            context.updateConfiguration(new Command[]{cmd});

            StorageAdapter adapter = context.getStorageAdapter();
            assertNotNull(adapter);
            assertEquals("my-storage.json", adapter.getFile());
            assertEquals(tempDir.toString(), adapter.getDirectory());
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when storage directory is invalid")
        void shouldThrowExceptionWhenStorageDirectoryIsInvalid() {
            Command cmd = createCommand(AppCommandType.STORE, "-d", "/invalid/store/path");

            assertThrows(IllegalArgumentException.class, () -> context.updateConfiguration(new Command[]{cmd}));
        }

        @Test
        @DisplayName("should nullify StorageAdapter when interrupt sub-command is encountered")
        void shouldNullifyStorageAdapterOnInterrupt() {
            Command setup = createCommand(AppCommandType.STORE, "-f", "store.json");
            context.updateConfiguration(new Command[]{setup});
            assertNotNull(context.getStorageAdapter());

            Command interrupt = createCommand(AppCommandType.STORE, "interrupt");
            context.updateConfiguration(new Command[]{interrupt});

            assertNull(context.getStorageAdapter());
        }

        @Test
        @DisplayName("should reset StorageAdapter to defaults when reset sub-command is encountered")
        void shouldResetStorageAdapterOnReset() {
            Command setup = createCommand(AppCommandType.STORE, "-f", "custom-store.json");
            context.updateConfiguration(new Command[]{setup});

            Command reset = createCommand(AppCommandType.STORE, "reset");
            context.updateConfiguration(new Command[]{reset});

            StorageAdapter adapter = context.getStorageAdapter();
            assertNotNull(adapter);
            assertEquals("storage.json", adapter.getFile());
        }
    }

    @Nested
    @DisplayName("updateConfiguration with LISTEN command")
    class ListenCommandTests {

        @Test
        @DisplayName("should configure listener file and directory")
        void shouldConfigureListenerAdapter(@TempDir Path tempDir) {
            Command cmd = createCommand(AppCommandType.LISTEN,
                    "-f", "my-daemon.socket",
                    "-d", tempDir.toString());

            context.updateConfiguration(new Command[]{cmd});

            ListenerAdapter adapter = context.getListenerAdapter();
            assertNotNull(adapter);
            assertEquals("my-daemon.socket", adapter.getFile());
            assertEquals(tempDir.toString(), adapter.getDirectory());
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when listener directory is invalid")
        void shouldThrowExceptionWhenListenerDirectoryIsInvalid() {
            Command cmd = createCommand(AppCommandType.LISTEN, "-d", "/invalid/listen/path");

            assertThrows(IllegalArgumentException.class, () -> context.updateConfiguration(new Command[]{cmd}));
        }

        @Test
        @DisplayName("should nullify ListenerAdapter when interrupt sub-command is encountered")
        void shouldNullifyListenerAdapterOnInterrupt() {
            Command setup = createCommand(AppCommandType.LISTEN, "-f", "socket.sock");
            context.updateConfiguration(new Command[]{setup});
            assertNotNull(context.getListenerAdapter());

            Command interrupt = createCommand(AppCommandType.LISTEN, "interrupt");
            context.updateConfiguration(new Command[]{interrupt});

            assertNull(context.getListenerAdapter());
        }

        @Test
        @DisplayName("should reset ListenerAdapter to defaults when reset sub-command is encountered")
        void shouldResetListenerAdapterOnReset() {
            Command setup = createCommand(AppCommandType.LISTEN, "-f", "custom.sock");
            context.updateConfiguration(new Command[]{setup});

            Command reset = createCommand(AppCommandType.LISTEN, "reset");
            context.updateConfiguration(new Command[]{reset});

            ListenerAdapter adapter = context.getListenerAdapter();
            assertNotNull(adapter);
            assertEquals(ListenerAdapter.FILE, adapter.getFile());
            assertEquals(ListenerAdapter.DIR, adapter.getDirectory());
        }
    }

    @Nested
    @DisplayName("updateConfiguration with PARAMETER command")
    class ParameterCommandTests {

        @Test
        @DisplayName("should configure parameter file")
        void shouldConfigureParameterFile() {
            Command cmd = createCommand(AppCommandType.PARAMETER, "-f", "image.png");

            context.updateConfiguration(new Command[]{cmd});

            ParameterAdapter adapter = context.getParameterAdapter();
            assertNotNull(adapter);
            assertEquals("image.png", adapter.getFile());
        }
    }

    @Nested
    @DisplayName("Unsupported Commands & Error Handling")
    class UnsupportedCommandsTests {

        @Test
        @DisplayName("should throw IllegalArgumentException when command is null")
        void shouldThrowExceptionWhenCommandIsNull() {
            Command cmd = new Command();
            cmd.setCommand(null);

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> context.updateConfiguration(new Command[]{cmd})
            );

            assertTrue(ex.getMessage().contains("No configuration found for null specified value."));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when sub-command is invalid")
        void shouldThrowExceptionWhenSubCommandIsInvalid() {
            Command cmd = createCommand(AppCommandType.LISTEN, "--unknown-flag");

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> context.updateConfiguration(new Command[]{cmd})
            );

            assertTrue(ex.getMessage().contains("--unknown-flag is not a valid Sub command."));
        }
    }
}