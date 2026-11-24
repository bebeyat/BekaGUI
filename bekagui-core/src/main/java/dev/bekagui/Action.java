package dev.bekagui;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * One line of a YAML {@code actions} list: {@code "type: value"} or just {@code "type"}.
 * <pre>
 * message: &aHello {player}      command: spawn          console: give {player} diamond 1
 * open: shop                     back                    close
 * refresh                        title: &8New title      sound: entity.experience_orb.pickup
 * </pre>
 */
final class Action {

    static final Set<String> TYPES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "message", "command", "console", "open", "back", "close", "refresh", "title", "sound")));
    private static final Set<String> NEEDS_VALUE = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "message", "command", "console", "open", "title", "sound")));

    final String type;
    final String value;

    private Action(String type, String value) {
        this.type = type;
        this.value = value;
    }

    static Action parse(String line) {
        if (line == null || line.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty action");
        }
        String text = line.trim();
        int colon = text.indexOf(':');
        String type = (colon < 0 ? text : text.substring(0, colon)).trim().toLowerCase(Locale.ROOT);
        String value = colon < 0 ? "" : text.substring(colon + 1).trim();
        if (!TYPES.contains(type)) {
            throw new IllegalArgumentException("Unknown action '" + type + "'. Use one of " + TYPES);
        }
        if (NEEDS_VALUE.contains(type) && value.isEmpty()) {
            throw new IllegalArgumentException("Action '" + type + "' needs a value, e.g. \"" + type + ": ...\"");
        }
        if ((type.equals("command") || type.equals("console")) && value.startsWith("/")) {
            value = value.substring(1);
        }
        return new Action(type, value);
    }
}
