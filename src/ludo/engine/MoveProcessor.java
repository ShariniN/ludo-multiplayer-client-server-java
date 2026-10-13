package ludo.engine;

import ludo.enums.Direction;
import ludo.enums.PieceState;
import ludo.event.GameEventListener;
import ludo.game.CoinToss;
import ludo.model.Board;
import ludo.model.Piece;
import ludo.model.Player;
import ludo.game.MoveResult;
import ludo.util.BoardUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Executes piece movement, blocks and captures. Teleports are delegated to MysteryManager.
 */
public class MoveProcessor {

    private final Board board;
    private final CoinToss coinToss;
    private final MysteryManager mysteryManager;
    private final GameEventListener listener;

    public MoveProcessor(Board board, CoinToss coinToss,
                         MysteryManager mysteryManager, GameEventListener listener) {
        this.board = board;
        this.coinToss = coinToss;
        this.mysteryManager = mysteryManager;
        this.listener = listener;
    }

    /**
     * Main entry point: decide which kind of move to execute for the chosen piece.
     *
     * @return true if a capture occurred (grants an extra turn to the player)
     */
    public boolean executeTurnAction(Player player, Piece chosenPiece, int diceValue) {
        if (chosenPiece == null) {
            listener.onNoValidMove(player.getColor());
            return false;
        }

        if (chosenPiece.isAtBase() && diceValue == 6) {
            return moveFromBaseToX(player, chosenPiece);
        }

        if (chosenPiece.isActive()) {
            boolean isBlock = board.hasBlockOfColorAt(chosenPiece.getPosition(), player.getColor());
            boolean preferBlock = isBlock && player.getStrategy().preferBlockMove(
                    player, chosenPiece, diceValue, board);

            return preferBlock
                    ? executeBlockMove(player, chosenPiece.getPosition(), diceValue)
                    : moveOnStandardPath(player, chosenPiece, diceValue);
        }

        if (chosenPiece.isInHomeStraight()) {
            return moveOnHomeStraight(player, chosenPiece, diceValue);
        }

        return false;
    }

    // Base → X

    /**
     * Moves a piece from base to its colour start position (X).
     * Requires the caller to notify MysteryManager so spawn countdown begins.
     *
     * @return true if a capture occurred at X
     */
    public boolean moveFromBaseToX(Player player, Piece piece) {
        int startPos = player.getColor().getStartPosition();
        board.placePieceOnStandard(piece, startPos);

        Direction direction = coinToss.toss();
        piece.setDirection(direction);
        piece.setOriginalDirection(direction);

        listener.onMoveFromBase(player, piece, direction);

        return handleCaptureAtPosition(player, piece, startPos);
    }

    // Standard Path

    private boolean moveOnStandardPath(Player player, Piece piece, int diceValue) {
        int effectiveMove = piece.getEffectiveMovement(diceValue);
        if (effectiveMove <= 0) {
            listener.onNoValidMove(player.getColor());
            return false;
        }

        int currentPos = piece.getPosition();
        Direction direction = piece.getDirection();

        MoveResult homeResult = checkHomeStraightEntry(piece, effectiveMove);
        if (homeResult != null) {
            return executeHomeStraightEntry(player, piece, currentPos, effectiveMove, homeResult);
        }

        int destination = (direction == Direction.CLOCKWISE)
                ? BoardUtils.moveClockwise(currentPos, effectiveMove)
                : BoardUtils.moveCounterclockwise(currentPos, effectiveMove);

        updateApproachPassTracking(piece, currentPos, effectiveMove);

        int blockCell = board.findFirstOpponentBlockInPath(currentPos, destination,
                direction, player.getColor());

        if (blockCell != BoardUtils.NO_POSITION) {
            return handleBlockedMovement(player, piece, currentPos, destination, blockCell, effectiveMove);
        }

        String fromLoc = BoardUtils.standardCellName(currentPos);
        String toLoc   = BoardUtils.standardCellName(destination);

        board.removePieceFromStandard(piece);
        board.placePieceOnStandard(piece, destination);

        listener.onPieceMoved(piece, fromLoc, toLoc, effectiveMove, direction);

        boolean captured = handleCaptureAtPosition(player, piece, destination);

        if (mysteryManager.isActiveAt(destination)) {
            mysteryManager.handleLanding(player, piece, board, listener,
                    this::handleCaptureAtPosition);
        }

        return captured;
    }

    // Home Straight Entry

    private MoveResult checkHomeStraightEntry(Piece piece, int effectiveMove) {
        if (!piece.canEnterHomeStraight()) return null;

        Direction direction = piece.getDirection();
        if (direction == Direction.COUNTERCLOCKWISE && !piece.hasPassedApproachOnce()) return null;

        int currentPos = piece.getPosition();
        int approach   = piece.getColor().getApproachPosition();

        int distToApproach = (direction == Direction.CLOCKWISE)
                ? BoardUtils.clockwiseDistance(currentPos, approach)
                : BoardUtils.counterclockwiseDistance(currentPos, approach);

        if (effectiveMove > distToApproach) {
            int stepsIn = effectiveMove - distToApproach - 1;
            if (stepsIn == BoardUtils.HOME_STRAIGHT_SIZE) {
                return new MoveResult(PieceState.HOME, BoardUtils.NO_POSITION);
            } else if (stepsIn < BoardUtils.HOME_STRAIGHT_SIZE) {
                return new MoveResult(PieceState.HOME_STRAIGHT, stepsIn);
            }
        }
        return null;
    }

