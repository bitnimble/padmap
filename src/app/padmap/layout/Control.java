package app.padmap.layout;

/** A touch control. x, y are fractions of the display's width and height; radius is a fraction of its height. */
public sealed interface Control permits Control.Tap, Control.Joystick, Control.Aim {
    double x();

    double y();

    double radius();

    Control moved(double x, double y);

    Control resized(double radius);

    /** Touches (x, y) while the button is held. */
    record Tap(String button, double x, double y, double radius) implements Control {
        public Control moved(double x, double y) {
            return new Tap(button, x, y, radius);
        }

        public Control resized(double radius) {
            return new Tap(button, x, y, radius);
        }
    }

    /** Touches down at the centre when the stick leaves its deadzone and follows the stick within radius. */
    record Joystick(String stick, double x, double y, double radius) implements Control {
        public Control moved(double x, double y) {
            return new Joystick(stick, x, y, radius);
        }

        public Control resized(double radius) {
            return new Joystick(stick, x, y, radius);
        }
    }

    /**
     * MOBA skill: touches down at (x, y) while the button is held, drags by the stick within radius, lifts to cast.
     * With fallback, the other stick aims while this one is centred.
     */
    record Aim(String button, String stick, boolean fallback, double x, double y, double radius) implements Control {
        public Control moved(double x, double y) {
            return new Aim(button, stick, fallback, x, y, radius);
        }

        public Control resized(double radius) {
            return new Aim(button, stick, fallback, x, y, radius);
        }
    }
}
