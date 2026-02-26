package com.trimsytrack.ui.screens

import android.content.Intent
import android.database.Cursor
import android.location.Geocoder
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trimsytrack.AppGraph
import com.trimsytrack.BuildConfig
import com.trimsytrack.data.entities.AttachmentEntity
import com.trimsytrack.data.entities.SyncStatus
import com.trimsytrack.ui.media.importDocumentToTripFiles
import com.trimsytrack.ui.vm.TripDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.flow.first
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TripDetailScreen(
    tripId: Long,
    showAddMediaImmediately: Boolean = false,
    onOpenCameraForTrip: (tripId: Long, scheduleReceiptReminder: Boolean) -> Unit,
    onOpenMediaReviewForTrip: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val vm: TripDetailViewModel = viewModel(factory = TripDetailViewModel.factory(tripId))

    val trip by vm.trip.collectAsState()
    val attachments by AppGraph.tripRepository.observeAttachments(tripId).collectAsState(initial = emptyList())

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importMessage = remember { mutableStateOf<String?>(null) }
    val showAddMediaPrompt = remember { mutableStateOf(showAddMediaImmediately) }

    val showFeeDialog = remember { mutableStateOf(false) }
    val feeInput = remember { mutableStateOf("") }
    val feeInputError = remember { mutableStateOf<String?>(null) }
    val pendingFeeMinor = remember { mutableStateOf<Int?>(null) }
    val pendingFeeHasMoms = remember { mutableStateOf<Boolean?>(null) }
    val pendingFeeAccountType = remember { mutableStateOf<String?>(null) }

    // Dialog-local selections (defaulted when opening the dialog)
    val feeHasMoms = remember { mutableStateOf(false) }
    val feeAccountType = remember { mutableStateOf("BUSINESS") }

    val showSyncRejectedDialog = remember { mutableStateOf(false) }

    val uploadFeePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            val feeMinor = pendingFeeMinor.value
            val hasMoms = pendingFeeHasMoms.value
            val accountType = pendingFeeAccountType.value
            if (uri == null || feeMinor == null) {
                pendingFeeMinor.value = null
                pendingFeeHasMoms.value = null
                pendingFeeAccountType.value = null
                return@rememberLauncherForActivityResult
            }

            if (BuildConfig.DEBUG) {
                Log.d(
                    "TrimsyTrack",
                    "ParkingFeePhoto result uri=$uri feeMinor=$feeMinor tripId=$tripId",
                )
            }

            val t = trip
            if (t == null) {
                importMessage.value = "Trip not loaded yet. Try again."
                pendingFeeMinor.value = null
                pendingFeeHasMoms.value = null
                pendingFeeAccountType.value = null
                return@rememberLauncherForActivityResult
            }

            scope.launch {
                try {
                    // Persist read access when the picker supports it (ACTION_OPEN_DOCUMENT).
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                    val baseEntity = importDocumentToTripFiles(
                        context = context,
                        uid = t.uid,
                        tripId = tripId,
                        tripDay = t.day,
                        tripStoreNameSnapshot = t.storeNameSnapshot,
                        sourceUri = uri,
                    )
                    val feeText = formatMinorAmount(feeMinor)
                    val parkingTicketId = t.parkingTicketId ?: UUID.randomUUID().toString()
                    val entity = baseEntity.copy(
                        clientRef = parkingTicketId,
                        displayName = "Parking/Traffic fee ${feeText} — receipt (${parkingTicketId.take(8)})"
                    )
                    AppGraph.tripRepository.addAttachment(entity)
                    vm.updateTrip(
                        t.copy(
                            parkingTrafficFeeMinor = feeMinor,
                            parkingTicketId = parkingTicketId,
                            parkingTicketHasMoms = hasMoms,
                            parkingTicketAccountType = accountType,
                        )
                    )
                    importMessage.value = "Fee saved and receipt photo attached."
                } catch (e: Exception) {
                    importMessage.value = e.message ?: "Failed to upload fee receipt photo"
                } finally {
                    pendingFeeMinor.value = null
                    pendingFeeHasMoms.value = null
                    pendingFeeAccountType.value = null
                }
            }
        },
    )

    val autoOpenedReview = remember { mutableStateOf(false) }
    LaunchedEffect(showAddMediaImmediately) {
        if (!showAddMediaImmediately) return@LaunchedEffect
        if (autoOpenedReview.value) return@LaunchedEffect
        autoOpenedReview.value = true
        showAddMediaPrompt.value = false
        onOpenCameraForTrip(tripId, true)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = {
                    val stopId = trip?.clientRef?.trim().orEmpty()
                    val runId = trip?.runId ?: 0L
                    Column {
                        Text(if (runId > 0L) "Trip ID #$runId" else "Trip")
                        if (stopId.isNotBlank()) {
                            Text(
                                text = "Stop ID $stopId",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            )
                        }
                    }
                },
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
        }
    ) { padding ->
        val zone = remember { ZoneId.systemDefault() }
        val dateFmt = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
        val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }

        val startAddressLine = remember { mutableStateOf<String?>(null) }
        val startCity = remember { mutableStateOf<String?>(null) }
        val endAddressLine = remember { mutableStateOf<String?>(null) }
        val endCity = remember { mutableStateOf<String?>(null) }

        LaunchedEffect(trip?.id) {
            val t = trip ?: return@LaunchedEffect

            suspend fun lookup(lat: Double, lng: Double): Pair<String?, String?> {
                return withContext(Dispatchers.IO) {
                    runCatching {
                        @Suppress("DEPRECATION")
                        val geo = Geocoder(context, Locale.getDefault())
                        @Suppress("DEPRECATION")
                        val list = geo.getFromLocation(lat, lng, 1)
                        val a = list?.firstOrNull()
                        val line = a?.getAddressLine(0)?.takeIf { it.isNotBlank() }
                        val city = a?.locality
                            ?.takeIf { it.isNotBlank() }
                            ?: a?.subAdminArea?.replace(" kommun", "")?.takeIf { it.isNotBlank() }
                        (line to city)
                    }.getOrDefault(null to null)
                }
            }

            val (sLine, sCity) = lookup(t.startLat, t.startLng)
            startAddressLine.value = sLine
            startCity.value = sCity

            val (eLine, eCity) = lookup(t.storeLatSnapshot, t.storeLngSnapshot)
            endAddressLine.value = eLine
            endCity.value = eCity
        }

        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            val t = trip
            Text(t?.storeNameSnapshot ?: "…", style = MaterialTheme.typography.titleMedium)

            Spacer(Modifier.height(8.dp))

            if (t != null) {
                val startLocal = runCatching { LocalDateTime.ofInstant(t.startedAt, zone) }.getOrNull()
                val endLocal = runCatching { LocalDateTime.ofInstant(t.endedAt, zone) }.getOrNull()

                val startTime = startLocal?.format(timeFmt).orEmpty()
                val startDate = startLocal?.format(dateFmt).orEmpty()
                val endTime = endLocal?.format(timeFmt).orEmpty()
                val endDate = endLocal?.format(dateFmt).orEmpty()

                if (t.syncStatus == SyncStatus.REJECTED) {
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = "Sync rejected — needs your attention",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = { showSyncRejectedDialog.value = true }) {
                            Text("Details")
                        }
                    }

                    if (showSyncRejectedDialog.value) {
                        val machine = t.syncErrorMachineCode?.takeIf { it.isNotBlank() }
                        val msg = t.syncErrorMessage?.takeIf { it.isNotBlank() }

                        AlertDialog(
                            onDismissRequest = { showSyncRejectedDialog.value = false },
                            title = { Text("Trip rejected by backend") },
                            text = {
                                Column {
                                    Text(
                                        msg ?: "The backend rejected this trip. You can edit the trip and retry.",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    if (machine != null) {
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "Reason code: $machine",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                                        )
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showSyncRejectedDialog.value = false
                                        vm.retrySync()
                                    }
                                ) {
                                    Text("Retry sync")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showSyncRejectedDialog.value = false }) {
                                    Text("Close")
                                }
                            },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    "Start",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                )
                Text(
                    listOfNotNull(startAddressLine.value, startCity.value).joinToString(" • ").ifBlank { t.startLabelSnapshot },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                )
                if (startTime.isNotBlank()) {
                    Text(
                        "Time: $startTime",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                    )
                }
                if (startDate.isNotBlank()) {
                    Text(
                        "Date: $startDate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                    )
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(10.dp))

                Text(
                    "End",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                )
                Text(
                    listOfNotNull(endAddressLine.value, endCity.value.takeIf { !it.isNullOrBlank() } ?: t.citySnapshot.takeIf { it.isNotBlank() })
                        .joinToString(" • ")
                        .ifBlank { t.storeNameSnapshot },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                )

                if (endTime.isNotBlank()) {
                    Text(
                        "Time: $endTime",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                    )
                }
                if (endDate.isNotBlank()) {
                    Text(
                        "Date: $endDate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                    )
                }

                Spacer(Modifier.height(12.dp))

                val feeMinor = t.parkingTrafficFeeMinor
                if (feeMinor != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Parking fee: ${formatMinorAmount(feeMinor)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(
                        onClick = {
                            feeInputError.value = null
                            feeInput.value = feeMinor?.let { formatMinorAmount(it) }.orEmpty()
                            feeHasMoms.value = t.parkingTicketHasMoms ?: false
                            feeAccountType.value = t.parkingTicketAccountType ?: "BUSINESS"
                            showFeeDialog.value = true
                        },
                    ) {
                        Text(if (feeMinor == null) "Add parking fee" else "Edit parking fee")
                    }
                }

                Spacer(Modifier.height(14.dp))

                val distanceKm = t.distanceMeters / 1000.0
                Text(
                    text = "Distance Traveled: ${String.format(Locale.getDefault(), "%.1f", distanceKm)} km",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(t?.notes ?: "", style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(18.dp))
            Text("Pictures/Receipts", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            if (importMessage.value != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    importMessage.value ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                )
            }

            Spacer(Modifier.height(8.dp))
            if (attachments.isEmpty()) {
                Text(
                    "No pictures/receipts saved yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                )
            } else {
                attachments.forEach { a ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            a.displayName,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(
                            onClick = {
                                runCatching {
                                    val uri = Uri.parse(a.uri)
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, a.mimeType)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Open receipt"))
                                }
                            }
                        ) { Text("Open") }
                        TextButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            val file = fileFromOurFileProviderUri(context, Uri.parse(a.uri))
                                            file?.delete()
                                        }
                                        AppGraph.tripRepository.deleteAttachment(a.id)
                                    } catch (_: Exception) {
                                        // Best-effort delete; keep UI simple.
                                    }
                                }
                            }
                        ) { Text("Delete") }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            if (showAddMediaPrompt.value) {
                Text(
                    "Add media to this trip now?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, alignment = androidx.compose.ui.Alignment.End),
            ) {
                OutlinedButton(
                    onClick = {
                        showAddMediaPrompt.value = false
                        onOpenMediaReviewForTrip(tripId)
                    },
                ) {
                    Text("Add Media")
                }
            }

            if (showAddMediaPrompt.value) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { showAddMediaPrompt.value = false }) {
                        Text("Not now")
                    }
                }
            }
        }
    }

    if (showFeeDialog.value) {
        AlertDialog(
            onDismissRequest = {
                showFeeDialog.value = false
                feeInputError.value = null
            },
            title = { Text("Parking/Traffic fee") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter the amount, then upload a photo of the receipt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    )
                    TextField(
                        value = feeInput.value,
                        onValueChange = {
                            feeInput.value = it
                            feeInputError.value = null
                        },
                        singleLine = true,
                        label = { Text("Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text(
                        "Konto",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val toggleShape = RoundedCornerShape(10.dp)
                        val selected = feeAccountType.value

                        if (selected == "PRIVATE") {
                            Button(
                                onClick = { feeAccountType.value = "PRIVATE" },
                                shape = toggleShape,
                            ) { Text("Privat") }
                        } else {
                            OutlinedButton(
                                onClick = { feeAccountType.value = "PRIVATE" },
                                shape = toggleShape,
                            ) { Text("Privat") }
                        }

                        if (selected == "BUSINESS") {
                            Button(
                                onClick = { feeAccountType.value = "BUSINESS" },
                                shape = toggleShape,
                            ) { Text("Affärskonto") }
                        } else {
                            OutlinedButton(
                                onClick = { feeAccountType.value = "BUSINESS" },
                                shape = toggleShape,
                            ) { Text("Affärskonto") }
                        }
                    }

                    Text(
                        "Moms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val toggleShape = RoundedCornerShape(10.dp)
                        val selected = feeHasMoms.value

                        if (selected) {
                            Button(
                                onClick = { feeHasMoms.value = true },
                                shape = toggleShape,
                            ) { Text("Ja") }
                        } else {
                            OutlinedButton(
                                onClick = { feeHasMoms.value = true },
                                shape = toggleShape,
                            ) { Text("Ja") }
                        }

                        if (!selected) {
                            Button(
                                onClick = { feeHasMoms.value = false },
                                shape = toggleShape,
                            ) { Text("Nej") }
                        } else {
                            OutlinedButton(
                                onClick = { feeHasMoms.value = false },
                                shape = toggleShape,
                            ) { Text("Nej") }
                        }
                    }

                    if (feeInputError.value != null) {
                        Text(
                            feeInputError.value ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsed = parseMinorAmountOrNull(feeInput.value)
                        if (parsed == null || parsed < 0) {
                            feeInputError.value = "Enter a valid amount"
                            return@TextButton
                        }

                        val t = trip
                        if (t == null) {
                            feeInputError.value = "Trip not loaded yet"
                            return@TextButton
                        }

                        pendingFeeMinor.value = parsed
                        pendingFeeHasMoms.value = feeHasMoms.value
                        pendingFeeAccountType.value = feeAccountType.value
                        showFeeDialog.value = false
                        if (BuildConfig.DEBUG) {
                            Log.d(
                                "TrimsyTrack",
                                "ParkingFeePhoto launch mime=image/* tripId=$tripId feeMinor=$parsed",
                            )
                        }
                        uploadFeePhotoLauncher.launch(arrayOf("image/*"))
                    }
                ) { Text("Upload photo") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showFeeDialog.value = false
                        feeInputError.value = null
                    }
                ) { Text("Cancel") }
            },
        )
    }
}

