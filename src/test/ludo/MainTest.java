package ludo;

import ludo.game.Dice;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

@DisplayName("Main Entry Point Tests")
class MainTest {

    private ByteArrayOutputStream outContent;
    private PrintStream originalOut;

    @BeforeEach
    void setUp() {
        Dice.resetInstance();
        outContent = new ByteArrayOutputStream();
        originalOut = System.out;
        System.setOut(new PrintStream(outContent));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        Dice.resetInstance();
    }

    @Test
    @DisplayName("Valid seed runs the game")
    void validSeed() {
        Main.main(new String[]{String.valueOf(Dice.DEFAULT_TEST_SEED)});
        String output = outContent.toString();
        assertTrue(output.contains("Random seed: " + Dice.DEFAULT_TEST_SEED));
    }

    @Test
    @DisplayName("No args uses default seed DEFAULT_TEST_SEED")
    void noArgsDefaultSeed() {
        Main.main(new String[]{});
        String output = outContent.toString();
        assertTrue(output.contains("Random seed: " + Dice.DEFAULT_TEST_SEED));
    }

    @Test
    @DisplayName("Invalid seed throws IllegalArgumentException")
    void invalidSeedThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> Main.main(new String[]{"not_a_number"}));
    }

    @Test
    @DisplayName("Empty string seed throws IllegalArgumentException")
    void emptySeedThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> Main.main(new String[]{""}));
    }

    @Test
    @DisplayName("Negative seed is valid")
    void negativeSeedValid() {
        Main.main(new String[]{"-1"});
        String output = outContent.toString();
        assertTrue(output.contains("Random seed: -1"));
    }
}
