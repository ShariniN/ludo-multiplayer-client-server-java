package ludo.model;

import ludo.enums.Color;
import ludo.enums.MysteryEffect;
import ludo.strategy.PlayerStrategy;
import ludo.util.BoardUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a player participating in the simulation.
 * Manages a set of 4 pieces, their strategy configuration, and consecutive sixes counter.
 */
public class Player {

    private final Color color;
    private final Piece[] pieces;
    private final PlayerStrategy strategy;

    private int consecutiveSixCount;

    public Player(Color color, PlayerStrategy strategy) {
        this.color = color;
        this.strategy = strategy;
        this.pieces = new Piece[BoardUtils.PIECES_PER_PLAYER];
        this.consecutiveSixCount = 0;

        String prefix = color.getInitial();
        for (int i = 0; i < BoardUtils.PIECES_PER_PLAYER; i++) {
            pieces[i] = new Piece(prefix + (i + 1), color);
        }
    }

    public Color getColor()            { return color; }
    public PlayerStrategy getStrategy(){ return strategy; }
    public Piece[] getPieces()         { return pieces; }
    public Piece getPiece(int index)   { return pieces[index]; }

    public int getConsecutiveSixCount()      { return consecutiveSixCount; }
    public void incrementConsecutiveSixCount(){ consecutiveSixCount++; }
    public void resetConsecutiveSixCount()    { consecutiveSixCount = 0; }

    public List<Piece> getPiecesInBase() {
        List<Piece> result = new ArrayList<>(BoardUtils.PIECES_PER_PLAYER);
        for (Piece p : pieces) {
            if (p.isAtBase()) result.add(p);
        }
        return result;
    }

    public List<Piece> getPiecesOnBoard() {
        List<Piece> result = new ArrayList<>(BoardUtils.PIECES_PER_PLAYER);
        for (Piece p : pieces) {
            if (p.isActive()) result.add(p);
        }
        return result;
    }

    public List<Piece> getPiecesInHomeStraight() {
        List<Piece> result = new ArrayList<>(BoardUtils.PIECES_PER_PLAYER);
        for (Piece p : pieces) {
            if (p.isInHomeStraight()) result.add(p);
        }
        return result;
    }

    public List<Piece> getPiecesAtHome() {
        List<Piece> result = new ArrayList<>(BoardUtils.PIECES_PER_PLAYER);
        for (Piece p : pieces) {
            if (p.isAtHome()) result.add(p);
        }
        return result;
    }

    // Active or home-straight pieces not locked by an effect
    public List<Piece> getMovablePieces() {
        List<Piece> result = new ArrayList<>(BoardUtils.PIECES_PER_PLAYER);
        for (Piece p : pieces) {
            if ((p.isActive() || p.isInHomeStraight()) && p.isMovable()) {
                result.add(p);
            }
        }
        return result;
    }

    public List<Piece> getPiecesInBriefing() {
        List<Piece> result = new ArrayList<>(BoardUtils.PIECES_PER_PLAYER);
        for (Piece p : pieces) {
            if (p.getMysteryEffect() == MysteryEffect.BRIEFING) {
                result.add(p);
            }
        }
        return result;
    }

    public boolean hasAllPiecesHome() {
        for (Piece p : pieces) {
            if (!p.isAtHome()) return false;
        }
        return true;
    }

    public int getPiecesOnBoardCount() {
        int count = 0;
        for (Piece p : pieces) {
            if (p.isActive() || p.isInHomeStraight()) count++;
        }
        return count;
    }

    public int getPiecesInBaseCount() {
        int count = 0;
        for (Piece p : pieces) {
            if (p.isAtBase()) count++;
        }
        return count;
    }

    @Override
    public String toString() {
        return color.getDisplayName() + " player";
    }
}
