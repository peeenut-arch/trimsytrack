package com.trimsytrack.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.location.Location
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.common.util.concurrent.ListenableFuture
import com.trimsytrack.AppGraph
import com.trimsytrack.data.entities.AttachmentEntity
import com.trimsytrack.data.entities.TripEntity
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CameraScreen(
    tripId: Long? = null,
    returnCaptureToCaller: Boolean = false,
    onCaptureConfirmed: (uri: String, capturedAtEpochMillis: Long) -> Unit = { _, _ -> },
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val trips by AppGraph.tripRepository.observeRecent(limit = 200).collectAsState(initial = emptyList())
    val displayTrips = remember(trips, tripId) {
        if (tripId == null) return@remember trips
        val preferred = trips.firstOrNull { it.id == tripId }
        if (preferred == null) trips else listOf(preferred) + trips.filterNot { it.id == tripId }
    }

    val permissionState = remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    val hasCameraPermission = permissionState.value[Manifest.permission.CAMERA] == true ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val hasFineLocationPermission = permissionState.value[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { result -> permissionState.value = result },
    )

    val cameraProviderState = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val boundCamera = remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    val boundOnce = remember { mutableStateOf(false) }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setJpegQuality(95)
            // Best-effort request for 9:16 in portrait (same ratio as 16:9).
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .build()
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val captureError = remember { mutableStateOf<String?>(null) }
    val pendingTempFile = remember { mutableStateOf<File?>(null) }
    val pendingPreviewUri = remember { mutableStateOf<String?>(null) }
    val pendingCapturedAt = remember { mutableStateOf<Instant?>(null) }
    val pendingLocation = remember { mutableStateOf<Location?>(null) }
    val saveStatus = remember { mutableStateOf<String?>(null) }

    val zoomRatio = remember { mutableStateOf(1f) }

    LaunchedEffect(Unit) {
        requestPermissionsLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        )
    }

    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) return@LaunchedEffect
        if (boundOnce.value) return@LaunchedEffect
        runCatching {
            val provider = awaitFuture(ProcessCameraProvider.getInstance(context))
            cameraProviderState.value = provider

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            provider.unbindAll()
            val cam = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
            )
            boundCamera.value = cam
            boundOnce.value = true
        }.onFailure {
            captureError.value = it.message ?: it.javaClass.simpleName
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { cameraProviderState.value?.unbindAll() }
            boundCamera.value = null
            boundOnce.value = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = { Text("Camera") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                )
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!hasCameraPermission) {
                Text(
                    "Camera permission is required.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = {
                        requestPermissionsLauncher.launch(
                            arrayOf(
                                Manifest.permission.CAMERA,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            )
                        )
                    },
                ) {
                    Text("Grant permissions")
                }
                return@Column
            }

            if (pendingPreviewUri.value == null) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .pointerInput(Unit) {
                            detectTransformGestures(
                                onGesture = { _, _, zoomChange, _ ->
                                    val cam = boundCamera.value ?: return@detectTransformGestures
                                    val zoomState = cam.cameraInfo.zoomState.value
                                    val current = zoomState?.zoomRatio ?: 1f
                                    val minZoom = zoomState?.minZoomRatio ?: 1f
                                    val maxZoom = zoomState?.maxZoomRatio ?: current
                                    val next = (current * zoomChange).coerceIn(minZoom, maxZoom)
                                    cam.cameraControl.setZoomRatio(next)
                                    zoomRatio.value = next
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { tap ->
                                    val cam = boundCamera.value ?: return@detectTapGestures
                                    val point = previewView.meteringPointFactory.createPoint(tap.x, tap.y)
                                    val action = FocusMeteringAction.Builder(
                                        point,
                                        FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB,
                                    )
                                        .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                        .build()
                                    runCatching { cam.cameraControl.startFocusAndMetering(action) }
                                },
                            )
                        },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Button(
                        onClick = {
                            captureError.value = null
                            saveStatus.value = null

                            // Ensure correct rotation for portrait JPEGs.
                            val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
                            imageCapture.targetRotation = rotation

                            val tmpDir = File(context.cacheDir, "camera_tmp").apply { mkdirs() }
                            val tempFile = File(tmpDir, "capture_${System.currentTimeMillis()}.jpg")
                            pendingTempFile.value = tempFile

                            val output = ImageCapture.OutputFileOptions.Builder(tempFile).build()
                            val mainExecutor = ContextCompat.getMainExecutor(context)

                            val capturedAt = Instant.now()
                            pendingCapturedAt.value = capturedAt

                            scope.launch {
                                pendingLocation.value = if (hasFineLocationPermission) {
                                    runCatching { getCurrentLocation(context) }.getOrNull()
                                } else {
                                    null
                                }
                            }

                            imageCapture.takePicture(
                                output,
                                mainExecutor,
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        if (!tempFile.exists() || tempFile.length() < 10_000L) {
                                            captureError.value = "Saved image is empty"
                                            return
                                        }
                                        runCatching {
                                            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                            BitmapFactory.decodeFile(tempFile.absolutePath, opts)
                                            if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                                                throw IllegalStateException("Invalid JPEG")
                                            }
                                        }.onFailure {
                                            captureError.value = "Saved image invalid"
                                            return
                                        }

                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            tempFile,
                                        )
                                        pendingPreviewUri.value = uri.toString()

                                        // If caller wants the captured media returned (review flow), do not auto-save.
                                        if (returnCaptureToCaller) return

                                        // If we navigated here from a specific trip, save immediately.
                                        val targetTripId = tripId
                                        if (targetTripId != null && targetTripId > 0L) {
                                            saveStatus.value = null
                                            scope.launch {
                                                runCatching {
                                                    withContext(Dispatchers.IO) {
                                                        val trip = AppGraph.tripRepository.get(targetTripId)
                                                            ?: throw IllegalStateException("Trip not found")
                                                        val saved = saveCapturedPhotoToTrip(
                                                            context = context,
                                                            trip = trip,
                                                            profileId = trip.profileId,
                                                            tempFile = tempFile,
                                                            capturedAt = capturedAt,
                                                            location = pendingLocation.value,
                                                        )
                                                        AppGraph.tripRepository.addAttachment(saved)
                                                    }
                                                }.onSuccess {
                                                    saveStatus.value = "Saved."
                                                    runCatching { tempFile.delete() }
                                                    pendingTempFile.value = null
                                                    pendingPreviewUri.value = null
                                                    pendingCapturedAt.value = null
                                                    pendingLocation.value = null
                                                }.onFailure {
                                                    saveStatus.value = "Failed to save: ${it.message ?: it.javaClass.simpleName}"
                                                }
                                            }
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        captureError.value = exception.message ?: exception.javaClass.simpleName
                                    }
                                }
                            )
                        },
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(72.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            } else {
                Text(
                    "Preview",
                    style = MaterialTheme.typography.titleMedium,
                )
                AsyncImage(
                    model = pendingPreviewUri.value,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                )

                Spacer(Modifier.height(4.dp))

                if (returnCaptureToCaller) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = {
                                val uri = pendingPreviewUri.value ?: return@Button
                                val capturedAt = pendingCapturedAt.value ?: Instant.now()
                                onCaptureConfirmed(uri, capturedAt.toEpochMilli())
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Use photo")
                        }
                        Button(
                            onClick = {
                                pendingTempFile.value?.let { runCatching { it.delete() } }
                                pendingTempFile.value = null
                                pendingPreviewUri.value = null
                                pendingCapturedAt.value = null
                                pendingLocation.value = null
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Retake")
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    return@Column
                }

                Text(
                    "Link to trip (most recent first)",
                    style = MaterialTheme.typography.titleMedium,
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(displayTrips, key = { it.id }) { t ->
                        ListItem(
                            headlineContent = { Text(t.storeNameSnapshot) },
                            supportingContent = { Text(t.day.toString()) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable {
                                    val tempFile = pendingTempFile.value
                                    val capturedAt = pendingCapturedAt.value
                                    if (tempFile == null || capturedAt == null) return@clickable

                                    saveStatus.value = null

                                    scope.launch {
                                        runCatching {
                                            withContext(Dispatchers.IO) {
                                                val saved = saveCapturedPhotoToTrip(
                                                    context = context,
                                                    trip = t,
                                                    profileId = t.profileId,
                                                    tempFile = tempFile,
                                                    capturedAt = capturedAt,
                                                    location = pendingLocation.value,
                                                )
                                                AppGraph.tripRepository.addAttachment(saved)
                                            }
                                        }.onSuccess {
                                            saveStatus.value = "Saved to ${t.day} ${t.storeNameSnapshot}."
                                            runCatching { tempFile.delete() }
                                            pendingTempFile.value = null
                                            pendingPreviewUri.value = null
                                            pendingCapturedAt.value = null
                                            pendingLocation.value = null
                                        }.onFailure {
                                            saveStatus.value = "Failed to save: ${it.message ?: it.javaClass.simpleName}"
                                        }
                                    }
                                },
                        )
                    }
                }
            }

            if (captureError.value != null) {
                Text(
                    "Error: ${captureError.value}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (saveStatus.value != null) {
                Text(
                    saveStatus.value ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f),
                )
            }
        }
    }
}

