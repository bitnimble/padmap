package app.padmap.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import app.padmap.layout.Control
import app.padmap.layout.Layout
import kotlin.math.hypot
import kotlin.math.max

/** What the editor waits for from the pad besides binding the selected control's button. */
enum class Awaiting { NOTHING, AIM_STICK, SET_CHORD, EDITOR_CHORD }

/** The editor's state: the layout being edited and what's selected. Written only by [EditorPresenter]. */
class EditorStore(
    val original: Layout,
    activeSet: Int,
    editorChord: String,
    style: ButtonStyle,
    displayWidth: Int,
    displayHeight: Int,
) {
    val sets: SnapshotStateList<SnapshotStateList<Control>> =
        original.sets.mapTo(mutableStateListOf()) { set -> set.controls.toMutableStateList() }
    val chords: SnapshotStateList<String?> = original.sets.mapTo(mutableStateListOf()) { it.chord }
    var current by mutableIntStateOf(activeSet.takeIf { it in original.sets.indices } ?: 0)
    var selected by mutableIntStateOf(-1)
    var awaiting by mutableStateOf(Awaiting.NOTHING)
    var style by mutableStateOf(style)
    var editorChord by mutableStateOf(editorChord)
    var displayWidth by mutableIntStateOf(displayWidth)
    var displayHeight by mutableIntStateOf(displayHeight)

    val controls: SnapshotStateList<Control> get() = sets[current]

    val selectedControl: Control? get() = controls.getOrNull(selected)

    val currentChord: String? get() = chords[current]

    fun layout(): Layout = Layout(sets.indices.map { Layout.MappingSet(chords[it], sets[it].toList()) })

    /** The topmost control under screen point (x, y), or -1. */
    fun controlAt(x: Float, y: Float): Int = controls.indices.reversed().firstOrNull { i ->
        val control = controls[i]
        val reach = max(control.radius * displayHeight, displayHeight * 0.05)
        hypot(control.x * displayWidth - x, control.y * displayHeight - y) <= reach
    } ?: -1
}
