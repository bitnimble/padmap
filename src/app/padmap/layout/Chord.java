package app.padmap.layout;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** One or more pad buttons that must be held together, written "LT+RB". Parts are kept in a canonical order. */
public final class Chord {
    private Chord() {}

    /** The canonical "A+B" form of the held buttons, or "A" for one; empty collection yields "". */
    public static String of(Collection<String> buttons) {
        List<String> sorted = new ArrayList<>(buttons);
        sorted.sort(Comparator.comparingInt(Button::rank).thenComparing(Comparator.naturalOrder()));
        return String.join("+", sorted);
    }

    public static List<String> parts(String spec) {
        return List.of(spec.split("\\+", -1));
    }

    public static boolean held(String spec, PadState pad) {
        for (String part : parts(spec)) {
            if (!pad.pressed(part)) return false;
        }
        return true;
    }

    /** True if every button of sub is also in sup and sup has more, i.e. sup shadows sub. */
    public static boolean strictlyWithin(String sub, String sup) {
        List<String> subParts = parts(sub), supParts = parts(sup);
        if (supParts.size() <= subParts.size()) return false;
        return supParts.containsAll(subParts);
    }
}
