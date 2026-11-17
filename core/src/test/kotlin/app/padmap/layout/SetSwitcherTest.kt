package app.padmap.layout

import app.padmap.layout.Layout.MappingSet
import kotlin.test.Test
import kotlin.test.assertEquals

class SetSwitcherTest {
    private val tap = listOf(Control.Tap("A", 0.5, 0.5, 0.05))
    private val layout = Layout(listOf(MappingSet(null, tap), MappingSet("LB+RB", tap), MappingSet("LB+RB+Y", tap)))

    private data class Step(val switched: Boolean, val active: Int, val mapped: Set<String>)

    private fun SetSwitcher.step(layout: Layout, vararg held: String): Step {
        val pad = PadState(held.toSet(), PadState.Stick.CENTRE, PadState.Stick.CENTRE)
        val switched = update(layout, pad)
        return Step(switched, active, strip(pad).buttons)
    }

    @Test
    fun `chords switch sets and hold their buttons back until released`() {
        val switcher = SetSwitcher()
        assertEquals(Step(false, 0, setOf("A")), switcher.step(layout, "A"), "no chord held: first set, pad untouched")
        assertEquals(Step(false, 0, setOf("LB")), switcher.step(layout, "LB"), "part of a chord switches nothing")
        assertEquals(Step(true, 1, setOf("A")), switcher.step(layout, "LB", "RB", "A"), "a held chord switches")
        assertEquals(Step(false, 1, setOf("A")), switcher.step(layout, "LB", "RB", "A"), "a held chord doesn't switch again")
        assertEquals(Step(false, 1, setOf()), switcher.step(layout, "LB"), "a chord button stays held back")
        assertEquals(Step(false, 1, setOf("RB")), switcher.step(layout, "LB", "RB"), "a re-pressed RB maps, no switch")
        assertEquals(Step(false, 1, setOf()), switcher.step(layout), "releasing everything clears the latch")
        assertEquals(Step(false, 1, setOf("RB")), switcher.step(layout, "RB"), "a released chord button maps again")
        assertEquals(Step(true, 0, setOf()), switcher.step(layout, "LB", "RB"), "the active set's chord returns to set 1")
    }

    @Test
    fun `the larger of two held chords wins`() {
        assertEquals(Step(true, 2, setOf()), SetSwitcher().step(layout, "LB", "RB", "Y"))
    }

    @Test
    fun `reset returns to the first set but keeps a held chord latched`() {
        val switcher = SetSwitcher()
        switcher.step(layout, "LB", "RB", "Y")
        switcher.reset()
        assertEquals(Step(false, 0, setOf()), switcher.step(layout, "LB", "RB", "Y"), "still held: latched")
        switcher.step(layout)
        assertEquals(Step(true, 2, setOf()), switcher.step(layout, "LB", "RB", "Y"), "after release it switches again")
    }

    @Test
    fun `the first set's own chord keeps it active`() {
        val chordedFirst = Layout(listOf(MappingSet("X+Y", tap), MappingSet("LB+RB", tap)))
        assertEquals(Step(false, 0, setOf()), SetSwitcher().step(chordedFirst, "X", "Y"))
    }
}
