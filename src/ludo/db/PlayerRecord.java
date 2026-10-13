package ludo.db;

/** One leaderboard row: accumulated statistics for a player across all recorded matches. */
public record PlayerRecord(String id, String name, int gamesPlayed, int gamesWon, int totalCaptures) {

    /** Win rate as a percentage with one decimal place (0.0 when no games were played). */
    public double winRate() {
        return gamesPlayed > 0 ? Math.round(gamesWon * 1000.0 / gamesPlayed) / 10.0 : 0.0;
    }
}
