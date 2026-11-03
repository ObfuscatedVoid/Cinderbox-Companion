package com.sdvsync.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * R44 modern Earthy system: app-owned text and shapes, no third-party art.
 * Classic intentionally keeps its pixel typography and square geometry.
 * Uses sp for all text; no fixed-height text containers or fontScale overrides.
 */
val R44ProductShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

val R44ProductTypography = Typography.copy(
    displayLarge = Typography.displayLarge.copy(
        fontFamily = NunitoFamily, fontWeight = FontWeight.Bold,
        fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp),
    displayMedium = Typography.displayMedium.copy(
        fontFamily = NunitoFamily, fontWeight = FontWeight.Bold,
        fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = 0.sp),
    displaySmall = Typography.displaySmall.copy(
        fontFamily = NunitoFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp, lineHeight = 34.sp, letterSpacing = 0.sp),
    headlineLarge = Typography.headlineLarge.copy(
        fontFamily = NunitoFamily, fontWeight = FontWeight.Bold,
        fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp),
    headlineMedium = Typography.headlineMedium.copy(
        fontFamily = NunitoFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
    headlineSmall = Typography.headlineSmall.copy(
        fontFamily = NunitoFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleLarge = Typography.titleLarge.copy(
        fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 30.sp),
    titleMedium = Typography.titleMedium.copy(
        fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 26.sp),
    titleSmall = Typography.titleSmall.copy(
        fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = Typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = Typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = Typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp)
)
