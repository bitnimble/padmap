package app.padmap.ui;

import static app.padmap.layout.Button.A;
import static app.padmap.layout.Button.B;
import static app.padmap.layout.Button.HOME;
import static app.padmap.layout.Button.LB;
import static app.padmap.layout.Button.LT;
import static app.padmap.layout.Button.RB;
import static app.padmap.layout.Button.RT;
import static app.padmap.layout.Button.X;
import static app.padmap.layout.Button.Y;

import app.padmap.layout.Button;
import java.util.Map;

/** How the editor names and colours the pad's buttons. Bindings keep the Xbox names whatever the style. */
enum ButtonStyle {
    XBOX("Xbox", Map.of(HOME, "Guide"),
            Map.of(A, 0xFF3FB950, B, 0xFFE5484D, X, 0xFF3B82F6, Y, 0xFFF2C94C)),
    NINTENDO("Nintendo", Map.of(A, "B", B, "A", X, "Y", Y, "X", LB, "L", RB, "R", LT, "ZL", RT, "ZR"),
            Map.of(A, 0xFFF2C94C, B, 0xFFE5484D, X, 0xFF3FB950, Y, 0xFF3B82F6)),
    PLAYSTATION("PlayStation", Map.of(A, "✕", B, "○", X, "□", Y, "△", LB, "L1", RB, "R1", LT, "L2", RT, "R2",
            HOME, "PS"),
            Map.of(A, 0xFF7CB2E8, B, 0xFFFF6B6B, X, 0xFFE58FD0, Y, 0xFF40C9A2));

    final String title;
    private final Map<Button, String> labels;
    private final Map<Button, Integer> faceColours;

    ButtonStyle(String title, Map<Button, String> labels, Map<Button, Integer> faceColours) {
        this.title = title;
        this.labels = labels;
        this.faceColours = faceColours;
    }

    /** The button's label in this style; PlayStation face symbols are ✕ ○ □ △. */
    String label(Button button) {
        return labels.getOrDefault(button, button.name());
    }

    int faceColour(Button button) {
        return faceColours.get(button);
    }
}
