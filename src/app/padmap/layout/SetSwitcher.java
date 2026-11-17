package app.padmap.layout;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Which mapping set is active. Holding a set's chord switches to it, or back to the first set if it's already
 * active; the chord's buttons then map nothing until each is released.
 */
public final class SetSwitcher {
    private int active;
    private final Set<String> latched = new HashSet<>();

    public int active() {
        return active;
    }

    public void reset() {
        active = 0;
    }

    /** Switches if a set chord is newly held; true if the active set changed. */
    public boolean update(Layout layout, PadState pad) {
        latched.retainAll(pad.buttons());
        List<Layout.MappingSet> sets = layout.sets();
        int target = -1;
        for (int i = 0; i < sets.size(); i++) {
            String chord = sets.get(i).chord();
            if (chord == null || !Chord.held(chord, pad) || !Collections.disjoint(Chord.parts(chord), latched)) continue;
            if (target < 0 || Chord.strictlyWithin(sets.get(target).chord(), chord)) target = i;
        }
        if (target < 0) return false;
        int before = active;
        active = target == active ? 0 : target;
        latched.addAll(Chord.parts(sets.get(target).chord()));
        return active != before;
    }

    /** The pad without the buttons of the chord that last switched, until each is released. */
    public PadState strip(PadState pad) {
        if (latched.isEmpty()) return pad;
        Set<String> buttons = new HashSet<>(pad.buttons());
        buttons.removeAll(latched);
        return new PadState(buttons, pad.left(), pad.right());
    }
}
