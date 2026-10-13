package ludo.model;

import ludo.enums.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

@DisplayName("Board Tests")
class BoardTest {

    private Board board;
    private Piece redPiece1, redPiece2, bluePiece1, bluePiece2;

    @BeforeEach
    void setUp() {
        board = new Board();
        redPiece1 = new Piece("R1", Color.RED);
        redPiece2 = new Piece("R2", Color.RED);
        bluePiece1 = new Piece("B1", Color.BLUE);
        bluePiece2 = new Piece("B2", Color.BLUE);
    }

    @Test
    @DisplayName("Place piece on standard cell — cell contains piece")
    void placePieceOnStandard() {
        board.placePieceOnStandard(redPiece1, 10);
        assertEquals(1, board.getPiecesAtStandard(10).size());
        assertTrue(board.getPiecesAtStandard(10).contains(redPiece1));
    }

    @Test
    @DisplayName("Place piece updates piece state to ACTIVE")
    void placePieceUpdatesPieceState() {
        board.placePieceOnStandard(redPiece1, 10);
        assertEquals(PieceState.ACTIVE, redPiece1.getState());
        assertEquals(10, redPiece1.getPosition());
    }

    @Test
    @DisplayName("Remove piece from standard cell")
    void removePieceFromStandard() {
        board.placePieceOnStandard(redPiece1, 10);
        board.removePieceFromStandard(redPiece1);
        assertEquals(0, board.getPiecesAtStandard(10).size());
    }

    @Test
    @DisplayName("Remove piece not on board is a no-op")
    void removePieceNotOnBoard() {
        
        board.removePieceFromStandard(redPiece1);
        
    }

    @Test
    @DisplayName("Multiple pieces on same cell")
    void multiplePiecesOnCell() {
        board.placePieceOnStandard(redPiece1, 10);
        board.placePieceOnStandard(redPiece2, 10);
        assertEquals(2, board.getPiecesAtStandard(10).size());
    }

    @Test
    @DisplayName("Place piece on home straight")
    void placePieceOnHomeStraight() {
        board.placePieceOnHomeStraight(redPiece1, 3);
        assertEquals(1, board.getPiecesAtHomeStraight(Color.RED, 3).size());
        assertEquals(PieceState.HOME_STRAIGHT, redPiece1.getState());
    }

    @Test
    @DisplayName("Remove piece from home straight")
    void removePieceFromHomeStraight() {
        board.placePieceOnHomeStraight(redPiece1, 3);
        board.removePieceFromHomeStraight(redPiece1);
        assertEquals(0, board.getPiecesAtHomeStraight(Color.RED, 3).size());
    }

    @Test
    @DisplayName("removePieceFromCurrentCell removes ACTIVE piece")
    void removeFromCurrentCellActive() {
        board.placePieceOnStandard(redPiece1, 20);
        board.removePieceFromCurrentCell(redPiece1);
        assertEquals(0, board.getPiecesAtStandard(20).size());
    }

    @Test
    @DisplayName("removePieceFromCurrentCell removes HOME_STRAIGHT piece")
    void removeFromCurrentCellHomeStraight() {
        board.placePieceOnHomeStraight(redPiece1, 2);
        board.removePieceFromCurrentCell(redPiece1);
        assertEquals(0, board.getPiecesAtHomeStraight(Color.RED, 2).size());
    }

    @Test
    @DisplayName("pieceCountAtStandard returns correct count")
    void pieceCountAtStandard() {
        assertEquals(0, board.pieceCountAtStandard(10));
        board.placePieceOnStandard(redPiece1, 10);
        assertEquals(1, board.pieceCountAtStandard(10));
    }

    @Test
    @DisplayName("No block with 0 or 1 piece")
    void noBlockWithFewerThan2() {
        assertFalse(board.hasBlockAt(10));
        board.placePieceOnStandard(redPiece1, 10);
        assertFalse(board.hasBlockAt(10));
    }

    @Test
    @DisplayName("Block detected with 2 same-colour pieces")
    void blockWithTwoSameColour() {
        board.placePieceOnStandard(redPiece1, 10);
        board.placePieceOnStandard(redPiece2, 10);
        assertTrue(board.hasBlockAt(10));
    }

    @Test
    @DisplayName("No block with 2 different-colour pieces")
    void noBlockDifferentColours() {
        board.placePieceOnStandard(redPiece1, 10);
        board.placePieceOnStandard(bluePiece1, 10);
        
        assertFalse(board.hasBlockOfColorAt(10, Color.RED));
        assertFalse(board.hasBlockOfColorAt(10, Color.BLUE));
    }

