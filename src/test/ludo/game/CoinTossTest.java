package ludo.game;

import ludo.enums.Direction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Random;

@DisplayName("CoinToss Tests")
class CoinTossTest {

    @Test
    @DisplayName("Heads (true) returns CLOCKWISE")
    void headsIsClockwise() {
        Random mockRandom = mock(Random.class);
        when(mockRandom.nextBoolean()).thenReturn(true);

        CoinToss coinToss = new CoinToss(mockRandom);
        assertEquals(Direction.CLOCKWISE, coinToss.toss());
    }

    @Test
    @DisplayName("Tails (false) returns COUNTERCLOCKWISE")
    void tailsIsCounterClockwise() {
        Random mockRandom = mock(Random.class);
        when(mockRandom.nextBoolean()).thenReturn(false);

        CoinToss coinToss = new CoinToss(mockRandom);
        assertEquals(Direction.COUNTERCLOCKWISE, coinToss.toss());
    }

    @Test
    @DisplayName("Multiple tosses use Random.nextBoolean each time")
    void multipleTossesCallRandom() {
        Random mockRandom = mock(Random.class);
        when(mockRandom.nextBoolean()).thenReturn(true, false, true);

        CoinToss coinToss = new CoinToss(mockRandom);
        assertEquals(Direction.CLOCKWISE, coinToss.toss());
        assertEquals(Direction.COUNTERCLOCKWISE, coinToss.toss());
        assertEquals(Direction.CLOCKWISE, coinToss.toss());

        verify(mockRandom, times(3)).nextBoolean();
    }
}
