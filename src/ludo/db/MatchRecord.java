package ludo.db;

/** One finished match as stored in the match_history table. */
public record MatchRecord(String matchId, String roomId, String winnerColor, String winnerName,
                          int totalRounds, int durationSeconds, long completedAtMillis) {
}
