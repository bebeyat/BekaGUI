package dev.bekagui;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogicTest {

    // ---------------------------------------------------------------- Layout

    @Test
    void layoutMapsKeysToSlotsInReadingOrder() {
        Layout l = Layout.parse(
                "#########",
                "#.A...A.#",
                "#########");
        assertEquals(3, l.rows());
        assertArrayEquals(new int[] {11, 15}, l.slots('A'));
        assertEquals(20, l.slots('#').length);
        assertEquals(0, l.slots('Z').length);
    }

    @Test
    void layoutIgnoresSpaces() {
        assertArrayEquals(new int[] {4}, Layout.parse(". . . . X . . . .").slots('X'));
    }

    @Test
    void layoutRejectsWrongWidthWithRowNumber() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Layout.parse("#########", "####"));
        assertTrue(e.getMessage().contains("row 2"), e.getMessage());
    }

    @Test
    void layoutRejectsTooManyRows() {
        assertThrows(IllegalArgumentException.class, () -> Layout.parse("a", "a", "a", "a", "a", "a", "a"));
    }

    // ---------------------------------------------------------------- Pages

    @Test
    void pageCountNeverZero() {
        assertEquals(1, Pages.count(0, 7));
        assertEquals(1, Pages.count(7, 7));
        assertEquals(2, Pages.count(8, 7));
    }

    @Test
    void sliceClampsPage() {
        List<Integer> all = Arrays.asList(1, 2, 3, 4, 5);
        assertEquals(Arrays.asList(3, 4), Pages.slice(all, 1, 2));
        assertEquals(Arrays.asList(5), Pages.slice(all, 99, 2));
        assertEquals(Arrays.asList(1, 2), Pages.slice(all, -3, 2));
    }

    // ---------------------------------------------------------------- Text

    @Test
    void colorTranslatesLegacyAndHex() {
        assertEquals("§aHi §lB", Text.color("&aHi &lB"));
        assertEquals("§x§f§f§8§8§0§0X", Text.color("&#FF8800X"));
    }

    @Test
    void colorLeavesPlainAmpersands() {
        assertEquals("Tom & Jerry &#12", Text.color("Tom & Jerry &#12"));
        assertEquals("", Text.color((String) null));
    }

    // ---------------------------------------------------------------- Action

    @Test
    void actionParsesTypeAndValue() {
        Action a = Action.parse("console: /give {player} diamond 1");
        assertEquals("console", a.type);
        assertEquals("give {player} diamond 1", a.value);
        assertEquals("close", Action.parse("  CLOSE ").type);
        assertEquals("&aHi: there", Action.parse("message: &aHi: there").value);
    }

    @Test
    void actionRejectsUnknownAndMissingValue() {
        assertThrows(IllegalArgumentException.class, () -> Action.parse("teleport: spawn"));
        assertThrows(IllegalArgumentException.class, () -> Action.parse("open"));
    }
}
