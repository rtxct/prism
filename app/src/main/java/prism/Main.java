package prism;

import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Objects;
import java.util.logging.Level;

import lombok.extern.java.Log;
import prism.adapter.cli.input.Command;
import prism.adapter.cli.procedure.ProcedureCaller;
import prism.adapter.operation.DaemonOperation;
import prism.adapter.operation.ExecutionerOperation;
import prism.domain.exception.CommandNotFoundException;
import prism.domain.exception.DaemonDownOnCommandException;
import prism.domain.exception.OrphanSubCommandTypeException;
import prism.infrastructure.daemon.SocketServer;

/**
 * The main entry point for the Prism application.
 * <p>
 * This class handles initialization and command routing, deciding whether to run in
 * standalone execution mode, start a daemon process, or forward commands to an existing
 * daemon.
 */
@Log
public class Main {

    /**
     * The command used to start the daemon process.
     */
    public static final String START_CMD = "start";

    /**
     * The command used to stop the daemon process.
     */
    public static final String STOP_CMD = "stop";

    /**
     * Parses command-line arguments and initiates the appropriate application behavior.
     *
     * @param args the command-line arguments provided by the user or system
     */
    static void main(String... args) {
        try {
            if (args.length == 0) {
                log.log(Level.FINE, "No arguments " +
                        "provided, check --help for system usages");
                return;
            }

            String cmd = args[0];
            ProcedureCaller.assertCall(cmd);

            if (START_CMD.equalsIgnoreCase(cmd)) {
                Command[] commands =
                        ProcedureCaller.assertAndGetDaemonCall(Arrays.copyOfRange(args, 1, args.length));

                log.info("Daemon mode " +
                        "identified. Starting the process.");

                DaemonOperation.start(commands);
                return;
            }

            if (Files.exists(DaemonOperation.getSocketPath())) {
                log.info("Daemon socket found. Forwarding call to the Daemon process.");

                try (SocketChannel client = SocketChannel.open(StandardProtocolFamily.UNIX)) {
                    client.connect(UnixDomainSocketAddress
                            .of(DaemonOperation.getSocketPath()));

                    String line = String.join(" ", args) + "\n";
                    client.write(ByteBuffer.wrap(line.getBytes(StandardCharsets.UTF_8)));
                }

                return;
            }

            Command[] commands = ProcedureCaller
                    .assertAndGetExecutionerCall(args);

            log.info("Single execution " +
                    "mode identified. Starting the process.");

            ExecutionerOperation.execute(commands);
        } catch (DaemonDownOnCommandException
                 | CommandNotFoundException
                 | OrphanSubCommandTypeException
                 | IllegalStateException
                 | IllegalArgumentException e) {

            log.warning(e.getMessage());

        } catch (Exception e) {
            if (Objects.nonNull(SocketServer.getSocketStatusType())) {
                log.log(
                        Level.SEVERE,
                        ("System error, " + "exiting program with: %s").formatted(e.getCause()),
                        e);
            } else {
                assert log != null;
                log.log(Level.SEVERE, e.getMessage());
            }

            System.exit(1);
        }
    }
}