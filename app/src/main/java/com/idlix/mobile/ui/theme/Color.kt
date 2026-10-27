package com.idlix.mobile.ui.theme

import androidx.compose.ui.graphics.Color

// B&W-minimalist tokens (matches TV): true-black base, white primary,
// #AAAAAA secondary text, #1F1F1F surfaces / #2A2A2A variant. RED (#E50914)
// is fenced to the wordmark, selected indicator, progress, badges, Top-10
// numerals, and focused-primary states ONLY. No new colors.
val IdlixRed = Color(0xFFE50914)
val IdlixRedDark = Color(0xFFB81D24)

// OLED true-black base with minimal-elevation gray surfaces.
// Depth reads through hierarchy: base #000 < surface #1F1F1F < variant #2A2A2A.
val BackgroundDark = Color(0xFF000000)
val SurfaceDark = Color(0xFF1F1F1F)
val SurfaceVariantDark = Color(0xFF2A2A2A)
val BackgroundElevated = SurfaceDark

val TextWhite = Color(0xFFFFFFFF)
val TextMuted = Color(0xFFAAAAAA)
val TextDim = Color(0xFFAAAAAA)
val TextSecondary = TextMuted

// Compact metadata row tokens
// Former match green: remapped to white per B&W fence. Name kept for
// source compatibility — do not reintroduce a green value.
val MatchGreen = Color(0xFFFFFFFF)
val AgeBadgeBorder = Color(0xFFAAAAAA)

// Former gold (star ratings): remapped to white per B&W fence. Name kept for
// source compatibility — do not reintroduce a gold value.
val RatingGold = Color(0xFFFFFFFF)
val GoldStar = RatingGold
val ProgressRed = Color(0xFFE50914)
val BorderDark = Color(0xFF2A2A2A)
