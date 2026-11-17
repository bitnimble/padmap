package app.padmap.layout

import kotlin.math.roundToLong
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * The layout file format: {"version": 2, "sets": [{"chord"?: "LB+RB", "controls": [{"type": "tap"|"joystick"|"aim",
 * …}]}]}. Version 1, {"version": 1, "controls": […]}, is one set without a chord.
 */
object LayoutJson {
    const val VERSION = 2
    private val SINGLE = Button.alternation() + "|KEY_[0-9A-F]+"
    private val BUTTON = Regex("($SINGLE)(\\+($SINGLE))*")

    fun parse(json: JSONObject): Layout {
        val version = json.optInt("version", if (json.has("sets")) VERSION else 1)
        if (version == 1) return Layout(listOf(Layout.MappingSet(null, controls(json))))
        if (version != VERSION) throw JSONException("unsupported layout version $version")
        val array = json.getJSONArray("sets")
        if (array.length() == 0) throw JSONException("sets is empty")
        return Layout(objects(array).map { set ->
            Layout.MappingSet(if (set.isNull("chord")) null else button(set, "chord"), controls(set))
        })
    }

    fun isChord(spec: String?): Boolean = spec != null && BUTTON.matches(spec)

    fun toJson(layout: Layout): JSONObject {
        val sets = JSONArray()
        for (set in layout.sets) {
            val controls = JSONArray()
            set.controls.forEach { controls.put(toJson(it)) }
            val json = JSONObject()
            set.chord?.let { json.put("chord", it) }
            sets.put(json.put("controls", controls))
        }
        return JSONObject().put("version", VERSION).put("sets", sets)
    }

    private fun objects(array: JSONArray): List<JSONObject> = (0 until array.length()).map(array::getJSONObject)

    private fun controls(json: JSONObject): List<Control> = objects(json.getJSONArray("controls")).map(::control)

    private fun control(json: JSONObject): Control {
        val x = fraction(json, "x")
        val y = fraction(json, "y")
        val radius = fraction(json, "radius")
        return when (val type = json.getString("type")) {
            "tap" -> Control.Tap(button(json, "button"), x, y, radius)
            "joystick" -> Control.Joystick(stick(json), x, y, radius)
            "aim" -> Control.Aim(button(json, "button"), stick(json), json.optBoolean("fallback"), x, y, radius)
            else -> throw JSONException("unknown control type $type")
        }
    }

    private fun fraction(json: JSONObject, key: String): Double {
        val value = json.getDouble(key)
        if (!(value >= 0 && value <= 1)) throw JSONException("$key must be between 0 and 1, got $value")
        return value
    }

    private fun button(json: JSONObject, key: String): String {
        val button = json.getString(key)
        if (!isChord(button)) throw JSONException("unknown button $button")
        return button
    }

    private fun stick(json: JSONObject): String {
        val stick = json.getString("stick")
        if (stick != "LS" && stick != "RS") throw JSONException("stick must be LS or RS, got $stick")
        return stick
    }

    private fun toJson(control: Control): JSONObject {
        val json = when (control) {
            is Control.Tap -> JSONObject().put("type", "tap").put("button", control.button)
            is Control.Joystick -> JSONObject().put("type", "joystick").put("stick", control.stick)
            is Control.Aim -> JSONObject().put("type", "aim").put("button", control.button).put("stick", control.stick)
                .put("fallback", control.fallback)
        }
        return json.put("x", round(control.x)).put("y", round(control.y)).put("radius", round(control.radius))
    }

    private fun round(value: Double): Double = (value * 10000).roundToLong() / 10000.0
}
