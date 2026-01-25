package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.nendo.argosy.ui.components.SectionFocusedScroll
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.BuiltinAudioState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens

internal sealed class BuiltinAudioItem(
    val key: String,
    val section: String,
    val visibleWhen: (BuiltinAudioState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = this !is Header

    class Header(key: String, section: String, val title: String) : BuiltinAudioItem(key, section)

    data object LowLatencyAudio : BuiltinAudioItem("lowLatencyAudio", "audio")
    data object Rumble : BuiltinAudioItem("rumble", "haptics")

    companion object {
        private val AudioHeader = Header("audioHeader", "audio", "Audio")
        private val HapticsHeader = Header("hapticsHeader", "haptics", "Haptics")

        val ALL: List<BuiltinAudioItem> = listOf(
            AudioHeader,
            LowLatencyAudio,
            HapticsHeader,
            Rumble
        )
    }
}

private val builtinAudioLayout = SettingsLayout<BuiltinAudioItem, BuiltinAudioState>(
    allItems = BuiltinAudioItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section }
)

internal fun builtinAudioMaxFocusIndex(state: BuiltinAudioState): Int =
    builtinAudioLayout.maxFocusIndex(state)

internal fun builtinAudioItemAtFocusIndex(index: Int, state: BuiltinAudioState): BuiltinAudioItem? =
    builtinAudioLayout.itemAtFocusIndex(index, state)

internal fun builtinAudioSections(state: BuiltinAudioState) =
    builtinAudioLayout.buildSections(state)

@Composable
fun BuiltinAudioSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
) {
    val listState = rememberLazyListState()
    val audioState = uiState.builtinAudio

    val visibleItems = remember(audioState) {
        builtinAudioLayout.visibleItems(audioState)
    }
    val sections = remember(audioState) {
        builtinAudioLayout.buildSections(audioState)
    }

    fun isFocused(item: BuiltinAudioItem): Boolean =
        uiState.focusedIndex == builtinAudioLayout.focusIndexOf(item, audioState)

    SectionFocusedScroll(
        listState = listState,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { builtinAudioLayout.focusToListIndex(it, audioState) },
        sections = sections
    )

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        items(visibleItems, key = { it.key }) { item ->
            when (item) {
                is BuiltinAudioItem.Header -> {
                    if (item.section != "audio") {
                        Spacer(modifier = Modifier.height(Dimens.spacingMd))
                    }
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(
                            start = Dimens.spacingSm,
                            top = Dimens.spacingXs,
                            bottom = Dimens.spacingXs
                        )
                    )
                }

                BuiltinAudioItem.LowLatencyAudio -> SwitchPreference(
                    title = "Low Latency Audio",
                    subtitle = "Reduce audio delay for better responsiveness",
                    isEnabled = audioState.lowLatencyAudio,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setBuiltinLowLatencyAudio(it) }
                )

                BuiltinAudioItem.Rumble -> SwitchPreference(
                    title = "Rumble",
                    subtitle = "Enable controller vibration feedback",
                    isEnabled = audioState.rumbleEnabled,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setBuiltinRumbleEnabled(it) }
                )
            }
        }
    }
}
