package app.padmap.ui

import app.padmap.layout.Control
import app.padmap.layout.Layout
import app.padmap.layout.Layout.MappingSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EditorPresenterTest {
    private val tap = Control.Tap("A", 0.5, 0.5, 0.05)
    private val aim = Control.Aim("RB", "RS", false, 0.3, 0.3, 0.1)
    private val notes = mutableListOf<String>()
    private val savedChords = mutableListOf<String>()

    private fun editor(layout: Layout = Layout(listOf(MappingSet(null, listOf(tap, aim))))): Pair<EditorStore, EditorPresenter> {
        val store = EditorStore(layout, 0, "SELECT+START", ButtonStyle.XBOX, 1000, 500)
        return store to EditorPresenter(store, {}, savedChords::add, notes::add)
    }

    private fun EditorPresenter.press(vararg frames: List<String>) = frames.forEach(::onButtons)

    @Test
    fun `nothing changed means nothing to save`() {
        val (_, presenter) = editor()
        assertNull(presenter.changedLayout())
    }

    @Test
    fun `buttons held together bind a chord on release`() {
        val (store, presenter) = editor()
        presenter.select(0)
        presenter.press(listOf("LB"), listOf("LB", "A"), listOf())
        assertEquals("A+LB", (store.controls[0] as Control.Tap).button)
    }

    @Test
    fun `binding an aim's button then waits for its stick`() {
        val (store, presenter) = editor()
        presenter.select(1)
        presenter.press(listOf("LT"), listOf())
        assertEquals(Awaiting.AIM_STICK, store.awaiting)
        presenter.onStick("LS")
        assertEquals(Control.Aim("LT", "LS", false, 0.3, 0.3, 0.1), store.controls[1])
        assertEquals(Awaiting.NOTHING, store.awaiting)
    }

    @Test
    fun `the editor chord's buttons can't bind a control`() {
        val (store, presenter) = editor()
        presenter.select(0)
        presenter.press(listOf("SELECT"), listOf("SELECT", "B"), listOf())
        assertEquals("B", (store.controls[0] as Control.Tap).button)
    }

    @Test
    fun `duplicate copies the shown set and waits for its activation chord`() {
        val (store, presenter) = editor()
        presenter.duplicateSet()
        assertEquals(1, store.current)
        assertEquals(listOf(tap, aim), store.controls.toList())
        assertEquals(Awaiting.SET_CHORD, store.awaiting)
        presenter.press(listOf("LB", "Y"), listOf())
        assertEquals(listOf(null, "Y+LB"), store.chords.toList())
        assertEquals(Awaiting.NOTHING, store.awaiting)
    }

    @Test
    fun `a chord another set uses is refused`() {
        val (store, presenter) = editor(Layout(listOf(MappingSet(null, listOf(tap)), MappingSet("LB+Y", listOf(tap)))))
        presenter.duplicateSet()
        presenter.press(listOf("LB", "Y"), listOf())
        assertEquals(listOf("Y+LB already activates set 2"), notes)
        assertEquals(Awaiting.SET_CHORD, store.awaiting)
    }

    @Test
    fun `deleting the last set shows the one before it`() {
        val (store, presenter) = editor(Layout(listOf(MappingSet(null, listOf(tap)), MappingSet("LB+Y", listOf(aim)))))
        presenter.showSet(1)
        presenter.deleteSet()
        assertEquals(0, store.current)
        assertEquals(Layout(listOf(MappingSet(null, listOf(tap)))), presenter.changedLayout())
    }

    @Test
    fun `the only set can't be deleted`() {
        val (store, presenter) = editor()
        presenter.deleteSet()
        assertEquals(1, store.sets.size)
    }

    @Test
    fun `a new editor chord may use any buttons and is saved`() {
        val (store, presenter) = editor()
        presenter.awaitEditorChord()
        presenter.press(listOf("SELECT"), listOf("SELECT", "A"), listOf())
        assertEquals("A+SELECT", store.editorChord)
        assertEquals(listOf("A+SELECT"), savedChords)
        assertEquals(listOf("Editor chord: A+View"), notes)
    }

    @Test
    fun `moves are clamped to the screen and resizes to the radius limits`() {
        val (store, presenter) = editor()
        presenter.move(0, 1.4, -0.2)
        presenter.select(0)
        repeat(40) { presenter.resize(1.15) }
        assertEquals(Control.Tap("A", 1.0, 0.0, 0.5), store.controls[0])
    }

    @Test
    fun `the topmost control under a point is hit`() {
        val (store, _) = editor(Layout(listOf(MappingSet(null, listOf(tap, tap.copy(button = "B"))))))
        assertEquals(1, store.controlAt(500f, 250f))
        assertEquals(-1, store.controlAt(10f, 10f))
    }
}
