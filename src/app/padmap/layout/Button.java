package app.padmap.layout;

import java.util.LinkedHashMap;
import java.util.Map;

/** The pad's button vocabulary: canonical name, evdev key code, and chord-sort order (enum order). */
public enum Button {
    A(0x130), B(0x131), X(0x133), Y(0x134),
    LB(0x136), RB(0x137), LT(0x138), RT(0x139),
    L3(0x13d), R3(0x13e),
    UP(0x220), DOWN(0x221), LEFT(0x222), RIGHT(0x223),
    SELECT(0x13a), START(0x13b), HOME(0x13c);

    public final int code;

    Button(int code) {
        this.code = code;
    }

    /** evdev key code to button name, for the raw gamepad reader. */
    public static Map<Integer, String> byCode() {
        Map<Integer, String> map = new LinkedHashMap<>();
        for (Button button : values()) map.put(button.code, button.name());
        return map;
    }

    /** Regex alternation of every name, e.g. "A|B|X". */
    public static String alternation() {
        StringBuilder names = new StringBuilder();
        for (Button button : values()) {
            if (names.length() > 0) names.append('|');
            names.append(button.name());
        }
        return names.toString();
    }

    /** Chord-sort rank of a name; unknown names (KEY_*) sort last. */
    public static int rank(String name) {
        try {
            return valueOf(name).ordinal();
        } catch (IllegalArgumentException e) {
            return values().length;
        }
    }
}
