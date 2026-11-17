package app.padmap.layout

import kotlin.math.hypot

/** Buttons held, by name ("A", "RB", "UP", …), and the two sticks, each axis in [-1, 1] with +y down. */
class PadState(buttons: Set<String>, val left: Stick, val right: Stick) {
    val buttons: Set<String> = buttons.toSet()

    data class Stick(val x: Double, val y: Double) {
        fun magnitude(): Double = hypot(x, y)

        companion object {
            val CENTRE = Stick(0.0, 0.0)
        }
    }

    fun stick(name: String): Stick = when (name) {
        "LS" -> left
        "RS" -> right
        else -> Stick.CENTRE
    }

    fun pressed(button: String): Boolean = button in buttons

    companion object {
        val NEUTRAL = PadState(emptySet(), Stick.CENTRE, Stick.CENTRE)
    }
}
