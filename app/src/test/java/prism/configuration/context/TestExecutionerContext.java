package prism.configuration.context;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import prism.adapter.cli.AppCommandType;
import prism.adapter.cli.input.Command;
import prism.configuration.adapter.ParameterAdapter;
import prism.configuration.adapter.WriterAdapter;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ExecutionerContext Unit Tests")
class TestExecutionerContext {

    private ExecutionerContext context;

    @BeforeEach
    void setUp() {
        context = new ExecutionerContext();
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
            assertEquals("rgb", context.getWriterAdapter().getType());
            assertEquals("palette.spec", context.getWriterAdapter().getFile());
        }

        @Test
        @DisplayName("should have null ParameterAdapter initially")
        void shouldHaveNullParameterAdapterInitially() {
            assertNull(context.getParameterAdapter());
        }

        @Test
        @DisplayName("should consume ParameterAdapter on getParameterAdapter, returning null on subsequent calls")
        void shouldConsumeParameterAdapterOnGet() {
            ParameterAdapter adapter = new ParameterAdapter();
            adapter.setFile("test.png");
            context.setParameterAdapter(adapter);

            ParameterAdapter firstCall = context.getParameterAdapter();
            ParameterAdapter secondCall = context.getParameterAdapter();

            assertSame(adapter, firstCall);
            assertNull(secondCall, "Subsequent call must return null after parameter adapter is consumed");
        }

        @Test
        @DisplayName("should return null for CacheAdapter as executioner does not support caching")
        void shouldReturnNullForCacheAdapter() {
            assertNull(context.getCacheAdapter());
        }

        @Test
        @DisplayName("should return null for StorageAdapter as executioner does not support storage")
        void shouldReturnNullForStorageAdapter() {
            assertNull(context.getStorageAdapter());
        }

        @Test
        @DisplayName("should return null for ListenerAdapter as executioner does not support listening")
        void shouldReturnNullForListenerAdapter() {
            assertNull(context.getListenerAdapter());
        }

