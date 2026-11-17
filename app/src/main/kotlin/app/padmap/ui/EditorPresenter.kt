package app.padmap.ui

import androidx.compose.runtime.toMutableStateList
import app.padmap.layout.Button
import app.padmap.layout.Chord
import app.padmap.layout.Control
import app.padmap.layout.Layout
import app.padmap.layout.LayoutJson

/** Every change the editor makes to [EditorStore]: placing and binding controls, mapping sets and settings. */
class EditorPresenter(
    private val store: EditorStore,
    private val saveStyle: (ButtonStyle) -> Unit,
    private val saveEditorChord: (String) -> Unit,
    private val notify: (String) -> Unit,
) {
    private val gesture = linkedSetOf<String>()

    /** The edited layout, or null if nothing changed. */
    fun changedLayout(): Layout? {
        val layout = store.layout()
        // at the file's precision: touching a control without dragging it nudges its float position
        val same = LayoutJson.toJson(layout).toString() == LayoutJson.toJson(store.original).toString()
        return if (same) null else layout
    }

    fun setDisplaySize(width: Int, height: Int) {
        store.displayWidth = width
        store.displayHeight = height
    }

    fun setEditorChord(chord: String) {
        store.editorChord = chord
    }

    /** The buttons held this frame; the chord is bound when they are all released, so holding several binds a chord. */
    fun onButtons(held: List<String>) {
        val bindsButton = store.selectedControl.let { it is Control.Tap || it is Control.Aim }
        val awaitingChord = store.awaiting == Awaiting.SET_CHORD || store.awaiting == Awaiting.EDITOR_CHORD
        if (!bindsButton && !awaitingChord) return
        val editorButtons = Chord.parts(store.editorChord)
        // the editor chord's buttons open and close the editor, so only the editor chord itself may use them
        gesture += held.filter { store.awaiting == Awaiting.EDITOR_CHORD || it !in editorButtons }
        if (held.isNotEmpty() || gesture.isEmpty()) return
        val chord = Chord.of(gesture)
        gesture.clear()
        when (store.awaiting) {
            Awaiting.EDITOR_CHORD -> bindEditorChord(chord)
            Awaiting.SET_CHORD -> bindSetChord(chord)
            else -> bind(chord)
        }
    }

    fun onStick(stick: String) {
        val selected = store.selectedControl ?: return
        when (selected) {
            is Control.Joystick -> store.controls[store.selected] = selected.copy(stick = stick)
            is Control.Aim -> {
                store.controls[store.selected] = selected.copy(stick = stick)
                store.awaiting = Awaiting.NOTHING
            }
            is Control.Tap -> {}
        }
    }

    fun add(control: Control) {
        store.controls += control
        select(store.controls.lastIndex)
    }

    fun select(index: Int) {
        store.selected = index
        store.awaiting = Awaiting.NOTHING
        gesture.clear()
    }

    fun move(index: Int, x: Double, y: Double) {
        store.controls[index] = store.controls[index].moved(x.coerceIn(0.0, 1.0), y.coerceIn(0.0, 1.0))
    }

    fun resize(factor: Double) {
        val control = store.selectedControl ?: return
        store.controls[store.selected] = control.resized((control.radius * factor).coerceIn(MIN_RADIUS, MAX_RADIUS))
    }

    fun delete() {
        if (store.selectedControl == null) return
        store.controls.removeAt(store.selected)
        select(-1)
    }

    fun setFallback(on: Boolean) {
        val aim = store.selectedControl as? Control.Aim ?: return
        store.controls[store.selected] = aim.copy(fallback = on)
    }

    fun showSet(index: Int) {
        store.current = index
        select(-1)
    }

    fun duplicateSet() {
        store.sets += store.controls.toMutableStateList()
        store.chords += null
        showSet(store.sets.lastIndex)
        awaitSetChord()
    }

    fun deleteSet() {
        if (store.sets.size <= 1) return
        store.sets.removeAt(store.current)
        store.chords.removeAt(store.current)
        showSet(minOf(store.current, store.sets.lastIndex))
    }

    fun awaitSetChord() {
        select(-1)
        store.awaiting = Awaiting.SET_CHORD
    }

    fun awaitEditorChord() {
        select(-1)
        store.awaiting = Awaiting.EDITOR_CHORD
    }

    fun setStyle(style: ButtonStyle) {
        store.style = style
        saveStyle(style)
    }

    private fun bind(chord: String) {
        when (val control = store.selectedControl) {
            is Control.Tap -> store.controls[store.selected] = control.copy(button = chord)
            is Control.Aim -> {
                store.controls[store.selected] = control.copy(button = chord)
                store.awaiting = Awaiting.AIM_STICK
            }
            else -> {}
        }
    }

    private fun bindSetChord(chord: String) {
        val other = store.chords.indexOfFirst { it != null && Chord.of(Chord.parts(it)) == chord }
        if (other >= 0 && other != store.current) {
            notify("${chordText(chord)} already activates set ${other + 1}")
            return
        }
        store.chords[store.current] = chord
        store.awaiting = Awaiting.NOTHING
    }

    private fun bindEditorChord(chord: String) {
        store.editorChord = chord
        saveEditorChord(chord)
        store.awaiting = Awaiting.NOTHING
        notify("Editor chord: ${chordText(chord)}")
    }

    private fun chordText(chord: String): String =
        Chord.parts(chord).joinToString("+") { part -> Button.named(part)?.let(store.style::label) ?: part }

    private companion object {
        const val MIN_RADIUS = 0.02
        const val MAX_RADIUS = 0.5
    }
}
