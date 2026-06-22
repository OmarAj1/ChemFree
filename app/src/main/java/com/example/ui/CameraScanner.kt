package com.example.ui

import android.Manifest
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors
import android.graphics.Bitmap
import android.graphics.Matrix

@Composable
fun CameraScannerScreen(
    onTextExtracted: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember { 
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) 
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasPermission = granted }
    )

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasPermission) {
        CameraCaptureWithAnalysis(onTextExtracted, onClose)
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Camera permission is required to scan ingredients.")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("Grant Permission")
            }
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onClose) {
                Text("Cancel")
            }
        }
    }
}

@Composable
fun CameraCaptureWithAnalysis(
    onTextExtracted: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
        }
    }

    var scannedText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    var screenWidth by remember { mutableFloatStateOf(0f) }
    var screenHeight by remember { mutableFloatStateOf(0f) }

    val imageCapture = remember { ImageCapture.Builder().build() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                screenWidth = size.width.toFloat()
                screenHeight = size.height.toFloat()
            }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )
                    } catch (exc: Exception) {
                        Log.e("CameraScanner", "Use case binding failed", exc)
                    }

                }, ContextCompat.getMainExecutor(context))

                previewView
            }
        )

        val infiniteTransition = rememberInfiniteTransition("scanLine")
        val scanLinePosition by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "scanLinePosition"
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            val rectWidth = canvasWidth * 0.85f
            val rectHeight = canvasHeight * 0.25f

            val rectLeft = (canvasWidth - rectWidth) / 2f
            val rectTop = (canvasHeight - rectHeight) / 2f

            val cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())

            // 1. Draw semi-transparent dark background with a clear rectangular cutout
            val backgroundPath = Path().apply {
                addRect(Rect(0f, 0f, canvasWidth, canvasHeight))
                addRoundRect(
                    RoundRect(
                        left = rectLeft,
                        top = rectTop,
                        right = rectLeft + rectWidth,
                        bottom = rectTop + rectHeight,
                        cornerRadius = cornerRadius
                    )
                )
                fillType = PathFillType.EvenOdd
            }
            drawPath(backgroundPath, Color.Black.copy(alpha = 0.65f))
            
            // 2. Draw white frame/brackets around the cutout
            val cornerLength = 48.dp.toPx()
            val strokeWidth = 4.dp.toPx()
            val bracketColor = Color.White
            val bracketStyle = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)

            // Top-left
            drawPath(Path().apply {
                moveTo(rectLeft, rectTop + cornerLength)
                lineTo(rectLeft, rectTop)
                lineTo(rectLeft + cornerLength, rectTop)
            }, color = bracketColor, style = bracketStyle)
            
            // Top-right
            drawPath(Path().apply {
                moveTo(rectLeft + rectWidth - cornerLength, rectTop)
                lineTo(rectLeft + rectWidth, rectTop)
                lineTo(rectLeft + rectWidth, rectTop + cornerLength)
            }, color = bracketColor, style = bracketStyle)

            // Bottom-left
            drawPath(Path().apply {
                moveTo(rectLeft, rectTop + rectHeight - cornerLength)
                lineTo(rectLeft, rectTop + rectHeight)
                lineTo(rectLeft + cornerLength, rectTop + rectHeight)
            }, color = bracketColor, style = bracketStyle)

            // Bottom-right
            drawPath(Path().apply {
                moveTo(rectLeft + rectWidth - cornerLength, rectTop + rectHeight)
                lineTo(rectLeft + rectWidth, rectTop + rectHeight)
                lineTo(rectLeft + rectWidth, rectTop + rectHeight - cornerLength)
            }, color = bracketColor, style = bracketStyle)

            // 3. Draw animated scan line
            val scanLineY = rectTop + (rectHeight * scanLinePosition)
            val gradient = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.4f), Color.Transparent),
                startY = scanLineY - 20f,
                endY = scanLineY + 20f
            )
            drawRect(
                brush = gradient,
                topLeft = Offset(rectLeft, scanLineY - 10f),
                size = Size(rectWidth, 20f)
            )
            // A thin bright line in the center of the gradient
            drawLine(
                color = Color.White,
                start = Offset(rectLeft, scanLineY),
                end = Offset(rectLeft + rectWidth, scanLineY),
                strokeWidth = 2f
            )
        }

        // Overlay UI
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), shape = MaterialTheme.shapes.small)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            // Bottom panel with text
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp).navigationBarsPadding(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Align text within the frame to scan", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (isProcessing) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Extracting text...", color = MaterialTheme.colorScheme.onSurface)
                    } else if (scannedText.isNotBlank()) {
                        Text(
                            text = scannedText,
                            maxLines = 6,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                onTextExtracted(scannedText)
                                onClose()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Use Scanned Text")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { scannedText = "" },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Retake")
                        }
                    } else {
                        Button(
                            onClick = {
                                isProcessing = true
                                imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                                    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
                                    override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                        try {
                                            val originalBitmap = imageProxy.toBitmap()
                                            val matrix = Matrix()
                                            matrix.postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                                            val rotatedBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)

                                            val imageWidth = rotatedBitmap.width.toFloat()
                                            val imageHeight = rotatedBitmap.height.toFloat()

                                            val croppedBitmap = if (screenWidth > 0 && screenHeight > 0) {
                                                val scale = kotlin.math.max(screenWidth / imageWidth, screenHeight / imageHeight)
                                                
                                                val scaledWidth = imageWidth * scale
                                                val scaledHeight = imageHeight * scale
                                                
                                                val offsetX = (screenWidth - scaledWidth) / 2f
                                                val offsetY = (screenHeight - scaledHeight) / 2f

                                                // The visual box defined by proportions in Canvas
                                                val viewCropLeft = screenWidth * 0.075f
                                                val viewCropTop = screenHeight * 0.375f
                                                val viewCropWidth = screenWidth * 0.85f
                                                val viewCropHeight = screenHeight * 0.25f

                                                // Map to original image coordinates
                                                val calculatedX = ((viewCropLeft - offsetX) / scale).toInt()
                                                val calculatedY = ((viewCropTop - offsetY) / scale).toInt()
                                                val calculatedWidth = (viewCropWidth / scale).toInt()
                                                val calculatedHeight = (viewCropHeight / scale).toInt()

                                                val finalCropX = calculatedX.coerceIn(0, rotatedBitmap.width - 1)
                                                val finalCropY = calculatedY.coerceIn(0, rotatedBitmap.height - 1)
                                                
                                                // Need to ensure width/height doesn't exceed bounds after coercing X/Y
                                                val availableWidth = rotatedBitmap.width - finalCropX
                                                val availableHeight = rotatedBitmap.height - finalCropY
                                                
                                                val finalCropWidth = calculatedWidth.coerceIn(1, availableWidth)
                                                val finalCropHeight = calculatedHeight.coerceIn(1, availableHeight)

                                                Bitmap.createBitmap(rotatedBitmap, finalCropX, finalCropY, finalCropWidth, finalCropHeight)
                                            } else {
                                                // Fallback if screen size is somehow not recorded
                                                val fallbackX = (imageWidth * 0.075f).toInt()
                                                val fallbackY = (imageHeight * 0.375f).toInt()
                                                val fallbackWidth = (imageWidth * 0.85f).toInt()
                                                val fallbackHeight = (imageHeight * 0.25f).toInt()
                                                Bitmap.createBitmap(rotatedBitmap, fallbackX, fallbackY, fallbackWidth, fallbackHeight)
                                            }
                                            
                                            val image = InputImage.fromBitmap(croppedBitmap, 0)

                                            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                                            recognizer.process(image)
                                                .addOnSuccessListener { visionText ->
                                                    scannedText = visionText.text
                                                    isProcessing = false
                                                }
                                                .addOnFailureListener { e ->
                                                    Log.e("CameraScanner", "Text recognition failed", e)
                                                    scannedText = "Failed to extract text."
                                                    isProcessing = false
                                                }
                                        } catch (e: Exception) {
                                            Log.e("CameraScanner", "Exception during capture processing", e)
                                            isProcessing = false
                                        } finally {
                                            imageProxy.close()
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        Log.e("CameraScanner", "Photo capture failed", exception)
                                        isProcessing = false
                                    }
                                })
                            },
                            modifier = Modifier.size(72.dp),
                            shape = androidx.compose.foundation.shape.CircleShape
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = "Capture Image", modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
        }
    }
}
