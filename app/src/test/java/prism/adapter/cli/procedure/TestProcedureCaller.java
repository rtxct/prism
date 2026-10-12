package prism.adapter.cli.procedure;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import prism.adapter.cli.AppCommandType;
import prism.adapter.cli.input.Command;
import prism.domain.exception.CommandNotFoundException;
import prism.domain.exception.DaemonDownOnCommandException;
import prism.domain.exception.OrphanSubCommandTypeException;
import prism.infrastructure.daemon.SocketServer;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ProcedureCaller Unit Tests")
class TestProcedureCaller {

    private Path socketPath;

    @BeforeEach
    void setUp() throws IOException {
        socketPath = SocketServer.getSocketPath();
        Files.deleteIfExists(socketPath);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (socketPath != null) {
            Files.deleteIfExists(socketPath);
        }
    }

    @Nested
    @DisplayName("expandArgs() and Combined Flags Tests")
    class ExpandArgsTests {

        @Test
        @DisplayName("expandArgs with empty argument array returns empty array")
        void expandArgs_withEmptyArgs_returnsEmptyArray() {
            String[] result = ProcedureCaller.expandArgs();
            assertNotNull(result);
            assertEquals(0, result.length);
        }

        @Test
        @DisplayName("expandArgs preserves null, empty, or short arguments")
        void expandArgs_withNullOrShortArgument_returnsArgumentAsIs() {
            assertArrayEquals(new String[]{null}, ProcedureCaller.expandArgs((String) null));
            assertArrayEquals(new String[]{""}, ProcedureCaller.expandArgs(""));
            assertArrayEquals(new String[]{"-"}, ProcedureCaller.expandArgs("-"));
            assertArrayEquals(new String[]{"-l"}, ProcedureCaller.expandArgs("-l"));
            assertArrayEquals(new String[]{"a"}, ProcedureCaller.expandArgs("a"));
        }

        @Test
        @DisplayName("expandArgs preserves non-flag or double-dash long flag arguments as-is")
        void expandArgs_withNonFlagOrLongFlag_returnsArgumentAsIs() {
            assertArrayEquals(new String[]{"start"}, ProcedureCaller.expandArgs("start"));
            assertArrayEquals(new String[]{"stop"}, ProcedureCaller.expandArgs("stop"));
            assertArrayEquals(new String[]{"--listen"}, ProcedureCaller.expandArgs("--listen"));
            assertArrayEquals(new String[]{"--directory"}, ProcedureCaller.expandArgs("--directory"));
            assertArrayEquals(new String[]{"/path/to/image.png"}, ProcedureCaller.expandArgs("/path/to/image.png"));
        }

        @Test
        @DisplayName("expandArgs expands valid combined short flags into separate flags")
        void expandArgs_withValidCombinedFlags_expandsSuccessfully() {
            // -ls -> -l, -s
            assertArrayEquals(new String[]{"-l", "-s"}, ProcedureCaller.expandArgs("-ls"));

            // -ca -> -c, -a
            assertArrayEquals(new String[]{"-c", "-a"}, ProcedureCaller.expandArgs("-ca"));

            // -wdf -> -w, -d, -f
            assertArrayEquals(new String[]{"-w", "-d", "-f"}, ProcedureCaller.expandArgs("-wdf"));
        }

        @Test
        @DisplayName("expandArgs does not expand combined flag containing invalid character")
        void expandArgs_withInvalidCharacterInCombinedFlag_returnsOriginalArgument() {
            // 'x' is neither command nor subcommand
            assertArrayEquals(new String[]{"-lx"}, ProcedureCaller.expandArgs("-lx"));
            assertArrayEquals(new String[]{"-zls"}, ProcedureCaller.expandArgs("-zls"));
        }

