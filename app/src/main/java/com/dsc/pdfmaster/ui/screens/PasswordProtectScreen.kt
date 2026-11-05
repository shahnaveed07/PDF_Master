package com.dsc.pdfmaster.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dsc.pdfmaster.pdfengine.PdfEngine
import com.dsc.pdfmaster.ui.PdfMasterViewModel
import com.dsc.pdfmaster.ui.theme.Dimens
import com.dsc.pdfmaster.ui.util.rememberWindowInfo
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
    val windowInfo = rememberWindowInfo()

    val selectedFile by viewModel.protectSourcePdf.collectAsStateWithLifecycle()
    val securityState by viewModel.protectPdfSecurityState.collectAsStateWithLifecycle()
    val requiresCurrentPassword by viewModel.protectPdfRequiresCurrentPassword.collectAsStateWithLifecycle()
    val errorMessage by viewModel.protectPdfErrorMessage.collectAsStateWithLifecycle()
    val protectResult by viewModel.protectResult.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val selectedKeyLengthBits by viewModel.selectedProtectKeyLengthBits.collectAsStateWithLifecycle()

    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var ownerPassword by remember { mutableStateOf("") }
    var currentPdfPassword by remember { mutableStateOf("") }

    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var ownerPasswordVisible by remember { mutableStateOf(false) }
    var currentPasswordVisible by remember { mutableStateOf(false) }

    var allowPrinting by remember { mutableStateOf(true) }
    var allowCopy by remember { mutableStateOf(true) }
    var useSeparateOwnerPassword by remember { mutableStateOf(false) }
    var replaceOriginal by rememberSaveable { mutableStateOf(false) }
    var outputName by rememberSaveable(selectedFile?.absolutePath) {
        mutableStateOf(
            selectedFile?.nameWithoutExtension?.let { "${it}_protected.pdf" } ?: "protected_document.pdf"
        )
    }

    fun clearSensitiveInputs() {
        password = ""
        confirmPassword = ""
        ownerPassword = ""
        currentPdfPassword = ""
        passwordVisible = false
        confirmPasswordVisible = false
        ownerPasswordVisible = false
        currentPasswordVisible = false
    }

    DisposableEffect(Unit) {
        onDispose { clearSensitiveInputs() }
    }

    LaunchedEffect(selectedFile?.absolutePath) {
        clearSensitiveInputs()
        replaceOriginal = false
        selectedFile?.nameWithoutExtension?.let {
            outputName = "${it}_protected.pdf"
        }
    }

    LaunchedEffect(protectResult) {
        if (protectResult != null) {
            clearSensitiveInputs()
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.selectProtectSourcePdfFromUri(it) }
    }

    val requiresOwnerPassword = !allowPrinting || !allowCopy || useSeparateOwnerPassword
    val passwordsMatch = password.isNotBlank() && password == confirmPassword
    val showMismatchMessage = confirmPassword.isNotEmpty() && password != confirmPassword
    val isFormValid = selectedFile != null &&
        passwordsMatch &&
        (!requiresOwnerPassword || ownerPassword.isNotBlank()) &&
        (!requiresCurrentPassword || currentPdfPassword.isNotBlank()) &&
        !isProcessing

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Protect PDF", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("protect_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Dimens.Spacing16),
            contentAlignment = Alignment.TopCenter
        ) {
            val contentModifier = Modifier
                .fillMaxSize()
                .widthIn(max = Dimens.MaxContentWidth)

            if (windowInfo.isExpanded) {
                // Two-Pane Side-by-Side Layout
                Row(
                    modifier = contentModifier.padding(vertical = Dimens.Spacing16),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing24)
                ) {
                    // Left Pane: Document Card & Key Length
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        ProtectDocumentCard(
                            selectedFile = selectedFile,
                            securityState = securityState,
                            onSelectPdf = { pdfPickerLauncher.launch(arrayOf("application/pdf")) }
                        )

                        Spacer(modifier = Modifier.height(Dimens.Spacing16))

                        Text(
                            text = "ENCRYPTION STRENGTH",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(Dimens.Spacing8))

                        listOf(256, 128).forEach { bits ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = Dimens.Spacing8)
                                    .clickable { viewModel.setProtectKeyLengthBits(bits) },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selectedKeyLengthBits == bits) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                ),
                                shape = RoundedCornerShape(Dimens.CornerSmall)
                            ) {
                                Row(
                                    modifier = Modifier.padding(Dimens.Spacing12),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedKeyLengthBits == bits,
                                        onClick = { viewModel.setProtectKeyLengthBits(bits) }
                                    )
                                    Spacer(modifier = Modifier.width(Dimens.Spacing8))
                                    Column {
                                        Text(
                                            text = "$bits-bit AES Encryption",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = if (bits == 256) "Maximum industry standard security" else "Standard PDF encryption compatibility",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Right Pane: Passwords & Execute
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            if (requiresCurrentPassword) {
                                OutlinedTextField(
                                    value = currentPdfPassword,
                                    onValueChange = { currentPdfPassword = it },
                                    label = { Text("Current PDF Password") },
                                    singleLine = true,
                                    visualTransformation = if (currentPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { currentPasswordVisible = !currentPasswordVisible }) {
                                            Icon(
                                                imageVector = if (currentPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = "Toggle password visibility"
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("current_pdf_password_field")
                                )
                                Spacer(modifier = Modifier.height(Dimens.Spacing12))
                            }

                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("User Password (Required to open)") },
                                singleLine = true,
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle password visibility"
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().testTag("user_password_field")
                            )

                            Spacer(modifier = Modifier.height(Dimens.Spacing12))

                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it },
                                label = { Text("Confirm User Password") },
                                singleLine = true,
                                isError = showMismatchMessage,
                                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                        Icon(
                                            imageVector = if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle password visibility"
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().testTag("confirm_user_password_field")
                            )

                            if (showMismatchMessage) {
                                Text(
                                    text = "Passwords do not match",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(Dimens.Spacing12))

                            OutlinedTextField(
                                value = outputName,
                                onValueChange = { outputName = it },
                                label = { Text("Protected PDF File Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("protected_output_name_field")
                            )
                        }

                        Spacer(modifier = Modifier.height(Dimens.Spacing24))

                        Button(
                            onClick = {
                                val file = selectedFile ?: return@Button
                                viewModel.executeProtectPdf(
                                    file = file,
                                    password = password,
                                    ownerPassword = if (requiresOwnerPassword) ownerPassword else password,
                                    currentPassword = currentPdfPassword,
                                    keyLengthBits = selectedKeyLengthBits,
                                    allowPrinting = allowPrinting,
                                    allowCopy = allowCopy,
                                    outputName = outputName.trim(),
                                    replaceOriginal = replaceOriginal
                                )
                            },
                            enabled = isFormValid,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(Dimens.ButtonHeight)
                                .testTag("execute_protect_button"),
                            shape = RoundedCornerShape(Dimens.CornerMedium)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null)
                            Spacer(modifier = Modifier.width(Dimens.Spacing8))
                            Text("Encrypt & Protect PDF", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            } else {
                // Stacked Vertical Layout
                Column(
                    modifier = contentModifier
                        .padding(vertical = Dimens.Spacing16)
                        .verticalScroll(rememberScrollState())
                ) {
                    ProtectDocumentCard(
                        selectedFile = selectedFile,
                        securityState = securityState,
                        onSelectPdf = { pdfPickerLauncher.launch(arrayOf("application/pdf")) }
                    )

                    Spacer(modifier = Modifier.height(Dimens.Spacing16))

                    if (requiresCurrentPassword) {
                        OutlinedTextField(
                            value = currentPdfPassword,
                            onValueChange = { currentPdfPassword = it },
                            label = { Text("Current PDF Password") },
                            singleLine = true,
                            visualTransformation = if (currentPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { currentPasswordVisible = !currentPasswordVisible }) {
                                    Icon(
                                        imageVector = if (currentPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle password visibility"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("current_pdf_password_field")
                        )
                        Spacer(modifier = Modifier.height(Dimens.Spacing12))
                    }

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("User Password (Required to open)") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password visibility"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("user_password_field")
                    )

                    Spacer(modifier = Modifier.height(Dimens.Spacing12))

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm User Password") },
                        singleLine = true,
                        isError = showMismatchMessage,
                        visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                Icon(
                                    imageVector = if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password visibility"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("confirm_user_password_field")
                    )

                    if (showMismatchMessage) {
                        Text(
                            text = "Passwords do not match",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(Dimens.Spacing12))

                    OutlinedTextField(
                        value = outputName,
                        onValueChange = { outputName = it },
                        label = { Text("Protected PDF File Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("protected_output_name_field")
                    )

                    Spacer(modifier = Modifier.height(Dimens.Spacing24))

                    Button(
                        onClick = {
                            val file = selectedFile ?: return@Button
                            viewModel.executeProtectPdf(
                                file = file,
                                password = password,
                                ownerPassword = if (requiresOwnerPassword) ownerPassword else password,
                                currentPassword = currentPdfPassword,
                                keyLengthBits = selectedKeyLengthBits,
                                allowPrinting = allowPrinting,
                                allowCopy = allowCopy,
                                outputName = outputName.trim(),
                                replaceOriginal = replaceOriginal
                            )
                        },
                        enabled = isFormValid,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Dimens.ButtonHeight)
                            .testTag("execute_protect_button"),
                        shape = RoundedCornerShape(Dimens.CornerMedium)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null)
                        Spacer(modifier = Modifier.width(Dimens.Spacing8))
                        Text("Encrypt & Protect PDF", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProtectDocumentCard(
    selectedFile: File?,
    securityState: PdfEngine.PdfSecurityState?,
    onSelectPdf: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(Dimens.CornerMedium)
    ) {
        Row(
            modifier = Modifier.padding(Dimens.Spacing16),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(Dimens.Spacing12))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = selectedFile?.name ?: "No PDF Selected",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = when (securityState) {
                        is PdfEngine.PdfSecurityState.Unencrypted -> "Unencrypted (${securityState.pageCount} pages)"
                        is PdfEngine.PdfSecurityState.UserPasswordRequired -> "Encrypted • User password required"
                        is PdfEngine.PdfSecurityState.OwnerPasswordRestricted -> "Permission-restricted"
                        else -> "Select a PDF file to encrypt"
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = onSelectPdf,
                modifier = Modifier.testTag("protect_select_file_button"),
                shape = RoundedCornerShape(Dimens.CornerMedium)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Select", fontSize = 12.sp)
            }
        }
    }
}
