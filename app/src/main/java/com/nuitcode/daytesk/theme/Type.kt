package com.nuitcode.daytesk.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object DayteskTypography {
    val display = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
    )
    val h1 = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
    )
    val h2 = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
    )
    val h3 = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
    )
    val bodyLg = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp,
    )
    val bodyMd = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    )
    val bodySm = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
    )
    val label = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
    )
    val caption = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
    )
    val badge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
    )
    val tiny = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
    )
    val statsNumber = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
    )
}

// M3 Typography mapping using Daytesk styles
val MaterialTypography = Typography(
    displayLarge = DayteskTypography.display,
    headlineLarge = DayteskTypography.h1,
    headlineMedium = DayteskTypography.h2,
    headlineSmall = DayteskTypography.h3,
    bodyLarge = DayteskTypography.bodyLg,
    bodyMedium = DayteskTypography.bodyMd,
    bodySmall = DayteskTypography.bodySm,
    labelLarge = DayteskTypography.label,
    labelMedium = DayteskTypography.caption,
    labelSmall = DayteskTypography.badge,
)