        @Test
        @DisplayName("expandArgs does not expand combined flag containing duplicate command or subcommand type")
        void expandArgs_withDuplicateTypeInCombinedFlag_returnsOriginalArgument() {
            // duplicate command '-l'
            assertArrayEquals(new String[]{"-ll"}, ProcedureCaller.expandArgs("-ll"));

            // duplicate subcommand '-d'
            assertArrayEquals(new String[]{"-dd"}, ProcedureCaller.expandArgs("-dd"));

            // duplicate command '-s' in '-lsl'
            assertArrayEquals(new String[]{"-lsl"}, ProcedureCaller.expandArgs("-lsl"));
        }

        @Test
        @DisplayName("expandArgs correctly processes a mix of combined flags, long flags, and positional values")
        void expandArgs_withMixedArguments_expandsOnlyCombinedFlags() {
            String[] input = {"-ls", "start", "-w", "--directory", "/tmp", "-ca", "5"};
            String[] expected = {"-l", "-s", "start", "-w", "--directory", "/tmp", "-c", "-a", "5"};

            assertArrayEquals(expected, ProcedureCaller.expandArgs(input));
        }
    }

    @Nested
    @DisplayName("assertStopCall() Tests")
    class AssertStopCallTests {

        @Test
        @DisplayName("assertStopCall returns stop Command array when STOP_CMD is present in arguments")
        void assertStopCall_whenStopCommandPresent_returnsStopCommandArray() {
            Command[] result = ProcedureCaller.assertStopCall("stop");

            assertNotNull(result);
            assertEquals(1, result.length);
            assertNull(result[0].getCommand());
            assertArrayEquals(new String[]{"stop"}, result[0].getSubCommands());
        }

        @Test
        @DisplayName("assertStopCall returns stop Command array when STOP_CMD is among other arguments")
        void assertStopCall_whenStopCommandAmongOtherArgs_returnsStopCommandArray() {
            Command[] result = ProcedureCaller.assertStopCall("-w", "stop", "-d");

            assertNotNull(result);
            assertEquals(1, result.length);
            assertArrayEquals(new String[]{"stop"}, result[0].getSubCommands());
        }

        @Test
        @DisplayName("assertStopCall returns null when STOP_CMD is absent")
        void assertStopCall_whenStopCommandAbsent_returnsNull() {
            assertNull(ProcedureCaller.assertStopCall("-w", "-d", "/tmp"));
            assertNull(ProcedureCaller.assertStopCall());
        }
    }

    @Nested
    @DisplayName("assertCall() - Daemon Down Tests")
    class AssertCallDaemonDownTests {

        @Test
        @DisplayName("assertCall allows 'start' command case-insensitively when daemon is down")
        void assertCall_withStartCommand_returnsNormally() {
            assertDoesNotThrow(() -> ProcedureCaller.assertCall("start"));
            assertDoesNotThrow(() -> ProcedureCaller.assertCall("START"));
            assertDoesNotThrow(() -> ProcedureCaller.assertCall("Start"));
        }

        @Test
        @DisplayName("assertCall throws DaemonDownOnCommandException for strictly daemon commands when daemon is down")
        void assertCall_withDaemonOnlyCommandWhenDaemonDown_throwsDaemonDownOnCommandException() {
            String[] daemonOnly = {"-l", "--listen", "-s", "--store", "-c", "--cache"};

            for (String cmd : daemonOnly) {
                DaemonDownOnCommandException ex = assertThrows(
                        DaemonDownOnCommandException.class,
                        () -> ProcedureCaller.assertCall(cmd),
                        "Expected DaemonDownOnCommandException for: " + cmd
                );
                assertTrue(ex.getMessage().contains("should only be used after process is active and ready"));
            }
        }

        @Test
        @DisplayName("assertCall throws DaemonDownOnCommandException for combined flags starting with daemon command")
        void assertCall_withCombinedFlagWhenDaemonDown_throwsDaemonDownOnCommandException() {
            // -ls expands to [-l, -s], first argument is -l
            assertThrows(DaemonDownOnCommandException.class, () -> ProcedureCaller.assertCall("-ls"));
        }

