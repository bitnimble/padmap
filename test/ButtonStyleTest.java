package app.padmap.ui;

import app.padmap.layout.Button;

public final class ButtonStyleTest {
    public static void main(String[] args) {
        check(ButtonStyle.XBOX.label(Button.A).equals("A"), "Xbox keeps the binding names");
        check(ButtonStyle.NINTENDO.label(Button.A).equals("B"), "Nintendo south is B");
        check(ButtonStyle.NINTENDO.label(Button.Y).equals("X"), "Nintendo north is X");
        check(ButtonStyle.NINTENDO.label(Button.LT).equals("ZL"), "Nintendo left trigger is ZL");
        check(ButtonStyle.PLAYSTATION.label(Button.A).equals("✕"), "PlayStation south is cross");
        check(ButtonStyle.PLAYSTATION.label(Button.X).equals("□"), "PlayStation west is square");
        check(ButtonStyle.PLAYSTATION.label(Button.RB).equals("R1"), "PlayStation right bumper is R1");
        check(ButtonStyle.PLAYSTATION.label(Button.UP).equals("UP"), "unstyled buttons keep their names");
        check(ButtonStyle.XBOX.label(Button.HOME).equals("Guide"), "Xbox home is Guide");
        check(ButtonStyle.PLAYSTATION.label(Button.HOME).equals("PS"), "PlayStation home is PS");
        check(ButtonStyle.NINTENDO.label(Button.HOME).equals("HOME"), "Nintendo home is the house");
        check(ButtonStyle.XBOX.label(Button.START).equals("Menu"), "Xbox start is Menu");
        check(ButtonStyle.NINTENDO.label(Button.SELECT).equals("−"), "Nintendo select is minus");
        check(ButtonStyle.PLAYSTATION.label(Button.START).equals("Options"), "PlayStation start is Options");
        check(ButtonStyle.STEAM.label(Button.A).equals("A"), "Steam south is A");
        check(ButtonStyle.STEAM.label(Button.LT).equals("L2"), "Steam left trigger is L2");
        check(ButtonStyle.STEAM.label(Button.HOME).equals("Steam"), "Steam home is the Steam button");
        check(ButtonStyle.STEAM_PLAYSTATION.label(Button.A).equals("✕"), "Steam + PS south is cross");
        check(ButtonStyle.STEAM_PLAYSTATION.label(Button.START).equals("Menu"), "Steam + PS start is Menu");
        check(ButtonStyle.STEAM_PLAYSTATION.label(Button.HOME).equals("Steam"), "Steam + PS home is the Steam button");
        check(ButtonStyle.STEAM_PLAYSTATION.faceColour(Button.Y) == ButtonStyle.PLAYSTATION.faceColour(Button.Y),
                "Steam + PS face buttons are PlayStation's colours");
        for (ButtonStyle style : ButtonStyle.values()) {
            for (Button face : new Button[] {Button.A, Button.B, Button.X, Button.Y}) style.faceColour(face);
        }
        System.out.println("ButtonStyleTest: all passed");
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
