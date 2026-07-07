package com.vrhub.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Corner radius per [CardCorner] preset. ROUNDED matches the original hardcoded
 * GameListItem card radius (12.dp), so the default look is unchanged.
 */
fun cardCornerShape(cardCorner: CardCorner): Shape = RoundedCornerShape(
    when (cardCorner) {
        CardCorner.SHARP -> 4.dp
        CardCorner.ROUNDED -> 12.dp
        CardCorner.EXTRA_ROUNDED -> 24.dp
    }
)

/**
 * Vertical spacing multiplier per [Density] preset. COMFORTABLE (1.0) matches
 * the original hardcoded paddings, so the default look is unchanged.
 */
fun Density.spacingFactor(): Float = when (this) {
    Density.COMFORTABLE -> 1f
    Density.COMPACT -> 0.75f
}