private fun parseMinorAmountOrNull(input: String): Int? {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return null

    // Accept both "," and "." as decimal separators.
    val normalized = trimmed.replace(" ", "").replace(',', '.')
    return runCatching {
        val bd = BigDecimal(normalized)
        val scaled = bd.setScale(2, java.math.RoundingMode.HALF_UP)
        val minor = scaled.multiply(BigDecimal(100))
        minor.intValueExact()
    }.getOrNull()
}

private fun formatMinorAmount(minor: Int): String {
    val abs = kotlin.math.abs(minor)
    val whole = abs / 100
    val frac = abs % 100
    val sign = if (minor < 0) "-" else ""
    return "$sign$whole.${frac.toString().padStart(2, '0')}"
}

private fun importReceiptToAppFiles(
    context: android.content.Context,
    uid: String,
    tripId: Long,
    tripDay: java.time.LocalDate?,
    tripStoreNameSnapshot: String?,
    sourceUri: Uri,
    receiptId: String,
): AttachmentEntity {
    val resolver = context.contentResolver

    val mimeType = resolver.getType(sourceUri) ?: guessMimeTypeFromName(queryDisplayName(resolver, sourceUri))
    val originalName = queryDisplayName(resolver, sourceUri).ifBlank { "receipt" }
    val safeName = sanitizeFileName(originalName)

    val extension = when {
        safeName.contains('.') -> ""
        mimeType == "application/pdf" -> ".pdf"
        mimeType == "image/png" -> ".png"
        mimeType == "image/jpeg" -> ".jpg"
        mimeType.startsWith("image/") -> ".img"
        else -> ""
    }

    val tripPrefix = buildString {
        if (tripDay != null) append(tripDay.toString())
        if (!tripStoreNameSnapshot.isNullOrBlank()) {
            if (isNotEmpty()) append(" ")
            append(tripStoreNameSnapshot)
        }
    }.trim()

    val safeTripPrefix = sanitizeFileName(tripPrefix).ifBlank { "trip_${tripId}" }

    val destDir = File(context.filesDir, "evidence/${tripId}").apply { mkdirs() }
    val safeReceiptId = sanitizeFileName(receiptId)
    val destFile = File(destDir, "${safeReceiptId}_${safeTripPrefix}_${System.currentTimeMillis()}_${safeName}${extension}")

    resolver.openInputStream(sourceUri).use { input ->
        requireNotNull(input) { "Could not open selected file" }
        destFile.outputStream().use { output ->
            input.copyTo(output)
        }
    }

    val sha256 = runCatching { com.trimsytrack.util.Hashing.sha256Hex(destFile) }.getOrNull()
    val sizeBytes = runCatching { destFile.length() }.getOrNull()
    val now = Instant.now()

    val contentUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        destFile,
    )

    return AttachmentEntity(
        uid = uid,
        tripId = tripId,
        uri = contentUri.toString(),
        mimeType = mimeType,
        displayName = when {
            tripPrefix.isBlank() -> "$receiptId — $originalName"
            else -> "$receiptId — $tripPrefix — $originalName"
        },
        capturedAt = now,
        addedAt = now,
        sha256 = sha256,
        sizeBytes = sizeBytes,
        linkedAt = now,
        linkedByDeviceId = null,
    )
}

