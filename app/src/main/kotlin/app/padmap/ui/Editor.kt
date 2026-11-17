package app.padmap.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.padmap.R
import app.padmap.layout.Chord
import app.padmap.layout.Control
import kotlin.math.max
import kotlin.math.min

private const val RESIZE_STEP = 1.15

private enum class Tone { NORMAL, PRIMARY, DANGER }

/** Full-screen layout editor drawn over the game: drag controls to place them, bind them from the pad. */
@Composable
fun Editor(store: EditorStore, presenter: EditorPresenter, onDone: () -> Unit) {
    val density = LocalDensity.current.density
    val painter = remember(density) { GlyphPainter(density) }
    Box(Modifier.fillMaxSize().background(Color(Palette.SCRIM))) {
        Controls(store, presenter, painter, Modifier.fillMaxSize())
        BoxWithConstraints(Modifier.fillMaxSize().padding(top = 12.dp), contentAlignment = Alignment.TopCenter) {
            Toolbar(store, presenter, painter, onDone, Modifier.width(minOf(820.dp, maxWidth * 0.92f)))
        }
    }
}

@Composable
private fun Controls(store: EditorStore, presenter: EditorPresenter, painter: GlyphPainter, modifier: Modifier) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    Canvas(
        modifier
            .onGloballyPositioned { origin = it.positionOnScreen() }
            .pointerInput(presenter) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val screen = down.position + origin
                    val hit = store.controlAt(screen.x, screen.y)
                    presenter.select(hit)
                    if (hit < 0) return@awaitEachGesture
                    val control = store.controls[hit]
                    val grab = Offset(
                        (control.x * store.displayWidth).toFloat(),
                        (control.y * store.displayHeight).toFloat(),
                    ) - screen
                    drag(down.id) { change ->
                        val at = change.position + origin + grab
                        presenter.move(hit, at.x.toDouble() / store.displayWidth, at.y.toDouble() / store.displayHeight)
                        change.consume()
                    }
                }
            },
    ) {
        val glyph = max(24.dp.toPx(), store.displayHeight * 0.042f)
        store.controls.forEachIndexed { i, control ->
            drawControl(store, painter, control, i == store.selected, label(store, i, control), origin, glyph)
        }
    }
}

private fun DrawScope.drawControl(
    store: EditorStore,
    painter: GlyphPainter,
    control: Control,
    selected: Boolean,
    label: String,
    origin: Offset,
    glyph: Float,
) {
    val centre = Offset(
        (control.x * store.displayWidth).toFloat() - origin.x,
        (control.y * store.displayHeight).toFloat() - origin.y,
    )
    val radius = (control.radius * store.displayHeight).toFloat()
    val strokeColour = if (selected) Color(Palette.SELECTED) else Color(255, 255, 255, 210)
    val stroke = Stroke(width = (if (selected) 4.dp else 2.5.dp).toPx())
    if (control is Control.Tap) {
        drawCircle(if (selected) Color(255, 182, 39, 70) else Color(255, 255, 255, 45), radius, centre)
        drawCircle(strokeColour, radius, centre, style = stroke)
    } else {
        val dash = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx()))
        drawCircle(strokeColour, radius, centre, style = Stroke(width = stroke.width, pathEffect = dash))
        val knob = min(radius, store.displayHeight * 0.05f)
        drawCircle(Color(Palette.KNOB), knob, centre)
        drawCircle(strokeColour, knob, centre, style = stroke)
    }
    drawIntoCanvas {
        painter.drawBinding(it.nativeCanvas, Chord.parts(label), store.style, centre.x, centre.y, glyph)
    }
}

private fun label(store: EditorStore, index: Int, control: Control): String = when (control) {
    is Control.Tap -> control.button
    is Control.Joystick -> control.stick
    is Control.Aim -> {
        val awaitingStick = store.awaiting == Awaiting.AIM_STICK && index == store.selected
        control.button + "+" + if (awaitingStick) "?" else control.stick
    }
}

