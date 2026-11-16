package app.padmap.layout;

import java.util.Set;

/** Buttons held, by name ("A", "RB", "UP", …), and the two sticks, each axis in [-1, 1] with +y down. */
public record PadState(Set<String> buttons, Stick left, Stick right) {
    public static final PadState NEUTRAL = new PadState(Set.of(), Stick.CENTRE, Stick.CENTRE);

    public PadState {
        buttons = Set.copyOf(buttons);
    }

    public record Stick(double x, double y) {
        public static final Stick CENTRE = new Stick(0, 0);

        public double magnitude() {
            return Math.hypot(x, y);
        }
    }

    public Stick stick(String name) {
        return switch (name) {
            case "LS" -> left;
            case "RS" -> right;
            default -> Stick.CENTRE;
        };
    }

    public boolean pressed(String button) {
        return buttons.contains(button);
    }
}