    private boolean executeHomeStraightEntry(Player player, Piece piece, int fromPos,
                                             int effectiveMove, MoveResult result) {
        String fromLoc = BoardUtils.standardCellName(fromPos);
        board.removePieceFromStandard(piece);

        if (result.reachedHome()) {
            piece.setState(PieceState.HOME);
            piece.setPosition(BoardUtils.NO_POSITION);
            listener.onPieceMoved(piece, fromLoc, "Home", effectiveMove, piece.getDirection());
            listener.onPieceReachedHome(piece);
        } else {
            board.placePieceOnHomeStraight(piece, result.getNewPosition());
            String toLoc = BoardUtils.homeStraightCellName(piece.getColor(), result.getNewPosition());
            listener.onPieceMoved(piece, fromLoc, toLoc, effectiveMove, piece.getDirection());
        }
        return false;
    }

    // Home Straight Movement

    private boolean moveOnHomeStraight(Player player, Piece piece, int diceValue) {
        int effectiveMove = piece.getEffectiveMovement(diceValue);
        int currentPos    = piece.getPosition();
        int newPos        = currentPos + effectiveMove;

        if (newPos == BoardUtils.HOME_STRAIGHT_SIZE) {
            String fromLoc = BoardUtils.homeStraightCellName(piece.getColor(), currentPos);
            board.removePieceFromHomeStraight(piece);
            piece.setState(PieceState.HOME);
            piece.setPosition(BoardUtils.NO_POSITION);
            listener.onPieceMoved(piece, fromLoc, "Home", effectiveMove, piece.getDirection());
            listener.onPieceReachedHome(piece);
        } else if (newPos < BoardUtils.HOME_STRAIGHT_SIZE) {
            String fromLoc = BoardUtils.homeStraightCellName(piece.getColor(), currentPos);
            String toLoc   = BoardUtils.homeStraightCellName(piece.getColor(), newPos);
            board.removePieceFromHomeStraight(piece);
            board.placePieceOnHomeStraight(piece, newPos);
            listener.onPieceMoved(piece, fromLoc, toLoc, effectiveMove, piece.getDirection());
        } else {
            listener.onNoValidMove(player.getColor());
        }
        return false;
    }

    // Blocked Movement

    private boolean handleBlockedMovement(Player player, Piece piece,
                                          int fromPos, int intendedDest,
                                          int blockCell, int effectiveMove) {
        int cellBefore    = board.getCellBeforeBlock(blockCell, piece.getDirection());
        String fromLoc    = BoardUtils.standardCellName(fromPos);
        String intendedLoc = BoardUtils.standardCellName(intendedDest);

        // Identify the blocking piece's colour for the event
        ludo.enums.Color blockColor = null;
        String blockPieceName = "";
        for (Piece bp : board.getPiecesAtStandard(blockCell)) {
            if (bp.getColor() != player.getColor()) {
                blockColor    = bp.getColor();
                blockPieceName = bp.getName();
                break;
            }
        }

        listener.onPieceBlocked(piece, fromLoc, intendedLoc,
                blockColor != null ? blockColor : player.getColor(), blockPieceName);

        if (cellBefore == fromPos) {
            listener.onBlockedIgnored(player.getColor());
            return false;
        }

        String cellBeforeLoc = BoardUtils.standardCellName(cellBefore);
        board.removePieceFromStandard(piece);
        board.placePieceOnStandard(piece, cellBefore);
        listener.onBlockedPartialMove(player.getColor(), cellBeforeLoc);

        if (mysteryManager.isActiveAt(cellBefore)) {
            mysteryManager.handleLanding(player, piece, board, listener,
                    this::handleCaptureAtPosition);
        }

        return false;
    }

    // Block Move

    private boolean executeBlockMove(Player player, int blockPos, int diceValue) {
        List<Piece> blockPieces = new ArrayList<>();
        for (Piece p : board.getPiecesAtStandard(blockPos)) {
            if (p.getColor() == player.getColor()) blockPieces.add(p);
        }

        int blockSize     = blockPieces.size();
        int effectiveMove = diceValue / blockSize;

        if (effectiveMove <= 0) {
            listener.onNoValidMove(player.getColor());
            return false;
        }

        Direction blockDir = determineBlockDirection(blockPieces);

        int destination = (blockDir == Direction.CLOCKWISE)
                ? BoardUtils.moveClockwise(blockPos, effectiveMove)
                : BoardUtils.moveCounterclockwise(blockPos, effectiveMove);

        int opponentBlock = board.findFirstOpponentBlockInPath(blockPos, destination,
                blockDir, player.getColor());
        if (opponentBlock != BoardUtils.NO_POSITION) {
            ludo.enums.Color opponentColor = board.getOpponentBlockColor(opponentBlock, player.getColor());
            if (opponentColor != null) {
                int opponentSize = board.getBlockSizeOfColor(opponentBlock, opponentColor);
                destination = (opponentSize == blockSize)
                        ? opponentBlock
                        : board.getCellBeforeBlock(opponentBlock, blockDir);
            }
        }

        for (Piece p : blockPieces) board.removePieceFromStandard(p);
        for (Piece p : blockPieces) board.placePieceOnStandard(p, destination);

        listener.onBlockMove(player.getColor(), blockPos, destination, effectiveMove, blockDir);

        return handleBlockCapture(player, blockPieces, destination);
    }