        @Test
        @DisplayName("assertCall permits polymath executioner commands when daemon is down")
        void assertCall_withPolymathCommandsWhenDaemonDown_returnsNormally() {
            assertDoesNotThrow(() -> ProcedureCaller.assertCall("-w"));
            assertDoesNotThrow(() -> ProcedureCaller.assertCall("--write"));
            assertDoesNotThrow(() -> ProcedureCaller.assertCall("-p"));
            assertDoesNotThrow(() -> ProcedureCaller.assertCall("--parameter"));
        }

        @Test
        @DisplayName("assertCall throws OrphanSubCommandTypeException when sub-command is called alone")
        void assertCall_withOrphanSubCommand_throwsOrphanSubCommandTypeException() {
            String[] subCommands = {"-d", "--directory", "-f", "--file", "-a", "--amount",
                    "-k", "--keepalive", "-t", "--type", "interrupt", "reset"};

            for (String subCmd : subCommands) {
                OrphanSubCommandTypeException ex = assertThrows(
                        OrphanSubCommandTypeException.class,
                        () -> ProcedureCaller.assertCall(subCmd),
                        "Expected OrphanSubCommandTypeException for: " + subCmd
                );
                assertTrue(ex.getMessage().contains("should not be used alone"));
            }
        }

        @Test
        @DisplayName("assertCall throws CommandNotFoundException for unrecognized commands")
        void assertCall_withUnrecognizedCommand_throwsCommandNotFoundException() {
            String[] unknownCommands = {"unknown", "--nonexistent", "-z", "foo"};

            for (String unknown : unknownCommands) {
                CommandNotFoundException ex = assertThrows(
                        CommandNotFoundException.class,
                        () -> ProcedureCaller.assertCall(unknown),
                        "Expected CommandNotFoundException for: " + unknown
                );
                assertTrue(ex.getMessage().contains("not found. Check --help for system usages."));
            }
        }
    }

    @Nested
    @DisplayName("assertCall() - Daemon Running Tests")
    class AssertCallDaemonRunningTests {

        @BeforeEach
        void setUpRunningDaemon() throws IOException {
            Files.createDirectories(socketPath.getParent());
            Files.createFile(socketPath);
        }

        @Test
        @DisplayName("assertCall allows daemon commands when daemon socket exists")
        void assertCall_withDaemonCommandsWhenDaemonUp_returnsNormally() {
            String[] daemonCommands = {"-l", "--listen", "-s", "--store", "-c", "--cache",
                    "-w", "--write", "-p", "--parameter", "-ls"};

            for (String cmd : daemonCommands) {
                assertDoesNotThrow(() -> ProcedureCaller.assertCall(cmd),
                        "Expected normal return for daemon command when daemon is active: " + cmd);
            }
        }

        @Test
        @DisplayName("assertCall still rejects orphan sub-commands when daemon is up")
        void assertCall_withOrphanSubCommandWhenDaemonUp_throwsOrphanSubCommandTypeException() {
            assertThrows(OrphanSubCommandTypeException.class, () -> ProcedureCaller.assertCall("-d"));
            assertThrows(OrphanSubCommandTypeException.class, () -> ProcedureCaller.assertCall("-f"));
            assertThrows(OrphanSubCommandTypeException.class, () -> ProcedureCaller.assertCall("interrupt"));
        }

        @Test
        @DisplayName("assertCall still rejects unrecognized commands when daemon is up")
        void assertCall_withUnrecognizedCommandWhenDaemonUp_throwsCommandNotFoundException() {
            assertThrows(CommandNotFoundException.class, () -> ProcedureCaller.assertCall("unknown"));
            assertThrows(CommandNotFoundException.class, () -> ProcedureCaller.assertCall("--bogus"));
        }
    }

    @Nested
    @DisplayName("assertAndGetExecutionerCall() Tests")
    class AssertAndGetExecutionerCallTests {

