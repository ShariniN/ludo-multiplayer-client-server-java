package ludo.model;

import ludo.enums.Color;
import ludo.enums.Direction;
import ludo.enums.PieceState;
import ludo.util.BoardUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks track spatial occupancy.
 * Implements dual-bookkeeping to map cell positions to occupant lists in O(1) time.
 */
public class Board {

    @SuppressWarnings("unchecked")
    private final List<Piece>[] standardCells = new ArrayList[BoardUtils.PATH_SIZE];

    @SuppressWarnings("unchecked")
    private final List<Piece>[][] homeStraightCells =
            new ArrayList[Color.values().length][BoardUtils.HOME_STRAIGHT_SIZE];

    public Board() {
        for (int i = 0; i < BoardUtils.PATH_SIZE; i++) {
            standardCells[i] = new ArrayList<>(4);
        }
        for (int c = 0; c < Color.values().length; c++) {
            for (int i = 0; i < BoardUtils.HOME_STRAIGHT_SIZE; i++) {
                homeStraightCells[c][i] = new ArrayList<>(2);
            }
        }
    }

    // Adds the piece to the cell's occupant list and updates its position
    public void placePieceOnStandard(Piece piece, int position) {
        standardCells[position].add(piece);
        piece.setState(PieceState.ACTIVE);
        piece.setPosition(position);
    }

    public void removePieceFromStandard(Piece piece) {
        if (piece.isActive()) {
            standardCells[piece.getPosition()].remove(piece);
        }
    }

    public void placePieceOnHomeStraight(Piece piece, int position) {
        homeStraightCells[piece.getColor().ordinal()][position].add(piece);
        piece.setState(PieceState.HOME_STRAIGHT);
        piece.setPosition(position);
    }

    public void removePieceFromHomeStraight(Piece piece) {
        if (piece.isInHomeStraight()) {
            homeStraightCells[piece.getColor().ordinal()][piece.getPosition()].remove(piece);
        }
    }

    public void removePieceFromCurrentCell(Piece piece) {
        if (piece.isActive()) {
            removePieceFromStandard(piece);
        } else if (piece.isInHomeStraight()) {
            removePieceFromHomeStraight(piece);
        }
    }

    public List<Piece> getPiecesAtStandard(int position) {
        return standardCells[position];
    }

    public List<Piece> getPiecesAtHomeStraight(Color color, int position) {
        return homeStraightCells[color.ordinal()][position];
    }

    public int pieceCountAtStandard(int position) {
        return standardCells[position].size();
    }

    // A block is 2+ same-colour pieces on one cell
    public boolean hasBlockAt(int position) {
        List<Piece> pieces = standardCells[position];
        if (pieces.size() < BoardUtils.MIN_BLOCK_SIZE) return false;

        return countSameColorPieces(pieces) >= BoardUtils.MIN_BLOCK_SIZE;
    }

    public boolean hasBlockOfColorAt(int position, Color color) {
        return getBlockSizeOfColor(position, color) >= BoardUtils.MIN_BLOCK_SIZE;
    }

    public boolean hasOpponentBlockAt(int position, Color moverColor) {
        List<Piece> pieces = standardCells[position];
        if (pieces.size() < BoardUtils.MIN_BLOCK_SIZE) return false;

        for (Color c : Color.values()) {
            if (c != moverColor && getBlockSizeOfColor(position, c) >= BoardUtils.MIN_BLOCK_SIZE) {
                return true;
            }
        }
        return false;
    }

    public int getBlockSizeOfColor(int position, Color color) {
        int count = 0;
        for (Piece p : standardCells[position]) {
            if (p.getColor() == color) count++;
        }
        return count;
    }

    public int findFirstOpponentBlockInPath(int from, int to, Direction direction,
                                            Color moverColor) {
        int totalSteps;
        if (direction == Direction.CLOCKWISE) {
            totalSteps = BoardUtils.clockwiseDistance(from, to);
        } else {
            totalSteps = BoardUtils.counterclockwiseDistance(from, to);
        }

        for (int step = 1; step <= totalSteps; step++) {
            int cell;
            if (direction == Direction.CLOCKWISE) {
                cell = BoardUtils.moveClockwise(from, step);
            } else {
                cell = BoardUtils.moveCounterclockwise(from, step);
            }
            if (hasOpponentBlockAt(cell, moverColor)) {
                return cell;
            }
        }
        return -1;
    }

    public int getCellBeforeBlock(int blockPosition, Direction direction) {
        if (direction == Direction.CLOCKWISE) {
            return BoardUtils.moveCounterclockwise(blockPosition, 1);
        } else {
            return BoardUtils.moveClockwise(blockPosition, 1);
        }
    }

    public Color getOpponentBlockColor(int position, Color moverColor) {
        for (Color c : Color.values()) {
            if (c != moverColor && getBlockSizeOfColor(position, c) >= BoardUtils.MIN_BLOCK_SIZE) {
                return c;
            }
        }
        return null;
    }

    public List<Integer> getPlayerBlockPositions(Color color) {
        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < BoardUtils.PATH_SIZE; i++) {
            if (getBlockSizeOfColor(i, color) >= BoardUtils.MIN_BLOCK_SIZE) {
                positions.add(i);
            }
        }
        return positions;
    }

    private int countSameColorPieces(List<Piece> pieces) {
        if (pieces.isEmpty()) return 0;

        int maxCount = 0;
        for (Color c : Color.values()) {
            int count = 0;
            for (Piece p : pieces) {
                if (p.getColor() == c) count++;
            }
            if (count > maxCount) maxCount = count;
        }
        return maxCount;
    }
}
