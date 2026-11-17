import app.padmap.layout.Control;
import app.padmap.layout.Layout;
import app.padmap.layout.Layout.MappingSet;
import app.padmap.layout.PadState;
import app.padmap.layout.PadState.Stick;
import app.padmap.layout.SetSwitcher;
import java.util.List;
import java.util.Set;

public final class SetSwitcherTest {
    private static final List<Control> TAP = List.of(new Control.Tap("A", 0.5, 0.5, 0.05));

    public static void main(String[] args) {
        Layout layout = new Layout(List.of(new MappingSet(null, TAP), new MappingSet("LB+RB", TAP),
                new MappingSet("LB+RB+Y", TAP)));
        SetSwitcher switcher = new SetSwitcher();
        check(step(switcher, layout, "A"), false, 0, Set.of("A"), "no chord held: first set, pad untouched");
        check(step(switcher, layout, "LB"), false, 0, Set.of("LB"), "part of a chord switches nothing");
        check(step(switcher, layout, "LB", "RB", "A"), true, 1, Set.of("A"), "a held chord switches; its buttons map nothing");
        check(step(switcher, layout, "LB", "RB", "A"), false, 1, Set.of("A"), "a chord still held doesn't switch again");
        check(step(switcher, layout, "LB"), false, 1, Set.of(), "a chord button stays held back until released");
        check(step(switcher, layout, "LB", "RB"), false, 1, Set.of("RB"),
                "with LB still held back, re-pressing RB maps RB and doesn't switch");
        check(step(switcher, layout), false, 1, Set.of(), "releasing everything clears the latch");
        check(step(switcher, layout, "RB"), false, 1, Set.of("RB"), "a released chord button maps again");
        check(step(switcher, layout, "LB", "RB"), true, 0, Set.of(), "the active set's chord returns to the first set");
        step(switcher, layout);
        check(step(switcher, layout, "LB", "RB", "Y"), true, 2, Set.of(), "the larger of two held chords wins");
        switcher.reset();
        check(step(switcher, layout, "LB", "RB", "Y"), false, 0, Set.of(),
                "reset returns to the first set; a chord still held stays latched");
        step(switcher, layout);
        check(step(switcher, layout, "LB", "RB", "Y"), true, 2, Set.of(), "after release the chord switches again");

        Layout chordedFirst = new Layout(List.of(new MappingSet("X+Y", TAP), new MappingSet("LB+RB", TAP)));
        SetSwitcher first = new SetSwitcher();
        check(step(first, chordedFirst, "X", "Y"), false, 0, Set.of(), "the first set's own chord keeps it active");
        System.out.println("SetSwitcherTest: all passed");
    }

    private record Step(boolean switched, int active, Set<String> mapped) {}

    private static Step step(SetSwitcher switcher, Layout layout, String... held) {
        PadState pad = new PadState(Set.of(held), Stick.CENTRE, Stick.CENTRE);
        boolean switched = switcher.update(layout, pad);
        return new Step(switched, switcher.active(), switcher.strip(pad).buttons());
    }

    private static void check(Step actual, boolean switched, int active, Set<String> mapped, String what) {
        Step expected = new Step(switched, active, mapped);
        if (!actual.equals(expected)) throw new AssertionError(what + ": expected " + expected + ", got " + actual);
    }
}
