package ludo.strategy;

import ludo.enums.Direction;
import ludo.model.Board;
import ludo.model.Piece;
import ludo.model.Player;
import ludo.util.BoardUtils;

import java.util.List;

/** Green AI (Strategy): defensive, prefers forming and moving blocks. */
public class GreenStrategy implements PlayerStrategy {

    @Override
    public Piece choosePiece(Player player, int diceValue, Board board, int mysteryCellPosition) {
        List<Piece> onBoard = player.getPiecesOnBoard();
        List<Piece> inBase = player.getPiecesInBase();
        List<Piece> inHomeStraight = player.getPiecesInHomeStraight();

        // On a 6, join an existing block if possible, otherwise deploy from base
        if (diceValue == 6 && !inBase.isEmpty()) {
            boolean canCreateBlock = false;
            for (Piece p : onBoard) {
                if (!p.isMovable()) continue;
                int effectiveMove = p.getEffectiveMovement(6);
                int dest;
                if (p.getDirection() == Direction.CLOCKWISE) {
                    dest = BoardUtils.moveClockwise(p.getPosition(), effectiveMove);
                } else {
                    dest = BoardUtils.moveCounterclockwise(p.getPosition(), effectiveMove);
                }
                if (board.getBlockSizeOfColor(dest, player.getColor()) > 0) {
                    canCreateBlock = true;
                    break;
                }
            }

            if (canCreateBlock) {
                for (Piece p : onBoard) {
                    if (!p.isMovable()) continue;
                    int effectiveMove = p.getEffectiveMovement(6);
                    int dest;
                    if (p.getDirection() == Direction.CLOCKWISE) {
                        dest = BoardUtils.moveClockwise(p.getPosition(), effectiveMove);
                    } else {
                        dest = BoardUtils.moveCounterclockwise(p.getPosition(), effectiveMove);
                    }
                    if (board.getBlockSizeOfColor(dest, player.getColor()) > 0) {
                        return p;
                    }
                }
            }

            return inBase.get(0);
        }

        for (Piece p : inHomeStraight) {
            if (!p.isMovable()) continue;
            int newPos = p.getPosition() + p.getEffectiveMovement(diceValue);
            if (newPos <= BoardUtils.HOME_STRAIGHT_SIZE) {
                return p;
            }
        }

        // Advance the non-block piece closest to home, keeping blocks intact
        Piece nonBlockPiece = null;
        for (Piece p : onBoard) {
            if (!p.isMovable()) continue;
            if (!board.hasBlockOfColorAt(p.getPosition(), player.getColor())) {
                if (nonBlockPiece == null) {
                    nonBlockPiece = p;
                } else {
                    int dist1 = BoardUtils.distanceToHome(nonBlockPiece.getPosition(),
                            nonBlockPiece.getColor(), nonBlockPiece.getDirection(),
                            nonBlockPiece.hasPassedApproachOnce());
                    int dist2 = BoardUtils.distanceToHome(p.getPosition(),
                            p.getColor(), p.getDirection(), p.hasPassedApproachOnce());
                    if (dist2 < dist1) {
                        nonBlockPiece = p;
                    }
                }
            }
        }

        if (nonBlockPiece != null) {
            // Not yet eligible for home straight: a capture would make it eligible
            if (!nonBlockPiece.canEnterHomeStraight()) {
                Piece capturePiece = findCaptureForEligibility(player, diceValue, board);
                if (capturePiece != null) return capturePiece;
            }
            return nonBlockPiece;
        }

        for (Piece p : onBoard) {
            if (p.isMovable()) return p;
        }

        if (diceValue == 6 && !inBase.isEmpty()) {
            return inBase.get(0);
        }

        return null;
    }

    private Piece findCaptureForEligibility(Player player, int diceValue, Board board) {
        for (Piece piece : player.getPiecesOnBoard()) {
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
                    return piece;
                }
            }
        }
        return null;
    }

    @Override
    public boolean preferBlockMove(Player player, Piece piece, int diceValue, Board board) {
        return true;
    }
}
