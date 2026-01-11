package com.trimsytrack.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private enum class ProfileKind { PRIVATE, BUSINESS }
private enum class BusinessKind { ENSKILD_FIRMA, AKTIEBOLAG }

private enum class DocType(val id: String, val title: String) {
    CONTRACTS("contracts", "Contracts"),
    PURCHASE_AGREEMENTS("purchase_agreements", "Purchase agreements"),
    DRIVING_JOURNAL("driving_journal", "Driving journal"),
    VERIFICATION_LIST("verification_list", "Verification list"),
    VAT_REPORT("vat_report", "VAT report"),
    SALES_RECEIPT("sales_receipt", "Sales receipt"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProfileScreen(
    onSubmit: suspend (CreateProfilePayload) -> Unit,
    onSignOut: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var step by rememberSaveable { mutableStateOf(1) }

    // Step 2
    var profileName by rememberSaveable { mutableStateOf("") }
    var profileKind by rememberSaveable { mutableStateOf(ProfileKind.BUSINESS) }
    var profilePictureUri by rememberSaveable { mutableStateOf<String?>(null) }

    // Private confirmation
    var showPrivateConfirm by remember { mutableStateOf(false) }

    // Step 4/5/6/7 business
    var businessKind by rememberSaveable { mutableStateOf(BusinessKind.ENSKILD_FIRMA) }
    var businessName by rememberSaveable { mutableStateOf("") }
    var useProfileNameAsBusinessName by rememberSaveable { mutableStateOf(true) }

    var organisationNumber by rememberSaveable { mutableStateOf("") }
    var vatRegistered by rememberSaveable { mutableStateOf(false) }

    var ownerName by rememberSaveable { mutableStateOf("") }
    var phoneNumber by rememberSaveable { mutableStateOf("") }
    var countryOfOrigin by rememberSaveable { mutableStateOf("SE") }
    var emailAddress by rememberSaveable { mutableStateOf("") }
    var companyAddress by rememberSaveable { mutableStateOf("") }
    var website by rememberSaveable { mutableStateOf("") }

    // Step 8 social
    var facebook by rememberSaveable { mutableStateOf("") }
    var instagram by rememberSaveable { mutableStateOf("") }
    var tiktok by rememberSaveable { mutableStateOf("") }
    var ebay by rememberSaveable { mutableStateOf("") }
    var tradera by rememberSaveable { mutableStateOf("") }
    var vinted by rememberSaveable { mutableStateOf("") }

    // Step 9 logos
    var squareLogoUri by rememberSaveable { mutableStateOf<String?>(null) }
    var rectLogoUri by rememberSaveable { mutableStateOf<String?>(null) }

    // Step 10 document usage
    var useLogosInDocuments by rememberSaveable { mutableStateOf(true) }
    var enabledDocIds by rememberSaveable {
        mutableStateOf(DocType.entries.map { it.id }.toSet())
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            when (step) {
                2 -> profilePictureUri = uri.toString()
                9 -> {
                    // If square empty pick that first; else rectangular.
                    if (squareLogoUri.isNullOrBlank()) squareLogoUri = uri.toString() else rectLogoUri = uri.toString()
                }
            }
        },
    )

    fun next() {
        if (busy) return
        error = null
        when (step) {
            1 -> step = 2
            2 -> {
                val name = profileName.trim()
                if (name.isBlank()) {
                    error = "Profile name is required"
                    return
                }
                if (profileKind == ProfileKind.PRIVATE) {
                    showPrivateConfirm = true
                } else {
                    step = 4
                }
            }
            4 -> step = 5
            5 -> {
                if (useProfileNameAsBusinessName) {
                    businessName = profileName.trim()
                }
                if (businessName.trim().isBlank()) {
                    error = "Business name is required"
                    return
                }
                step = 6
            }
            6 -> {
                if (organisationNumber.trim().isBlank()) {
                    error = "Organisationsnummer is required"
                    return
                }
                step = 7
            }
            7 -> {
                if (ownerName.trim().isBlank()) {
                    error = "Owner’s name is required"
                    return
                }
                if (emailAddress.trim().isBlank()) {
                    error = "Email address is required"
                    return
                }
                if (companyAddress.trim().isBlank()) {
                    error = "Company address is required"
                    return
                }
                step = 8
            }
            8 -> step = 9
            9 -> step = 10
            10 -> {
                val payload = CreateProfilePayload(
                    profileName = profileName.trim(),
                    profileKind = profileKind.name,
                    profilePictureUri = profilePictureUri,
                    businessKind = businessKind.name.takeIf { profileKind == ProfileKind.BUSINESS },
                    businessName = businessName.trim().takeIf { profileKind == ProfileKind.BUSINESS },
                    organisationNumber = organisationNumber.trim().takeIf { profileKind == ProfileKind.BUSINESS },
                    vatRegistered = vatRegistered.takeIf { profileKind == ProfileKind.BUSINESS },
                    ownerName = ownerName.trim().takeIf { profileKind == ProfileKind.BUSINESS },
                    phoneNumber = phoneNumber.trim().takeIf { profileKind == ProfileKind.BUSINESS },
                    countryOfOrigin = countryOfOrigin.trim().takeIf { profileKind == ProfileKind.BUSINESS },
                    emailAddress = emailAddress.trim().takeIf { profileKind == ProfileKind.BUSINESS },
                    companyAddress = companyAddress.trim().takeIf { profileKind == ProfileKind.BUSINESS },
                    website = website.trim().takeIf { profileKind == ProfileKind.BUSINESS && website.trim().isNotBlank() },
                    social = mapOf(
                        "facebook" to facebook.trim(),
                        "instagram" to instagram.trim(),
                        "tiktok" to tiktok.trim(),
                        "ebay" to ebay.trim(),
                        "tradera" to tradera.trim(),
                        "vinted" to vinted.trim(),
                    ).filterValues { it.isNotBlank() }.takeIf { profileKind == ProfileKind.BUSINESS },
                    squareLogoUri = squareLogoUri,
                    rectangularLogoUri = rectLogoUri,
                    useLogosInDocuments = useLogosInDocuments,
                    documentLogoOptOutIds = if (useLogosInDocuments) {
                        DocType.entries.map { it.id }.filterNot { enabledDocIds.contains(it) }
                    } else {
                        DocType.entries.map { it.id }
                    },
                )

                scope.launch {
                    busy = true
                    error = null
                    try {
                        onSubmit(payload)
                    } catch (t: Throwable) {
                        error = t.message ?: "Something went wrong"
                    } finally {
                        busy = false
                    }
                }
            }
        }
    }

    fun back() {
        if (busy) return
        error = null
        when (step) {
            1 -> {}
            2 -> step = 1
            4 -> step = 2
            5 -> step = 4
            6 -> step = 5
            7 -> step = 6
            8 -> step = 7
            9 -> step = 8
            10 -> step = 9
        }
    }

    if (showPrivateConfirm) {
        AlertDialog(
            onDismissRequest = { /* mandatory */ },
            title = { Text("Create Private Profile?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("A Business Profile unlocks powerful automatic features:")
                    Text("• VAT / Moms reports\n• Automatic driving journals\n• Logos on documents\n• Professional receipts and contracts\n• Compliance tools")
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        showPrivateConfirm = false
                        // Completion rules: create only after confirmation.
                        val payload = CreateProfilePayload(
                            profileName = profileName.trim(),
                            profileKind = ProfileKind.PRIVATE.name,
                            profilePictureUri = profilePictureUri,
                            businessKind = null,
                            businessName = null,
                            organisationNumber = null,
                            vatRegistered = null,
                            ownerName = null,
                            phoneNumber = null,
                            countryOfOrigin = null,
                            emailAddress = null,
                            companyAddress = null,
                            website = null,
                            social = null,
                            squareLogoUri = null,
                            rectangularLogoUri = null,
                            useLogosInDocuments = false,
                            documentLogoOptOutIds = DocType.entries.map { it.id },
                        )

                        scope.launch {
                            busy = true
                            error = null
                            try {
                                onSubmit(payload)
                            } catch (t: Throwable) {
                                error = t.message ?: "Something went wrong"
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) {
                    Text("Create Private Profile")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        showPrivateConfirm = false
                        profileKind = ProfileKind.BUSINESS
                        step = 4
                    },
                ) {
                    Text("Switch to Business Profile")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Profile") },
                actions = {
                    TextButton(onClick = onSignOut, enabled = !busy) {
                        Text("Sign out")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Step $step of 10", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))

                if (!error.isNullOrBlank()) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }

                when (step) {
                    1 -> {
                        Text("Create Profile", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("This is mandatory and blocks the app until completed.")
                    }

                    2 -> {
                        Text("Basic Profile Setup", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = profileName,
                            onValueChange = { profileName = it },
                            label = { Text("Profile name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy,
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = profileKind == ProfileKind.PRIVATE,
                                onCheckedChange = { checked -> profileKind = if (checked) ProfileKind.PRIVATE else ProfileKind.BUSINESS },
                                enabled = !busy,
                            )
                            Text("Private profile")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = profileKind == ProfileKind.BUSINESS,
                                onCheckedChange = { checked -> profileKind = if (checked) ProfileKind.BUSINESS else ProfileKind.PRIVATE },
                                enabled = !busy,
                            )
                            Text("Business profile")
                        }

                        OutlinedButton(
                            onClick = { pickImageLauncher.launch("image/*") },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (profilePictureUri.isNullOrBlank()) "Pick profile picture" else "Profile picture selected")
                        }
                    }

                    4 -> {
                        Text("Business Type", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = businessKind == BusinessKind.ENSKILD_FIRMA,
                                onCheckedChange = { if (it) businessKind = BusinessKind.ENSKILD_FIRMA },
                                enabled = !busy,
                            )
                            Text("Enskild Firma")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = businessKind == BusinessKind.AKTIEBOLAG,
                                onCheckedChange = { if (it) businessKind = BusinessKind.AKTIEBOLAG },
                                enabled = !busy,
                            )
                            Text("Aktiebolag")
                        }
                    }

                    5 -> {
                        Text("Business Name", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = useProfileNameAsBusinessName,
                                onCheckedChange = { useProfileNameAsBusinessName = it },
                                enabled = !busy,
                            )
                            Text("Use profile name as business name")
                        }
                        OutlinedTextField(
                            value = if (useProfileNameAsBusinessName) profileName else businessName,
                            onValueChange = { businessName = it },
                            label = { Text("Business name") },
                            singleLine = true,
                            enabled = !busy && !useProfileNameAsBusinessName,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    6 -> {
                        Text("Business Registration Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = organisationNumber,
                            onValueChange = { organisationNumber = it },
                            label = { Text("Organisationsnummer") },
                            singleLine = true,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = vatRegistered,
                                onCheckedChange = { vatRegistered = it },
                                enabled = !busy,
                            )
                            Text("VAT / Moms registered")
                        }
                    }

                    7 -> {
                        Text("Business Contact & Identity", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = ownerName,
                            onValueChange = { ownerName = it },
                            label = { Text("Owner’s name") },
                            singleLine = true,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it },
                            label = { Text("Phone number") },
                            singleLine = true,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = countryOfOrigin,
                            onValueChange = { countryOfOrigin = it },
                            label = { Text("Country of origin") },
                            singleLine = true,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = emailAddress,
                            onValueChange = { emailAddress = it },
                            label = { Text("Email address") },
                            singleLine = true,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = companyAddress,
                            onValueChange = { companyAddress = it },
                            label = { Text("Company address") },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = website,
                            onValueChange = { website = it },
                            label = { Text("Website (optional)") },
                            singleLine = true,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    8 -> {
                        Text("Social & Marketplace Profiles (optional)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(value = facebook, onValueChange = { facebook = it }, label = { Text("Facebook") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = instagram, onValueChange = { instagram = it }, label = { Text("Instagram") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = tiktok, onValueChange = { tiktok = it }, label = { Text("TikTok") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = ebay, onValueChange = { ebay = it }, label = { Text("eBay") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = tradera, onValueChange = { tradera = it }, label = { Text("Tradera") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = vinted, onValueChange = { vinted = it }, label = { Text("Vinted") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    }

                    9 -> {
                        Text("Logo Uploads (Business)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Recommended sizes:\n" +
                                "• Square logo (small corner/slot): 500×500\n" +
                                "• Wide logo (document header): 1200×500",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                        )
                        OutlinedButton(
                            onClick = { pickImageLauncher.launch("image/*") },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (squareLogoUri.isNullOrBlank()) "Pick square logo" else "Square logo selected")
                        }
                        OutlinedButton(
                            onClick = { pickImageLauncher.launch("image/*") },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (rectLogoUri.isNullOrBlank()) "Pick rectangular logo" else "Rectangular logo selected")
                        }
                        Text("If a logo is missing, documents render without it.")
                    }

                    10 -> {
                        Text("Document Usage Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Use logos in documents")
                            Switch(
                                checked = useLogosInDocuments,
                                onCheckedChange = { useLogosInDocuments = it },
                                enabled = !busy,
                            )
                        }
                        HorizontalDivider()
                        Text("Allow per-document opt-out")
                        DocType.entries.forEach { doc ->
                            val enabled = enabledDocIds.contains(doc.id)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(doc.title)
                                Checkbox(
                                    checked = enabled,
                                    enabled = !busy && useLogosInDocuments,
                                    onCheckedChange = { checked ->
                                        enabledDocIds = if (checked) enabledDocIds + doc.id else enabledDocIds - doc.id
                                    },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = { back() }, enabled = !busy && step != 1) {
                        Text("Back")
                    }

                    if (busy) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 10.dp))
                            Text("Working…")
                        }
                    } else {
                        Button(onClick = { next() }, enabled = !busy) {
                            Text(if (step == 10) "Create" else "Next")
                        }
                    }
                }

                if (step == 8 || step == 9) {
                    TextButton(onClick = { step += 1 }, enabled = !busy) {
                        Text("Skip")
                    }
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

data class CreateProfilePayload(
    val profileName: String,
    val profileKind: String,
    val profilePictureUri: String?,

    val businessKind: String?,
    val businessName: String?,
    val organisationNumber: String?,
    val vatRegistered: Boolean?,

    val ownerName: String?,
    val phoneNumber: String?,
    val countryOfOrigin: String?,
    val emailAddress: String?,
    val companyAddress: String?,
    val website: String?,

    val social: Map<String, String>?,

    val squareLogoUri: String?,
    val rectangularLogoUri: String?,

    val useLogosInDocuments: Boolean,
    val documentLogoOptOutIds: List<String>,
)
