package app.padmap.layout

/** The pad's button vocabulary: canonical name, evdev key code, and chord-sort order (declaration order). */
enum class Button(val code: Int) {
    A(0x130), B(0x131), X(0x133), Y(0x134),
    LB(0x136), RB(0x137), LT(0x138), RT(0x139),
    L3(0x13d), R3(0x13e),
    UP(0x220), DOWN(0x221), LEFT(0x222), RIGHT(0x223),
    SELECT(0x13a), START(0x13b), HOME(0x13c);

    companion object {
        /** evdev key code to button name, for the raw gamepad reader. */
        fun byCode(): Map<Int, String> = entries.associate { it.code to it.name }

        /** Regex alternation of every name, e.g. "A|B|X". */
        fun alternation(): String = entries.joinToString("|") { it.name }

        /** The button with this name, or null for anything else (KEY_*, a stick name). */
        fun named(name: String): Button? = entries.firstOrNull { it.name == name }

        /** Chord-sort rank of a name; unknown names (KEY_*) sort last. */
        fun rank(name: String): Int = named(name)?.ordinal ?: entries.size
    }
}
