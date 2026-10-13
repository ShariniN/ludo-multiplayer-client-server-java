package ludo.db;

import org.h2.jdbcx.JdbcConnectionPool;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * H2/JDBC persistence gateway. Each call borrows its own pooled connection (Connections
 * are not safe to share), and concurrent writers are isolated by H2 row locks, not Java locks.
 */
public final class JdbcPersistenceGateway implements PersistenceGateway {

    /** Database path relative to the H2 server's base directory (H2 requires the explicit "./"). */
    public static final String DATABASE_PATH = "./ludo";
    private static final String USER = "sa";
    private static final String PASSWORD = "";
    private static final int MAX_CONNECTIONS = 8;
    private static final int INSERT_RETRIES = 3;

    private final JdbcConnectionPool pool;
    private final String description;

    private JdbcPersistenceGateway(String url, String description) {
        this.pool = JdbcConnectionPool.create(url, USER, PASSWORD);
        this.pool.setMaxConnections(MAX_CONNECTIONS);
        this.description = description;
    }

    /**
     * Connects to a running DatabaseServer process. IFEXISTS=TRUE stops the client from
     * silently creating an empty database if it points at the wrong server.
     *
     * @throws PersistenceException if the server is not reachable or the schema is missing
     */
    public static JdbcPersistenceGateway remote(String host, int port) {
        String url = "jdbc:h2:tcp://" + host + ":" + port + "/" + DATABASE_PATH + ";IFEXISTS=TRUE";
        JdbcPersistenceGateway gateway = new JdbcPersistenceGateway(url, "H2 DatabaseServer at " + host + ":" + port);
        try (Connection c = gateway.pool.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM players");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return gateway;
        } catch (SQLException e) {
            gateway.close();
            throw new PersistenceException("cannot reach " + url, e);
        }
    }

    /** Private in-memory database with the same schema (fallback, tests, stress harness). */
    public static JdbcPersistenceGateway inMemory(String name) {
        String url = "jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1";
        JdbcPersistenceGateway gateway = new JdbcPersistenceGateway(url, "in-memory H2 database '" + name + "'");
        try (Connection c = gateway.pool.getConnection()) {
            SchemaScript.apply(c, true);
            return gateway;
        } catch (SQLException e) {
            gateway.close();
            throw new PersistenceException("cannot create in-memory database " + name, e);
        }
    }

    @Override
    public void recordMatchResult(String matchId, String roomId, String winnerColor,
                                  String winnerName, int totalRounds, int durationSeconds) {
        String sql = "INSERT INTO match_history (match_id, room_id, winner_color, winner_name, total_rounds, duration_seconds) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = pool.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, matchId);
            ps.setString(2, roomId);
            ps.setString(3, winnerColor);
            ps.setString(4, winnerName);
            ps.setInt(5, totalRounds);
            ps.setInt(6, durationSeconds);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("recordMatchResult failed for " + matchId, e);
        }
    }

    /**
     * Concurrency-safe upsert: UPDATE adds the deltas in SQL; if no row exists, INSERT,
     * and if another writer inserted first (PK violation), retry the UPDATE.
     */
    @Override
    public void recordPlayerStats(String id, String name, int gamesPlayed, int gamesWon, int totalCaptures) {
        String update = "UPDATE players SET player_name = ?, games_played = games_played + ?, "
                + "games_won = games_won + ?, total_captures = total_captures + ?, last_active = CURRENT_TIMESTAMP "
                + "WHERE player_id = ?";
        String insert = "INSERT INTO players (player_id, player_name, games_played, games_won, total_captures) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (Connection c = pool.getConnection();
             PreparedStatement up = c.prepareStatement(update);
             PreparedStatement in = c.prepareStatement(insert)) {
            up.setString(1, name);
            up.setInt(2, gamesPlayed);
            up.setInt(3, gamesWon);
            up.setInt(4, totalCaptures);
            up.setString(5, id);
            in.setString(1, id);
            in.setString(2, name);
            in.setInt(3, gamesPlayed);
            in.setInt(4, gamesWon);
            in.setInt(5, totalCaptures);
            for (int attempt = 1; ; attempt++) {
                if (up.executeUpdate() > 0) return;
                try {
                    in.executeUpdate();
                    return;
                } catch (SQLIntegrityConstraintViolationException raced) {
                    if (attempt >= INSERT_RETRIES) throw raced;
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException("recordPlayerStats failed for " + id, e);
        }
    }

    @Override
    public void saveGameState(String saveId, String roomId, String serializedState,
                              int roundNumber, String activePlayer) {
        String sql = "MERGE INTO saved_games (save_id, room_id, serialized_state, round_number, active_player, saved_at) "
                + "KEY (save_id) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        try (Connection c = pool.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, saveId);
            ps.setString(2, roomId);
            ps.setString(3, serializedState);
            ps.setInt(4, roundNumber);
            ps.setString(5, activePlayer);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("saveGameState failed for " + saveId, e);
        }
    }

    /** The last saved state of a room, if any. */
    public Optional<String> loadGameState(String saveId) {
        String sql = "SELECT serialized_state FROM saved_games WHERE save_id = ?";
        try (Connection c = pool.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, saveId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("loadGameState failed for " + saveId, e);
        }
    }

    @Override
    public List<PlayerRecord> getLeaderboard() {
        String sql = "SELECT player_id, player_name, games_played, games_won, total_captures FROM players "
                + "ORDER BY games_won DESC, player_name";
        List<PlayerRecord> rows = new ArrayList<>();
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new PlayerRecord(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5)));
            }
            return rows;
        } catch (SQLException e) {
            throw new PersistenceException("getLeaderboard failed", e);
        }
    }

    @Override
    public List<MatchRecord> getMatchHistory() {
        String sql = "SELECT match_id, room_id, winner_color, winner_name, total_rounds, duration_seconds, completed_at "
                + "FROM match_history ORDER BY completed_at, match_id";
        List<MatchRecord> rows = new ArrayList<>();
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Timestamp completed = rs.getTimestamp(7);
                rows.add(new MatchRecord(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getInt(5), rs.getInt(6), completed.getTime()));
            }
            return rows;
        } catch (SQLException e) {
            throw new PersistenceException("getMatchHistory failed", e);
        }
    }

    @Override
    public String describe() {
        return description;
    }

    /** Number of connections currently borrowed from the pool (for tests and diagnostics). */
    public int getActiveConnections() {
        return pool.getActiveConnections();
    }

    @Override
    public void close() {
        pool.dispose();
    }
}
