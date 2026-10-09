package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.SparkRose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.UUID

/**
 * Filter definition for Snapchat-style photo snaps.
 */
enum class SnapFilter(val label: String, val matrixValues: FloatArray) {
    ORIGINAL(
        label = "Normal",
        matrixValues = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    SUNSET(
        label = "Sunset",
        matrixValues = floatArrayOf(
            1.30f, 0f, 0f, 0f, 25f,
            0f, 1.05f, 0f, 0f, 10f,
            0f, 0f, 0.82f, 0f, -10f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    NOIR(
        label = "Noir",
        matrixValues = floatArrayOf(
            0.33f, 0.59f, 0.11f, 0f, -20f,
            0.33f, 0.59f, 0.11f, 0f, -20f,
            0.33f, 0.59f, 0.11f, 0f, -20f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    CYBERPUNK(
        label = "Cyberpunk",
        matrixValues = floatArrayOf(
            1.15f, 0f, 0.3f, 0f, 20f,
            0f, 0.9f, 0.4f, 0f, -10f,
            0.2f, 0f, 1.4f, 0f, 30f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    ROSE(
        label = "Rosé",
        matrixValues = floatArrayOf(
            1.22f, 0.1f, 0.1f, 0f, 20f,
            0.05f, 0.95f, 0.05f, 0f, 5f,
            0.1f, 0.05f, 1.15f, 0f, 15f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    VINTAGE(
        label = "Vintage 90s",
        matrixValues = floatArrayOf(
            0.393f * 1.5f, 0.769f * 0.9f, 0.189f, 0f, 25f,
            0.349f * 1.2f, 0.686f * 1.1f, 0.168f, 0f, 15f,
            0.272f * 0.8f, 0.534f * 0.8f, 0.131f * 1.2f, 0f, -10f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    VIVID(
        label = "Vivid Pop",
        matrixValues = floatArrayOf(
            1.35f, -0.15f, -0.15f, 0f, 10f,
            -0.15f, 1.35f, -0.15f, 0f, 10f,
            -0.15f, -0.15f, 1.35f, 0f, 10f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    EMERALD(
        label = "Emerald",
        matrixValues = floatArrayOf(
            0.85f, 0.05f, 0.05f, 0f, -10f,
            0.1f, 1.30f, 0.1f, 0f, 25f,
            0.05f, 0.2f, 1.10f, 0f, 15f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    GOLDEN(
        label = "Golden",
        matrixValues = floatArrayOf(
            1.35f, 0.15f, 0f, 0f, 30f,
            0.1f, 1.20f, 0f, 0f, 20f,
            0f, 0f, 0.70f, 0f, -25f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    MOODY(
        label = "Moody",
        matrixValues = floatArrayOf(
            1.15f, 0f, 0f, 0f, -25f,
            0f, 1.15f, 0f, 0f, -25f,
            0f, 0f, 1.15f, 0f, -25f,
            0f, 0f, 0f, 1f, 0f
        )
    )
}

/**
 * Data model for freehand drawing strokes on snap photos.
 */
data class DrawingStroke(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float = 12f
)

/**
 * Data model for emoji stickers placed on snap photos.
 */
data class SnapStickerItem(
    val id: String = UUID.randomUUID().toString(),
    val emoji: String,
    var xPercent: Float = 0.5f,
    var yPercent: Float = 0.5f
)

/**
 * Fullscreen Snapchat-style Camera and Editor Dialog.
 */
@Composable
fun SnapCameraDialog(
    recipientName: String,
    onDismiss: () -> Unit,
    onSendSnap: (ByteArray) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // Camera states
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var previewView: PreviewView? by remember { mutableStateOf(null) }
    var isCapturing by remember { mutableStateOf(false) }

    // Editor states
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var activeFilter by remember { mutableStateOf(SnapFilter.ORIGINAL) }
    var showFilterPill by remember { mutableStateOf(false) }
    var rotationDegrees by remember { mutableIntStateOf(0) }

    // Drawing tool states
    var isDrawingMode by remember { mutableStateOf(false) }
    var currentDrawColor by remember { mutableStateOf(Color.White) }
    val drawStrokes = remember { mutableStateListOf<DrawingStroke>() }
    var currentPoints = remember { mutableStateListOf<Offset>() }

    // Text tool states
    var isTextMode by remember { mutableStateOf(false) }
    var captionText by remember { mutableStateOf("") }
    var captionYPercent by remember { mutableFloatStateOf(0.70f) }

    // Sticker tool states
    var showStickerDrawer by remember { mutableStateOf(false) }
    val stickers = remember { mutableStateListOf<SnapStickerItem>() }

    // Processing indicator
    var isRenderingFinalSnap by remember { mutableStateOf(false) }

    // Trigger filter name badge fadeout
    LaunchedEffect(activeFilter) {
        if (capturedBitmap != null) {
            showFilterPill = true
            delay(1200)
            showFilterPill = false
        }
    }

    Dialog(
        onDismissRequest = {
            if (!isCapturing && !isRenderingFinalSnap) onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (capturedBitmap == null) {
                // ==============================================================
                // 1. LIVE CAMERA VIEWFINDER (CameraX)
                // ==============================================================
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            previewView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Setup CameraX binding
                LaunchedEffect(lensFacing) {
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                previewView?.let { pv -> it.setSurfaceProvider(pv.surfaceProvider) }
                            }
                            val capture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .setFlashMode(flashMode)
                                .build()
                            imageCapture = capture

                            val cameraSelector = CameraSelector.Builder()
                                .requireLensFacing(lensFacing)
                                .build()

                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                capture
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("SnapCamera", "Camera binding failed: ${e.message}")
                        }
                    }, ContextCompat.getMainExecutor(context))
                }

                // Top Controls: Close, Flash, Flip Camera
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(42.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close Camera",
                            tint = Color.White
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Flash mode toggle
                        IconButton(
                            onClick = {
                                flashMode = when (flashMode) {
                                    ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                                    ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                                    else -> ImageCapture.FLASH_MODE_OFF
                                }
                                imageCapture?.flashMode = flashMode
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                        ) {
                            Icon(
                                imageVector = when (flashMode) {
                                    ImageCapture.FLASH_MODE_ON -> Icons.Filled.FlashOn
                                    ImageCapture.FLASH_MODE_AUTO -> Icons.Filled.FlashAuto
                                    else -> Icons.Filled.FlashOff
                                },
                                contentDescription = "Flash Toggle",
                                tint = if (flashMode != ImageCapture.FLASH_MODE_OFF) SparkRose else Color.White
                            )
                        }

                        // Flip Camera toggle
                        IconButton(
                            onClick = {
                                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                    CameraSelector.LENS_FACING_FRONT
                                } else {
                                    CameraSelector.LENS_FACING_BACK
                                }
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FlipCameraAndroid,
                                contentDescription = "Flip Camera",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Bottom Controls: Shutter Button
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Shutter Ring
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(86.dp)
                            .border(4.dp, Color.White, CircleShape)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(if (isCapturing) SparkRose else Color.White)
                            .clickable(enabled = !isCapturing) {
                                val capture = imageCapture ?: return@clickable
                                isCapturing = true

                                capture.takePicture(
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageCapturedCallback() {
                                        override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                            scope.launch(Dispatchers.Default) {
                                                val bmp = imageProxyToBitmap(imageProxy, lensFacing)
                                                imageProxy.close()
                                                withContext(Dispatchers.Main) {
                                                    capturedBitmap = bmp
                                                    isCapturing = false
                                                }
                                            }
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            android.util.Log.e("SnapCamera", "Photo capture failed", exception)
                                            isCapturing = false
                                        }
                                    }
                                )
                            }
                    ) {
                        if (isCapturing) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(32.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(Color.White, CircleShape)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Tap to take a Quicky",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }

            } else {
                // ==============================================================
                // 2. SNAPCHAT POST-CAPTURE & EDITING CANVAS
                // ==============================================================
                val baseBitmap = capturedBitmap!!

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(isDrawingMode) {
                            if (!isDrawingMode) {
                                // Swipe horizontally to change filters
                                detectDragGestures(
                                    onDragEnd = {}
                                ) { change, dragAmount ->
                                    change.consume()
                                    if (dragAmount.x > 35f) {
                                        // Swipe right -> previous filter
                                        val values = SnapFilter.values()
                                        val prevIndex = (activeFilter.ordinal - 1 + values.size) % values.size
                                        activeFilter = values[prevIndex]
                                    } else if (dragAmount.x < -35f) {
                                        // Swipe left -> next filter
                                        val values = SnapFilter.values()
                                        val nextIndex = (activeFilter.ordinal + 1) % values.size
                                        activeFilter = values[nextIndex]
                                    }
                                }
                            }
                        }
                ) {
                    val boxWidth = maxWidth
                    val boxHeight = maxHeight

                    // Display filtered photo
                    androidx.compose.foundation.Image(
                        bitmap = baseBitmap.asImageBitmap(),
                        contentDescription = "Captured Snap",
                        contentScale = ContentScale.Crop,
                        colorFilter = ColorFilter.colorMatrix(
                            androidx.compose.ui.graphics.ColorMatrix(activeFilter.matrixValues)
                        ),
                        modifier = Modifier.fillMaxSize()
                    )

                    // Drawing Strokes Layer
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(isDrawingMode) {
                                if (isDrawingMode) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            currentPoints.clear()
                                            currentPoints.add(offset)
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            currentPoints.add(change.position)
                                        },
                                        onDragEnd = {
                                            if (currentPoints.isNotEmpty()) {
                                                drawStrokes.add(
                                                    DrawingStroke(
                                                        points = currentPoints.toList(),
                                                        color = currentDrawColor
                                                    )
                                                )
                                                currentPoints.clear()
                                            }
                                        }
                                    )
                                }
                            }
                    ) {
                        // Render completed strokes
                        drawStrokes.forEach { stroke ->
                            if (stroke.points.size > 1) {
                                val path = Path().apply {
                                    moveTo(stroke.points[0].x, stroke.points[0].y)
                                    for (i in 1 until stroke.points.size) {
                                        lineTo(stroke.points[i].x, stroke.points[i].y)
                                    }
                                }
                                drawPath(
                                    path = path,
                                    color = stroke.color,
                                    style = Stroke(
                                        width = stroke.strokeWidth,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }

                        // Render active stroke in progress
                        if (currentPoints.size > 1) {
                            val activePath = Path().apply {
                                moveTo(currentPoints[0].x, currentPoints[0].y)
                                for (i in 1 until currentPoints.size) {
                                    lineTo(currentPoints[i].x, currentPoints[i].y)
                                }
                            }
                            drawPath(
                                path = activePath,
                                color = currentDrawColor,
                                style = Stroke(
                                    width = 12f,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }

                    // Emoji Stickers Layer
                    stickers.forEach { sticker ->
                        var offsetX by remember { mutableFloatStateOf(sticker.xPercent) }
                        var offsetY by remember { mutableFloatStateOf(sticker.yPercent) }

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset {
                                    IntOffset(
                                        (offsetX * boxWidth.toPx()).toInt() - 36,
                                        (offsetY * boxHeight.toPx()).toInt() - 36
                                    )
                                }
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        offsetX = (offsetX + dragAmount.x / boxWidth.toPx()).coerceIn(0.05f, 0.95f)
                                        offsetY = (offsetY + dragAmount.y / boxHeight.toPx()).coerceIn(0.05f, 0.95f)
                                        sticker.xPercent = offsetX
                                        sticker.yPercent = offsetY
                                    }
                                }
                        ) {
                            Text(
                                text = sticker.emoji,
                                fontSize = 48.sp,
                                modifier = Modifier.padding(4.dp)
                            )
                        }
                    }

                    // Text Caption Overlay (Snapchat Classic Translucent Banner)
                    if (captionText.isNotEmpty() || isTextMode) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset {
                                    IntOffset(0, (captionYPercent * boxHeight.toPx()).toInt())
                                }
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.58f))
                                .padding(vertical = 10.dp, horizontal = 16.dp)
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        captionYPercent = (captionYPercent + dragAmount.y / boxHeight.toPx())
                                            .coerceIn(0.12f, 0.85f)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isTextMode) {
                                BasicTextField(
                                    value = captionText,
                                    onValueChange = { captionText = it },
                                    textStyle = TextStyle(
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Text(
                                    text = captionText,
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isTextMode = true }
                                )
                            }
                        }
                    }

                    // Snapchat Filter Name Pill Overlay
                    AnimatedVisibility(
                        visible = showFilterPill,
                        enter = fadeIn() + slideInVertically { -20 },
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(bottom = 60.dp)
                    ) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(20.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                        ) {
                            Text(
                                text = activeFilter.label,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                        }
                    }

                    // Top Toolbar (Editing Tools)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Discard / Retake photo
                        IconButton(
                            onClick = {
                                capturedBitmap = null
                                drawStrokes.clear()
                                stickers.clear()
                                captionText = ""
                                isDrawingMode = false
                                isTextMode = false
                                activeFilter = SnapFilter.ORIGINAL
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Retake",
                                tint = Color.White
                            )
                        }

                        // Tool icons row
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Text tool toggle
                            IconButton(
                                onClick = {
                                    isTextMode = !isTextMode
                                    if (isTextMode) isDrawingMode = false
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(
                                        if (isTextMode || captionText.isNotEmpty()) SparkRose else Color.Black.copy(alpha = 0.5f),
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.TextFields,
                                    contentDescription = "Text Caption",
                                    tint = Color.White
                                )
                            }

                            // Brush / Doodle tool toggle
                            IconButton(
                                onClick = {
                                    isDrawingMode = !isDrawingMode
                                    if (isDrawingMode) isTextMode = false
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(
                                        if (isDrawingMode) SparkRose else Color.Black.copy(alpha = 0.5f),
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Brush,
                                    contentDescription = "Draw",
                                    tint = Color.White
                                )
                            }

                            // Undo drawing
                            if (drawStrokes.isNotEmpty()) {
                                IconButton(
                                    onClick = { drawStrokes.removeLastOrNull() },
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Undo,
                                        contentDescription = "Undo Draw",
                                        tint = Color.White
                                    )
                                }
                            }

                            // Emoji Sticker tool
                            IconButton(
                                onClick = { showStickerDrawer = !showStickerDrawer },
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(
                                        if (showStickerDrawer) SparkRose else Color.Black.copy(alpha = 0.5f),
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.EmojiEmotions,
                                    contentDescription = "Stickers",
                                    tint = Color.White
                                )
                            }

                            // Rotate photo 90 degrees
                            IconButton(
                                onClick = {
                                    rotationDegrees = (rotationDegrees + 90) % 360
                                    val matrix = Matrix().apply { postRotate(90f) }
                                    capturedBitmap = Bitmap.createBitmap(
                                        baseBitmap,
                                        0,
                                        0,
                                        baseBitmap.width,
                                        baseBitmap.height,
                                        matrix,
                                        true
                                    )
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.RotateRight,
                                    contentDescription = "Rotate",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    // Color Palette Selector when in Drawing Mode
                    AnimatedVisibility(
                        visible = isDrawingMode,
                        enter = slideInVertically { -20 } + fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 64.dp)
                    ) {
                        val palette = listOf(
                            Color.White,
                            SparkRose,
                            Color(0xFFFF3B30),
                            Color(0xFFFF9500),
                            Color(0xFFFFCC00),
                            Color(0xFF34C759),
                            Color(0xFF00C7BE),
                            Color(0xFF30B0C7),
                            Color(0xFF5856D6),
                            Color(0xFFAF52DE),
                            Color.Black
                        )
                        Row(
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            palette.forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(color, CircleShape)
                                        .border(
                                            width = if (currentDrawColor == color) 3.dp else 1.dp,
                                            color = if (currentDrawColor == color) SparkRose else Color.White.copy(alpha = 0.4f),
                                            shape = CircleShape
                                        )
                                        .clickable { currentDrawColor = color }
                                )
                            }
                        }
                    }

                    // Emoji Sticker Picker Drawer
                    AnimatedVisibility(
                        visible = showStickerDrawer,
                        enter = slideInVertically { 50 } + fadeIn(),
                        exit = slideOutVertically { 50 } + fadeOut(),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 24.dp)
                    ) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(20.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Add Stickers",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                val emojis = listOf(
                                    "🔥", "❤️", "✨", "📸", "😎", "🥂", "🎉", "💯",
                                    "👻", "💋", "⚡", "🌸", "👑", "🥳", "💬", "🍕",
                                    "😍", "💖", "⭐", "☀️", "🦋", "🥂", "🎂", "🚀"
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    emojis.chunked(8).forEach { rowEmojis ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            rowEmojis.forEach { emoji ->
                                                Text(
                                                    text = emoji,
                                                    fontSize = 28.sp,
                                                    modifier = Modifier
                                                        .clickable {
                                                            stickers.add(SnapStickerItem(emoji = emoji))
                                                            showStickerDrawer = false
                                                        }
                                                        .padding(2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Bar: Filter Carousel (ABOVE) & Send Button (BELOW)
                    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    val safeBottom = if (navBarBottom > 0.dp) navBarBottom + 12.dp else 44.dp

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.75f),
                                        Color.Black.copy(alpha = 0.95f)
                                    )
                                )
                            )
                            .padding(bottom = safeBottom, top = 8.dp)
                    ) {
                        // 1. Filter Selection Carousel (ABOVE send button, hidden while editing text caption)
                        if (!isTextMode) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
                            ) {
                                items(SnapFilter.values()) { filter ->
                                    val isSelected = activeFilter == filter
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(
                                                if (isSelected) SparkRose else Color.Black.copy(alpha = 0.6f)
                                            )
                                            .border(
                                                1.dp,
                                                if (isSelected) Color.White else Color.White.copy(alpha = 0.3f),
                                                RoundedCornerShape(16.dp)
                                            )
                                            .clickable { activeFilter = filter }
                                            .padding(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = filter.label,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            ),
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // 2. Send Button Row (BELOW filter carousel)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isTextMode) {
                                Button(
                                    onClick = { isTextMode = false },
                                    colors = ButtonDefaults.buttonColors(containerColor = SparkRose),
                                    shape = RoundedCornerShape(24.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Done",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Done Editing Caption",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            } else {
                                // Clear all edits
                                TextButton(
                                    onClick = {
                                        drawStrokes.clear()
                                        stickers.clear()
                                        captionText = ""
                                        activeFilter = SnapFilter.ORIGINAL
                                    }
                                ) {
                                    Text("Reset Edits", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                                }

                                // Prominent Send Quicky Button
                                Button(
                                    onClick = {
                                        if (isRenderingFinalSnap) return@Button
                                        isRenderingFinalSnap = true
                                        scope.launch(Dispatchers.Default) {
                                            val renderedBytes = renderFinalSnapImage(
                                                baseBitmap = baseBitmap,
                                                filter = activeFilter,
                                                strokes = drawStrokes.toList(),
                                                stickers = stickers.toList(),
                                                caption = captionText,
                                                captionY = captionYPercent,
                                                viewWidth = boxWidth.value,
                                                viewHeight = boxHeight.value
                                            )
                                            withContext(Dispatchers.Main) {
                                                isRenderingFinalSnap = false
                                                if (renderedBytes != null && renderedBytes.isNotEmpty()) {
                                                    onSendSnap(renderedBytes)
                                                    onDismiss()
                                                }
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(28.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SparkRose),
                                    modifier = Modifier
                                        .height(50.dp)
                                        .padding(start = 12.dp)
                                ) {
                                    if (isRenderingFinalSnap) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Sending...", color = Color.White, fontWeight = FontWeight.Bold)
                                    } else {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "Send Quicky to $recipientName",
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                fontSize = 15.sp
                                            )
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Send,
                                                contentDescription = "Send Quicky",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Camera Permission Rationale Dialog.
 * Beautifully informs the user why camera access is required and provides
 * smooth one-tap action to grant access or open app settings.
 */
@Composable
fun CameraPermissionRationaleDialog(
    onDismiss: () -> Unit,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .background(SparkRose.copy(alpha = 0.15f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = "Camera Permission",
                    tint = SparkRose,
                    modifier = Modifier.size(30.dp)
                )
            }
        },
        title = {
            Text(
                text = "Camera Access Needed",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                text = "Quicky needs camera access to take snaps with Snapchat-style filters, drawings, and stickers to send view-once photos in chat.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        },
        confirmButton = {
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = SparkRose),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Allow Camera", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not Now", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

/**
 * Converts an ImageProxy from CameraX to a properly oriented Bitmap.
 */
private fun imageProxyToBitmap(imageProxy: ImageProxy, lensFacing: Int): Bitmap {
    val buffer: ByteBuffer = imageProxy.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    val matrix = Matrix()
    matrix.postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
    if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
        // Mirror front camera horizontally
        matrix.postScale(-1f, 1f)
    }

    return Bitmap.createBitmap(
        original,
        0,
        0,
        original.width,
        original.height,
        matrix,
        true
    )
}

/**
 * Renders base bitmap + active color matrix filter + drawings + caption + stickers into JPEG bytes.
 */
private fun renderFinalSnapImage(
    baseBitmap: Bitmap,
    filter: SnapFilter,
    strokes: List<DrawingStroke>,
    stickers: List<SnapStickerItem>,
    caption: String,
    captionY: Float,
    viewWidth: Float,
    viewHeight: Float
): ByteArray? {
    return runCatching {
        // Scale target bitmap so max edge is <= 1600px for network efficiency
        val maxDim = maxOf(baseBitmap.width, baseBitmap.height)
        val targetScale = if (maxDim > 1600) 1600f / maxDim else 1f
        val outWidth = (baseBitmap.width * targetScale).toInt().coerceAtLeast(1)
        val outHeight = (baseBitmap.height * targetScale).toInt().coerceAtLeast(1)

        val compositeBitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(compositeBitmap)

        // 1. Draw base photo with filter ColorMatrix
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(filter.matrixValues)
        }
        val srcRect = android.graphics.Rect(0, 0, baseBitmap.width, baseBitmap.height)
        val dstRect = android.graphics.Rect(0, 0, outWidth, outHeight)
        canvas.drawBitmap(baseBitmap, srcRect, dstRect, paint)

        val scaleX = outWidth / viewWidth
        val scaleY = outHeight / viewHeight

        // 2. Draw brush strokes
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        strokes.forEach { stroke ->
            if (stroke.points.size > 1) {
                strokePaint.color = stroke.color.toArgb()
                strokePaint.strokeWidth = stroke.strokeWidth * scaleX
                val path = android.graphics.Path().apply {
                    moveTo(stroke.points[0].x * scaleX, stroke.points[0].y * scaleY)
                    for (i in 1 until stroke.points.size) {
                        lineTo(stroke.points[i].x * scaleX, stroke.points[i].y * scaleY)
                    }
                }
                canvas.drawPath(path, strokePaint)
            }
        }

        // 3. Draw text caption banner
        if (caption.isNotBlank()) {
            val bannerY = captionY * outHeight
            val bannerHeight = 44f * scaleY
            val bannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(150, 0, 0, 0)
            }
            canvas.drawRect(
                0f,
                bannerY - bannerHeight / 2,
                outWidth.toFloat(),
                bannerY + bannerHeight / 2,
                bannerPaint
            )

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = 20f * scaleY
                textAlign = Paint.Align.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            canvas.drawText(
                caption,
                outWidth / 2f,
                bannerY + (textPaint.textSize / 3),
                textPaint
            )
        }

        // 4. Draw emoji stickers
        if (stickers.isNotEmpty()) {
            val stickerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 48f * scaleY
                textAlign = Paint.Align.CENTER
            }
            stickers.forEach { sticker ->
                val sx = sticker.xPercent * outWidth
                val sy = sticker.yPercent * outHeight
                canvas.drawText(sticker.emoji, sx, sy, stickerPaint)
            }
        }

        // 5. Compress to JPEG
        val out = ByteArrayOutputStream()
        compositeBitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
        out.toByteArray()
    }.getOrNull()
}
