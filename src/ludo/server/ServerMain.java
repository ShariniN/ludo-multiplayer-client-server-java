package ludo.server;

import ludo.db.DatabaseServer;
import ludo.db.PersistenceGateway;
import ludo.network.NetworkProtocol;
import ludo.servlet.TomcatServerRunner;

/**
 * Game server entry point (Tier 2).
 * Usage: ServerMain [tcpPort] [httpPort] [dbHost] [dbPort]  (defaults: 8080 8081 127.0.0.1 8082)
 * Falls back to an in-memory database if the DatabaseServer is not running.
 */
public class ServerMain {

    public static void main(String[] args) {
        int port = parseInt(args, 0, NetworkProtocol.DEFAULT_PORT);
        int httpPort = parseInt(args, 1, TomcatServerRunner.DEFAULT_HTTP_PORT);
        String dbHost = args.length > 2 ? args[2] : "127.0.0.1";
        int dbPort = parseInt(args, 3, DatabaseServer.DEFAULT_PORT);

        PersistenceGateway persistence = PersistenceGateway.connect(dbHost, dbPort);
        LudoServer server = new LudoServer(port, httpPort, persistence);

        // Clean shutdown on Ctrl+C / window close
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop();
            persistence.close();
        }, "ServerShutdownHook"));

        server.start();
    }

    private static int parseInt(String[] args, int index, int fallback) {
        if (args.length <= index) return fallback;
        try {
            return Integer.parseInt(args[index]);
        } catch (NumberFormatException e) {
            System.err.println("Invalid number '" + args[index] + "', defaulting to " + fallback);
            return fallback;
        }
    }
}
