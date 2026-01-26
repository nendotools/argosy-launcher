package com.nendo.argosy.libretro.ui.cheats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.theme.Dimens

@Composable
fun ActivatedTab(
    cheats: List<CheatDisplayItem>,
    focusedIndex: Int,
    onToggleCheat: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    if (cheats.isNotEmpty()) {
        FocusedScroll(listState = listState, focusedIndex = focusedIndex)
    }

    if (cheats.isEmpty()) {
        Box(
            modifier = modifier.padding(Dimens.spacingLg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No cheats enabled",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.padding(Dimens.spacingSm).focusProperties { canFocus = false },
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            itemsIndexed(cheats, key = { _, cheat -> cheat.id }) { index, cheat ->
                CheatRow(
                    title = cheat.description,
                    isEnabled = cheat.enabled,
                    isFocused = index == focusedIndex,
                    onToggle = { onToggleCheat(cheat.id, it) }
                )
            }
        }
    }
}
