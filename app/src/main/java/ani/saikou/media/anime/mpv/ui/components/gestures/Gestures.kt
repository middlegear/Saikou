package ani.saikou.media.anime.mpv.ui.components.gestures

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@Composable
fun PlayerGestures(
    isControlsLocked: Boolean,
    isDoubleTapEnabled: Boolean,
    isVerticalSwipeEnabled: Boolean,
    holdToFastForward: Boolean,
    onSingleTap: () -> Unit,
    onDoubleTapLeft: () -> Unit,
    onDoubleTapRight: () -> Unit,
    onBrightnessGestureStart: () -> Unit,
    onBrightnessChanged: (deltaFraction: Float) -> Unit,
    onVolumeGestureStart: () -> Unit,
    onVolumeChanged: (deltaFraction: Float) -> Unit,
    onSpeedChanged: (speed: Float) -> Unit,
    modifier: Modifier = Modifier,
    gestureSensitivity: Float = 2f
) {
    val currentSingleTap by rememberUpdatedState(onSingleTap)
    val currentDoubleTapLeft by rememberUpdatedState(onDoubleTapLeft)
    val currentDoubleTapRight by rememberUpdatedState(onDoubleTapRight)
    val currentBrightnessStart by rememberUpdatedState(onBrightnessGestureStart)
    val currentBrightnessChanged by rememberUpdatedState(onBrightnessChanged)
    val currentVolumeStart by rememberUpdatedState(onVolumeGestureStart)
    val currentVolumeChanged by rememberUpdatedState(onVolumeChanged)
    val currentSpeedChanged by rememberUpdatedState(onSpeedChanged)

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(isControlsLocked, isDoubleTapEnabled) {
                if (isControlsLocked) return@pointerInput

                detectTapGestures(
                    onTap = {
                        currentSingleTap()
                    },
                    onDoubleTap = { offset ->
                        if (isDoubleTapEnabled) {
                            if (offset.x < size.width / 2f) {
                                currentDoubleTapLeft()
                            } else {
                                currentDoubleTapRight()
                            }
                        }
                    }
                )
            }
            .pointerInput(isControlsLocked, isVerticalSwipeEnabled, holdToFastForward) {
                if (isControlsLocked) return@pointerInput

                val dragSlopPx = 30.dp.toPx()
                val longPressTimeout = viewConfiguration.longPressTimeoutMillis - 100L

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)

                    val isNearTopEdge = down.position.y < 40.dp.toPx()
                    val isNearBottomEdge =
                        down.position.y > size.height - 48.dp.toPx()
                    val isNearLeftEdge =
                        down.position.x <= size.width * 0.05f
                    val isNearRightEdge =
                        down.position.x >= size.width * 0.95f

                    if (isNearTopEdge || isNearBottomEdge || isNearLeftEdge || isNearRightEdge) {
                        return@awaitEachGesture
                    }

                    val touchStartX = down.position.x
                    val touchStartY = down.position.y
                    val isLeftSide = touchStartX < size.width / 2f
                    val pointerId = down.id

                    var dragStarted = false
                    var isSpeedBoosted = false
                    if (!holdToFastForward) {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change =
                                event.changes.firstOrNull { it.id == pointerId }
                                    ?: break

                            if (!change.pressed) {
                                break
                            }

                            val totalAccumulatedY =
                                change.position.y - touchStartY
                            val totalAccumulatedX =
                                change.position.x - touchStartX

                            if (
                                isVerticalSwipeEnabled &&
                                abs(totalAccumulatedY) > dragSlopPx &&
                                abs(totalAccumulatedY) > abs(totalAccumulatedX)
                            ) {
                                dragStarted = true

                                if (isLeftSide) {
                                    currentBrightnessStart()
                                } else {
                                    currentVolumeStart()
                                }

                                break
                            }
                        }
                    } else {
                        val gestureResult = withTimeoutOrNull(longPressTimeout) {
                            while (true) {
                                val event =
                                    awaitPointerEvent(PointerEventPass.Initial)

                                val change =
                                    event.changes.firstOrNull { it.id == pointerId }
                                        ?: break

                                if (!change.pressed) {
                                    return@withTimeoutOrNull false
                                }

                                val totalAccumulatedY =
                                    change.position.y - touchStartY
                                val totalAccumulatedX =
                                    change.position.x - touchStartX

                                if (
                                    isVerticalSwipeEnabled &&
                                    abs(totalAccumulatedY) > dragSlopPx &&
                                    abs(totalAccumulatedY) > abs(totalAccumulatedX)
                                ) {
                                    dragStarted = true

                                    if (isLeftSide) {
                                        currentBrightnessStart()
                                    } else {
                                        currentVolumeStart()
                                    }

                                    return@withTimeoutOrNull false
                                }
                            }

                            false
                        }

                        if (gestureResult == null) {
                            isSpeedBoosted = true
                            currentSpeedChanged(2.0f)
                        }
                    }

                    if (!dragStarted && !isSpeedBoosted) {
                        return@awaitEachGesture
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val change =
                            event.changes.firstOrNull { it.id == pointerId }
                                ?: break

                        if (!change.pressed) {
                            if (isSpeedBoosted) {
                                change.consume()
                                currentSpeedChanged(1.0f)
                            }
                            break
                        }

                        val dragAmount = change.positionChange()

                        if (
                            dragStarted &&
                            !isSpeedBoosted &&
                            isVerticalSwipeEnabled
                        ) {
                            if (abs(dragAmount.x) <= abs(dragAmount.y)) {
                                change.consume()

                                val deltaFraction =
                                    (-dragAmount.y / size.height) *
                                            gestureSensitivity

                                if (isLeftSide) {
                                    currentBrightnessChanged(deltaFraction)
                                } else {
                                    currentVolumeChanged(deltaFraction)
                                }
                            }
                        }

                        if (isSpeedBoosted) {
                            change.consume()
                        }
                    }
                }
            }
    )
}