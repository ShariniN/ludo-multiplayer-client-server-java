package ludo.util;

import ludo.model.Piece;
import ludo.model.Player;
import ludo.enums.Color;
import ludo.enums.PieceState;
import ludo.strategy.PlayerStrategy;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

@DisplayName("GameLogger Tests")
class GameLoggerTest {

    private GameLogger logger;
    private ByteArrayOutputStream outContent;
    private PrintStream originalOut;

    @BeforeEach
    void setUp() {
        logger = new GameLogger();
        outContent = new ByteArrayOutputStream();
        originalOut = System.out;
        System.setOut(new PrintStream(outContent));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    private String getOutput() {
        return outContent.toString().trim();
    }

    @Test
    @DisplayName("printPieceCount shows correct board/base counts")
    void printPieceCount() {
        Player player = new Player(Color.RED, mock(PlayerStrategy.class));
        player.getPiece(0).setState(PieceState.ACTIVE);
        logger.printPieceCount(player);
        assertTrue(getOutput().contains("1/4 on pieces on the board"));
        assertTrue(getOutput().contains("3/4 pieces on the base"));
    }

    @Test
    @DisplayName("printMoveFromBase only prints move message (SRP)")
    void printMoveFromBase() {
        Player player = new Player(Color.RED, mock(PlayerStrategy.class));
        Piece piece = player.getPiece(0);
        logger.printMoveFromBase(player, piece);
        String output = getOutput();
        assertTrue(output.contains("moves piece R1 to the starting point"));
        
        assertFalse(output.contains("on pieces on the board"));
    }

    @Test
    @DisplayName("printCoinToss shows direction")
    void printCoinToss() {
        Piece piece = new Piece("R1", Color.RED);
        piece.setDirection(ludo.enums.Direction.CLOCKWISE);
        logger.printCoinToss(piece, ludo.enums.Direction.CLOCKWISE);
        assertTrue(getOutput().contains("clockwise"));
    }

    @Test
    @DisplayName("printDiceRoll shows player and value")
    void printDiceRoll() {
        logger.printDiceRoll(Color.RED, 6);
        assertTrue(getOutput().contains("Red player rolled 6"));
    }

    @Test
    @DisplayName("printNoValidMove shows ignore message")
    void printNoValidMove() {
        logger.printNoValidMove(Color.RED);
        assertTrue(getOutput().contains("no valid moves"));
    }

    @Test
    @DisplayName("printTieDetected shows tie value")
    void printTieDetected() {
        logger.printTieDetected(5);
        assertTrue(getOutput().contains("Tie detected at value 5"));
    }

    @Test
    @DisplayName("printMaxRoundsReached shows round limit")
    void printMaxRoundsReached() {
        logger.printMaxRoundsReached(1000);
        assertTrue(getOutput().contains("Maximum rounds reached (1000)"));
    }

    @Test
    @DisplayName("printRoundStatus prints player summaries")
    void printRoundStatus() {
        Player p1 = new Player(Color.RED, mock(PlayerStrategy.class));
        Player p2 = new Player(Color.GREEN, mock(PlayerStrategy.class));
        Player[] order = {p1, p2};
        logger.printRoundStatus(order, 10, 3, true);
        String output = getOutput();
        assertTrue(output.contains("Red"));
        assertTrue(output.contains("Green"));
        assertTrue(output.contains("mystery cell is at 10"));
    }

    @Test
    @DisplayName("printRoundStatus omits mystery cell when inactive")
    void printRoundStatusNoMystery() {
        Player p1 = new Player(Color.RED, mock(PlayerStrategy.class));
        Player[] order = {p1};
        logger.printRoundStatus(order, -1, 0, false);
        assertFalse(getOutput().contains("mystery cell"));
    }

    @Test
    @DisplayName("printPieceLocations shows all piece positions")
    void printPieceLocations() {
        Player player = new Player(Color.RED, mock(PlayerStrategy.class));
        logger.printPieceLocations(player);
        String output = getOutput();
        assertTrue(output.contains("Location of pieces Red"));
        assertTrue(output.contains("R1 -> Base"));
        assertTrue(output.contains("R4 -> Base"));
    }

    @Test
    @DisplayName("printMysteryCellStatus shows position and rounds")
    void printMysteryCellStatus() {
        logger.printMysteryCellStatus(25, 3);
        assertTrue(getOutput().contains("mystery cell is at 25"));
        assertTrue(getOutput().contains("next 3 rounds"));
    }
}
