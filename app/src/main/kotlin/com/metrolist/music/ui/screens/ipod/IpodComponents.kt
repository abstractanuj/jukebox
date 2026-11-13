package com.metrolist.music.ui.screens.ipod

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.playback.PlayerConnection
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

@Composable
fun PlayingEqualizerIcon() {
    val infiniteTransition = rememberInfiniteTransition()
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(400, easing = LinearEasing), repeatMode = RepeatMode.Reverse)
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(300, easing = LinearEasing), repeatMode = RepeatMode.Reverse)
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), repeatMode = RepeatMode.Reverse)
    )

    Row(
        modifier = Modifier.size(width = 14.dp, height = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight(bar1).background(Color(0xFF2563EB), RoundedCornerShape(1.dp)))
        Box(Modifier.width(3.dp).fillMaxHeight(bar2).background(Color(0xFF2563EB), RoundedCornerShape(1.dp)))
        Box(Modifier.width(3.dp).fillMaxHeight(bar3).background(Color(0xFF2563EB), RoundedCornerShape(1.dp)))
    }
}

@Composable
fun IpodHeader(title: String, playerConnection: PlayerConnection?, onRefresh: (() -> Unit)? = null) {
    val isPlaying = playerConnection?.isPlaying?.collectAsStateWithLifecycle(false)?.value ?: false
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp) // Strict 22px height
            .background(Brush.verticalGradient(listOf(Color(0xFFE6E9EE), Color(0xFFB4B9C2)))) // Classic Title Bar Fill
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isPlaying) {
                PlayingEqualizerIcon()
                Spacer(Modifier.width(6.dp))
            } else {
                Text("▶", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Spacer(Modifier.width(6.dp))
            }
        }
        AnimatedContent(
            targetState = title,
            transitionSpec = {
                (fadeIn(tween(140)) + slideInHorizontally(tween(140)) { it / 2 }) togetherWith
                (fadeOut(tween(140)) + slideOutHorizontally(tween(140)) { -it / 2 })
            },
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            label = "TitleDrift"
        ) { targetTitle ->
            Text(
                text = targetTitle,
                fontSize = 13.sp, // Strict 13px
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.basicMarquee(initialDelayMillis = 1500, velocity = 35.dp),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onRefresh != null) {
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(24.dp).padding(end = 4.dp)
                ) {
                    Icon(
                        painter = painterResource(com.metrolist.music.R.drawable.sync),
                        contentDescription = "Refresh",
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            IpodBatteryIcon()
        }
    }
    // Strict 1px bottom divider line in #7E838A with a subtle 2px drop shadow (black at 12% opacity)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0xFF7E838A))
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.12f), Color.Transparent)))
    )
}

@Composable
fun IpodBatteryIcon() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var batteryLevel by remember { mutableFloatStateOf(1f) }
    var isCharging by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                if (level != -1 && scale != -1) {
                    batteryLevel = level.toFloat() / scale.toFloat()
                }
                val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                             status == android.os.BatteryManager.BATTERY_STATUS_FULL
            }
        }
        context.registerReceiver(receiver, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(width = 22.dp, height = 11.dp)) {
            val strokeWidth = 1.dp.toPx()
            val batteryBodyRect = Size(size.width * 0.9f, size.height)
            
            // Battery terminal
            drawRect(
                color = Color.Black,
                topLeft = Offset(batteryBodyRect.width, size.height * 0.25f),
                size = Size(size.width * 0.1f, size.height * 0.5f),
                style = Fill
            )
            
            // Outer casing
            drawRoundRect(
                color = Color.Black,
                size = batteryBodyRect,
                cornerRadius = CornerRadius(2.dp.toPx()),
                style = Stroke(width = strokeWidth)
            )
            
            // Glossy Green Fill (#52D769)
            val innerTopLeft = Offset(strokeWidth + 1.dp.toPx(), strokeWidth + 1.dp.toPx())
            val innerSize = Size(
                (batteryBodyRect.width - (strokeWidth * 2) - 2.dp.toPx()) * batteryLevel, 
                size.height - (strokeWidth * 2) - 2.dp.toPx()
            )
            
            if (batteryLevel > 0f) {
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        0f to Color(0xFF6AE17F), // Top highlight
                        0.5f to Color(0xFF52D769), // Base green
                        1f to Color(0xFF38B24C)  // Shadow bottom
                    ),
                    topLeft = innerTopLeft,
                    size = innerSize,
                    cornerRadius = CornerRadius(1.dp.toPx()),
                    style = Fill
                )
            }
        }
    }
}

