package ludo.db;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.net.ServerSocket;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DatabaseServer (Tier 3, H2 over TCP) tests")
class DatabaseServerTest {

    @TempDir
    Path dataDir;

    private DatabaseServer server;
    private int port;

    @BeforeEach
    void startServer() throws Exception {
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }
        server = new DatabaseServer(port, dataDir.toString(), false);
        server.start(false);
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    @DisplayName("start-up creates the schema, so a remote gateway can read the empty tables")
    void schemaCreatedOnStart() throws Exception {
        assertTrue(server.isRunning());
        assertEquals("0 players, 0 matches, 0 saved games", server.describeContents());
        try (JdbcPersistenceGateway gw = JdbcPersistenceGateway.remote("127.0.0.1", port)) {
            assertTrue(gw.getLeaderboard().isEmpty());
            assertTrue(gw.describe().contains(String.valueOf(port)));
        }
    }

    @Test
    @DisplayName("data written through JDBC survives a restart of the database process")
    void dataSurvivesRestart() throws Exception {
        try (JdbcPersistenceGateway gw = JdbcPersistenceGateway.remote("127.0.0.1", port)) {
            gw.recordMatchResult("M-1", "ROOM-1", "RED", "Ann", 40, 30);
            gw.recordPlayerStats("P-ANN", "Ann", 1, 1, 4);
        }
        server.stop();
        server = new DatabaseServer(port, dataDir.toString(), false);
        server.start(false);
        assertEquals("1 players, 1 matches, 0 saved games", server.describeContents());
    }

    @Test
    @DisplayName("--reset returns the database to its initial empty state")
    void resetRestoresInitialState() throws Exception {
        try (JdbcPersistenceGateway gw = JdbcPersistenceGateway.remote("127.0.0.1", port)) {
            gw.recordPlayerStats("P-1", "Someone", 1, 0, 0);
        }
        server.stop();
        server = new DatabaseServer(port, dataDir.toString(), false);
        server.start(true);
        assertEquals("0 players, 0 matches, 0 saved games", server.describeContents());
    }

    @Test
    @DisplayName("PersistenceGateway.connect uses the server when it is up and falls back to memory when it is not")
    void gatewayFactory() {
        try (PersistenceGateway remote = PersistenceGateway.connect("127.0.0.1", port)) {
            assertTrue(remote.describe().startsWith("H2 DatabaseServer"));
        }
        server.stop();
        try (PersistenceGateway fallback = PersistenceGateway.connect("127.0.0.1", port)) {
            assertTrue(fallback.describe().startsWith("in-memory H2"));
            assertDoesNotThrow(() -> fallback.recordPlayerStats("P", "p", 1, 0, 0));
        }
    }

    @Test
    @DisplayName("remote() reports an unreachable server as a PersistenceException")
    void unreachableServer() {
        server.stop();
        assertThrows(PersistenceException.class, () -> JdbcPersistenceGateway.remote("127.0.0.1", port));
    }
}
