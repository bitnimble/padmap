package app.padmap.layout

/**
 * Which mapping set is active. Holding a set's chord switches to it, or back to the first set if it's already
 * active; the chord's buttons then map nothing until each is released.
 */
class SetSwitcher {
    var active = 0
        private set
    private val latched = mutableSetOf<String>()

    fun reset() {
        active = 0
    }

    /** Switches if a set chord is newly held; true if the active set changed. */
    fun update(layout: Layout, pad: PadState): Boolean {
        latched.retainAll(pad.buttons)
        var target = -1
        var targetChord = ""
        for ((i, set) in layout.sets.withIndex()) {
            val chord = set.chord ?: continue
            if (!Chord.held(chord, pad) || Chord.parts(chord).any { it in latched }) continue
            if (target < 0 || Chord.strictlyWithin(targetChord, chord)) {
                target = i
                targetChord = chord
            }
        }
        if (target < 0) return false
        val before = active
        active = if (target == active) 0 else target
        latched += Chord.parts(targetChord)
        return active != before
    }

    /** The pad without the buttons of the chord that last switched, until each is released. */
    fun strip(pad: PadState): PadState =
        if (latched.isEmpty()) pad else PadState(pad.buttons - latched, pad.left, pad.right)
}
