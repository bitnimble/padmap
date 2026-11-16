package app.padmap.daemon;

import java.util.List;
import java.util.Set;

public final class GeteventTest {
    private static final String STEAM_PAD = """
            add device 1: /dev/input/event10
              name:     "Microsoft X-Box 360 pad 0"
              events:
                KEY (0001): 0130  0131  0133  0134  0136  0137  013a  013b\s
                            013c  013d  013e\s
                ABS (0003): 0000  : value 0, min -32768, max 32767, fuzz 0, flat 0, resolution 0
                            0001  : value 0, min -32768, max 32767, fuzz 0, flat 0, resolution 0
                            0002  : value 0, min 0, max 255, fuzz 0, flat 0, resolution 0
                            0003  : value 0, min -32768, max 32767, fuzz 0, flat 0, resolution 0
                            0004  : value 0, min -32768, max 32767, fuzz 0, flat 0, resolution 0
                            0005  : value 0, min 0, max 255, fuzz 0, flat 0, resolution 0
                            0010  : value 0, min -1, max 1, fuzz 0, flat 0, resolution 0
                            0011  : value 0, min -1, max 1, fuzz 0, flat 0, resolution 0
              input props:
                <none>
            """;
    private static final String TOUCHSCREEN_AND_HID_PAD = """
            add device 1: /dev/input/event2
              name:     "touchscreen"
              events:
                KEY (0001): 014a
                ABS (0003): 0035  : value 0, min 0, max 1279, fuzz 0, flat 0, resolution 0
            add device 2: /dev/input/event5
              name:     "Generic HID pad"
              events:
                KEY (0001): 0130  0131
                ABS (0003): 0000  : value 128, min 0, max 255, fuzz 0, flat 15, resolution 0
                            0001  : value 128, min 0, max 255, fuzz 0, flat 15, resolution 0
                            0002  : value 128, min 0, max 255, fuzz 0, flat 15, resolution 0
                            0003  : value 0, min 0, max 255, fuzz 0, flat 0, resolution 0
                            0004  : value 0, min 0, max 255, fuzz 0, flat 0, resolution 0
                            0005  : value 128, min 0, max 255, fuzz 0, flat 15, resolution 0
            """;

    public static void main(String[] args) {
        List<Getevent.Device> steam = Getevent.parse(STEAM_PAD);
        check(steam.size() == 1 && steam.get(0).path().equals("/dev/input/event10"), "one device with its path");
        Getevent.Device pad = steam.get(0);
        check(pad.name().equals("Microsoft X-Box 360 pad 0"), "name");
        check(pad.keys().equals(Set.of(0x130, 0x131, 0x133, 0x134, 0x136, 0x137, 0x13a, 0x13b, 0x13c, 0x13d, 0x13e)),
                "keys across continuation lines, got " + pad.keys());
        check(pad.ranges().get(0x02).equals(new Getevent.Range(0, 0, 255)), "trigger range");
        check(pad.isGamepad(), "xpad is a gamepad");
        check(Getevent.Axes.of(pad.ranges()).equals(new Getevent.Axes(0x03, 0x04, 0x02, 0x05)),
                "xpad: right stick on RX/RY, triggers on Z/RZ");

        List<Getevent.Device> mixed = Getevent.parse(TOUCHSCREEN_AND_HID_PAD);
        check(mixed.size() == 2 && !mixed.get(0).isGamepad() && mixed.get(1).isGamepad(), "touchscreen isn't a pad");
        check(Getevent.Axes.of(mixed.get(1).ranges()).equals(new Getevent.Axes(0x02, 0x05, 0x03, 0x04)),
                "HID pad with triggers resting on RX/RY: right stick on Z/RZ");
        check(Math.abs(mixed.get(1).ranges().get(0x00).centred(128)) < 0.01, "0..255 stick centred at 128");
        System.out.println("GeteventTest: all passed");
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
