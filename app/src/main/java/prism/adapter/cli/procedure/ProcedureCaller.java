package prism.adapter.cli.procedure;


import prism.Main;
import prism.adapter.cli.AppCommandType;
import prism.adapter.cli.AppSubCommandType;
import prism.adapter.operation.DaemonOperation;
import prism.domain.exception.CommandNotFoundException;
import prism.domain.exception.DaemonDownOnCommandException;
import prism.domain.exception.OrphanSubCommandTypeException;
import prism.adapter.cli.input.Command;
import prism.infrastructure.daemon.SocketServer;
import prism.infrastructure.daemon.SocketStatusType;
import prism.utils.ArrayUtils;

import java.nio.file.Files;
import java.util.Objects;

/**
 * Utility class for validating and expanding CLI command procedures.
 * Ensures the proper association between main commands and their sub-commands.
 */
public class ProcedureCaller {

    private static final int SHORT_FLAG_LENGTH = 2;

    /**
     * Expands combined short arguments (e.g., "-ls") into separate arguments ("-l", "-s").
     *
     * @param args the original array of arguments
     * @return a new array with combined arguments expanded
     */
    public static String[] expandArgs(String... args) {
        String[] expandedArgs = new String[]{};

        for (String arg : args) {
            for (String single : expandCombinedCommand(arg)) {
                expandedArgs = ArrayUtils.add(expandedArgs, single);
            }
        }

        return expandedArgs;
    }

    /**
     * Helper to expand a single combined argument string.
     *
     * @param arg the argument to expand
     * @return an array of individual short arguments, or the original if not expandable
     */
    private static String[] expandCombinedCommand(String arg) {
        if (Objects.isNull(arg)
                || arg.length() <= SHORT_FLAG_LENGTH
                || arg.charAt(0) != '-'
                || arg.charAt(1) == '-') {
            return new String[]{arg};
        }

        String[] expanded = new String[]{};
        Object[] seen = new Object[]{};

        for (int i = 1; i < arg.length(); i++) {
            String single = "-" + arg.charAt(i);
            Object type = AppCommandType.getByCommand(single);

            if (type == null) {
                type = AppSubCommandType.getByCommand(single);

                if (type == null) {
                    return new String[]{arg};
                }
            }

            if (ArrayUtils.contains(seen, type)) {
                return new String[]{arg};
            }

            seen = ArrayUtils.add(seen, type);
            expanded = ArrayUtils.add(expanded, single);
        }

        return expanded;
    }

    /**
     * Asserts whether a given command argument is valid for the current application state.
     * Checks if the daemon is running and enforces corresponding allowed commands.
     *
     * @param arg the command argument to check
     * @throws prism.domain.exception.DaemonDownOnCommandException if a daemon-only command is called without a running daemon
     * @throws prism.domain.exception.OrphanSubCommandTypeException if a sub-command is called on its own
     * @throws prism.domain.exception.CommandNotFoundException if the command is unrecognized
     */
    public static void assertCall(String arg) {
        if (Main.START_CMD.equalsIgnoreCase(arg) || Main.STOP_CMD.equalsIgnoreCase(arg)) {
            return;
        }

        arg = expandArgs(arg)[0];

        boolean daemonRunning = Files.exists(SocketServer.getSocketPath());
        String[] daemonCmds = AppCommandType.getDaemonCmds();
        String[] daemonOnlyCmds = AppCommandType.getDaemonNonPolymathCmds();

        if (daemonRunning) {
            if (ArrayUtils.contains(daemonCmds, arg)) {
                return;
            }
        } else {
            if (ArrayUtils.contains(daemonOnlyCmds, arg)) {
                throw new DaemonDownOnCommandException(("Daemon command %s, " +
                        "should only be used after process is active and ready").formatted(arg));
            }
        }

        String[] exeCmd = AppCommandType.getExeCmds();
        if (ArrayUtils.contains(exeCmd, arg)) {
            return;
        }

        String[] subCmds = AppSubCommandType.getSubCmds();
        if (ArrayUtils.contains(subCmds, arg)) {
            throw new OrphanSubCommandTypeException(("Sub command %s, should " +
                    "not be used alone. Check --help for system usages.").formatted(arg));
        }

        throw new CommandNotFoundException(("Command %s, " +
                "not found. Check --help for system usages.").formatted(arg));
    }

    /**
     * Asserts that the provided arguments are valid for single execution and parses them into Commands.
     * Rejects commands that are exclusively for daemon operations.
     *
     * @param args the command line arguments
     * @return an array of parsed Command objects
     * @throws IllegalStateException if a daemon-only command is found
     */
    public static Command[] assertAndGetExecutionerCall(String... args) {
        Command[] intiList = assertStopCall(args);
        if (Objects.nonNull(intiList)) {
            return intiList;
        }

        return assertAndGetCommands(
                AppCommandType.getDaemonNonPolymathCmds(), "Daemon command %s, should not be " +
                        "used as a single execution system command. Check --help for system usages.", args);
    }