private fun queryDisplayName(resolver: android.content.ContentResolver, uri: Uri): String {
    var cursor: Cursor? = null
    return try {
        cursor = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        if (cursor != null && cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) cursor.getString(idx) ?: "" else ""
        } else {
            ""
        }
    } catch (_: Exception) {
        ""
    } finally {
        cursor?.close()
    }
}

private fun sanitizeFileName(name: String): String {
    val trimmed = name.trim().ifBlank { "receipt" }
    return trimmed.replace(Regex("[^A-Za-z0-9._-]+"), "_")
}

private fun guessMimeTypeFromName(name: String): String {
    val lower = name.lowercase()
    return when {
        lower.endsWith(".pdf") -> "application/pdf"
        lower.endsWith(".png") -> "image/png"
        lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
        else -> "application/octet-stream"
    }
}

private fun fileFromOurFileProviderUri(context: android.content.Context, uri: Uri): File? {
    if (uri.scheme != "content") return null
    if (uri.authority != "${context.packageName}.fileprovider") return null

    val segments = uri.pathSegments
    if (segments.isEmpty()) return null

    val root = segments.first()
    val relativePath = segments.drop(1).joinToString(File.separator)

    return when (root) {
        "files" -> File(context.filesDir, relativePath)
        "cache" -> File(context.cacheDir, relativePath)
        else -> null
    }
}
