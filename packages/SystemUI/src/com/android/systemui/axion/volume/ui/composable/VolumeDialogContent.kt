/*
 * Copyright (C) 2025-2026 Axion OS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.android.systemui.axion.volume.ui.composable

import android.media.AudioManager
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.systemui.axion.volume.domain.model.VolumeSliderItem
import com.android.systemui.axion.volume.ui.viewmodel.AxionVolumeDialogUiState
import com.android.systemui.axion.volume.ui.viewmodel.AxionVolumeDialogViewModel
import com.android.systemui.res.R
import kotlinx.coroutines.delay

private val CardShape = RoundedCornerShape(DialogCornerRadius)
private const val PANEL_TRANSITION_DURATION_MS = 300

@Composable
fun AxionVolumeDialogContent(viewModel: AxionVolumeDialogViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isVisible = uiState.isVisible
    val isLeft = uiState.isLeftSide
    val isExpanded = uiState.isExpanded

    val motionScheme = MaterialTheme.motionScheme
    val visibilityAnimatable = remember { Animatable(0f) }
    val visibilityProgress = visibilityAnimatable.value

    LaunchedEffect(isVisible) {
        if (isVisible) {
            visibilityAnimatable.snapTo(0f)
            viewModel.collapseIfExpanded()
            visibilityAnimatable.animateTo(1f, tween(200))
        } else {
            runCatching {
                visibilityAnimatable.animateTo(0f, tween(200))
            }
            viewModel.resetState()
            viewModel.onDismissAnimationEnd()
        }
    }

    val overscrollOffset by viewModel.overscrollOffset.collectAsStateWithLifecycle()
    val animatedOverscroll by animateFloatAsState(
        targetValue = overscrollOffset,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "overscroll"
    )

    val view = LocalView.current
    val currentView by rememberUpdatedState(view)
    LaunchedEffect(Unit) {
        viewModel.volumeKeyHapticTrigger.collect {
            currentView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    val sliderItems by viewModel.sliderItems.collectAsStateWithLifecycle()
    val currStreamCount by viewModel.currStreamCount.collectAsStateWithLifecycle()
    val edgeAlignment = if (isLeft) Alignment.CenterStart else Alignment.CenterEnd
    val slideDirection = if (isLeft) -1 else 1

    var showCollapsed by remember { mutableStateOf(!isExpanded) }
    var showExpanded by remember { mutableStateOf(isExpanded) }

    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            showCollapsed = false
            delay(PANEL_TRANSITION_DURATION_MS.toLong())
            showExpanded = true
        } else {
            showExpanded = false
            delay(PANEL_TRANSITION_DURATION_MS.toLong())
            showCollapsed = true
        }
    }

    Box(
        modifier = Modifier
            .graphicsLayer {
                alpha = visibilityProgress
                translationX = slideDirection * 24f * (1f - visibilityProgress)
                translationY = animatedOverscroll
            },
        contentAlignment = edgeAlignment
    ) {
        AnimatedVisibility(
            visible = showCollapsed,
            enter = slideInHorizontally(
                initialOffsetX = { slideDirection * it },
                animationSpec = tween(PANEL_TRANSITION_DURATION_MS)
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { slideDirection * it },
                animationSpec = tween(PANEL_TRANSITION_DURATION_MS)
            )
        ) {
            CollapsedPanelContent(
                viewModel = viewModel,
                uiState = uiState,
                isLeft = isLeft
            )
        }

        AnimatedVisibility(
            visible = showExpanded,
            enter = slideInHorizontally(
                initialOffsetX = { slideDirection * it },
                animationSpec = tween(PANEL_TRANSITION_DURATION_MS)
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { slideDirection * it },
                animationSpec = tween(PANEL_TRANSITION_DURATION_MS)
            )
        ) {
            ExpandedPanelContent(
                viewModel = viewModel,
                uiState = uiState,
                sliderItems = sliderItems,
                currStreamCount = currStreamCount
            )
        }
    }
}

@Composable
private fun CollapsedPanelContent(
    viewModel: AxionVolumeDialogViewModel,
    uiState: AxionVolumeDialogUiState,
    isLeft: Boolean,
) {
    val dialogState = uiState.dialogState
    val ringerMode = dialogState.ringerMode
    val activeStream = dialogState.activeStream
    val streamModel = dialogState.volumeStreams.find { it.streamType == activeStream }
        ?: dialogState.volumeStreams.find { it.streamType == AudioManager.STREAM_MUSIC }
    val appVolumes = dialogState.appVolumes

    val surfaceBright = MaterialTheme.colorScheme.surfaceBright

    var isCardVisible by remember { mutableStateOf(appVolumes.isNotEmpty()) }
    var lastAppVolumes by remember { mutableStateOf(appVolumes) }

    LaunchedEffect(appVolumes) {
        if (appVolumes.isNotEmpty()) lastAppVolumes = appVolumes
    }
    LaunchedEffect(appVolumes.isNotEmpty()) {
        if (appVolumes.isNotEmpty()) {
            isCardVisible = true
        } else {
            isCardVisible = false
        }
    }

    val mainContent: @Composable () -> Unit = {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .requiredWidth(CollapsedPanelWidth)
                .pointerInput(Unit) { detectTapGestures {} }
        ) {
            RingerCircleButton(
                ringerMode = ringerMode,
                onClick = {
                    viewModel.rescheduleTimeout()
                    viewModel.cycleRingerMode()
                }
            )

            Spacer(modifier = Modifier.height(RingerToSliderGap))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(CardShape)
                    .background(surfaceBright)
                    .padding(bottom = ContentSpacingSmall)
            ) {
                if (streamModel != null) {
                    SliderColumn(
                        stream = streamModel,
                        viewModel = viewModel,
                        touchWidth = CollapsedPanelWidth,
                        showPercentage = false
                    )
                } else {
                    Spacer(
                        modifier = Modifier.height(
                            SliderTopPadding + SliderTrackHeight + TrackToIconSpacing + SliderIconContainerSize
                        )
                    )
                }

                Spacer(modifier = Modifier.height(IconToExpandSpacing))

                Box(
                    modifier = Modifier
                        .size(SliderIconContainerSize)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            viewModel.rescheduleTimeout()
                            viewModel.toggleExpanded()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowRight,
                        contentDescription = "Expand",
                        modifier = Modifier
                            .size(HeaderIconSize)
                            .graphicsLayer { scaleX = if (isLeft) 1f else -1f },
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }

    if (isCardVisible) {
        val appCards: @Composable () -> Unit = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(RingerToSliderGap),
                verticalAlignment = Alignment.Top
            ) {
                lastAppVolumes.take(MaxVisibleSliders).forEach { app ->
                    key(app.packageName) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(modifier = Modifier.height(RingerCircleSize + RingerToSliderGap))
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(CardShape)
                                    .background(surfaceBright)
                                    .padding(bottom = ContentSpacingSmall)
                                    .pointerInput(Unit) { detectTapGestures {} }
                            ) {
                                AppVolumeSlider(
                                    appVolume = app,
                                    viewModel = viewModel,
                                    touchWidth = CollapsedPanelWidth,
                                    showPercentage = false
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(RingerToSliderGap),
            verticalAlignment = Alignment.Top
        ) {
            if (isLeft) {
                mainContent()
                appCards()
            } else {
                appCards()
                mainContent()
            }
        }
    } else {
        mainContent()
    }
}

@Composable
private fun ExpandedPanelContent(
    viewModel: AxionVolumeDialogViewModel,
    uiState: AxionVolumeDialogUiState,
    sliderItems: List<VolumeSliderItem>,
    currStreamCount: Int,
) {
    val dialogState = uiState.dialogState
    val ringerMode = dialogState.ringerMode
    val supportedModes = dialogState.supportedRingerModes
    val streamItems = sliderItems.filterIsInstance<VolumeSliderItem.Stream>()
    val streamCount = currStreamCount.coerceIn(1, MaxVisibleSliders)
    val panelWidth = (8 + 56 * streamCount).dp
    val collapseButtonEdgePadding =
        SliderRowHorizontalPadding + (SliderWidthExpanded - SliderIconContainerSize) / 2

    val surfaceBright = MaterialTheme.colorScheme.surfaceBright

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .requiredWidth(panelWidth)
            .pointerInput(Unit) { detectTapGestures {} }
    ) {
        Box(
            modifier = Modifier
                .clip(CardShape)
                .background(surfaceBright)
        ) {
            RingerRow(
                ringerMode = ringerMode,
                supportedModes = supportedModes,
                isLeftSide = uiState.isLeftSide,
                panelWidth = panelWidth,
                onModeSelected = { mode ->
                    viewModel.rescheduleTimeout()
                    viewModel.setRingerMode(mode)
                }
            )
        }

        Spacer(modifier = Modifier.height(RingerToSliderGap))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(CardShape)
                .background(surfaceBright)
                .padding(bottom = ContentSpacingSmall)
        ) {
            VolumeSlidersRow(
                viewModel = viewModel,
                sliderItems = streamItems,
                showPercentage = false,
                sliderCount = streamCount
            )

            Spacer(modifier = Modifier.height(IconToExpandSpacing))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SeeMoreHeight),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.see_more_title).uppercase(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        viewModel.rescheduleTimeout()
                        viewModel.onSeeMoreClick()
                    },
                )

                Box(
                    modifier = Modifier
                        .align(if (uiState.isLeftSide) Alignment.CenterStart else Alignment.CenterEnd)
                        .padding(
                            start = if (uiState.isLeftSide) collapseButtonEdgePadding else 0.dp,
                            end = if (uiState.isLeftSide) 0.dp else collapseButtonEdgePadding,
                        )
                        .size(SliderIconContainerSize)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            viewModel.rescheduleTimeout()
                            viewModel.collapseIfExpanded()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowRight,
                        contentDescription = "Collapse",
                        modifier = Modifier
                            .size(HeaderIconSize)
                            .graphicsLayer { scaleX = if (uiState.isLeftSide) -1f else 1f },
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
