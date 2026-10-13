package ludo.strategy;

import ludo.enums.Direction;
import ludo.model.Board;
import ludo.model.Piece;
import ludo.model.Player;
import ludo.util.BoardUtils;

import java.util.List;

/** Yellow AI (Strategy): deploys from base first, captures to gain home-straight eligibility, then races home. */
public class YellowStrategy implements PlayerStrategy {

    @Override
    public Piece choosePiece(Player player, int diceValue, Board board, int mysteryCellPosition) {
        List<Piece> inBase = player.getPiecesInBase();
        List<Piece> onBoard = player.getPiecesOnBoard();
        List<Piece> inHomeStraight = player.getPiecesInHomeStraight();

        if (diceValue == 6 && !inBase.isEmpty()) {
            return inBase.get(0);
        }

        // Only pieces not yet eligible for the home straight look for captures
        Piece capturePiece = null;
        for (Piece piece : onBoard) {
            if (piece.canEnterHomeStraight()) continue;
            if (!piece.isMovable()) continue;

            int effectiveMove = piece.getEffectiveMovement(diceValue);
            if (effectiveMove <= 0) continue;

            int dest;
            if (piece.getDirection() == Direction.CLOCKWISE) {
                dest = BoardUtils.moveClockwise(piece.getPosition(), effectiveMove);
            } else {
                dest = BoardUtils.moveCounterclockwise(piece.getPosition(), effectiveMove);
            }

            for (Piece target : board.getPiecesAtStandard(dest)) {
                if (target.getColor() != player.getColor()
                        && board.getBlockSizeOfColor(dest, target.getColor()) < 2) {
                    capturePiece = piece;
                    break;
                }
            }
            if (capturePiece != null) break;
        }

        if (capturePiece != null) {
            return capturePiece;
        }

        // Otherwise move the piece closest to home
        Piece closestPiece = null;
        int closestDist = Integer.MAX_VALUE;

        for (Piece p : inHomeStraight) {
            if (!p.isMovable()) continue;
            int newPos = p.getPosition() + p.getEffectiveMovement(diceValue);
            if (newPos <= BoardUtils.HOME_STRAIGHT_SIZE) {
                int dist = BoardUtils.HOME_STRAIGHT_SIZE - p.getPosition();
                if (dist < closestDist) {
                    closestDist = dist;
                    closestPiece = p;
                }
            }
        }

        for (Piece p : onBoard) {
            if (!p.isMovable()) continue;
            int effectiveMove = p.getEffectiveMovement(diceValue);
            if (effectiveMove <= 0) continue;

            int dist = BoardUtils.distanceToHome(
                    p.getPosition(), p.getColor(), p.getDirection(), p.hasPassedApproachOnce());
            if (dist < closestDist) {
                closestDist = dist;
                closestPiece = p;
            }
        }

        if (closestPiece != null) {
            return closestPiece;
        }

        if (diceValue == 6 && !inBase.isEmpty()) {
            return inBase.get(0);
        }

        return null;
    }

    @Override
    public boolean preferBlockMove(Player player, Piece piece, int diceValue, Board board) {
        return false;
    }
}
