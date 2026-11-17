package app.padmap.layout

/** One or more pad buttons that must be held together, written "LT+RB". Parts are kept in a canonical order. */
object Chord {
    /** The canonical "A+B" form of the held buttons, or "A" for one; an empty collection yields "". */
    fun of(buttons: Collection<String>): String =
        buttons.sortedWith(compareBy<String> { Button.rank(it) }.thenBy { it }).joinToString("+")

    fun parts(spec: String): List<String> = spec.split("+")

    fun held(spec: String, pad: PadState): Boolean = parts(spec).all(pad::pressed)

    /** True if every button of sub is also in sup and sup has more, i.e. sup shadows sub. */
    fun strictlyWithin(sub: String, sup: String): Boolean {
        val subParts = parts(sub)
        val supParts = parts(sup)
        return supParts.size > subParts.size && supParts.containsAll(subParts)
    }
}
