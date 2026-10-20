package com.dsc.pdfmaster.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsc.pdfmaster.pdfengine.PdfEngine
import com.dsc.pdfmaster.ui.PdfMasterViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemovePasswordScreen(
    viewModel: PdfMasterViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentPdf by viewModel.selectedPdfFile.collectAsState()
    val securityState by viewModel.selectedPdfSecurityState.collectAsState()
    val errorMessage by viewModel.unprotectErrorMessage.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()

    var passwordInput by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var replaceOriginal by remember(currentPdf) { mutableStateOf(false) }
    var outputNameInput by remember(currentPdf) {
        mutableStateOf("${currentPdf?.nameWithoutExtension ?: "Document"}_unprotected.pdf")
    }

    LaunchedEffect(currentPdf) {
        viewModel.refreshSelectedPdfSecurityState(currentPdf)
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            passwordInput = ""
            replaceOriginal = false
            viewModel.importAndSelectPdf(it, openViewer = false)
        }
    }

    val canReplaceOriginalSafely = remember(currentPdf) {
        val file = currentPdf
        file != null &&
            file.exists() &&
            file.canWrite() &&
            file.parentFile?.canWrite() == true &&
            PdfEngine.isInsideDirectory(file, context.filesDir)
    }

    val securitySubtitle = when (val state = securityState) {
        null -> if (currentPdf != null) "Inspecting PDF security..." else "Select a file to unlock"
        is PdfEngine.PdfSecurityState.UserPasswordRequired -> "Encrypted • User or owner password required"
        is PdfEngine.PdfSecurityState.OwnerPasswordRestricted -> "Permission-restricted (${state.pageCount} pages) • Owner password required"
        is PdfEngine.PdfSecurityState.EncryptedWithBlankPassword -> "Encrypted with blank password (${state.pageCount} pages) • Ready to unlock"
        is PdfEngine.PdfSecurityState.Unencrypted -> "Not encrypted (${state.pageCount} pages) • Select a protected PDF"
        is PdfEngine.PdfSecurityState.UnsupportedEncryption -> "Unsupported encryption algorithm"
        is PdfEngine.PdfSecurityState.InvalidOrCorrupted -> "Invalid or corrupted PDF file"
    }

    val isFileEligibleForUnprotect = when (securityState) {
        null -> currentPdf != null
        is PdfEngine.PdfSecurityState.UserPasswordRequired,
        is PdfEngine.PdfSecurityState.OwnerPasswordRestricted,
        is PdfEngine.PdfSecurityState.EncryptedWithBlankPassword -> true
        is PdfEngine.PdfSecurityState.Unencrypted,
        is PdfEngine.PdfSecurityState.UnsupportedEncryption,
        is PdfEngine.PdfSecurityState.InvalidOrCorrupted -> false
    }

    val isPasswordInputSatisfied = when (securityState) {
        is PdfEngine.PdfSecurityState.EncryptedWithBlankPassword -> true
        else -> passwordInput.isNotEmpty()
    }

    val isActionEnabled = currentPdf != null &&
        !isProcessing &&
        isFileEligibleForUnprotect &&
        isPasswordInputSatisfied &&
        (replaceOriginal || outputNameInput.isNotBlank())

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Remove PDF Password", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("remove_password_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentPdf?.name ?: "No PDF Selected",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = securitySubtitle,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                        enabled = !isProcessing,
                        modifier = Modifier.testTag("remove_password_select_file_button")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Select", fontSize = 12.sp)
                    }
                }
            }

            val inlineWarningOrError = errorMessage ?: when (val state = securityState) {
                is PdfEngine.PdfSecurityState.UnsupportedEncryption -> state.message
                is PdfEngine.PdfSecurityState.InvalidOrCorrupted -> state.message
                is PdfEngine.PdfSecurityState.Unencrypted -> "This PDF is already unencrypted. Select a password-protected PDF to remove its security."
                else -> null
            }

            if (inlineWarningOrError != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("remove_password_error_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = inlineWarningOrError,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "CURRENT SECURITY DETAILS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            val passwordLabel = when (securityState) {
                is PdfEngine.PdfSecurityState.OwnerPasswordRestricted -> "Enter Owner Password"
                is PdfEngine.PdfSecurityState.EncryptedWithBlankPassword -> "Password (Optional — Blank Password Supported)"
                else -> "Enter Current Password"
            }

            OutlinedTextField(
                value = passwordInput,
                onValueChange = {
                    passwordInput = it
                    viewModel.clearUnprotectFeedback()
                },
                label = { Text(passwordLabel) },
                singleLine = true,
                enabled = !isProcessing,
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle password visibility"
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("current_password_input_field")
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = outputNameInput,
                onValueChange = {
                    outputNameInput = it
                    viewModel.clearUnprotectFeedback()
                },
                label = { Text("Unprotected PDF Name (Save as New)") },
                singleLine = true,
                enabled = !isProcessing && !replaceOriginal,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("unprotected_output_name_field")
            )

            if (canReplaceOriginalSafely) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = replaceOriginal,
                        onCheckedChange = { replaceOriginal = it },
                        enabled = !isProcessing,
                        modifier = Modifier.testTag("replace_original_checkbox")
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = "Replace original file instead of saving as new",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Original is only replaced after the unlocked output is verified.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val pdf = currentPdf ?: return@Button
                    viewModel.executeRemovePassword(
                        file = pdf,
                        password = passwordInput,
                        outputName = outputNameInput.trim(),
                        replaceOriginal = replaceOriginal && canReplaceOriginalSafely
                    )
                },
                enabled = isActionEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("execute_remove_password_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (replaceOriginal && canReplaceOriginalSafely) {
                        "Remove Password & Replace Original"
                    } else {
                        "Remove Password & Save as New"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}
