package app.padmap.layout

import app.padmap.layout.Mapper.Contact
import app.padmap.layout.PadState.Stick
import kotlin.test.Test
import kotlin.test.assertEquals

class MapperTest {
    private val controls = listOf(
        Control.Joystick("LS", 0.2, 0.7, 0.1),
        Control.Tap("A", 0.9, 0.8, 0.05),
        Control.Aim("RB", "RS", false, 0.8, 0.6, 0.2),
        Control.Aim("LB", "RS", true, 0.6, 0.4, 0.1),
    )

    private fun contacts(buttons: Set<String>, left: Stick, right: Stick) =
        Mapper.contacts(controls, PadState(buttons, left, right), 2.0).map(::rounded)

    private fun contacts(layout: List<Control>, buttons: Set<String>) =
        Mapper.contacts(layout, PadState(buttons, Stick.CENTRE, Stick.CENTRE), 2.0).map(::rounded)

    private fun rounded(c: Contact) = Contact(c.control, r(c.anchorX), r(c.anchorY), r(c.x), r(c.y))

    private fun r(value: Double) = Math.round(value * 1e9) / 1e9

    @Test
    fun `sticks and taps`() {
        assertEquals(listOf(), contacts(setOf(), Stick.CENTRE, Stick.CENTRE), "neutral pad touches nothing")
        assertEquals(listOf(), contacts(setOf(), Stick(0.1, 0.1), Stick.CENTRE), "stick inside deadzone is ignored")
        assertEquals(listOf(Contact(0, 0.2, 0.7, 0.25, 0.7)), contacts(setOf(), Stick(1.0, 0.0), Stick.CENTRE),
            "full right stick drags joystick one radius right")
        assertEquals(listOf(Contact(0, 0.2, 0.7, 0.2, 0.6)), contacts(setOf(), Stick(0.0, -2.0), Stick.CENTRE),
            "over-range stick is clamped to the radius")
        assertEquals(listOf(Contact(1, 0.9, 0.8, 0.9, 0.8)), contacts(setOf("A"), Stick.CENTRE, Stick.CENTRE),
            "held button taps in place")
    }

    @Test
    fun aims() {
        assertEquals(listOf(Contact(2, 0.8, 0.6, 0.8, 0.6)), contacts(setOf("RB"), Stick.CENTRE, Stick(0.1, 0.0)),
            "aim without stick casts in place")
        assertEquals(
            listOf(Contact(1, 0.9, 0.8, 0.9, 0.8), Contact(2, 0.8, 0.6, 0.8, 0.8)),
            contacts(setOf("RB", "A"), Stick.CENTRE, Stick(0.0, 1.0)),
            "a tap touches while an aim stays held and dragged",
        )
        assertEquals(
            listOf(Contact(0, 0.2, 0.7, 0.25, 0.7), Contact(1, 0.9, 0.8, 0.9, 0.8), Contact(2, 0.8, 0.6, 0.8, 0.8)),
            contacts(setOf("RB", "A"), Stick(1.0, 0.0), Stick(0.0, 1.0)),
            "the movement stick, a tap and an aim all touch at once",
        )
        assertEquals(listOf(), contacts(setOf(), Stick.CENTRE, Stick(1.0, 0.0)), "aim stick alone touches nothing")
        assertEquals(
            listOf(Contact(0, 0.2, 0.7, 0.25, 0.7), Contact(2, 0.8, 0.6, 0.8, 0.6)),
            contacts(setOf("RB"), Stick(1.0, 0.0), Stick.CENTRE),
            "aim without fallback ignores the other stick",
        )
        assertEquals(
            listOf(Contact(0, 0.2, 0.7, 0.2, 0.8), Contact(3, 0.6, 0.4, 0.6, 0.5)),
            contacts(setOf("LB"), Stick(0.0, 1.0), Stick.CENTRE),
            "fallback aim follows the other stick while its own is centred",
        )
        assertEquals(
            listOf(Contact(0, 0.2, 0.7, 0.2, 0.8), Contact(3, 0.6, 0.4, 0.55, 0.4)),
            contacts(setOf("LB"), Stick(0.0, 1.0), Stick(-1.0, 0.0)),
            "fallback aim prefers its own stick when moved",
        )
    }

    @Test
    fun chords() {
        val layout = listOf(
            Control.Tap("LT", 0.1, 0.1, 0.05),
            Control.Tap("LT+RB", 0.2, 0.2, 0.05),
            Control.Tap("RB", 0.3, 0.3, 0.05),
        )
        assertEquals(listOf(Contact(0, 0.1, 0.1, 0.1, 0.1)), contacts(layout, setOf("LT")),
            "one held button fires only its own tap")
        assertEquals(listOf(Contact(1, 0.2, 0.2, 0.2, 0.2)), contacts(layout, setOf("LT", "RB")),
            "a held chord fires and shadows both single-button taps it covers")
        assertEquals(listOf(Contact(2, 0.3, 0.3, 0.3, 0.3)), contacts(layout, setOf("RB", "X")),
            "an unrelated extra button doesn't shadow a single tap")
        assertEquals(listOf(Contact(0, 0.4, 0.4, 0.4, 0.4)),
            contacts(listOf(Control.Tap("RB+LT", 0.4, 0.4, 0.05)), setOf("LT", "RB")),
            "chord button order doesn't matter")
    }
}