        @Test
        @DisplayName("assertAndGetExecutionerCall delegates to assertStopCall when 'stop' is present")
        void assertAndGetExecutionerCall_whenStopCommandPresent_returnsStopCommand() {
            Command[] result = ProcedureCaller.assertAndGetExecutionerCall("stop");

            assertNotNull(result);
            assertEquals(1, result.length);
            assertArrayEquals(new String[]{"stop"}, result[0].getSubCommands());
        }

        @Test
        @DisplayName("assertAndGetExecutionerCall rejects daemon-only commands with IllegalStateException")
        void assertAndGetExecutionerCall_withDaemonOnlyCommand_throwsIllegalStateException() {
            String[] daemonOnly = {"-l", "--listen", "-s", "--store", "-c", "--cache"};

            for (String cmd : daemonOnly) {
                IllegalStateException ex = assertThrows(
                        IllegalStateException.class,
                        () -> ProcedureCaller.assertAndGetExecutionerCall(cmd)
                );
                assertTrue(ex.getMessage().contains("should not be used as a single execution system command"));
            }
        }

        @Test
        @DisplayName("assertAndGetExecutionerCall parses valid WRITE and PARAMETER commands with arguments")
        void assertAndGetExecutionerCall_withValidCommands_parsesCorrectly() {
            Command[] writeCommands = ProcedureCaller.assertAndGetExecutionerCall("-w", "-d", "/tmp/dir");
            assertEquals(1, writeCommands.length);
            assertEquals(AppCommandType.WRITE, writeCommands[0].getCommand());
            assertArrayEquals(new String[]{"-d", "/tmp/dir"}, writeCommands[0].getSubCommands());

            Command[] paramCommands = ProcedureCaller.assertAndGetExecutionerCall("-p", "-f", "/tmp/img.png");
            assertEquals(1, paramCommands.length);
            assertEquals(AppCommandType.PARAMETER, paramCommands[0].getCommand());
            assertArrayEquals(new String[]{"-f", "/tmp/img.png"}, paramCommands[0].getSubCommands());
        }

        @Test
        @DisplayName("assertAndGetExecutionerCall parses multiple executioner commands in single call")
        void assertAndGetExecutionerCall_withMultipleCommands_parsesAllCommands() {
            Command[] commands = ProcedureCaller.assertAndGetExecutionerCall(
                    "-w", "-d", "/output/dir", "-t", "hex", "-p", "-f", "/input/img.png");

            assertEquals(2, commands.length);

            assertEquals(AppCommandType.WRITE, commands[0].getCommand());
            assertArrayEquals(new String[]{"-d", "/output/dir", "-t", "hex"}, commands[0].getSubCommands());

            assertEquals(AppCommandType.PARAMETER, commands[1].getCommand());
            assertArrayEquals(new String[]{"-f", "/input/img.png"}, commands[1].getSubCommands());
        }
    }

    @Nested
    @DisplayName("assertAndGetDaemonCall() Tests")
    class AssertAndGetDaemonCallTests {

        @Test
        @DisplayName("assertAndGetDaemonCall delegates to assertStopCall when 'stop' is present")
        void assertAndGetDaemonCall_whenStopCommandPresent_returnsStopCommand() {
            Command[] result = ProcedureCaller.assertAndGetDaemonCall("stop");

            assertNotNull(result);
            assertEquals(1, result.length);
            assertArrayEquals(new String[]{"stop"}, result[0].getSubCommands());
        }

        @Test
        @DisplayName("assertAndGetDaemonCall parses standalone and argument-bearing daemon commands")
        void assertAndGetDaemonCall_withValidDaemonCommands_parsesCorrectly() {
            Command[] listenCmd = ProcedureCaller.assertAndGetDaemonCall("-l");
            assertEquals(1, listenCmd.length);
            assertEquals(AppCommandType.LISTEN, listenCmd[0].getCommand());
            assertEquals(0, listenCmd[0].getSubCommands().length);

            Command[] storeCmd = ProcedureCaller.assertAndGetDaemonCall("-s", "-d", "/wallpapers", "-f", "bg.jpg");
            assertEquals(1, storeCmd.length);
            assertEquals(AppCommandType.STORE, storeCmd[0].getCommand());
            assertArrayEquals(new String[]{"-d", "/wallpapers", "-f", "bg.jpg"}, storeCmd[0].getSubCommands());

            Command[] cacheCmd = ProcedureCaller.assertAndGetDaemonCall("-c", "-a", "10", "-k", "60");
            assertEquals(1, cacheCmd.length);
            assertEquals(AppCommandType.CACHE, cacheCmd[0].getCommand());
            assertArrayEquals(new String[]{"-a", "10", "-k", "60"}, cacheCmd[0].getSubCommands());
        }

