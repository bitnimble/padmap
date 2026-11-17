package app.padmap.layout

import kotlin.math.max
import kotlin.math.min

/** Which touches a mapping set wants for a pad state. Pure: the injector diffs successive results. */
object Mapper {
    const val DEADZONE = 0.2

    /** A touch for control #control, put down at the anchor and currently at (x, y); normalized coordinates. */
    data class Contact(val control: Int, val anchorX: Double, val anchorY: Double, val x: Double, val y: Double)

    /** aspect = display width / height, so stick offsets (in units of the height-relative radius) stay circular. */
    fun contacts(controls: List<Control>, pad: PadState, aspect: Double): List<Contact> =
        controls.mapIndexedNotNull { i, control ->
            when (control) {
                is Control.Tap -> if (fires(control.button, controls, pad)) {
                    Contact(i, control.x, control.y, control.x, control.y)
                } else {
                    null
                }
                is Control.Joystick -> pad.stick(control.stick).takeIf { it.magnitude() >= DEADZONE }
                    ?.let { dragged(i, control, it, aspect) }
                is Control.Aim -> if (fires(control.button, controls, pad)) {
                    var stick = pad.stick(control.stick)
                    if (stick.magnitude() < DEADZONE && control.fallback) {
                        stick = pad.stick(if (control.stick == "LS") "RS" else "LS")
                    }
                    dragged(i, control, if (stick.magnitude() >= DEADZONE) stick else PadState.Stick.CENTRE, aspect)
                } else {
                    null
                }
            }
        }

    /** A chord fires when it is held and no held superset chord shadows it, so A+B suppresses a plain A. */
    private fun fires(chord: String, controls: List<Control>, pad: PadState): Boolean {
        if (!Chord.held(chord, pad)) return false
        return controls.none { other ->
            val sup = when (other) {
                is Control.Tap -> other.button
                is Control.Aim -> other.button
                is Control.Joystick -> null
            }
            sup != null && Chord.strictlyWithin(chord, sup) && Chord.held(sup, pad)
        }
    }

    private fun dragged(index: Int, control: Control, stick: PadState.Stick, aspect: Double): Contact {
        val scale = min(1.0, 1 / max(stick.magnitude(), 1e-9)) * control.radius
        return Contact(index, control.x, control.y, control.x + stick.x * scale / aspect, control.y + stick.y * scale)
    }
}
