package app.padmap.layout;

import java.util.ArrayList;
import java.util.List;

/** Which touches a mapping set wants for a pad state. Pure: the injector diffs successive results. */
public final class Mapper {
    public static final double DEADZONE = 0.2;

    /** A touch for control #control, put down at the anchor and currently at (x, y); normalized coordinates. */
    public record Contact(int control, double anchorX, double anchorY, double x, double y) {}

    private Mapper() {}

    /** aspect = display width / height, so stick offsets (in units of the height-relative radius) stay circular. */
    public static List<Contact> contacts(List<Control> controls, PadState pad, double aspect) {
        List<Contact> contacts = new ArrayList<>();
        for (int i = 0; i < controls.size(); i++) {
            Control control = controls.get(i);
            if (control instanceof Control.Tap tap) {
                if (fires(tap.button(), controls, pad)) contacts.add(new Contact(i, tap.x(), tap.y(), tap.x(), tap.y()));
            } else if (control instanceof Control.Joystick stick) {
                PadState.Stick s = pad.stick(stick.stick());
                if (s.magnitude() >= DEADZONE) contacts.add(dragged(i, stick, s, aspect));
            } else if (control instanceof Control.Aim aim && fires(aim.button(), controls, pad)) {
                PadState.Stick s = pad.stick(aim.stick());
                if (s.magnitude() < DEADZONE && aim.fallback()) s = pad.stick(aim.stick().equals("LS") ? "RS" : "LS");
                contacts.add(dragged(i, aim, s.magnitude() >= DEADZONE ? s : PadState.Stick.CENTRE, aspect));
            }
        }
        return contacts;
    }

    /** A chord fires when it is held and no held superset chord shadows it, so A+B suppresses a plain A. */
    private static boolean fires(String chord, List<Control> controls, PadState pad) {
        if (!Chord.held(chord, pad)) return false;
        for (Control other : controls) {
            String sup = other instanceof Control.Tap tap ? tap.button()
                    : other instanceof Control.Aim aim ? aim.button() : null;
            if (sup != null && Chord.strictlyWithin(chord, sup) && Chord.held(sup, pad)) return false;
        }
        return true;
    }

    private static Contact dragged(int index, Control control, PadState.Stick stick, double aspect) {
        double scale = Math.min(1, 1 / Math.max(stick.magnitude(), 1e-9)) * control.radius();
        return new Contact(index, control.x(), control.y(),
                control.x() + stick.x() * scale / aspect, control.y() + stick.y() * scale);
    }
}
