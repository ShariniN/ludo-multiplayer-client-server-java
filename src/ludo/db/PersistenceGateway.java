package ludo.db;

import java.util.List;

/** Database tier contract; the game server never sees SQL or JDBC directly. */
public interface PersistenceGateway extends AutoCloseable {

    void recordMatchResult(String matchId, String roomId, String winnerColor,
                           String winnerName, int totalRounds, int durationSeconds);

    /** Adds the given deltas to the player's accumulated counters (creating the row if needed). */
    void recordPlayerStats(String id, String name, int gamesPlayed, int gamesWon, int totalCaptures);

    void saveGameState(String saveId, String roomId, String serializedState,
                       int roundNumber, String activePlayer);

    /** Players ordered by wins (descending), then by name. */
    List<PlayerRecord> getLeaderboard();

    /** Finished matches, oldest first. */
    List<MatchRecord> getMatchHistory();

    /** Human-readable description of where data is actually going (shown in server banner). */
    String describe();

    /** Releases connections; the gateway must not be used afterwards. */
    @Override
    void close();

    /**
     * Factory: prefer the standalone H2 DatabaseServer process; if it is not reachable
     * fall back to an in-memory H2 database (same SQL) so the game is always playable.
     */
    static PersistenceGateway connect(String host, int port) {
        try {
            return JdbcPersistenceGateway.remote(host, port);
        } catch (PersistenceException e) {
            System.out.println("[Persistence] DatabaseServer not reachable at " + host + ":" + port
                    + " (" + e.getMessage() + ") - using an in-memory database instead.");
            return JdbcPersistenceGateway.inMemory("ludo-fallback");
        }
    }
}
