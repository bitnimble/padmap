package app.padmap.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.json.JSONException
import org.json.JSONObject

class LayoutJsonTest {
    private val layout = Layout(listOf(
        Layout.MappingSet(null, listOf(
            Control.Joystick("LS", 0.16, 0.75, 0.12),
            Control.Tap("LT+RB", 0.7, 0.9, 0.05),
            Control.Aim("RB", "RS", true, 0.75, 0.7, 0.15),
        )),
        Layout.MappingSet("LB+RB+Y", listOf(Control.Tap("A", 0.5, 0.9, 0.05))),
    ))

    private fun parse(json: String) = LayoutJson.parse(JSONObject(json))

    @Test
    fun `a layout survives a round trip`() {
        assertEquals(layout, LayoutJson.parse(JSONObject(LayoutJson.toJson(layout).toString())))
    }

    @Test
    fun `a set without a chord is written without one`() {
        val json = LayoutJson.toJson(layout).getJSONArray("sets")
        assertEquals(false, json.getJSONObject(0).has("chord"))
        assertEquals("LB+RB+Y", json.getJSONObject(1).getString("chord"))
    }

    @Test
    fun `positions are written to four decimal places`() {
        val rounded = Layout(listOf(Layout.MappingSet(null, listOf(Control.Tap("A", 0.123456, 0.5, 0.05)))))
        assertEquals(0.1235, LayoutJson.toJson(rounded).getJSONArray("sets").getJSONObject(0)
            .getJSONArray("controls").getJSONObject(0).getDouble("x"))
    }

    @Test
    fun `a version 1 file is one set without a chord`() {
        assertEquals(
            Layout(listOf(Layout.MappingSet(null, listOf(Control.Tap("A", 0.5, 0.5, 0.05))))),
            parse("""{"version": 1, "controls": [{"type": "tap", "button": "A", "x": 0.5, "y": 0.5, "radius": 0.05}]}"""),
        )
    }

    @Test
    fun `a null chord reads as no chord`() {
        assertEquals(null, parse("""{"version": 2, "sets": [{"chord": null, "controls": []}]}""").sets[0].chord)
    }

    @Test
    fun `bad files are rejected`() {
        assertFailsWith<JSONException>("no sets") { parse("""{"version": 2, "sets": []}""") }
        assertFailsWith<JSONException>("unknown chord button") {
            parse("""{"version": 2, "sets": [{"chord": "LB+Q", "controls": []}]}""")
        }
        assertFailsWith<JSONException>("off screen") {
            parse("""{"version": 1, "controls": [{"type": "tap", "button": "A", "x": 1.5, "y": 0.5, "radius": 0.05}]}""")
        }
        assertFailsWith<JSONException>("unknown version") { parse("""{"version": 3, "sets": []}""") }
    }

    @Test
    fun `chords are buttons or key codes joined by plus`() {
        assertEquals(true, LayoutJson.isChord("SELECT+START"))
        assertEquals(true, LayoutJson.isChord("KEY_2C0"))
        assertEquals(false, LayoutJson.isChord("LB+"))
        assertEquals(false, LayoutJson.isChord(null))
    }
}
