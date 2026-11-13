package com.metrolist.music.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.unit.dp

object DesignTokens {
    object Spacing {
        val none = 0.dp
        val extraSmall = 4.dp
        val small = 8.dp
        val medium = 16.dp
        val large = 24.dp
        val extraLarge = 32.dp
        val huge = 48.dp
    }
    
    object Easing {
        val emphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
        val emphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
        val standard = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    }
}
