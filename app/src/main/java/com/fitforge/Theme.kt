package com.fitforge

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

object FitForgeThemeColors {
    // Backgrounds & Surfaces (Obsidian Midnight / Titanium Slate)
    val Background = Color(0xFF090C10)
    val Surface = Color(0xFF11151F)
    val SurfaceVariant = Color(0xFF181F2C)
    val SurfaceElevated = Color(0xFF1E2638)
    val Outline = Color(0xFF263042)
    val OutlineVariant = Color(0xFF1A212E)

    // Modern High-Performance Accents
    val Primary = Color(0xFF22D3EE)          // Luminous Electric Cyan
    val PrimaryContainer = Color(0xFF083344)
    val OnPrimary = Color(0xFF082F49)
    val OnPrimaryContainer = Color(0xFFCFFAFE)

    val Secondary = Color(0xFFFF6D3B)        // Energetic Sunset Coral (Energy & Workouts)
    val SecondaryContainer = Color(0xFF431407)
    val OnSecondary = Color(0xFFFFFFFF)
    val OnSecondaryContainer = Color(0xFFFFEDD5)

    val Tertiary = Color(0xFFA78BFA)         // Iris / Intelligence Violet (AI Coach)
    val TertiaryContainer = Color(0xFF2E1065)
    val OnTertiary = Color(0xFFFFFFFF)
    val OnTertiaryContainer = Color(0xFFEDE9FE)

    // Athletic Nutrient Accents
    val Protein = Color(0xFF34D399)          // Clean Emerald Mint
    val Carbs = Color(0xFF38BDF8)            // Sky Electric Blue
    val Fat = Color(0xFFFBBF24)              // Amber Gold
    val Calories = Color(0xFFFF6D3B)         // Coral Flame

    // Text & Content
    val TextPrimary = Color(0xFFF8FAFC)      // Crisp High-Contrast White
    val TextSecondary = Color(0xFF94A3B8)    // Refined Cool Slate
    val TextMuted = Color(0xFF64748B)        // Muted Slate

    // Feedback
    val Error = Color(0xFFF87171)
    val Success = Color(0xFF34D399)
}

val ModernDarkColorScheme = darkColorScheme(
    primary = FitForgeThemeColors.Primary,
    onPrimary = FitForgeThemeColors.OnPrimary,
    primaryContainer = FitForgeThemeColors.PrimaryContainer,
    onPrimaryContainer = FitForgeThemeColors.OnPrimaryContainer,
    secondary = FitForgeThemeColors.Secondary,
    onSecondary = FitForgeThemeColors.OnSecondary,
    secondaryContainer = FitForgeThemeColors.SecondaryContainer,
    onSecondaryContainer = FitForgeThemeColors.OnSecondaryContainer,
    tertiary = FitForgeThemeColors.Tertiary,
    onTertiary = FitForgeThemeColors.OnTertiary,
    tertiaryContainer = FitForgeThemeColors.TertiaryContainer,
    onTertiaryContainer = FitForgeThemeColors.OnTertiaryContainer,
    background = FitForgeThemeColors.Background,
    onBackground = FitForgeThemeColors.TextPrimary,
    surface = FitForgeThemeColors.Surface,
    onSurface = FitForgeThemeColors.TextPrimary,
    surfaceVariant = FitForgeThemeColors.SurfaceVariant,
    onSurfaceVariant = FitForgeThemeColors.TextSecondary,
    outline = FitForgeThemeColors.Outline,
    outlineVariant = FitForgeThemeColors.OutlineVariant,
    error = FitForgeThemeColors.Error
)
