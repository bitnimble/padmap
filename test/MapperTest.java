import app.padmap.layout.Control;
import app.padmap.layout.Mapper;
import app.padmap.layout.Mapper.Contact;
import app.padmap.layout.PadState;
import app.padmap.layout.PadState.Stick;
import java.util.List;
import java.util.Set;

public final class MapperTest {
    private static final List<Control> CONTROLS = List.of(
            new Control.Joystick("LS", 0.2, 0.7, 0.1),
            new Control.Tap("A", 0.9, 0.8, 0.05),
            new Control.Aim("RB", "RS", false, 0.8, 0.6, 0.2),
            new Control.Aim("LB", "RS", true, 0.6, 0.4, 0.1));

    public static void main(String[] args) {
        check(contacts(Set.of(), Stick.CENTRE, Stick.CENTRE), List.of(), "neutral pad touches nothing");
        check(contacts(Set.of(), new Stick(0.1, 0.1), Stick.CENTRE), List.of(), "stick inside deadzone is ignored");
        check(contacts(Set.of(), new Stick(1, 0), Stick.CENTRE),
                List.of(new Contact(0, 0.2, 0.7, 0.25, 0.7)), "full right stick drags joystick one radius right");
        check(contacts(Set.of(), new Stick(0, -2), Stick.CENTRE),
                List.of(new Contact(0, 0.2, 0.7, 0.2, 0.6)), "over-range stick is clamped to the radius");
        check(contacts(Set.of("A"), Stick.CENTRE, Stick.CENTRE),
                List.of(new Contact(1, 0.9, 0.8, 0.9, 0.8)), "held button taps in place");
        check(contacts(Set.of("RB"), Stick.CENTRE, new Stick(0.1, 0)),
                List.of(new Contact(2, 0.8, 0.6, 0.8, 0.6)), "aim without stick casts in place");
        check(contacts(Set.of("RB", "A"), Stick.CENTRE, new Stick(0, 1)),
                List.of(new Contact(2, 0.8, 0.6, 0.8, 0.8)),
                "a tap held during an aim doesn't touch, so it can't interrupt the aim");
        check(contacts(Set.of("RB", "A"), new Stick(1, 0), new Stick(0, 1)),
                List.of(new Contact(0, 0.2, 0.7, 0.25, 0.7), new Contact(2, 0.8, 0.6, 0.8, 0.8)),
                "the movement stick keeps working during an aim");
        check(contacts(Set.of(), Stick.CENTRE, new Stick(1, 0)), List.of(), "aim stick alone touches nothing");
        check(contacts(Set.of("RB"), new Stick(1, 0), Stick.CENTRE),
                List.of(new Contact(0, 0.2, 0.7, 0.25, 0.7), new Contact(2, 0.8, 0.6, 0.8, 0.6)),
                "aim without fallback ignores the other stick");
        check(contacts(Set.of("LB"), new Stick(0, 1), Stick.CENTRE),
                List.of(new Contact(0, 0.2, 0.7, 0.2, 0.8), new Contact(3, 0.6, 0.4, 0.6, 0.5)),
                "fallback aim follows the other stick while its own is centred");
        check(contacts(Set.of("LB"), new Stick(0, 1), new Stick(-1, 0)),
                List.of(new Contact(0, 0.2, 0.7, 0.2, 0.8), new Contact(3, 0.6, 0.4, 0.55, 0.4)),
                "fallback aim prefers its own stick when moved");
        chords();
        System.out.println("MapperTest: all passed");
    }

    private static void chords() {
        List<Control> layout = List.of(
                new Control.Tap("LT", 0.1, 0.1, 0.05),
                new Control.Tap("LT+RB", 0.2, 0.2, 0.05),
                new Control.Tap("RB", 0.3, 0.3, 0.05));
        check(contacts(layout, Set.of("LT")),
                List.of(new Contact(0, 0.1, 0.1, 0.1, 0.1)), "one held button fires only its own tap");
        check(contacts(layout, Set.of("LT", "RB")),
                List.of(new Contact(1, 0.2, 0.2, 0.2, 0.2)),
                "a held chord fires and shadows both single-button taps it covers");
        check(contacts(layout, Set.of("RB", "X")),
                List.of(new Contact(2, 0.3, 0.3, 0.3, 0.3)), "an unrelated extra button doesn't shadow a single tap");
        check(contacts(List.of(new Control.Tap("RB+LT", 0.4, 0.4, 0.05)), Set.of("LT", "RB")),
                List.of(new Contact(0, 0.4, 0.4, 0.4, 0.4)), "chord button order doesn't matter");
    }

    private static List<Contact> contacts(Set<String> buttons, Stick left, Stick right) {
        return Mapper.contacts(CONTROLS, new PadState(buttons, left, right), 2.0);
    }

    private static List<Contact> contacts(List<Control> layout, Set<String> buttons) {
        return Mapper.contacts(layout, new PadState(buttons, Stick.CENTRE, Stick.CENTRE), 2.0);
    }

    private static void check(List<Contact> actual, List<Contact> expected, String what) {
        boolean same = actual.size() == expected.size();
        for (int i = 0; same && i < actual.size(); i++) {
            Contact a = actual.get(i), e = expected.get(i);
            same = a.control() == e.control() && close(a.anchorX(), e.anchorX()) && close(a.anchorY(), e.anchorY())
                    && close(a.x(), e.x()) && close(a.y(), e.y());
        }
        if (!same) throw new AssertionError(what + ": expected " + expected + ", got " + actual);
    }

    private static boolean close(double a, double b) {
        return Math.abs(a - b) < 1e-9;
    }
}
