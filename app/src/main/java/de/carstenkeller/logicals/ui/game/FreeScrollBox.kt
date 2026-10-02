package de.carstenkeller.logicals.ui.game

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Container, dessen Inhalt in alle Richtungen (auch diagonal) verschoben werden kann.
 * Passt der Inhalt auf den Bildschirm, wird er zentriert.
 */
@Composable
fun FreeScrollBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val horizontal = rememberScrollState()
    val vertical = rememberScrollState()
    val scope = rememberCoroutineScope()
    val decay = rememberSplineBasedDecay<Float>()
    val flingJob = remember { arrayOfNulls<Job>(1) }

    BoxWithConstraints(
        modifier
            .clipToBounds()
            .pointerInput(Unit) {
                val tracker = VelocityTracker()
                detectDragGestures(
                    onDragStart = {
                        flingJob[0]?.cancel()
                        tracker.resetTracking()
                    },
                    onDragEnd = {
                        val velocity = tracker.calculateVelocity()
                        flingJob[0] = scope.launch {
                            launch { horizontal.fling(-velocity.x, decay) }
                            launch { vertical.fling(-velocity.y, decay) }
                        }
                    },
                ) { change, dragAmount ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    horizontal.dispatchRawDelta(-dragAmount.x)
                    vertical.dispatchRawDelta(-dragAmount.y)
                }
            },
    ) {
        val minWidth = maxWidth
        val minHeight = maxHeight
        Box(
            Modifier
                .horizontalScroll(horizontal, enabled = false)
                .verticalScroll(vertical, enabled = false),
        ) {
            Box(
                Modifier.sizeIn(minWidth = minWidth, minHeight = minHeight),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}

private suspend fun ScrollState.fling(velocity: Float, decay: DecayAnimationSpec<Float>) {
    if (abs(velocity) < 1f) return
    scroll {
        var last = 0f
        AnimationState(initialValue = 0f, initialVelocity = velocity).animateDecay(decay) {
            val delta = value - last
            last = value
            val consumed = scrollBy(delta)
            if (abs(delta - consumed) > 0.5f) cancelAnimation()
        }
    }
}