    private Direction determineBlockDirection(List<Piece> blockPieces) {
        Direction firstDir = blockPieces.get(0).getDirection();
        for (Piece p : blockPieces) {
            if (p.getDirection() != firstDir) {
                // Mixed directions — use the piece furthest from home
                Piece furthest = null;
                int maxDist = -1;
                for (Piece bp : blockPieces) {
                    int dist = BoardUtils.distanceToHome(
                            bp.getPosition(), bp.getColor(),
                            bp.getDirection(), bp.hasPassedApproachOnce());
                    if (dist > maxDist) { maxDist = dist; furthest = bp; }
                }
                return furthest != null ? furthest.getDirection() : firstDir;
            }
        }
        return firstDir;
    }

    private boolean handleBlockCapture(Player player, List<Piece> attackingBlock, int position) {
        List<Piece> piecesAtCell = new ArrayList<>(board.getPiecesAtStandard(position));
        boolean captured = false;

        for (Piece p : piecesAtCell) {
            if (p.getColor() != player.getColor()) {
                listener.onCapture(attackingBlock.get(0),
                        BoardUtils.standardCellName(position), p);
                board.removePieceFromStandard(p);
                p.resetToBase();
                captured = true;
            }
        }

        if (captured) {
            for (Piece p : attackingBlock) p.incrementCaptureCount();
            listener.onPieceCount(player);
        }

        return captured;
    }

    // Force Break

    /**
     * Disperses a blockade when the third consecutive six is rolled.
     * All pieces beyond the first are moved 6 cells in their original direction.
     */
    public void forceBreakBlockade(Player player, int blockPos) {
        List<Piece> blockPieces = new ArrayList<>();
        for (Piece p : board.getPiecesAtStandard(blockPos)) {
            if (p.getColor() == player.getColor()) blockPieces.add(p);
        }

        if (blockPieces.size() < BoardUtils.MIN_BLOCK_SIZE) return;

        for (int i = 1; i < blockPieces.size(); i++) {
            Piece piece = blockPieces.get(i);
            Direction originalDir = (piece.getOriginalDirection() != null)
                    ? piece.getOriginalDirection() : Direction.CLOCKWISE;
            piece.setDirection(originalDir);

            int newPos = (originalDir == Direction.CLOCKWISE)
                    ? BoardUtils.moveClockwise(blockPos, BoardUtils.HOME_STRAIGHT_ENTRY_DISTANCE)
                    : BoardUtils.moveCounterclockwise(blockPos, BoardUtils.HOME_STRAIGHT_ENTRY_DISTANCE);

            board.removePieceFromStandard(piece);
            board.placePieceOnStandard(piece, newPos);

            listener.onPieceMoved(piece,
                    BoardUtils.standardCellName(blockPos),
                    BoardUtils.standardCellName(newPos),
                    BoardUtils.HOME_STRAIGHT_ENTRY_DISTANCE, originalDir);
        }
    }

    // Capture

    /**
     * Checks and applies capture at the given position for a single moving piece.
     *
     * @return true if at least one capture occurred
     */
    public boolean handleCaptureAtPosition(Player player, Piece movingPiece, int position) {
        List<Piece> piecesAtCell = new ArrayList<>(board.getPiecesAtStandard(position));
        boolean captured = false;

        for (Piece p : piecesAtCell) {
            if (p.getColor() == player.getColor() || p == movingPiece) continue;

            int opponentCount = board.getBlockSizeOfColor(position, p.getColor());
            if (opponentCount >= BoardUtils.MIN_BLOCK_SIZE) continue; // protected by block

            listener.onCapture(movingPiece, BoardUtils.standardCellName(position), p);
            board.removePieceFromStandard(p);
            p.resetToBase();
            movingPiece.incrementCaptureCount();
            captured = true;
        }

        if (captured) listener.onPieceCount(player);

        return captured;
    }

    // Helpers

    private void updateApproachPassTracking(Piece piece, int from, int effectiveMove) {
        if (piece.getDirection() != Direction.COUNTERCLOCKWISE) return;
        if (piece.hasPassedApproachOnce()) return;

        int approach = piece.getColor().getApproachPosition();
        int distToApproach = BoardUtils.counterclockwiseDistance(from, approach);

        if (distToApproach > 0 && distToApproach <= effectiveMove) {
            piece.setPassedApproachOnce(true);
        }
    }
}
