package app.padmap.layout

/** A touch control. x, y are fractions of the display's width and height; radius is a fraction of its height. */
sealed interface Control {
    val x: Double
    val y: Double
    val radius: Double

    fun moved(x: Double, y: Double): Control

    fun resized(radius: Double): Control

    /** Touches (x, y) while the button is held. */
    data class Tap(val button: String, override val x: Double, override val y: Double, override val radius: Double) :
        Control {
        override fun moved(x: Double, y: Double) = copy(x = x, y = y)

        override fun resized(radius: Double) = copy(radius = radius)
    }

    /** Touches down at the centre when the stick leaves its deadzone and follows the stick within radius. */
    data class Joystick(val stick: String, override val x: Double, override val y: Double, override val radius: Double) :
        Control {
        override fun moved(x: Double, y: Double) = copy(x = x, y = y)

        override fun resized(radius: Double) = copy(radius = radius)
    }

    /**
     * MOBA skill: touches down at (x, y) while the button is held, drags by the stick within radius, lifts to cast.
     * With fallback, the other stick aims while this one is centred.
     */
    data class Aim(
        val button: String,
        val stick: String,
        val fallback: Boolean,
        override val x: Double,
        override val y: Double,
        override val radius: Double,
    ) : Control {
        override fun moved(x: Double, y: Double) = copy(x = x, y = y)

        override fun resized(radius: Double) = copy(radius = radius)
    }
}
