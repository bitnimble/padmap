package app.padmap.ui

import app.padmap.layout.Button
import kotlin.test.Test
import kotlin.test.assertEquals

class ButtonStyleTest {
    @Test
    fun `each style names the buttons the way its controllers print them`() {
        assertEquals("A", ButtonStyle.XBOX.label(Button.A), "Xbox keeps the binding names")
        assertEquals("B", ButtonStyle.NINTENDO.label(Button.A), "Nintendo south is B")
        assertEquals("X", ButtonStyle.NINTENDO.label(Button.Y), "Nintendo north is X")
        assertEquals("ZL", ButtonStyle.NINTENDO.label(Button.LT), "Nintendo left trigger is ZL")
        assertEquals("✕", ButtonStyle.PLAYSTATION.label(Button.A), "PlayStation south is cross")
        assertEquals("□", ButtonStyle.PLAYSTATION.label(Button.X), "PlayStation west is square")
        assertEquals("R1", ButtonStyle.PLAYSTATION.label(Button.RB), "PlayStation right bumper is R1")
        assertEquals("UP", ButtonStyle.PLAYSTATION.label(Button.UP), "unstyled buttons keep their names")
        assertEquals("Guide", ButtonStyle.XBOX.label(Button.HOME), "Xbox home is Guide")
        assertEquals("PS", ButtonStyle.PLAYSTATION.label(Button.HOME), "PlayStation home is PS")
        assertEquals("HOME", ButtonStyle.NINTENDO.label(Button.HOME), "Nintendo home is the house")
        assertEquals("Menu", ButtonStyle.XBOX.label(Button.START), "Xbox start is Menu")
        assertEquals("−", ButtonStyle.NINTENDO.label(Button.SELECT), "Nintendo select is minus")
        assertEquals("Options", ButtonStyle.PLAYSTATION.label(Button.START), "PlayStation start is Options")
        assertEquals("A", ButtonStyle.STEAM.label(Button.A), "Steam south is A")
        assertEquals("L2", ButtonStyle.STEAM.label(Button.LT), "Steam left trigger is L2")
        assertEquals("Steam", ButtonStyle.STEAM.label(Button.HOME), "Steam home is the Steam button")
        assertEquals("✕", ButtonStyle.STEAM_PLAYSTATION.label(Button.A), "Steam + PS south is cross")
        assertEquals("Menu", ButtonStyle.STEAM_PLAYSTATION.label(Button.START), "Steam + PS start is Menu")
        assertEquals("Steam", ButtonStyle.STEAM_PLAYSTATION.label(Button.HOME), "Steam + PS home is Steam")
    }

    @Test
    fun `every style colours every face button`() {
        assertEquals(
            ButtonStyle.PLAYSTATION.faceColour(Button.Y),
            ButtonStyle.STEAM_PLAYSTATION.faceColour(Button.Y),
            "Steam + PS face buttons are PlayStation's colours",
        )
        for (style in ButtonStyle.entries) {
            for (face in listOf(Button.A, Button.B, Button.X, Button.Y)) style.faceColour(face)
        }
    }
}
