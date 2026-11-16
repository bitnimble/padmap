package app.padmap.layout;

import java.util.List;

public record Layout(List<Control> controls) {
    public static final Layout EMPTY = new Layout(List.of());

    public Layout {
        controls = List.copyOf(controls);
    }
}
