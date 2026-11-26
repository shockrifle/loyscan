package com.bdaniel.loyscan.domain

/** How a store's logo is drawn on its brand colour (#240), when As is would blend into it. */
enum class LogoStyle {
    /** The whole logo in [LogoTreatment.tint], its shape and inner detail kept by each pixel's brightness. */
    SINGLE_COLOUR,
}

/**
 * How a store's logo is drawn (#240): its [style] in [tint] (`#RRGGBB`), `null` style being As is; [outline] is drawn
 * around it with any style. Setting a new logo, or removing it, resets it to [AS_IS].
 */
data class LogoTreatment(
    val style: LogoStyle? = null,
    val tint: String? = null,
    val outline: Boolean = false,
) {
    companion object {
        val AS_IS = LogoTreatment()
    }
}
