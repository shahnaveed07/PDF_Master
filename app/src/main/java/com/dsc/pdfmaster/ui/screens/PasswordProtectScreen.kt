package com.dsc.pdfmaster.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dsc.pdfmaster.pdfengine.PdfEngine
import com.dsc.pdfmaster.ui.PdfMasterViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordProtectScreen(
    viewModel: PdfMasterViewModel,
    onBack: () -> Unit = { viewModel.navigateBack() }
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val selectedFile by viewModel.protectSourcePdf.collectAsStateWithLifecycle()
    val securityState by viewModel.protectPdfSecurityState.collectAsStateWithLifecycle()
    val requiresCurrentPassword by viewModel.protectPdfRequiresCurrentPassword.collectAsStateWithLifecycle()
    val errorMessage by viewModel.protectPdfErrorMessage.collectAsStateWithLifecycle()
    val protectResult by viewModel.protectResult.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val processingMessage by viewModel.processingMessage.collectAsStateWithLifecycle()

    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var currentPdfPassword by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var currentPasswordVisible by rememberSaveable { mutableStateOf(false) }

    var allowPrinting by rememberSaveable { mutableStateOf(true) }
    var allowCopy by rememberSaveable { mutableStateOf(true) }
    var replaceOriginal by rememberSaveable { mutableStateOf(false) }
    var outputName by rememberSaveable(selectedFile?.absolutePath) {
        mutableStateOf(
            selectedFile?.nameWithoutExtension?.let { "${it}_protected.pdf" } ?: "protected_document.pdf"
        )
    }

    LaunchedEffect(selectedFile?.absolutePath) {
        currentPdfPassword = ""
        replaceOriginal = false
        selectedFile?.nameWithoutExtension?.let {
            outputName = "${it}_protected.pdf"
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.selectProtectSourcePdfFromUri(it) }
    }

    var pendingExportFile by remember { mutableStateOf<File?>(null) }
    val exportPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { targetUri ->
        val fileToExport = pendingExportFile
        if (targetUri != null && fileToExport != null && fileToExport.exists()) {
            scope.launch {
                val copied = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(targetUri)?.use { out ->
                            fileToExport.inputStream().use { input ->
                                input.copyTo(out)
                            }
                        }
                        true
                    } catch (e: Exception) {
                        false
                    }
                }
                if (copied) {
                    viewModel.showNotification("Saved protected PDF copy to device")
                } else {
                    viewModel.showNotification("Failed to export protected PDF")
                }
            }
        }
    }

    val passwordsMatch = password.isNotEmpty() && password == confirmPassword
    val showMismatchMessage = confirmPassword.isNotEmpty() && password != confirmPassword
    val showBlankPasswordHint = password.isNotEmpty() && password.isBlank()

    val isUnsupportedOrCorrupt = securityState is PdfEngine.PdfSecurityState.InvalidOrCorrupted ||
        securityState is PdfEngine.PdfSecurityState.UnsupportedEncryption

    val canExecuteProtect = selectedFile != null &&
        !isProcessing &&
        !isUnsupportedOrCorrupt &&
        password.isNotBlank() &&
        passwordsMatch &&
        (!requiresCurrentPassword || currentPdfPassword.isNotBlank()) &&
        (replaceOriginal || outputName.isNotBlank())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Protect PDF", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.padding(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "256-bit AES PDF Encryption",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Keep this password safe. It will be required every time you open the protected PDF.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Source PDF Section
            if (selectedFile == null) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("protect_empty_state_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "No PDF Document Selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Select a PDF document from your device to apply password protection.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                            enabled = !isProcessing,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("select_protect_pdf_button")
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select PDF", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                val file = selectedFile!!
                Card(
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("selected_protect_file_card")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = file.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.testTag("selected_protect_file_name")
                                    )
                                    val sizeLabel = PdfEngine.formatFileSize(file.length())
                                    val statusLabel = when (val state = securityState) {
                                        is PdfEngine.PdfSecurityState.Unencrypted ->
                                            "$sizeLabel • ${state.pageCount} page(s) • Unencrypted"
                                        is PdfEngine.PdfSecurityState.UserPasswordRequired ->
                                            "$sizeLabel • Password Protected (Current password required)"
                                        is PdfEngine.PdfSecurityState.OwnerPasswordRestricted ->
                                            "$sizeLabel • ${state.pageCount} page(s) • Owner Restricted"
                                        is PdfEngine.PdfSecurityState.EncryptedWithBlankPassword ->
                                            "$sizeLabel • ${state.pageCount} page(s) • Blank User Password"
                                        is PdfEngine.PdfSecurityState.UnsupportedEncryption ->
                                            "$sizeLabel • Unsupported Encryption"
                                        is PdfEngine.PdfSecurityState.InvalidOrCorrupted ->
                                            "$sizeLabel • Invalid or Corrupted PDF"
                                        null -> sizeLabel
                                    }
                                    Text(
                                        text = statusLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.testTag("selected_protect_file_info")
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                                enabled = !isProcessing,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("change_protect_pdf_button")
                            ) {
                                Text("Change")
                            }
                        }
                    }
                }
            }

            // Error Message Banner
            if (!errorMessage.isNullOrBlank()) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("protect_error_card")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.testTag("protect_error_text")
                        )
                    }
                }
            }

            // Current Password Field if source PDF is already encrypted / owner-restricted
            if (selectedFile != null && requiresCurrentPassword) {
                OutlinedTextField(
                    value = currentPdfPassword,
                    onValueChange = {
                        currentPdfPassword = it
                        viewModel.clearProtectFeedback()
                    },
                    label = { Text("Current / Owner PDF Password") },
                    supportingText = {
                        Text("This PDF is already protected. Enter its existing password to authorize re-encryption.")
                    },
                    singleLine = true,
                    enabled = !isProcessing,
                    visualTransformation = if (currentPasswordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(
                            onClick = { currentPasswordVisible = !currentPasswordVisible },
                            modifier = Modifier.testTag("toggle_current_password_visibility")
                        ) {
                            Icon(
                                imageVector = if (currentPasswordVisible) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = if (currentPasswordVisible) {
                                    "Hide current password"
                                } else {
                                    "Show current password"
                                }
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("current_pdf_password_input")
                )
            }

            // New Password Field
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    viewModel.clearProtectFeedback()
                },
                label = { Text("New Open Password") },
                isError = showBlankPasswordHint,
                supportingText = {
                    if (showBlankPasswordHint) {
                        Text(
                            text = "Password cannot be blank or whitespace only.",
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text("Required to open and view the encrypted PDF.")
                    }
                },
                singleLine = true,
                enabled = selectedFile != null && !isProcessing && !isUnsupportedOrCorrupt,
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        modifier = Modifier.testTag("toggle_password_visibility")
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("password_input")
            )

            // Confirm Password Field
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    viewModel.clearProtectFeedback()
                },
                label = { Text("Confirm Password") },
                isError = showMismatchMessage,
                supportingText = {
                    if (showMismatchMessage) {
                        Text(
                            text = "Passwords do not match.",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("password_mismatch_error")
                        )
                    } else if (passwordsMatch) {
                        Text("Passwords match.")
                    } else {
                        Text("Re-enter the password to confirm.")
                    }
                },
                singleLine = true,
                enabled = selectedFile != null && !isProcessing && !isUnsupportedOrCorrupt,
                visualTransformation = if (confirmPasswordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(
                        onClick = { confirmPasswordVisible = !confirmPasswordVisible },
                        modifier = Modifier.testTag("toggle_confirm_password_visibility")
                    ) {
                        Icon(
                            imageVector = if (confirmPasswordVisible) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = if (confirmPasswordVisible) {
                                "Hide confirm password"
                            } else {
                                "Show confirm password"
                            }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("confirm_password_input")
            )

            // Document Permissions & Output Options
            if (selectedFile != null && !isUnsupportedOrCorrupt) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Permissions & Output Mode",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isProcessing) { allowPrinting = !allowPrinting }
                        ) {
                            Checkbox(
                                checked = allowPrinting,
                                onCheckedChange = { allowPrinting = it },
                                enabled = !isProcessing,
                                modifier = Modifier.testTag("allow_printing_checkbox")
                            )
                            Text(
                                text = "Allow Printing",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isProcessing) { allowCopy = !allowCopy }
                        ) {
                            Checkbox(
                                checked = allowCopy,
                                onCheckedChange = { allowCopy = it },
                                enabled = !isProcessing,
                                modifier = Modifier.testTag("allow_copy_checkbox")
                            )
                            Text(
                                text = "Allow Copying Text & Graphics",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isProcessing) { replaceOriginal = false }
                        ) {
                            RadioButton(
                                selected = !replaceOriginal,
                                onClick = { replaceOriginal = false },
                                enabled = !isProcessing,
                                modifier = Modifier.testTag("save_as_new_radio")
                            )
                            Column {
                                Text(
                                    text = "Save as New Protected PDF (Recommended)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Keeps the original file untouched and creates a new encrypted copy.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isProcessing) { replaceOriginal = true }
                        ) {
                            RadioButton(
                                selected = replaceOriginal,
                                onClick = { replaceOriginal = true },
                                enabled = !isProcessing,
                                modifier = Modifier.testTag("replace_original_radio")
                            )
                            Column {
                                Text(
                                    text = "Replace Original File",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Atomically replaces the source PDF only after verifying the encrypted output.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!replaceOriginal) {
                            OutlinedTextField(
                                value = outputName,
                                onValueChange = { outputName = it },
                                label = { Text("Output PDF Filename") },
                                singleLine = true,
                                enabled = !isProcessing,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                                    .testTag("output_name_input")
                            )
                        }
                    }
                }
            }

            // Processing Indicator
            if (isProcessing) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = processingMessage.ifBlank { "Applying 256-bit AES encryption..." },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        OutlinedButton(
                            onClick = { viewModel.resetProcessingState() },
                            modifier = Modifier.testTag("cancel_protect_button")
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            }

            // Primary Action Button
            Button(
                onClick = {
                    val file = selectedFile ?: return@Button
                    viewModel.executeProtectPdf(
                        file = file,
                        password = password,
                        outputName = outputName.ifBlank { "${file.nameWithoutExtension}_protected.pdf" },
                        confirmPassword = confirmPassword,
                        currentPassword = currentPdfPassword.takeIf { it.isNotBlank() },
                        allowPrinting = allowPrinting,
                        allowCopy = allowCopy,
                        replaceOriginal = replaceOriginal
                    )
                },
                enabled = canExecuteProtect,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("protect_pdf_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (replaceOriginal) "Encrypt & Replace Original PDF" else "Encrypt & Save Protected PDF",
                    fontWeight = FontWeight.Bold
                )
            }

            // Verified Result Card
            protectResult?.let { result ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("protect_result_card")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "PDF Encrypted & Verified",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${result.file.name} • ${result.pageCount} page(s) • ${PdfEngine.formatFileSize(result.fileSizeBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag("protect_result_summary")
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.openPdfInViewer(result.file) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("open_protected_pdf_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Open in Reader")
                            }

                            OutlinedButton(
                                onClick = {
                                    try {
                                        val shareUri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            result.file
                                        )
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/pdf"
                                            putExtra(Intent.EXTRA_STREAM, shareUri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Protected PDF"))
                                    } catch (e: Exception) {
                                        viewModel.showNotification("Unable to share file")
                                    }
                                },
                                modifier = Modifier.testTag("share_protected_pdf_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share")
                            }

                            OutlinedButton(
                                onClick = {
                                    pendingExportFile = result.file
                                    exportPdfLauncher.launch(result.file.name)
                                },
                                modifier = Modifier.testTag("export_protected_pdf_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = "Save As")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SupportingTextContent(text: String): @Composable () -> Unit = {
    Text(text = text)
}