@Composable
private fun Toolbar(
    store: EditorStore,
    presenter: EditorPresenter,
    painter: GlyphPainter,
    onDone: () -> Unit,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            // a tap on the panel's own background would otherwise reach the controls beneath it
            .pointerInput(Unit) {}
            .shadow(8.dp, shape)
            .background(Color(Palette.PANEL), shape)
            .border(1.dp, Color(Palette.EDGE), shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            EditorButton("+ Tap", { presenter.add(Control.Tap("A", 0.5, 0.5, 0.06)) })
            EditorButton("+ Stick", { presenter.add(Control.Joystick("LS", 0.5, 0.5, 0.12)) })
            EditorButton("+ Aim", { presenter.add(Control.Aim("RB", "RS", false, 0.5, 0.5, 0.12)) })
            if (store.selectedControl != null) {
                Box(Modifier.padding(horizontal = 8.dp).size(1.dp, 32.dp).background(Color(Palette.EDGE)))
                EditorButton("Smaller", { presenter.resize(1 / RESIZE_STEP) })
                EditorButton("Larger", { presenter.resize(RESIZE_STEP) })
                EditorButton("Delete", presenter::delete, Tone.DANGER)
            }
            Text(
                if (store.awaiting == Awaiting.EDITOR_CHORD) "Press new editor chord buttons now" else "",
                Modifier.weight(1f).padding(start = 8.dp, end = 4.dp),
                color = Color(Palette.TEXT),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
            )
            SettingsMenu(store, presenter, painter)
            EditorButton("Done", onDone, Tone.PRIMARY)
        }
        SetRow(store, presenter, painter)
        val aim = store.selectedControl as? Control.Aim
        if (aim != null) {
            val other = if (aim.stick == "LS") "RS" else "LS"
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    aim.fallback,
                    presenter::setFallback,
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(Palette.SELECTED),
                        uncheckedColor = Color(Palette.TEXT_DIM),
                    ),
                )
                Text(
                    "Aim with $other when ${aim.stick} is idle",
                    Modifier.clickable { presenter.setFallback(!aim.fallback) },
                    color = Color(Palette.TEXT),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun SetRow(store: EditorStore, presenter: EditorPresenter, painter: GlyphPainter) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box {
            var open by remember { mutableStateOf(false) }
            EditorButton("Set ${store.current + 1}", { open = true }, trailingIcon = R.drawable.chevron_down)
            DropdownMenu(open, { open = false }) {
                store.sets.indices.forEach { i ->
                    DropdownMenuItem({ Text("Set ${i + 1}") }, {
                        open = false
                        presenter.showSet(i)
                    })
                }
            }
        }
        Spacer(Modifier.weight(1f))
        val chord = store.currentChord
        when {
            store.awaiting == Awaiting.SET_CHORD -> Label("Press activation chord buttons now")
            chord != null -> ChordGlyphs(chord, store.style, painter, 30.dp, Color(Palette.TEXT))
        }
        Spacer(Modifier.width(4.dp))
        EditorButton("Set activation chord", presenter::awaitSetChord)
        Box {
            var open by remember { mutableStateOf(false) }
            IconButton(R.drawable.ellipsis, "Set actions") { open = true }
            DropdownMenu(open, { open = false }) {
                DropdownMenuItem({ Text("Duplicate") }, {
                    open = false
                    presenter.duplicateSet()
                })
                if (store.sets.size > 1) {
                    DropdownMenuItem({ Text("Delete set") }, {
                        open = false
                        presenter.deleteSet()
                    })
                }
            }
        }
    }
}

private enum class SettingsPage { CLOSED, MAIN, STYLES }

@Composable
private fun SettingsMenu(store: EditorStore, presenter: EditorPresenter, painter: GlyphPainter) {
    Box {
        var page by remember { mutableStateOf(SettingsPage.CLOSED) }
        val close = { page = SettingsPage.CLOSED }
        IconButton(R.drawable.settings, "Settings") { page = SettingsPage.MAIN }
        DropdownMenu(page == SettingsPage.MAIN, close) {
            DropdownMenuItem(
                { Text("Button style: ${store.style.title}") },
                { page = SettingsPage.STYLES },
                trailingIcon = { Icon(painterResource(R.drawable.chevron_right), null, Modifier.size(20.dp)) },
            )
            DropdownMenuItem(
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Editor chord")
                        Spacer(Modifier.width(12.dp))
                        ChordGlyphs(store.editorChord, store.style, painter, 32.dp, Color.Unspecified)
                    }
                },
                {
                    close()
                    presenter.awaitEditorChord()
                },
            )
        }
        DropdownMenu(page == SettingsPage.STYLES, close) {
            ButtonStyle.entries.forEach { style ->
                DropdownMenuItem({ Text(style.title) }, {
                    close()
                    presenter.setStyle(style)
                })
            }
        }
    }
}

/** A chord as button glyphs joined by a text "+". */
@Composable
private fun ChordGlyphs(chord: String, style: ButtonStyle, painter: GlyphPainter, height: Dp, plusColour: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Chord.parts(chord).forEachIndexed { i, part ->
            if (i > 0) Text(" + ", color = plusColour, fontSize = 18.sp)
            Glyph(part, style, painter, height)
        }
    }
}

@Composable
private fun Glyph(part: String, style: ButtonStyle, painter: GlyphPainter, height: Dp) {
    val density = LocalDensity.current
    val margin = 2.dp
    val heightPx = with(density) { height.toPx() }
    val width = with(density) { painter.bindingWidth(listOf(part), style, heightPx).toDp() }
    Canvas(Modifier.size(width + margin * 2, height + margin * 2)) {
        drawIntoCanvas {
            painter.drawBinding(it.nativeCanvas, listOf(part), style, size.width / 2, size.height / 2, heightPx)
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = Color(Palette.TEXT), fontSize = 18.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun EditorButton(
    label: String,
    onClick: () -> Unit,
    tone: Tone = Tone.NORMAL,
    @DrawableRes trailingIcon: Int? = null,
) {
    ButtonSurface(tone, onClick) {
        Row(
            Modifier.defaultMinSize(minWidth = 48.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            val colour = when (tone) {
                Tone.PRIMARY -> Color.White
                Tone.DANGER -> Color(Palette.DANGER)
                Tone.NORMAL -> Color(Palette.TEXT)
            }
            Text(label, color = colour, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            if (trailingIcon != null) {
                Icon(painterResource(trailingIcon), null, Modifier.padding(start = 4.dp).size(20.dp), tint = colour)
            }
        }
    }
}

@Composable
private fun IconButton(@DrawableRes icon: Int, description: String, onClick: () -> Unit) {
    ButtonSurface(Tone.NORMAL, onClick) {
        Icon(
            painterResource(icon),
            description,
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp).size(24.dp),
            tint = Color(Palette.TEXT),
        )
    }
}

@Composable
private fun ButtonSurface(tone: Tone, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .padding(horizontal = 4.dp)
            .height(44.dp)
            .clip(shape)
            .background(Color(if (tone == Tone.PRIMARY) Palette.PRIMARY else Palette.SURFACE))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
