package de.carstenkeller.logicals.ui.game

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import de.carstenkeller.logicals.ui.Texte
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Spielfeld-Container mit Zwei-Finger-Zoom und Verschieben in alle Richtungen.
 *
 * Der Inhalt wird nicht als Bild skaliert, sondern mit dem Zoomfaktor neu ausgelegt
 * (scharfe Schrift, korrekte Tipp-Ziele). [content] erhält den Zoomfaktor und die Größe
 * des sichtbaren Bereichs; passt der Inhalt hinein, wird er zentriert.
 */
@Composable
fun ZoomPanBox(
    modifier: Modifier = Modifier,
    minZoom: Float = 1f,
    maxZoom: Float = 4f,
    content: @Composable (zoom: Float, viewport: DpSize) -> Unit,
) {
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    // Zuletzt gemessene Inhalts- und Sichtgröße (für Begrenzung und Zoom-Mittelpunkt).
    val sizes = remember { IntArray(4) }
    val scope = rememberCoroutineScope()
    val decay = rememberSplineBasedDecay<Float>()
    val flingJob = remember { arrayOfNulls<Job>(1) }

    fun clampX(x: Float) = x.coerceIn(0f, (sizes[0] - sizes[2]).coerceAtLeast(0).toFloat())
    fun clampY(y: Float) = y.coerceIn(0f, (sizes[1] - sizes[3]).coerceAtLeast(0).toFloat())

    /** Zoomt um den Bildschirmpunkt [focus], sodass der Punkt darunter stehen bleibt. */
    fun zoomBy(factor: Float, focus: Offset) {
        val newZoom = (zoom * factor).coerceIn(minZoom, maxZoom)
        val f = newZoom / zoom
        if (f == 1f) return
        val padX = ((sizes[2] - sizes[0]) / 2f).coerceAtLeast(0f)
        val padY = ((sizes[3] - sizes[1]) / 2f).coerceAtLeast(0f)
        val contentX = offsetX + focus.x - padX
        val contentY = offsetY + focus.y - padY
        zoom = newZoom
        // Inhalt wächst (annähernd) linear mit dem Zoom; Größe vorab anpassen.
        sizes[0] = (sizes[0] * f).roundToInt()
        sizes[1] = (sizes[1] * f).roundToInt()
        offsetX = clampX(contentX * f - focus.x)
        offsetY = clampY(contentY * f - focus.y)
    }

    BoxWithConstraints(
        modifier
            .clipToBounds()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    flingJob[0]?.cancel()
                    val tracker = VelocityTracker()
                    var transforming = false
                    var panSum = Offset.Zero
                    var zoomSum = 1f
                    var lastPointers = 1
                    var multiTouch = false
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size != lastPointers) {
                            tracker.resetTracking()
                            lastPointers = pressed.size
                        }
                        if (pressed.size >= 2) multiTouch = true
                        val zoomChange = event.calculateZoom()
                        val pan = event.calculatePan()
                        if (!transforming) {
                            panSum += pan
                            zoomSum *= zoomChange
                            transforming = panSum.getDistance() > viewConfiguration.touchSlop ||
                                abs(1f - zoomSum) > 0.02f
                        }
                        if (transforming) {
                            if (pressed.size >= 2 && zoomChange != 1f) {
                                zoomBy(zoomChange, event.calculateCentroid(useCurrent = true))
                            }
                            offsetX = clampX(offsetX - pan.x)
                            offsetY = clampY(offsetY - pan.y)
                            pressed.firstOrNull()?.let { tracker.addPosition(it.uptimeMillis, it.position) }
                            // Verbraucht die Bewegung, damit Felder kein Tippen auslösen.
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })

                    if (transforming && !multiTouch) {
                        val velocity = tracker.calculateVelocity()
                        flingJob[0] = scope.launch {
                            launch {
                                var last = 0f
                                AnimationState(0f, -velocity.x).animateDecay(decay) {
                                    offsetX = clampX(offsetX + value - last)
                                    last = value
                                }
                            }
                            launch {
                                var last = 0f
                                AnimationState(0f, -velocity.y).animateDecay(decay) {
                                    offsetY = clampY(offsetY + value - last)
                                    last = value
                                }
                            }
                        }
                    }
                }
            },
    ) {
        val viewport = DpSize(maxWidth, maxHeight)
        Box(
            Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(Constraints())
                val vw = constraints.maxWidth
                val vh = constraints.maxHeight
                sizes[0] = placeable.width
                sizes[1] = placeable.height
                sizes[2] = vw
                sizes[3] = vh
                layout(vw, vh) {
                    // Kleiner als der Sichtbereich: zentrieren, sonst verschoben platzieren.
                    val x = if (placeable.width <= vw) (vw - placeable.width) / 2 else -clampX(offsetX).roundToInt()
                    val y = if (placeable.height <= vh) (vh - placeable.height) / 2 else -clampY(offsetY).roundToInt()
                    placeable.place(x, y)
                }
            },
        ) {
            content(zoom, viewport)
        }
        if (abs(zoom - 1f) > 0.01f) {
            FilledTonalButton(
                onClick = {
                    zoom = 1f
                    offsetX = 0f
                    offsetY = 0f
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
            ) { Text(Texte.ZOOM_RESET) }
        }
    }
}
