package ludo.db;

import org.h2.tools.Server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Database tier (Tier 3): an H2 server over TCP. Applies schema.sql on start-up so the
 * tables exist before the game server connects; H2 serves each connection on its own thread.
 *
 * Usage: DatabaseServer [port] [--reset] [--allow-remote]
 *   --reset         drop all objects and recreate the schema (initial state for a demonstration)
 *   --allow-remote  accept connections from other computers (default: this computer only)
 */
public final class DatabaseServer {

    public static final int DEFAULT_PORT = 8082;
    public static final String DEFAULT_BASE_DIR = "data";

    private final int port;
    private final String baseDir;
    private final boolean allowRemote;
    private Server tcpServer;

    public DatabaseServer(int port, String baseDir, boolean allowRemote) {
        this.port = port;
        this.baseDir = baseDir;
        this.allowRemote = allowRemote;
    }

    /** Starts the TCP server and applies the schema (dropping everything first if reset). */
    public void start(boolean reset) throws SQLException {
        List<String> args = new ArrayList<>(List.of("-tcpPort", String.valueOf(port), "-baseDir", baseDir, "-ifNotExists"));
        if (allowRemote) {
            args.add("-tcpAllowOthers");
        }
        tcpServer = Server.createTcpServer(args.toArray(new String[0])).start();
        try (Connection c = openConnection()) {
            SchemaScript.apply(c, reset);
        }
    }

    public void stop() {
        if (tcpServer != null) {
            tcpServer.stop();
            tcpServer = null;
        }
    }

    public boolean isRunning() {
        return tcpServer != null && tcpServer.isRunning(false);
    }

    public int getPort() {
        return port;
    }

    /** Row counts per table, for the start-up banner. */
    public String describeContents() throws SQLException {
        try (Connection c = openConnection(); Statement st = c.createStatement()) {
            return count(st, "players") + " players, " + count(st, "match_history") + " matches, "
                    + count(st, "saved_games") + " saved games";
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(
                "jdbc:h2:tcp://localhost:" + port + "/" + JdbcPersistenceGateway.DATABASE_PATH, "sa", "");
    }

    private static int count(Statement st, String table) throws SQLException {
        try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    public static void main(String[] args) throws Exception {
        int port = DEFAULT_PORT;
        boolean reset = false;
        boolean allowRemote = false;
        for (String arg : args) {
            switch (arg) {
                case "--reset" -> reset = true;
                case "--allow-remote" -> allowRemote = true;
                default -> port = Integer.parseInt(arg);
            }
        }

        DatabaseServer server = new DatabaseServer(port, DEFAULT_BASE_DIR, allowRemote);
        server.start(reset);

        CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop();
            stopped.countDown();
        }, "DbShutdownHook"));

        System.out.println("===============================================================");
        System.out.println("   LUDO-T DATABASE SERVER (TIER 3) - H2 over TCP, port " + port);
        System.out.println("   JDBC URL: jdbc:h2:tcp://<host>:" + port + "/" + JdbcPersistenceGateway.DATABASE_PATH);
        System.out.println("   Data directory: " + DEFAULT_BASE_DIR + (reset ? "   (reset to initial state)" : ""));
        System.out.println("   Accepts: " + (allowRemote ? "any computer" : "this computer only"));
        System.out.println("   Contents: " + server.describeContents());
        System.out.println("===============================================================");
        stopped.await();
    }
}