    /**
     * Asserts that the provided arguments are valid for daemon execution and parses them into Commands.
     * Rejects commands that are exclusively for single execution operations.
     *
     * @param args the command line arguments
     * @return an array of parsed Command objects
     * @throws IllegalStateException if a single-execution-only command is found
     */
    public static Command[] assertAndGetDaemonCall(String... args) {
        Command[] intiList = assertStopCall(args);
        if (Objects.nonNull(intiList)) {
            return intiList;
        }

        return assertAndGetCommands(
                AppCommandType.getExeNonPolymathCmds(), "Single execution command %s, " +
                        "should not be used as a Daemon system command. Check --help for system usages.", args);
    }

    public static Command[] assertStopCall(String... args) {
        if (ArrayUtils.contains(args, Main.STOP_CMD)) {
            Command stopCmd = new Command();
            stopCmd.add("stop");

            return new Command[]{stopCmd};
        }

        return null;
    }

    /**
     * Core assertion and parsing method for CLI commands. Validates dependencies between
     * commands and sub-commands, verifying that forbidden commands are not present.
     *
     * @param rejectedCommands an array of commands that are not permitted in the current context
     * @param rejectedMessage  the error message template if a rejected command is found
     * @param args             the raw command line arguments
     * @return an array of properly formatted Command objects
     * @throws IllegalStateException if commands are invalid or missing required values
     */
    private static Command[] assertAndGetCommands(
            String[] rejectedCommands,
            String rejectedMessage,
            String[] args
    ) {
        args = expandArgs(args);

        Command[] commandsArray = new Command[]{};
        Command crrCommand = new Command();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            AppCommandType commandArg = AppCommandType.getByCommand(arg);

            if (Objects.nonNull(commandArg)) {
                if (ArrayUtils.contains(rejectedCommands, arg)) {
                    throw new IllegalStateException(
                            rejectedMessage.formatted(arg));
                }

                if (Objects.nonNull(crrCommand.getCommand())) {
                    commandsArray = ArrayUtils
                            .add(commandsArray, crrCommand);
                    crrCommand = new Command();
                }

                crrCommand.setCommand(commandArg);

                continue;
            }

            AppSubCommandType subCmdArg = AppSubCommandType.getByCommand(arg);

            if (Objects.nonNull(subCmdArg)) {
                AppCommandType appCommandArg = crrCommand.getCommand();

                if (Objects.isNull(appCommandArg)) {
                    throw new IllegalStateException(("Sub command %s, " +
                            "found when no base command was given").formatted(arg));
                }

                AppSubCommandType[] commandArgSubCommands = appCommandArg.getSubCommands();
                String[] subCmdArgs = AppSubCommandType.getCommandsByArray(commandArgSubCommands);

                if (!ArrayUtils.contains(subCmdArgs, arg)) {
                    throw new IllegalArgumentException(("Sub command %s, can " +
                            "not be used with %s, command").formatted(arg, appCommandArg));
                }

                crrCommand.add(arg);
                if (i + 1 < args.length) {
                    if (isAnyCommandType(args[i + 1])) {
                        if (subCmdArg.isNullable()) {
                            continue;
                        }

                        throw new IllegalStateException(
                                "Sub command %s, used without a value.".formatted(arg));
                    }

                    crrCommand.add(args[i + 1]);
                    i++;
                } else if (!subCmdArg.isNullable()) {
                    throw new IllegalStateException(
                            "Sub command %s, used without a value.".formatted(arg));
                }

                continue;
            }

            throw new CommandNotFoundException(("Command %s, " +
                    "not found. Check --help for system usages.").formatted(args[i]));
        }

        if (Objects.nonNull(crrCommand.getCommand())) {
            commandsArray = ArrayUtils.add(commandsArray, crrCommand);
        }

        return commandsArray;
    }

    /**
     * Checks if a given argument corresponds to any valid main command or sub-command.
     *
     * @param arg the argument to check
     * @return true if the argument is a known command or sub-command, false otherwise
     */
    private static Boolean isAnyCommandType(String arg) {
        AppCommandType appCommandType = AppCommandType.getByCommand(arg);
        if (Objects.nonNull(appCommandType)) {
            return true;
        }

        AppSubCommandType appSubCommandType = AppSubCommandType.getByCommand(arg);
        return Objects.nonNull(appSubCommandType);
    }
}