package com.nodeplayer.ui.component.backdrop

import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import com.kyant.backdrop.Backdrop as KyantBackdrop
import com.kyant.backdrop.BackdropEffectScope as KyantBackdropEffectScope
import com.kyant.backdrop.backdrops.LayerBackdrop as KyantLayerBackdrop

/**
 * Temporary compatibility shims bridging legacy `com.nodeplayer.ui.component.backdrop`
 * constructs to the canonical upstream `com.kyant.backdrop` engine.
 *
 * Staged per D-02. Callers should migrate directly to `com.kyant.backdrop.*`.
 * These shims and legacy backdrop files will be purged in Phase 1 Plan 02.
 */

typealias CanonicalBackdrop = com.kyant.backdrop.Backdrop
typealias CanonicalBackdropEffectScope = com.kyant.backdrop.BackdropEffectScope
typealias CanonicalLayerBackdrop = com.kyant.backdrop.backdrops.LayerBackdrop

/**
 * Adapts a legacy [Backdrop] to the canonical [com.kyant.backdrop.Backdrop] interface.
 */
fun Backdrop.asKyantBackdrop(): KyantBackdrop {
    val legacy = this
    return object : KyantBackdrop {
        override val isCoordinatesDependent: Boolean
            get() = legacy.isCoordinatesDependent

        override fun DrawScope.drawBackdrop(
            density: Density,
            coordinates: LayoutCoordinates?,
            layerBlock: (GraphicsLayerScope.() -> Unit)?
        ) {
            with(legacy) {
                drawBackdrop(density, coordinates, layerBlock)
            }
        }
    }
}

/**
 * Adapts a canonical [com.kyant.backdrop.Backdrop] to the legacy [Backdrop] interface.
 */
fun KyantBackdrop.asLegacyBackdrop(): Backdrop {
    val canonical = this
    return object : Backdrop {
        override val isCoordinatesDependent: Boolean
            get() = canonical.isCoordinatesDependent

        override fun DrawScope.drawBackdrop(
            density: Density,
            coordinates: LayoutCoordinates?,
            layerBlock: (GraphicsLayerScope.() -> Unit)?
        ) {
            with(canonical) {
                drawBackdrop(density, coordinates, layerBlock)
            }
        }
    }
}
