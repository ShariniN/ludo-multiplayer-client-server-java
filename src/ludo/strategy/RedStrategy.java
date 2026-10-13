package ludo.strategy;

import ludo.enums.Direction;
import ludo.model.Board;
import ludo.model.Piece;
import ludo.model.Player;
import ludo.util.BoardUtils;

import java.util.List;

/** Red AI (Strategy): aggressive, captures first, then advances while avoiding self-blocks. */
public class RedStrategy implements PlayerStrategy {

    @Override
    public Piece choosePiece(Player player, int diceValue, Board board, int mysteryCellPosition) {
        Piece capturePiece = findBestCapture(player, diceValue, board);
        if (capturePiece != null) {
            return capturePiece;
        }

        if (diceValue == 6) {
            List<Piece> onBoard = player.getPiecesOnBoard();
            List<Piece> inBase = player.getPiecesInBase();

            if (!inBase.isEmpty()) {
                if (onBoard.isEmpty()) {
                    return inBase.get(0);
                }
            }
        }

        // Prefer a move that doesn't stack onto our own piece
        List<Piece> movable = player.getMovablePieces();
        Piece bestPiece = null;

        for (Piece piece : movable) {
            if (piece.isActive()) {
                int effectiveMove = piece.getEffectiveMovement(diceValue);
                if (effectiveMove <= 0) continue;

                int dest;
                if (piece.getDirection() == Direction.CLOCKWISE) {
                    dest = BoardUtils.moveClockwise(piece.getPosition(), effectiveMove);
                } else {
                    dest = BoardUtils.moveCounterclockwise(piece.getPosition(), effectiveMove);
                }

                boolean wouldCreateBlock = board.getBlockSizeOfColor(dest, player.getColor()) > 0;
                if (!wouldCreateBlock || bestPiece == null) {
                    bestPiece = piece;
                    if (!wouldCreateBlock) break;
                }
            } else if (piece.isInHomeStraight()) {
                int newPos = piece.getPosition() + piece.getEffectiveMovement(diceValue);
                if (newPos <= BoardUtils.HOME_STRAIGHT_SIZE) {
                    return piece;
                }
            }
        }

        if (bestPiece == null && diceValue == 6 && !player.getPiecesInBase().isEmpty()) {
            return player.getPiecesInBase().get(0);
        }

        return bestPiece;
    }

    // Picks the capture that hits the opponent piece closest to home (blocks can't be captured)
    private Piece findBestCapture(Player player, int diceValue, Board board) {
        Piece bestPiece = null;
        int bestOpponentDistToHome = Integer.MAX_VALUE;

        for (Piece piece : player.getPiecesOnBoard()) {
            int effectiveMove = piece.getEffectiveMovement(diceValue);
            if (effectiveMove <= 0) continue;

            int dest;
            if (piece.getDirection() == Direction.CLOCKWISE) {
                dest = BoardUtils.moveClockwise(piece.getPosition(), effectiveMove);
            } else {
                dest = BoardUtils.moveCounterclockwise(piece.getPosition(), effectiveMove);
            }

            List<Piece> targetPieces = board.getPiecesAtStandard(dest);
            for (Piece target : targetPieces) {
                if (target.getColor() != player.getColor()) {
                    if (board.getBlockSizeOfColor(dest, target.getColor()) >= 2) continue;

                    int opponentDist = BoardUtils.distanceToHome(
                            target.getPosition(), target.getColor(),
                            target.getDirection(), target.hasPassedApproachOnce());

                    if (opponentDist < bestOpponentDistToHome) {
                        bestOpponentDistToHome = opponentDist;
                        bestPiece = piece;
                    }
                }
            }
        }

        return bestPiece;
    }

    @Override
    public boolean preferBlockMove(Player player, Piece piece, int diceValue, Board board) {
        return false;
    }
}
