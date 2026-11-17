package app.padmap.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The layout file format: {"version": 2, "sets": [{"chord"?: "LB+RB", "controls": [{"type": "tap"|"joystick"|"aim",
 * …}]}]}. Version 1, {"version": 1, "controls": […]}, is one set without a chord.
 */
public final class LayoutJson {
    public static final int VERSION = 2;
    private static final String SINGLE = Button.alternation() + "|KEY_[0-9A-F]+";
    private static final Pattern BUTTON = Pattern.compile("(" + SINGLE + ")(\\+(" + SINGLE + "))*");

    private LayoutJson() {}

    public static Layout parse(JSONObject json) throws JSONException {
        int version = json.optInt("version", json.has("sets") ? VERSION : 1);
        if (version == 1) return new Layout(List.of(new Layout.MappingSet(null, controls(json))));
        if (version != VERSION) throw new JSONException("unsupported layout version " + version);
        JSONArray array = json.getJSONArray("sets");
        if (array.length() == 0) throw new JSONException("sets is empty");
        List<Layout.MappingSet> sets = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject set = array.getJSONObject(i);
            sets.add(new Layout.MappingSet(set.isNull("chord") ? null : button(set, "chord"), controls(set)));
        }
        return new Layout(sets);
    }

    private static List<Control> controls(JSONObject json) throws JSONException {
        JSONArray array = json.getJSONArray("controls");
        List<Control> controls = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) controls.add(control(array.getJSONObject(i)));
        return controls;
    }

    private static Control control(JSONObject json) throws JSONException {
        double x = fraction(json, "x"), y = fraction(json, "y"), radius = fraction(json, "radius");
        String type = json.getString("type");
        return switch (type) {
            case "tap" -> new Control.Tap(button(json, "button"), x, y, radius);
            case "joystick" -> new Control.Joystick(stick(json), x, y, radius);
            case "aim" -> new Control.Aim(button(json, "button"), stick(json), json.optBoolean("fallback"), x, y, radius);
            default -> throw new JSONException("unknown control type " + type);
        };
    }

    private static double fraction(JSONObject json, String key) throws JSONException {
        double value = json.getDouble(key);
        if (!(value >= 0 && value <= 1)) throw new JSONException(key + " must be between 0 and 1, got " + value);
        return value;
    }

    private static String button(JSONObject json, String key) throws JSONException {
        String button = json.getString(key);
        if (!BUTTON.matcher(button).matches()) throw new JSONException("unknown button " + button);
        return button;
    }

    private static String stick(JSONObject json) throws JSONException {
        String stick = json.getString("stick");
        if (!stick.equals("LS") && !stick.equals("RS")) throw new JSONException("stick must be LS or RS, got " + stick);
        return stick;
    }

    public static JSONObject toJson(Layout layout) {
        try {
            JSONArray sets = new JSONArray();
            for (Layout.MappingSet set : layout.sets()) {
                JSONArray controls = new JSONArray();
                for (Control control : set.controls()) controls.put(toJson(control));
                JSONObject json = new JSONObject();
                if (set.chord() != null) json.put("chord", set.chord());
                sets.put(json.put("controls", controls));
            }
            return new JSONObject().put("version", VERSION).put("sets", sets);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JSONObject toJson(Control control) throws JSONException {
        JSONObject json = new JSONObject();
        if (control instanceof Control.Tap tap) {
            json.put("type", "tap").put("button", tap.button());
        } else if (control instanceof Control.Joystick stick) {
            json.put("type", "joystick").put("stick", stick.stick());
        } else if (control instanceof Control.Aim aim) {
            json.put("type", "aim").put("button", aim.button()).put("stick", aim.stick()).put("fallback", aim.fallback());
        }
        return json.put("x", round(control.x())).put("y", round(control.y())).put("radius", round(control.radius()));
    }

    private static double round(double value) {
        return Math.round(value * 10000) / 10000.0;
    }
}
