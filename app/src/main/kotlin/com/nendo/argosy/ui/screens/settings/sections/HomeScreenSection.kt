package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nendo.argosy.ui.components.ListSection
import com.nendo.argosy.ui.components.SectionFocusedScroll
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.SliderPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.theme.Dimens

@Composable
fun HomeScreenSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val listState = rememberLazyListState()
    val display = uiState.display
    val hasCustomImage = !display.useGameBackground
    val sliderOffset = if (hasCustomImage) 1 else 0

    val sections = if (hasCustomImage) {
        listOf(
            ListSection(listStartIndex = 0, listEndIndex = 5, focusStartIndex = 0, focusEndIndex = 4),
            ListSection(listStartIndex = 6, listEndIndex = 9, focusStartIndex = 5, focusEndIndex = 7),
            ListSection(listStartIndex = 10, listEndIndex = 11, focusStartIndex = 8, focusEndIndex = 8)
        )
    } else {
        listOf(
            ListSection(listStartIndex = 0, listEndIndex = 4, focusStartIndex = 0, focusEndIndex = 3),
            ListSection(listStartIndex = 5, listEndIndex = 8, focusStartIndex = 4, focusEndIndex = 6),
            ListSection(listStartIndex = 9, listEndIndex = 10, focusStartIndex = 7, focusEndIndex = 7)
        )
    }

    val focusToListIndex: (Int) -> Int = { focus ->
        when {
            focus <= 3 + sliderOffset -> focus + 1
            focus <= 6 + sliderOffset -> focus + 2
            else -> focus + 3
        }
    }

    SectionFocusedScroll(
        listState = listState,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = focusToListIndex,
        sections = sections
    )

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        item {
            HomeScreenSectionHeader("Background")
        }
        item {
            SwitchPreference(
                title = "Game Artwork",
                subtitle = "Use game cover as background",
                isEnabled = display.useGameBackground,
                isFocused = uiState.focusedIndex == 0,
                onToggle = { viewModel.setUseGameBackground(it) }
            )
        }
        if (!display.useGameBackground) {
            item {
                val subtitle = if (display.customBackgroundPath != null) {
                    "Custom image selected"
                } else {
                    "No image selected"
                }
                ActionPreference(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = "Custom Image",
                    subtitle = subtitle,
                    isFocused = uiState.focusedIndex == 1,
                    onClick = { viewModel.openBackgroundPicker() }
                )
            }
        }
        item {
            SliderPreference(
                title = "Blur",
                value = display.backgroundBlur / 10,
                minValue = 0,
                maxValue = 10,
                isFocused = uiState.focusedIndex == 1 + sliderOffset,
                onClick = { viewModel.cycleBackgroundBlur() }
            )
        }
        item {
            SliderPreference(
                title = "Saturation",
                value = display.backgroundSaturation / 10,
                minValue = 0,
                maxValue = 10,
                isFocused = uiState.focusedIndex == 2 + sliderOffset,
                onClick = { viewModel.cycleBackgroundSaturation() }
            )
        }
        item {
            SliderPreference(
                title = "Opacity",
                value = display.backgroundOpacity / 10,
                minValue = 0,
                maxValue = 10,
                isFocused = uiState.focusedIndex == 3 + sliderOffset,
                onClick = { viewModel.cycleBackgroundOpacity() }
            )
        }
        item {
            HomeScreenSectionHeader("Video Wallpaper")
        }
        item {
            SwitchPreference(
                title = "Show Video Wallpaper",
                subtitle = "Play video backgrounds on home screen",
                isEnabled = display.videoWallpaperEnabled,
                isFocused = uiState.focusedIndex == 4 + sliderOffset,
                onToggle = { viewModel.setVideoWallpaperEnabled(!display.videoWallpaperEnabled) }
            )
        }
        item {
            val delayText = when (display.videoWallpaperDelaySeconds) {
                0 -> "Instant"
                1 -> "1 second"
                else -> "${display.videoWallpaperDelaySeconds} seconds"
            }
            CyclePreference(
                title = "Delay Before Playback",
                value = delayText,
                isFocused = uiState.focusedIndex == 5 + sliderOffset,
                onClick = { viewModel.cycleVideoWallpaperDelay() }
            )
        }
        item {
            val hasCustomBgm = uiState.ambientAudio.enabled && uiState.ambientAudio.audioUri != null
            val effectiveMuted = hasCustomBgm || display.videoWallpaperMuted
            SwitchPreference(
                title = "Muted Playback",
                subtitle = if (hasCustomBgm) "Auto-muted while Custom BGM is active" else "Mute video audio",
                isEnabled = effectiveMuted,
                isFocused = uiState.focusedIndex == 6 + sliderOffset,
                onToggle = { if (!hasCustomBgm) viewModel.setVideoWallpaperMuted(!display.videoWallpaperMuted) }
            )
        }
        item {
            HomeScreenSectionHeader("Footer")
        }
        item {
            SwitchPreference(
                title = "Accent Color Footer",
                subtitle = "Use accent color for footer background",
                isEnabled = display.useAccentColorFooter,
                isFocused = uiState.focusedIndex == 7 + sliderOffset,
                onToggle = { viewModel.setUseAccentColorFooter(it) }
            )
        }
    }
}

@Composable
private fun HomeScreenSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = Dimens.spacingXs)
    )
}
