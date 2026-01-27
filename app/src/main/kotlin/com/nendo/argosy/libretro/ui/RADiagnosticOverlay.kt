package com.nendo.argosy.libretro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RADiagnosticOverlay(
    isConnected: Boolean,
    isHardcore: Boolean,
    earnedCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier
) {
    val bgColor = Color.Black.copy(alpha = 0.7f)
    val textColor = Color.White

    Column(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "RA: ${if (isConnected) "CONNECTED" else "OFFLINE"}",
            color = if (isConnected) Color(0xFF4CAF50) else Color(0xFFFF5722),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "Mode: ${if (isHardcore) "HARDCORE" else "CASUAL"}",
            color = if (isHardcore) Color(0xFFFFD700) else textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        if (totalCount > 0) {
            Text(
                text = "Achievements: $earnedCount/$totalCount",
                color = textColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