private suspend fun getCurrentLocation(context: android.content.Context): Location? {
    val client = LocationServices.getFusedLocationProviderClient(context)
    return suspendCancellableCoroutine { cont ->
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                cont.resume(loc)
            }
            .addOnFailureListener {
                cont.resume(null)
            }
    }
}

private suspend fun <T> awaitFuture(future: ListenableFuture<T>): T {
    return suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { future.cancel(true) }

        future.addListener(
            {
                try {
                    cont.resume(future.get())
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            },
            Executor { runnable -> runnable.run() },
        )
    }
}

private fun saveCapturedPhotoToTrip(
    context: android.content.Context,
    trip: TripEntity,
    profileId: String,
    tempFile: File,
    capturedAt: Instant,
    location: Location?,
): AttachmentEntity {
    val destDir = File(context.filesDir, "evidence/${trip.id}").apply { mkdirs() }

    val timeLabel = capturedAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HHmmss"))
    val safeStore = sanitizeFileName(trip.storeNameSnapshot).ifBlank { "trip" }
    val baseName = "${trip.day}_${safeStore}"

    val destFile = File(destDir, "${baseName}_${timeLabel}.jpg")
    tempFile.inputStream().use { input ->
        destFile.outputStream().use { output ->
            input.copyTo(output)
        }
    }

    // Validate saved image (do not trust preview).
    if (!destFile.exists() || destFile.length() < 10_000L) {
        runCatching { destFile.delete() }
        throw IllegalStateException("Saved image is empty or missing")
    }
    runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(destFile.absolutePath, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) {
            throw IllegalStateException("Saved image is not a valid bitmap")
        }
    }.onFailure {
        runCatching { destFile.delete() }
        throw it
    }

    // Burn an overlay onto the actual JPEG (date/time + GPS), as requested.
    runCatching {
        val dtText = capturedAt
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        val gpsText = if (location != null) {
            "${"%.5f".format(location.latitude)}, ${"%.5f".format(location.longitude)}"
        } else {
            null
        }
        burnTextOverlayOnJpeg(
            file = destFile,
            lines = buildList {
                add(dtText)
                if (gpsText != null) add(gpsText)
            },
        )
    }

    runCatching {
        val exif = ExifInterface(destFile)
        val dt = capturedAt
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"))
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dt)
        exif.setAttribute(ExifInterface.TAG_DATETIME, dt)
        exif.setAttribute(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL.toString(),
        )
        if (location != null) {
            exif.setGpsInfo(location)
        }
        exif.saveAttributes()
    }

    val contentUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        destFile,
    )

    return AttachmentEntity(
        profileId = profileId,
        tripId = trip.id,
        uri = contentUri.toString(),
        mimeType = "image/jpeg",
        displayName = "${trip.day} ${trip.storeNameSnapshot} ${timeLabel}",
        addedAt = capturedAt,
    )

}

