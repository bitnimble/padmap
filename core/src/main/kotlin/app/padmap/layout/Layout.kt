package app.padmap.layout

/** A game's mapping sets; the first is active at the start. */
data class Layout(val sets: List<MappingSet>) {
    init {
        require(sets.isNotEmpty()) { "a layout needs at least one mapping set" }
    }

    /** One set of controls; chord (null for none) is the buttons that switch to it. */
    data class MappingSet(val chord: String?, val controls: List<Control>)

    fun maps(): Boolean = sets.any { it.controls.isNotEmpty() }

    companion object {
        val EMPTY = Layout(listOf(MappingSet(null, emptyList())))
    }
}
