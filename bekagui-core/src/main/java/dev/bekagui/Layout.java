package dev.bekagui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A menu drawn with characters, one string per row, 9 characters each. Spaces are ignored,
 * so {@code "# # # # # # # # #"} and {@code "#########"} are the same row.
 * Every character is a key you bind with {@code Menu.Builder#button}. {@code '.'} means empty.
 */
public final class Layout {

    private final int rows;
    private final Map<Character, int[]> slots;

    private Layout(int rows, Map<Character, int[]> slots) {
        this.rows = rows;
        this.slots = slots;
    }

    public static Layout parse(String... lines) {
        if (lines == null || lines.length < 1 || lines.length > 6) {
            throw new IllegalArgumentException("A layout needs 1 to 6 rows, got " + (lines == null ? 0 : lines.length));
        }
        Map<Character, List<Integer>> found = new LinkedHashMap<Character, List<Integer>>();
        for (int row = 0; row < lines.length; row++) {
            String line = lines[row] == null ? "" : lines[row].replace(" ", "");
            if (line.length() != 9) {
                throw new IllegalArgumentException("Layout row " + (row + 1) + " must have 9 characters (spaces ignored), got "
                        + line.length() + ": \"" + lines[row] + "\"");
            }
            for (int col = 0; col < 9; col++) {
                char key = line.charAt(col);
                if (key == '.') {
                    continue;
                }
                List<Integer> list = found.get(key);
                if (list == null) {
                    list = new ArrayList<Integer>();
                    found.put(key, list);
                }
                list.add(row * 9 + col);
            }
        }
        Map<Character, int[]> slots = new LinkedHashMap<Character, int[]>();
        for (Map.Entry<Character, List<Integer>> e : found.entrySet()) {
            int[] arr = new int[e.getValue().size()];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = e.getValue().get(i);
            }
            slots.put(e.getKey(), arr);
        }
        return new Layout(lines.length, Collections.unmodifiableMap(slots));
    }

    public int rows() {
        return rows;
    }

    /** Slots of a key in reading order (left to right, top to bottom). Empty array if the key is not in the layout. */
    public int[] slots(char key) {
        int[] s = slots.get(key);
        return s == null ? new int[0] : s.clone();
    }

    public Iterable<Character> keys() {
        return slots.keySet();
    }
}