private fun burnTextOverlayOnJpeg(
    file: File,
    lines: List<String>,
) {
    if (lines.isEmpty()) return

    val originalExif = runCatching { ExifInterface(file) }.getOrNull()
    val orientation = originalExif?.getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_UNDEFINED,
    ) ?: ExifInterface.ORIENTATION_UNDEFINED

    val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return

    val rotatedBitmap = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
        else -> bitmap
    }

    val canvas = Canvas(rotatedBitmap)
    val density = 2f // simple scaling; avoids needing Resources in a non-Compose helper

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 16f * density
        setShadowLayer(2f * density, 0f, 1f * density, android.graphics.Color.BLACK)
    }
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(140, 0, 0, 0)
    }

    val padding = (10f * density)
    val lineGap = (6f * density)
    val textHeights = lines.map { textPaint.fontMetrics.run { bottom - top } }
    val totalTextHeight = textHeights.sum() + lineGap * (lines.size - 1)
    val maxTextWidth = lines.maxOf { textPaint.measureText(it) }

    val left = padding
    val bottom = rotatedBitmap.height - padding
    val top = bottom - totalTextHeight - padding * 2
    val right = left + maxTextWidth + padding * 2

    canvas.drawRoundRect(left, top, right, bottom, 10f * density, 10f * density, bgPaint)

    var y = top + padding - textPaint.fontMetrics.top
    for ((index, line) in lines.withIndex()) {
        canvas.drawText(line, left + padding, y, textPaint)
        y += textHeights[index] + lineGap
    }

    file.outputStream().use { out ->
        rotatedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
    }

    if (rotatedBitmap !== bitmap) {
        bitmap.recycle()
    }
}

private fun rotateBitmap(source: android.graphics.Bitmap, degrees: Float): android.graphics.Bitmap {
    val matrix = Matrix().apply { postRotate(degrees) }
    return android.graphics.Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

private fun sanitizeFileName(input: String): String {
    val trimmed = input.trim()
    val cleaned = trimmed
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .replace(Regex("\\s+"), " ")
        .trim()
    return cleaned.take(80)
}
