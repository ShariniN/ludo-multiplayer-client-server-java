package ludo.servlet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ludo.db.MatchRecord;
import ludo.db.PersistenceException;
import ludo.db.PersistenceGateway;
import ludo.db.PlayerRecord;
import ludo.server.GameRoomManager;
import ludo.server.RequestQueueProcessor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GET /api/leaderboard — rankings and match history read from the Database Tier
 * through the PersistenceGateway (H2 DatabaseServer when running, else the in-memory fallback).
 */
public class LeaderboardServlet extends LudoServlet {

    private static final long serialVersionUID = 1L;

    private final transient PersistenceGateway persistence;

    public LeaderboardServlet(GameRoomManager roomManager, RequestQueueProcessor queueProcessor, PersistenceGateway persistence) {
        super(roomManager, queueProcessor);
        this.persistence = persistence;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        List<PlayerRecord> leaderboard;
        List<MatchRecord> matches;
        try {
            leaderboard = persistence.getLeaderboard();
            matches = persistence.getMatchHistory();
        } catch (PersistenceException e) {
            writeError(resp, 503, "Database tier unavailable: " + e.getMessage());
            return;
        }

        List<Object> rows = new ArrayList<>();
        int rank = 1;
        for (PlayerRecord p : leaderboard) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rank", rank++);
            row.put("id", p.id());
            row.put("name", p.name());
            row.put("won", p.gamesWon());
            row.put("played", p.gamesPlayed());
            row.put("captures", p.totalCaptures());
            row.put("winRate", p.winRate());
            rows.add(row);
        }
        List<Object> history = new ArrayList<>();
        for (MatchRecord m : matches) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("matchId", m.matchId());
            row.put("roomId", m.roomId());
            row.put("winnerColor", m.winnerColor());
            row.put("winnerName", m.winnerName());
            row.put("rounds", m.totalRounds());
            row.put("durationSeconds", m.durationSeconds());
            row.put("timestamp", m.completedAtMillis());
            history.add(row);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "SUCCESS");
        body.put("source", persistence.describe());
        body.put("leaderboard", rows);
        body.put("totalMatchesPlayed", history.size());
        body.put("matchHistory", history);
        writeJson(resp, 200, body);
    }
}
