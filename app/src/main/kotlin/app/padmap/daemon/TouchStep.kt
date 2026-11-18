package app.padmap.daemon

internal data class Pointer(val id: Int, val x: Float, val y: Float)

/** One change to the gesture. [pointers] are the touches down after it, or before it for [Up] and [Cancel]. */
internal sealed interface TouchStep {
    val pointers: List<Pointer>

    data class Down(val id: Int, override val pointers: List<Pointer>) : TouchStep

    data class Move(override val pointers: List<Pointer>) : TouchStep

    data class Up(val id: Int, override val pointers: List<Pointer>) : TouchStep

    data class Cancel(override val pointers: List<Pointer>) : TouchStep
}

internal fun interface TouchOutput {
    /** False if the touches were taken away or the output broke; the rest of [steps] is dropped. */
    fun send(steps: List<TouchStep>): Boolean
}
