package dev.bekagui;

import java.util.ArrayList;
import java.util.List;

/**
 * Color codes. {@code &a}, {@code &l} ... and hex {@code &#FF8800} (hex needs 1.16+).
 * <pre>Text.color("&#FF8800Orange &lbold")</pre>
 */
public final class Text {

    private static final String CODES = "0123456789abcdefklmnorABCDEFKLMNOR";

    private Text() {
    }

    public static String color(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length() && text.charAt(i + 1) == '#' && isHex(text, i + 2)) {
                out.append('§').append('x');
                for (int k = i + 2; k < i + 8; k++) {
                    out.append('§').append(Character.toLowerCase(text.charAt(k)));
                }
                i += 7;
            } else if (c == '&' && i + 1 < text.length() && CODES.indexOf(text.charAt(i + 1)) >= 0) {
                out.append('§').append(Character.toLowerCase(text.charAt(i + 1)));
                i++;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    public static List<String> color(List<String> lines) {
        List<String> out = new ArrayList<String>(lines.size());
        for (String line : lines) {
            out.add(color(line));
        }
        return out;
    }

    private static boolean isHex(String s, int from) {
        if (from + 6 > s.length()) {
            return false;
        }
        for (int i = from; i < from + 6; i++) {
            if (Character.digit(s.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }
}