        @Test
        @DisplayName("assertAndGetDaemonCall accepts nullable sub-commands (interrupt, reset) without values")
        void assertAndGetDaemonCall_withNullableSubCommands_acceptsWithoutValue() {
            Command[] interruptCmd = ProcedureCaller.assertAndGetDaemonCall("-l", "interrupt");
            assertEquals(1, interruptCmd.length);
            assertEquals(AppCommandType.LISTEN, interruptCmd[0].getCommand());
            assertArrayEquals(new String[]{"interrupt"}, interruptCmd[0].getSubCommands());

            Command[] resetCmd = ProcedureCaller.assertAndGetDaemonCall("-c", "reset");
            assertEquals(1, resetCmd.length);
            assertEquals(AppCommandType.CACHE, resetCmd[0].getCommand());
            assertArrayEquals(new String[]{"reset"}, resetCmd[0].getSubCommands());

            // Nullable subcommand followed by next command
            Command[] combined = ProcedureCaller.assertAndGetDaemonCall("-l", "interrupt", "-s", "reset");
            assertEquals(2, combined.length);
            assertEquals(AppCommandType.LISTEN, combined[0].getCommand());
            assertArrayEquals(new String[]{"interrupt"}, combined[0].getSubCommands());
            assertEquals(AppCommandType.STORE, combined[1].getCommand());
            assertArrayEquals(new String[]{"reset"}, combined[1].getSubCommands());
        }

        @Test
        @DisplayName("assertAndGetDaemonCall expands combined flags and generates multiple commands")
        void assertAndGetDaemonCall_withCombinedFlags_expandsAndParsesMultipleCommands() {
            Command[] commands = ProcedureCaller.assertAndGetDaemonCall("-ls");

            assertEquals(2, commands.length);
            assertEquals(AppCommandType.LISTEN, commands[0].getCommand());
            assertEquals(AppCommandType.STORE, commands[1].getCommand());
        }
    }

    @Nested
    @DisplayName("Command and SubCommand Parsing Rules (assertAndGetCommands)")
    class CommandParsingRulesTests {

        @Test
        @DisplayName("assertAndGetCommands throws IllegalStateException when sub-command appears before any base command")
        void assertAndGetCommands_whenSubCommandWithoutBaseCommand_throwsIllegalStateException() {
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> ProcedureCaller.assertAndGetExecutionerCall("-d", "/tmp")
            );
            assertTrue(ex.getMessage().contains("found when no base command was given"));
        }

        @Test
        @DisplayName("assertAndGetCommands throws IllegalArgumentException when sub-command is incompatible with base command")
        void assertAndGetCommands_whenIncompatibleSubCommandUsed_throwsIllegalArgumentException() {
            // PARAMETER (-p) only accepts FILE (-f), not DIRECTORY (-d)
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> ProcedureCaller.assertAndGetExecutionerCall("-p", "-d", "/tmp")
            );
            assertTrue(ex.getMessage().contains("can not be used with"));

