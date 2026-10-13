package ludo.db;

import org.junit.jupiter.api.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JdbcPersistenceGateway (H2, in-memory) tests")
class JdbcPersistenceGatewayTest {

    private JdbcPersistenceGateway db;

    @BeforeEach
    void setUp() {
        db = JdbcPersistenceGateway.inMemory("gw-" + UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    @DisplayName("schema.sql is applied: a fresh database is empty, with no invented rows")
    void freshDatabaseIsEmpty() {
        assertTrue(db.getLeaderboard().isEmpty());
        assertTrue(db.getMatchHistory().isEmpty());
        assertTrue(db.describe().startsWith("in-memory H2"));
    }

    @Test
    @DisplayName("leaderboard is ordered by wins, then name, by the database")
    void leaderboardOrdering() {
        db.recordPlayerStats("P-1", "Charlie", 3, 1, 2);
        db.recordPlayerStats("P-2", "Alice", 3, 2, 0);
        db.recordPlayerStats("P-3", "Bob", 3, 1, 5);
        List<PlayerRecord> lb = db.getLeaderboard();
        assertEquals(List.of("Alice", "Bob", "Charlie"), lb.stream().map(PlayerRecord::name).toList());
        assertEquals(66.7, lb.get(0).winRate());
    }

    @Test
    @DisplayName("player statistics accumulate across matches and the latest name is kept")
    void accumulation() {
        db.recordPlayerStats("P-X", "Xavier", 1, 1, 3);
        db.recordPlayerStats("P-X", "Xavier II", 1, 0, 2);
        PlayerRecord x = db.getLeaderboard().get(0);
        assertEquals(new PlayerRecord("P-X", "Xavier II", 2, 1, 5), x);
    }

    @Test
    @DisplayName("match history and saved state round-trip, including awkward characters")
    void roundTrip() {
        db.recordMatchResult("M-42", "ROOM-1", "BLUE", "Player 4, the \"Blue\"; ok|", 58, 420);
        db.saveGameState("ROOM-1", "ROOM-1", "R1:ACTIVE:3:NONE;R2:BASE:-1:NONE", 12, "RED");
        db.saveGameState("ROOM-1", "ROOM-1", "R1:ACTIVE:9:NONE", 13, "GREEN");

        MatchRecord m = db.getMatchHistory().get(0);
        assertEquals("Player 4, the \"Blue\"; ok|", m.winnerName());
        assertEquals(58, m.totalRounds());
        assertTrue(m.completedAtMillis() > 0);
        assertEquals("R1:ACTIVE:9:NONE", db.loadGameState("ROOM-1").orElseThrow(), "MERGE replaces the earlier save");
        assertTrue(db.loadGameState("NOPE").isEmpty());
    }

    @Test
    @DisplayName("a duplicate match id is rejected with a PersistenceException, not swallowed")
    void duplicateMatchRejected() {
        db.recordMatchResult("M-1", "R", "RED", "a", 1, 1);
        assertThrows(PersistenceException.class, () -> db.recordMatchResult("M-1", "R", "RED", "a", 1, 1));
    }

    @Test
    @DisplayName("8 threads x 50 concurrent upserts of the same new player lose no update")
    void concurrentWriters() throws Exception {
        int threads = 8, perThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                start.await();
                for (int i = 0; i < perThread; i++) db.recordPlayerStats("P-C", "Concurrent", 1, 0, 1);
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));
        PlayerRecord c = db.getLeaderboard().get(0);
        assertEquals(threads * perThread, c.gamesPlayed());
        assertEquals(threads * perThread, c.totalCaptures());
        assertEquals(0, db.getActiveConnections(), "every borrowed connection was returned to the pool");
    }
}