    @Test
    @DisplayName("hasBlockOfColorAt for specific colour")
    void hasBlockOfColorAt() {
        board.placePieceOnStandard(redPiece1, 10);
        board.placePieceOnStandard(redPiece2, 10);
        assertTrue(board.hasBlockOfColorAt(10, Color.RED));
        assertFalse(board.hasBlockOfColorAt(10, Color.BLUE));
    }

    @Test
    @DisplayName("hasOpponentBlockAt: opponent block → true")
    void hasOpponentBlockTrue() {
        board.placePieceOnStandard(bluePiece1, 10);
        board.placePieceOnStandard(bluePiece2, 10);
        assertTrue(board.hasOpponentBlockAt(10, Color.RED));
    }

    @Test
    @DisplayName("hasOpponentBlockAt: own block → false")
    void hasOpponentBlockFalseOwnBlock() {
        board.placePieceOnStandard(redPiece1, 10);
        board.placePieceOnStandard(redPiece2, 10);
        assertFalse(board.hasOpponentBlockAt(10, Color.RED));
    }

    @Test
    @DisplayName("getBlockSizeOfColor counts correctly")
    void getBlockSizeOfColor() {
        board.placePieceOnStandard(redPiece1, 10);
        board.placePieceOnStandard(redPiece2, 10);
        assertEquals(2, board.getBlockSizeOfColor(10, Color.RED));
        assertEquals(0, board.getBlockSizeOfColor(10, Color.BLUE));
    }

    @Test
    @DisplayName("findFirstOpponentBlockInPath: block in path → returns position")
    void findBlockInPathCw() {
        board.placePieceOnStandard(bluePiece1, 15);
        board.placePieceOnStandard(bluePiece2, 15);
        int blockPos = board.findFirstOpponentBlockInPath(10, 20, Direction.CLOCKWISE, Color.RED);
        assertEquals(15, blockPos);
    }

    @Test
    @DisplayName("findFirstOpponentBlockInPath: no block → returns -1")
    void findNoBlockInPath() {
        int blockPos = board.findFirstOpponentBlockInPath(10, 20, Direction.CLOCKWISE, Color.RED);
        assertEquals(-1, blockPos);
    }

    @Test
    @DisplayName("findFirstOpponentBlockInPath: wraparound CCW")
    void findBlockInPathCcw() {
        board.placePieceOnStandard(bluePiece1, 50);
        board.placePieceOnStandard(bluePiece2, 50);
        int blockPos = board.findFirstOpponentBlockInPath(2, 48, Direction.COUNTERCLOCKWISE, Color.RED);
        assertEquals(50, blockPos);
    }

    @Test
    @DisplayName("getCellBeforeBlock CW: one cell before (counterclockwise)")
    void cellBeforeBlockCw() {
        assertEquals(14, board.getCellBeforeBlock(15, Direction.CLOCKWISE));
    }

    @Test
    @DisplayName("getCellBeforeBlock CCW: one cell after (clockwise)")
    void cellBeforeBlockCcw() {
        assertEquals(16, board.getCellBeforeBlock(15, Direction.COUNTERCLOCKWISE));
    }

    @Test
    @DisplayName("getCellBeforeBlock wraps around at 0")
    void cellBeforeBlockWraparound() {
        assertEquals(51, board.getCellBeforeBlock(0, Direction.CLOCKWISE));
    }

    @Test
    @DisplayName("getPlayerBlockPositions returns all block positions")
    void getPlayerBlockPositions() {
        board.placePieceOnStandard(redPiece1, 10);
        board.placePieceOnStandard(redPiece2, 10);
        List<Integer> positions = board.getPlayerBlockPositions(Color.RED);
        assertEquals(1, positions.size());
        assertTrue(positions.contains(10));
    }

    @Test
    @DisplayName("getPlayerBlockPositions returns empty when no blocks")
    void getPlayerBlockPositionsEmpty() {
        assertTrue(board.getPlayerBlockPositions(Color.RED).isEmpty());
    }

    @Test
    @DisplayName("getOpponentBlockColor returns opponent colour")
    void getOpponentBlockColor() {
        board.placePieceOnStandard(bluePiece1, 10);
        board.placePieceOnStandard(bluePiece2, 10);
        assertEquals(Color.BLUE, board.getOpponentBlockColor(10, Color.RED));
    }

    @Test
    @DisplayName("getOpponentBlockColor returns null when no opponent block")
    void getOpponentBlockColorNull() {
        assertNull(board.getOpponentBlockColor(10, Color.RED));
    }
}
