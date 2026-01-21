package com.nendo.argosy.ui.screens.gamedetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.Dimens

@Composable
fun GameDetailSkeleton() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.5f),
                        Color.Black.copy(alpha = 0.9f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimens.spacingXl)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXl)
            ) {
                Box(
                    modifier = Modifier
                        .width(200.dp)
                        .height(280.dp)
                        .clip(RoundedCornerShape(Dimens.radiusLg))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(Dimens.radiusMd))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    )
                    Spacer(modifier = Modifier.height(Dimens.spacingMd))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.3f)
                            .height(24.dp)
                            .clip(RoundedCornerShape(Dimens.radiusSm))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    )
                }
            }
        }
    }
}
