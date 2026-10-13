package ludo.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MysteryEffect Enum Tests")
class MysteryEffectTest {

    @Test
    @DisplayName("All four effects exist")
    void allEffectsExist() {
        assertEquals(4, MysteryEffect.values().length);
    }

    @Test
    @DisplayName("NONE is the null-object default (ordinal 0)")
    void noneIsDefault() {
        assertEquals(0, MysteryEffect.NONE.ordinal());
    }

    @Test
    @DisplayName("Effect names match spec terminology")
    void effectNames() {
        assertNotNull(MysteryEffect.valueOf("NONE"));
        assertNotNull(MysteryEffect.valueOf("ENERGIZED"));
        assertNotNull(MysteryEffect.valueOf("SICK"));
        assertNotNull(MysteryEffect.valueOf("BRIEFING"));
    }
}