        @Test
        @DisplayName("should allow explicitly setting a custom WriterAdapter")
        void shouldAllowSettingCustomWriterAdapter() {
            WriterAdapter custom = new WriterAdapter();
            custom.setFile("custom.spec");
            context.setWriterAdapter(custom);

            assertSame(custom, context.getWriterAdapter());
        }
    }

    @Nested
    @DisplayName("updateConfiguration with PARAMETER command")
    class ParameterCommandTests {

        @Test
        @DisplayName("should parse file flag (-f) for parameter command")
        void shouldParseFileShortFlagForParameter() {
            Command cmd = createCommand(AppCommandType.PARAMETER, "-f", "wallpaper.png");

            context.updateConfiguration(new Command[]{cmd});

            ParameterAdapter adapter = context.getParameterAdapter();
            assertNotNull(adapter);
            assertEquals("wallpaper.png", adapter.getFile());
        }

        @Test
        @DisplayName("should parse file long flag (--file) for parameter command")
        void shouldParseFileLongFlagForParameter() {
            Command cmd = createCommand(AppCommandType.PARAMETER, "--file", "photo.jpg");

            context.updateConfiguration(new Command[]{cmd});

            ParameterAdapter adapter = context.getParameterAdapter();
            assertNotNull(adapter);
            assertEquals("photo.jpg", adapter.getFile());
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when sub-command in parameter is invalid")
        void shouldThrowExceptionWhenParameterSubCommandIsInvalid() {
            Command cmd = createCommand(AppCommandType.PARAMETER, "--invalid-flag", "value");

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> context.updateConfiguration(new Command[]{cmd})
            );

            assertTrue(ex.getMessage().contains("--invalid-flag is not a valid Sub command."));
        }
    }

    @Nested
    @DisplayName("updateConfiguration with WRITE command")
    class WriteCommandTests {

        @Test
        @DisplayName("should parse file flag for write command")
        void shouldParseFileFlagForWrite() {
            Command cmd = createCommand(AppCommandType.WRITE, "-f", "output.spec");

            context.updateConfiguration(new Command[]{cmd});

            assert context.getWriterAdapter() != null;
            assertEquals("output.spec", context.getWriterAdapter().getFile());
        }

        @Test
        @DisplayName("should parse directory flag with a valid existing directory")
        void shouldParseDirectoryFlagWithValidDir(@TempDir Path tempDir) {
            Command cmd = createCommand(AppCommandType.WRITE, "-d", tempDir.toString());

            context.updateConfiguration(new Command[]{cmd});

            assert context.getWriterAdapter() != null;
            assertEquals(tempDir.toString(), context.getWriterAdapter().getDirectory());
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when directory is invalid or non-existent")
        void shouldThrowExceptionWhenDirectoryIsInvalid() {
            Command cmd = createCommand(AppCommandType.WRITE, "-d", "/non/existent/prism/dir/99999");

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> context.updateConfiguration(new Command[]{cmd})
            );

            assertTrue(ex.getMessage().contains("is not a valid directory."));
        }

        @Test
        @DisplayName("should parse type flag with valid 'rgb' type")
        void shouldParseTypeFlagRgb() {
            Command cmd = createCommand(AppCommandType.WRITE, "-t", "RGB");

            context.updateConfiguration(new Command[]{cmd});

            assert context.getWriterAdapter() != null;
            assertEquals("rgb", context.getWriterAdapter().getType());
        }

        @Test
        @DisplayName("should parse type flag with valid 'hex' type")
        void shouldParseTypeFlagHex() {
            Command cmd = createCommand(AppCommandType.WRITE, "-t", "HEX");

            context.updateConfiguration(new Command[]{cmd});

            assert context.getWriterAdapter() != null;
            assertEquals("hex", context.getWriterAdapter().getType());
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when color write type is neither rgb nor hex")
        void shouldThrowExceptionWhenTypeIsInvalid() {
            Command cmd = createCommand(AppCommandType.WRITE, "-t", "cmyk");

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> context.updateConfiguration(new Command[]{cmd})
            );

            assertTrue(ex.getMessage().contains("cmyk is not a valid type for color writing."));
        }

        @Test
        @DisplayName("should reset WriterAdapter to default on reset sub-command")
        void shouldResetWriterAdapterOnReset() {
            Command setupCmd = createCommand(AppCommandType.WRITE, "-f", "custom.spec", "-t", "hex");
            context.updateConfiguration(new Command[]{setupCmd});

            assert context.getWriterAdapter() != null;
            assertEquals("custom.spec", context.getWriterAdapter().getFile());

            assertEquals("hex", context.getWriterAdapter().getType());

            Command resetCmd = createCommand(AppCommandType.WRITE, "reset");
            context.updateConfiguration(new Command[]{resetCmd});

            assertEquals("palette.spec", context.getWriterAdapter().getFile());
            assertEquals("rgb", context.getWriterAdapter().getType());
        }

        @Test
        @DisplayName("should preserve previously configured fields when updating different fields")
        void shouldPreserveExistingFieldsWhenUpdating(@TempDir Path tempDir) {
            Command cmd1 = createCommand(AppCommandType.WRITE, "-f", "palette.spec");
            context.updateConfiguration(new Command[]{cmd1});

            Command cmd2 = createCommand(AppCommandType.WRITE, "-d", tempDir.toString());
            context.updateConfiguration(new Command[]{cmd2});

            assert context.getWriterAdapter() != null;
            assertEquals("palette.spec", context.getWriterAdapter().getFile());

            assertEquals(tempDir.toString(), context.getWriterAdapter().getDirectory());
        }
    }

    @Nested
    @DisplayName("Unsupported Commands & Error Handling")
    class UnsupportedCommandsTests {

        @Test
        @DisplayName("should throw IllegalArgumentException when LISTEN command is passed to executioner")
        void shouldThrowExceptionForListenCommand() {
            Command cmd = createCommand(AppCommandType.LISTEN, "-f", "file.png");

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> context.updateConfiguration(new Command[]{cmd})
            );

            assertTrue(ex.getMessage().contains("No configuration found for LISTEN specified value."));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when STORE command is passed to executioner")
        void shouldThrowExceptionForStoreCommand() {
            Command cmd = createCommand(AppCommandType.STORE, "-f", "file.png");

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> context.updateConfiguration(new Command[]{cmd})
            );

            assertTrue(ex.getMessage().contains("No configuration found for STORE specified value."));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when CACHE command is passed to executioner")
        void shouldThrowExceptionForCacheCommand() {
            Command cmd = createCommand(AppCommandType.CACHE, "-a", "5");

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> context.updateConfiguration(new Command[]{cmd})
            );

            assertTrue(ex.getMessage().contains("No configuration found for CACHE specified value."));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when command type is null")
        void shouldThrowExceptionWhenCommandIsNull() {
            Command cmd = new Command();
            cmd.setCommand(null);

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> context.updateConfiguration(new Command[]{cmd})
            );

            assertTrue(ex.getMessage().contains("No configuration found for null specified value."));
        }
    }
}