            // CACHE (-c) does not accept FILE (-f)
            IllegalArgumentException ex2 = assertThrows(
                    IllegalArgumentException.class,
                    () -> ProcedureCaller.assertAndGetDaemonCall("-c", "-f", "/file.png")
            );
            assertTrue(ex2.getMessage().contains("can not be used with"));
        }

        @Test
        @DisplayName("assertAndGetCommands throws IllegalStateException when non-nullable sub-command is at end of input without value")
        void assertAndGetCommands_whenNonNullableSubCommandAtEndWithoutValue_throwsIllegalStateException() {
            String[] nonNullableSubCmds = {"-d", "-f"};

            for (String subCmd : nonNullableSubCmds) {
                IllegalStateException ex = assertThrows(
                        IllegalStateException.class,
                        () -> ProcedureCaller.assertAndGetExecutionerCall("-w", subCmd)
                );
                assertTrue(ex.getMessage().contains("used without a value."));
            }
        }

        @Test
        @DisplayName("assertAndGetCommands throws IllegalStateException when non-nullable sub-command is followed immediately by another command")
        void assertAndGetCommands_whenNonNullableSubCommandFollowedByCommand_throwsIllegalStateException() {
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> ProcedureCaller.assertAndGetExecutionerCall("-w", "-d", "-p", "-f", "/file")
            );
            assertTrue(ex.getMessage().contains("Sub command -d, used without a value."));
        }

        @Test
        @DisplayName("assertAndGetCommands throws IllegalStateException when non-nullable sub-command is followed immediately by another sub-command")
        void assertAndGetCommands_whenNonNullableSubCommandFollowedBySubCommand_throwsIllegalStateException() {
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> ProcedureCaller.assertAndGetExecutionerCall("-w", "-d", "-f", "/file")
            );
            assertTrue(ex.getMessage().contains("Sub command -d, used without a value."));
        }

        @Test
        @DisplayName("assertAndGetCommands throws CommandNotFoundException when unrecognized token is encountered")
        void assertAndGetCommands_whenUnrecognizedTokenEncountered_throwsCommandNotFoundException() {
            CommandNotFoundException ex = assertThrows(
                    CommandNotFoundException.class,
                    () -> ProcedureCaller.assertAndGetExecutionerCall("-w", "unrecognized")
            );
            assertTrue(ex.getMessage().contains("not found. Check --help for system usages."));
        }

        @Test
        @DisplayName("assertAndGetCommands returns empty Command array when no arguments are given")
        void assertAndGetCommands_withNoArguments_returnsEmptyArray() {
            Command[] result = ProcedureCaller.assertAndGetExecutionerCall();
            assertNotNull(result);
            assertEquals(0, result.length);
        }
    }

    @Nested
    @DisplayName("Private Helper Methods and Reflection Tests")
    class PrivateMethodsReflectionTests {

        @Test
        @DisplayName("isAnyCommandType returns true for valid commands and sub-commands, false otherwise")
        void isAnyCommandType_reflectionVerification() throws Exception {
            Method method = ProcedureCaller.class.getDeclaredMethod("isAnyCommandType", String.class);
            method.setAccessible(true);

            // Valid main commands
            assertTrue((Boolean) method.invoke(null, "-l"));
            assertTrue((Boolean) method.invoke(null, "--write"));
            assertTrue((Boolean) method.invoke(null, "-p"));

            // Valid sub-commands
            assertTrue((Boolean) method.invoke(null, "-d"));
            assertTrue((Boolean) method.invoke(null, "--file"));
            assertTrue((Boolean) method.invoke(null, "interrupt"));
            assertTrue((Boolean) method.invoke(null, "reset"));

            // Non-commands / values / null
            assertFalse((Boolean) method.invoke(null, "/tmp/dir"));
            assertFalse((Boolean) method.invoke(null, "unknown"));
            assertFalse((Boolean) method.invoke(null, ""));
            assertFalse((Boolean) method.invoke(null, (Object) null));
        }

        @Test
        @DisplayName("SHORT_FLAG_LENGTH constant verification")
        void shortFlagLength_constantVerification() throws Exception {
            Field field = ProcedureCaller.class.getDeclaredField("SHORT_FLAG_LENGTH");
            field.setAccessible(true);
            int value = (int) field.get(null);

            assertEquals(2, value);
        }
    }
}