@Composable
fun IpodLoadingIndicator(modifier: Modifier = Modifier, color: Color = Color.DarkGray) {
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    val step = (rotation / 30f).toInt()

    Canvas(modifier = modifier.size(32.dp)) {
        val lineCount = 12
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.width / 2f
        val innerRadius = radius * 0.5f

        for (i in 0 until lineCount) {
            val diff = (i - step).let { if (it > 0) it - lineCount else it }
            val alpha = (1f + (diff.toFloat() / lineCount)).coerceIn(0.2f, 1f)

            rotate(degrees = i * (360f / lineCount), pivot = center) {
                drawLine(
                    color = color.copy(alpha = alpha),
                    start = Offset(center.x, center.y - innerRadius),
                    end = Offset(center.x, center.y - radius),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun IpodClickWheel(
    onInteract: () -> Unit,
    onMenu: () -> Unit,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onScroll: (Int) -> Unit,
    onSelect: () -> Unit
) {
    val wheelColor = LocalIpodTheme.current.wheel
    val centerButtonColor = LocalIpodTheme.current.center
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    val currentOnInteract by rememberUpdatedState(onInteract)
    val currentOnMenu by rememberUpdatedState(onMenu)
    val currentOnPlayPause by rememberUpdatedState(onPlayPause)
    val currentOnPrev by rememberUpdatedState(onPrev)
    val currentOnNext by rememberUpdatedState(onNext)
    val currentOnScroll by rememberUpdatedState(onScroll)
    val currentOnSelect by rememberUpdatedState(onSelect)

    val threshold = 0.40f
    val wheelSize = 240.dp
    val centerSize = 90.dp

    val velocityTracker = remember { VelocityTracker() }
    var touchGlow by remember { mutableStateOf<Offset?>(null) }
    var pressedAngle by remember { mutableStateOf<Float?>(null) }
    var centerPressed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(wheelSize)
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(wheelColor, wheelColor.copy(alpha = 0.85f))
                )
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent().changes.firstOrNull { it.pressed } ?: continue
                        var isDragging = false
                        var previousAngle = atan2(down.position.y - size.height / 2f, down.position.x - size.width / 2f)
                        var accumulatedDelta = 0f
                        velocityTracker.resetTracking()
                        var decayJob: kotlinx.coroutines.Job? = null
                        
                        decayJob?.cancel()
                        currentOnInteract()

                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull()
                            if (change == null || !change.pressed) break

                            velocityTracker.addPosition(change.uptimeMillis, change.position)

                            val currentX = change.position.x - size.width / 2f
                            val currentY = change.position.y - size.height / 2f
                            val currentAngle = atan2(currentY, currentX)

                            var delta = currentAngle - previousAngle
                            if (delta > PI) delta -= (2 * PI).toFloat()
                            if (delta < -PI) delta += (2 * PI).toFloat()

                            // Lock into drag mode if moved sufficiently
                            if (!isDragging && abs(delta) > 0.05f) {
                                isDragging = true
                            }

                            if (isDragging) {
                                touchGlow = change.position
                                change.consume()
                                val velocity = velocityTracker.calculateVelocity()
                                val speedPx = hypot(velocity.x, velocity.y)
                                val speedDeg = (speedPx / (size.width / 2f)) * (180f / PI.toFloat())
                                val multiplier = if (speedDeg > 540f) 4 else if (speedDeg > 180f) 2 else 1
                                val dynamicThreshold = 0.26f / multiplier

                                accumulatedDelta += delta
                                if (abs(accumulatedDelta) > dynamicThreshold) {
                                    val steps = (accumulatedDelta / dynamicThreshold).toInt()
                                    if (steps != 0) {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        currentOnScroll(steps)
                                        accumulatedDelta -= steps * dynamicThreshold
                                    }
                                }
                            }
                            previousAngle = currentAngle
                        } while (true)
                        touchGlow = null

                        if (!isDragging) {
                            // Perfect tap detected
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            val x = down.position.x - size.width / 2f
                            val y = down.position.y - size.height / 2f
                            val r = hypot(x, y)

                            if (r < (centerSize.toPx() / 2f)) {
                                coroutineScope.launch { centerPressed = true; delay(100); centerPressed = false }
                                currentOnSelect()
                            } else {
                                val angle = atan2(y, x)
                                coroutineScope.launch { pressedAngle = angle; delay(100); pressedAngle = null }
                                val pi4 = PI / 4
                                when {
                                    angle > -pi4 && angle < pi4 -> currentOnNext() // Right
                                    angle > pi4 && angle < 3 * pi4 -> currentOnPlayPause() // Bottom
                                    angle < -pi4 && angle > -3 * pi4 -> currentOnMenu() // Top
                                    else -> currentOnPrev() // Left
                                }
                            }
                            currentOnInteract()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            touchGlow?.let { drawCircle(Color.White.copy(alpha = 0.08f), radius = 30.dp.toPx(), center = it) }
            pressedAngle?.let { 
                drawArc(Color.Black.copy(alpha = 0.15f), it * (180f/PI.toFloat()) - 45f, 90f, useCenter = true)
            }
        }

        val labelColor = if (wheelColor == Color(0xFF1C1E26)) Color.White else Color.Gray
        Text("MENU", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = labelColor, modifier = Modifier.align(Alignment.TopCenter).padding(16.dp))
        Text("▶❚❚", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = labelColor, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
        Text("∣◀◀", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = labelColor, modifier = Modifier.align(Alignment.CenterStart).padding(16.dp))
        Text("▶▶∣", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = labelColor, modifier = Modifier.align(Alignment.CenterEnd).padding(16.dp))

        Box(
            modifier = Modifier
                .size(centerSize)
                .clip(CircleShape)
                .background(centerButtonColor)
                .then(if (centerPressed) Modifier.border(1.dp, Color.Black.copy(alpha = 0.3f), CircleShape) else Modifier)
        )
    }
}