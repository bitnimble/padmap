package app.padmap.ui

import app.padmap.layout.Button
import app.padmap.layout.Button.A
import app.padmap.layout.Button.B
import app.padmap.layout.Button.HOME
import app.padmap.layout.Button.LB
import app.padmap.layout.Button.LT
import app.padmap.layout.Button.RB
import app.padmap.layout.Button.RT
import app.padmap.layout.Button.SELECT
import app.padmap.layout.Button.START
import app.padmap.layout.Button.X
import app.padmap.layout.Button.Y

/** How the editor names and colours the pad's buttons. Bindings keep the Xbox names whatever the style. */
enum class ButtonStyle(val title: String, private val labels: Map<Button, String>, private val faceColours: Map<Button, Int>) {
    XBOX(
        "Xbox",
        mapOf(SELECT to "View", START to "Menu", HOME to "Guide"),
        mapOf(A to 0xFF3FB950.toInt(), B to 0xFFE5484D.toInt(), X to 0xFF3B82F6.toInt(), Y to 0xFFF2C94C.toInt()),
    ),
    NINTENDO(
        "Nintendo",
        mapOf(A to "B", B to "A", X to "Y", Y to "X", LB to "L", RB to "R", LT to "ZL", RT to "ZR", SELECT to "−",
            START to "+"),
        mapOf(A to 0xFFF2C94C.toInt(), B to 0xFFE5484D.toInt(), X to 0xFF3FB950.toInt(), Y to 0xFF3B82F6.toInt()),
    ),
    PLAYSTATION(
        "PlayStation",
        mapOf(A to "✕", B to "○", X to "□", Y to "△", LB to "L1", RB to "R1", LT to "L2", RT to "R2",
            SELECT to "Create", START to "Options", HOME to "PS"),
        PLAYSTATION_FACES,
    ),
    STEAM(
        "Steam",
        mapOf(LB to "L1", RB to "R1", LT to "L2", RT to "R2", SELECT to "View", START to "Menu", HOME to "Steam"),
        mapOf(A to 0xFFC9D1D9.toInt(), B to 0xFFC9D1D9.toInt(), X to 0xFFC9D1D9.toInt(), Y to 0xFFC9D1D9.toInt()),
    ),
    STEAM_PLAYSTATION(
        "Steam + PS",
        mapOf(A to "✕", B to "○", X to "□", Y to "△", LB to "L1", RB to "R1", LT to "L2", RT to "R2",
            SELECT to "View", START to "Menu", HOME to "Steam"),
        PLAYSTATION_FACES,
    );

    /** The button's label in this style; PlayStation face symbols are ✕ ○ □ △. */
    fun label(button: Button): String = labels[button] ?: button.name

    fun faceColour(button: Button): Int = faceColours.getValue(button)
}

private val PLAYSTATION_FACES =
    mapOf(A to 0xFF7CB2E8.toInt(), B to 0xFFFF6B6B.toInt(), X to 0xFFE58FD0.toInt(), Y to 0xFF40C9A2.toInt())
