package com.neuralroots.kosh.ui.screens.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neuralroots.kosh.ui.theme.Cream
import com.neuralroots.kosh.ui.theme.Ember
import com.neuralroots.kosh.ui.theme.Ink
import com.neuralroots.kosh.ui.theme.MonoLabel
import com.neuralroots.kosh.ui.theme.SerifFamily
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

private val EaseOut = CubicBezierEasing(0.2f, 0.7f, 0.2f, 1f) // nullchat's ease
private val Overshoot = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

/** Total on-screen time of the opening animation. */
const val SPLASH_MS = 2000

/**
 * 2-second opening animation drawn over the app while it loads:
 *  0–700ms ring draws in · 450–1000ms ember dot pops and glows · 650–1250ms ₹ strokes + "Kosh"
 *  rise in · 1250–1650ms tagline · 1650–2000ms everything fades out. Tap to skip.
 */
@Composable
fun KoshSplash(onFinished: () -> Unit) {
    val ring = remember { Animatable(0f) }
    val dot = remember { Animatable(0f) }
    val mark = remember { Animatable(0f) }
    val title = remember { Animatable(0f) }
    val tagline = remember { Animatable(0f) }
    val exit = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            val a = async { ring.animateTo(1f, tween(700, easing = EaseOut)) }
            val b = async { delay(450); dot.animateTo(1f, tween(550, easing = Overshoot)) }
            val c = async { delay(650); mark.animateTo(1f, tween(500, easing = EaseOut)) }
            val d = async { delay(700); title.animateTo(1f, tween(550, easing = EaseOut)) }
            val e = async { delay(1250); tagline.animateTo(1f, tween(400, easing = EaseOut)) }
            a.await(); b.await(); c.await(); d.await(); e.await()
        }
        delay((SPLASH_MS - 1650 - 350).coerceAtLeast(0).toLong())
        exit.animateTo(0f, tween(350, easing = LinearEasing))
        onFinished()
    }

    val pulse by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )

    Box(
        Modifier.fillMaxSize()
            .alpha(exit.value)
            .background(Ink)
            .background(
                Brush.radialGradient(
                    listOf(Ember.copy(alpha = 0.16f * dot.value), Color.Transparent),
                    radius = 900f,
                ),
            )
            // Swallow touches so nothing underneath is pressed; a tap skips the animation.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onFinished() },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Canvas(Modifier.size(132.dp).graphicsLayer { val s = 0.85f + 0.15f * ring.value; scaleX = s; scaleY = s }) {
                // Geometry follows the launcher icon (108-unit viewport: ring r=22 at 54,54; dot at 70,38).
                val u = size.minDimension / 60f
                val c = Offset(size.width / 2, size.height / 2)
                val r = 22f * u
                // Ring draws clockwise from the top.
                drawArc(
                    color = Cream, startAngle = -90f, sweepAngle = 360f * ring.value, useCenter = false,
                    topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2),
                    style = Stroke(width = 4.2f * u, cap = StrokeCap.Round),
                )
                // ₹-like ledger mark (two bars + hook), revealed by alpha and a short rise.
                val m = mark.value
                if (m > 0f) {
                    val dy = (1f - m) * 6f * u
                    val ink = Cream.copy(alpha = m)
                    drawRect(ink, Offset(c.x - 9f * u, c.y - 7f * u + dy), Size(18f * u, 3.2f * u))
                    drawRect(ink, Offset(c.x - 9f * u, c.y + 0f * u + dy), Size(18f * u, 3.2f * u))
                    val p = Path().apply {
                        moveTo(c.x - 5f * u, c.y - 4f * u + dy)
                        cubicTo(c.x + 1f * u, c.y - 4f * u + dy, c.x + 4f * u, c.y - 2f * u + dy, c.x + 4f * u, c.y + 1.6f * u + dy)
                        cubicTo(c.x + 4f * u, c.y + 5.2f * u + dy, c.x + 1f * u, c.y + 7f * u + dy, c.x - 5f * u, c.y + 7f * u + dy)
                        lineTo(c.x + 5f * u, c.y + 17f * u + dy)
                    }
                    drawPath(p, ink, style = Stroke(width = 3.2f * u, cap = StrokeCap.Round))
                }
                // Ember dot sitting on the ring at 45° (top-right), with a pulsing glow.
                val d = dot.value
                if (d > 0f) {
                    val ang = Math.toRadians(-45.0)
                    val dc = Offset(c.x + (r * cos(ang)).toFloat(), c.y + (r * sin(ang)).toFloat())
                    drawCircle(Ember.copy(alpha = 0.18f * pulse * d.coerceAtMost(1f)), radius = 11f * u * d, center = dc)
                    drawCircle(Ember.copy(alpha = 0.35f * pulse * d.coerceAtMost(1f)), radius = 7f * u * d, center = dc)
                    drawCircle(Ember, radius = 4.5f * u * d, center = dc)
                }
            }
            Spacer(Modifier.height(22.dp))
            Text(
                "Kosh",
                color = Cream, fontFamily = SerifFamily, fontSize = 52.sp,
                modifier = Modifier.graphicsLayer {
                    alpha = title.value
                    translationY = (1f - title.value) * 24.dp.toPx()
                },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "accounted for.",
                color = Ember, fontFamily = SerifFamily, fontStyle = FontStyle.Italic, fontSize = 24.sp,
                modifier = Modifier.graphicsLayer {
                    alpha = title.value
                    translationY = (1f - title.value) * 16.dp.toPx()
                },
            )
        }
        Text(
            "EVERY RUPEE  ✺  PRIVATE  ✺  YOURS",
            style = MonoLabel, color = Cream.copy(alpha = 0.5f),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 40.dp)
                .alpha(tagline.value),
        )
    }
}
