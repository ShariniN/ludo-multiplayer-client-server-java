package ludo.game;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Random;

@DisplayName("Dice Singleton Tests")
class DiceTest {

    @BeforeEach
    void resetSingleton() {
        Dice.resetInstance();
    }

    @AfterEach
    void cleanup() {
        Dice.resetInstance();
    }

    @Test
    @DisplayName("getInstance(Random) creates the singleton")
    void getInstanceCreates() {
        Dice dice = Dice.getInstance(new Random(Dice.DEFAULT_TEST_SEED));
        assertNotNull(dice);
    }

    @Test
    @DisplayName("getInstance(Random) returns same instance on second call")
    void getInstanceReturnsSame() {
        Dice first = Dice.getInstance(new Random(Dice.DEFAULT_TEST_SEED));
        Dice second = Dice.getInstance(new Random(99));
        assertSame(first, second, "Singleton must return same instance");
    }

    @Test
    @DisplayName("getInstance() without init throws IllegalStateException")
    void getInstanceWithoutInitThrows() {
        assertThrows(IllegalStateException.class, Dice::getInstance);
    }

    @Test
    @DisplayName("resetInstance allows re-initialisation")
    void resetAllowsReinit() {
        Dice first = Dice.getInstance(new Random(Dice.DEFAULT_TEST_SEED));
        Dice.resetInstance();
        Dice second = Dice.getInstance(new Random(99));
        assertNotSame(first, second, "After reset, new instance should be created");
    }

    @Test
    @DisplayName("roll() returns values in [1, 6]")
    void rollRange() {
        Dice dice = Dice.getInstance(new Random(Dice.DEFAULT_TEST_SEED));
        for (int i = 0; i < 100; i++) {
            int value = dice.roll();
            assertTrue(value >= 1 && value <= 6,
                    "Roll value " + value + " is outside [1,6]");
        }
    }

    @Test
    @DisplayName("Same seed produces same roll sequence")
    void determinism() {
        Dice d1 = Dice.getInstance(new Random(Dice.DEFAULT_TEST_SEED));
        int[] seq1 = new int[10];
        for (int i = 0; i < 10; i++) seq1[i] = d1.roll();

        Dice.resetInstance();
        Dice d2 = Dice.getInstance(new Random(Dice.DEFAULT_TEST_SEED));
        for (int i = 0; i < 10; i++) {
            assertEquals(seq1[i], d2.roll(), "Roll " + i + " differs");
        }
    }
}
