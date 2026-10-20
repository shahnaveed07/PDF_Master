package com.dsc.pdfmaster.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dsc.pdfmaster.pdfengine.OfficeToPdfConverter
import com.dsc.pdfmaster.pdfengine.PdfEngine
import com.dsc.pdfmaster.ui.PdfMasterViewModel
import com.dsc.pdfmaster.ui.ScreenRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class ConversionMode(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val isSupported: Boolean = true,
    val officeFormat: OfficeToPdfConverter.OfficeDocumentType? = null
) {
    WORD_TO_PDF(
        title = "Word to PDF (.docx)",
        subtitle = "Convert Word documents (headings, paragraphs, lists, tables, images) to PDF",
        icon = Icons.Default.Description,
        isSupported = true,
        officeFormat = OfficeToPdfConverter.OfficeDocumentType.WORD_DOCX
    ),
    EXCEL_TO_PDF(
        title = "Excel to PDF (.xlsx)",
        subtitle = "Convert Excel workbooks and sheets to structured multi-page PDF tables",
        icon = Icons.Default.TableChart,
        isSupported = true,
        officeFormat = OfficeToPdfConverter.OfficeDocumentType.EXCEL_XLSX
    ),
    PPT_TO_PDF(
        title = "PowerPoint to PDF (.pptx)",
        subtitle = "Convert PowerPoint presentation slides, shapes, tables, and images to PDF",
        icon = Icons.Default.Slideshow,
        isSupported = true,
        officeFormat = OfficeToPdfConverter.OfficeDocumentType.POWERPOINT_PPTX
    ),
    IMAGES_TO_PDF(
        title = "Images to PDF (JPG / PNG)",
        subtitle = "Combine one or more photos into a single multi-page PDF document",
        icon = Icons.Default.PictureAsPdf,
        isSupported = true
    ),
    PDF_TO_IMAGES(
        title = "PDF to Images (PNG / JPG)",
        subtitle = "Render every page of a PDF file into high-resolution PNG or JPEG images",
        icon = Icons.Default.Collections,
        isSupported = true
    ),
    IMAGE_FORMAT(
        title = "PNG ↔ JPEG Image Converter",
        subtitle = "Convert image files between lossless PNG and compressed JPEG formats",
        icon = Icons.Default.SwapHoriz,
        isSupported = true
    ),
    PDF_TO_TEXT(
        title = "PDF to Text (.txt)",
        subtitle = "Extract selectable text from all pages of a PDF into a plain-text file",
        icon = Icons.AutoMirrored.Filled.Notes,
        isSupported = true
    ),
    TEXT_TO_PDF(
        title = "Text (.txt) to PDF",
        subtitle = "Convert a plain-text document or typed text into a formatted multi-page PDF",
        icon = Icons.Default.Description,
        isSupported = true
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConvertPdfScreen(
    viewModel: PdfMasterViewModel,
    onBack: () -> Unit = { viewModel.navigateBack() }
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val conversionPdf by viewModel.conversionSourcePdf.collectAsStateWithLifecycle()
    val pdfRequiresPassword by viewModel.conversionPdfRequiresPassword.collectAsStateWithLifecycle()
    val selectedImageUris by viewModel.selectedImageUris.collectAsStateWithLifecycle()
    val sourceImageUri by viewModel.conversionSourceImageUri.collectAsStateWithLifecycle()
    val sourceImageName by viewModel.conversionSourceImageName.collectAsStateWithLifecycle()
    val sourceTextUri by viewModel.conversionSourceTextUri.collectAsStateWithLifecycle()
    val sourceTextName by viewModel.conversionSourceTextName.collectAsStateWithLifecycle()
    val sourceTextPreview by viewModel.conversionSourceTextPreview.collectAsStateWithLifecycle()
    val sourceOfficeUri by viewModel.conversionSourceOfficeUri.collectAsStateWithLifecycle()
    val sourceOfficeName by viewModel.conversionSourceOfficeName.collectAsStateWithLifecycle()
    val sourceOfficeSummary by viewModel.conversionSourceOfficeSummary.collectAsStateWithLifecycle()
    val sourceOfficePreview by viewModel.conversionSourceOfficePreview.collectAsStateWithLifecycle()
    val conversionResultSummary by viewModel.conversionResultSummary.collectAsStateWithLifecycle()
    val conversionLayoutDisclosure by viewModel.conversionLayoutDisclosure.collectAsStateWithLifecycle()
    val outputFiles by viewModel.conversionOutputFiles.collectAsStateWithLifecycle()
    val extractedText by viewModel.conversionExtractedText.collectAsStateWithLifecycle()
    val errorMessage by viewModel.conversionErrorMessage.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val processingMessage by viewModel.processingMessage.collectAsStateWithLifecycle()

    var selectedMode by rememberSaveable { mutableStateOf(ConversionMode.WORD_TO_PDF) }

    // Options for PDF -> Images
    var targetImageFormatIsPng by rememberSaveable { mutableStateOf(true) }
    var pdfPassword by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    // Options for Image Format conversion
    var convertImageToJpeg by rememberSaveable { mutableStateOf(true) }

    // Options for Images -> PDF
    var imagesToPdfOutputName by rememberSaveable { mutableStateOf("Converted_Images.pdf") }

    // Options for Text -> PDF
    var useTypedTextMode by rememberSaveable { mutableStateOf(false) }
    var typedTitle by rememberSaveable { mutableStateOf("Converted Document") }
    var typedBody by rememberSaveable { mutableStateOf("") }
    var txtToPdfOutputName by rememberSaveable { mutableStateOf("Text_Document.pdf") }

    // Options for Office -> PDF
    var officeOutputName by rememberSaveable(sourceOfficeName, selectedMode) {
        val base = sourceOfficeName?.substringBeforeLast('.') ?: when (selectedMode) {
            ConversionMode.WORD_TO_PDF -> "Word_Document"
            ConversionMode.EXCEL_TO_PDF -> "Excel_Workbook"
            ConversionMode.PPT_TO_PDF -> "Presentation_Slides"
            else -> "Office_Document"
        }
        mutableStateOf("$base.pdf")
    }

    LaunchedEffect(sourceOfficeName) {
        sourceOfficeName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }?.let {
            officeOutputName = "$it.pdf"
        }
    }

    // Launchers
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.selectConversionPdfFromUri(it) }
    }

    val multiPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addImagesForPdf(uris)
        }
    }

    val singleImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.selectConversionImageFromUri(it) }
    }

    val txtFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.selectConversionTextFileFromUri(it)
        }
    }

    val officeFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.selectConversionOfficeFileFromUri(it, selectedMode.officeFormat)
        }
    }

    var fileToExport by remember { mutableStateOf<File?>(null) }
    val saveDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { targetUri: Uri? ->
        val source = fileToExport
        if (targetUri != null && source != null && source.exists()) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(targetUri)?.use { out ->
                            source.inputStream().use { input ->
                                input.copyTo(out)
                            }
                        }
                        true
                    } catch (e: Exception) {
                        false
                    }
                }
                if (ok) {
                    viewModel.showNotification("Saved ${source.name} to device")
                } else {
                    viewModel.showNotification("Failed to export file")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Convert Files & PDF", fontWeight = FontWeight.Bold) },
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
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Select Conversion Format",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Conversion Mode Selector Chips
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ConversionMode.entries.forEach { mode ->
                    FilterChip(
                        selected = selectedMode == mode,
                        enabled = !isProcessing,
                        onClick = {
                            if (selectedMode != mode) {
                                selectedMode = mode
                                pdfPassword = ""
                                viewModel.clearConversionState()
                                if (mode.officeFormat != null) {
                                    viewModel.resetConversionSession()
                                }
                            }
                        },
                        label = { Text(mode.title) },
                        leadingIcon = {
                            Icon(
                                imageVector = mode.icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.testTag("mode_chip_${mode.name.lowercase()}")
                    )
                }
            }

            // Active Mode Header Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
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
                            selectedMode.icon,
                            contentDescription = null,
                            modifier = Modifier.padding(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = selectedMode.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = selectedMode.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                        .testTag("conversion_error_card")
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
                            modifier = Modifier.testTag("conversion_error_text")
                        )
                    }
                }
            }

            // Mode-Specific Input & Controls
            when (selectedMode) {
                ConversionMode.WORD_TO_PDF,
                ConversionMode.EXCEL_TO_PDF,
                ConversionMode.PPT_TO_PDF -> {
                    val expectedFormat = selectedMode.officeFormat!!
                    val extLabel = ".${expectedFormat.expectedExtension}"
                    val pickerMimeTypes = when (expectedFormat) {
                        OfficeToPdfConverter.OfficeDocumentType.WORD_DOCX -> arrayOf(
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "application/msword",
                            "application/octet-stream",
                            "*/*"
                        )
                        OfficeToPdfConverter.OfficeDocumentType.EXCEL_XLSX -> arrayOf(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/vnd.ms-excel",
                            "application/octet-stream",
                            "*/*"
                        )
                        OfficeToPdfConverter.OfficeDocumentType.POWERPOINT_PPTX -> arrayOf(
                            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                            "application/vnd.ms-powerpoint",
                            "application/octet-stream",
                            "*/*"
                        )
                    }

                    // Honest offline layout-fidelity disclosure card
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("office_fidelity_disclosure_card")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = expectedFormat.layoutDisclosure,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Office Source Picker Card
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Source ${expectedFormat.label}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = sourceOfficeName ?: "No $extLabel file selected",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.testTag("selected_office_file_name")
                                    )
                                    if (!sourceOfficeSummary.isNullOrBlank()) {
                                        Text(
                                            text = sourceOfficeSummary!!,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.testTag("selected_office_file_summary")
                                        )
                                    }
                                }
                                OutlinedButton(
                                    onClick = { officeFilePickerLauncher.launch(pickerMimeTypes) },
                                    enabled = !isProcessing,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("select_office_source_button")
                                ) {
                                    Icon(
                                        Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (sourceOfficeUri == null) "Select $extLabel" else "Change")
                                }
                            }

                            if (!sourceOfficePreview.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "Detected Content Preview",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = sourceOfficePreview!!,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 4,
                                            modifier = Modifier.testTag("office_preview_snippet")
                                        )
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = officeOutputName,
                        onValueChange = { officeOutputName = it },
                        label = { Text("Output PDF Filename") },
                        singleLine = true,
                        enabled = !isProcessing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("office_output_name_input")
                    )

                    Button(
                        onClick = {
                            sourceOfficeUri?.let { uri ->
                                viewModel.executeConvertOfficeToPdf(
                                    uri = uri,
                                    expectedType = expectedFormat,
                                    outputName = officeOutputName
                                )
                            }
                        },
                        enabled = sourceOfficeUri != null && !isProcessing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("execute_convert_office_to_pdf_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Convert ${expectedFormat.label} to PDF",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                ConversionMode.IMAGES_TO_PDF -> {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Selected Images (${selectedImageUris.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                OutlinedButton(
                                    onClick = {
                                        multiPhotoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    enabled = !isProcessing,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("pick_images_for_pdf_button")
                                ) {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Select Photos")
                                }
                            }

                            if (selectedImageUris.isNotEmpty()) {
                                selectedImageUris.forEachIndexed { idx, uri ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${idx + 1}. ${uri.lastPathSegment ?: "Image_${idx + 1}"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { viewModel.removeImageForPdf(uri) },
                                            enabled = !isProcessing
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Remove image"
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "No images selected yet. Choose one or more JPG/PNG images.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = imagesToPdfOutputName,
                        onValueChange = { imagesToPdfOutputName = it },
                        label = { Text("Output PDF Filename") },
                        singleLine = true,
                        enabled = !isProcessing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("images_to_pdf_name_input")
                    )

                    Button(
                        onClick = {
                            viewModel.executeImagesToPdf(
                                imagesToPdfOutputName.ifBlank { "Converted_Images.pdf" }
                            )
                        },
                        enabled = selectedImageUris.isNotEmpty() && !isProcessing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("execute_images_to_pdf_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Convert ${selectedImageUris.size} Image(s) to PDF",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                ConversionMode.PDF_TO_IMAGES -> {
                    PdfSourcePickerCard(
                        selectedPdf = conversionPdf,
                        isProcessing = isProcessing,
                        onPickPdf = { pdfPickerLauncher.launch(arrayOf("application/pdf")) }
                    )

                    if (pdfRequiresPassword) {
                        PasswordFieldCard(
                            password = pdfPassword,
                            passwordVisible = passwordVisible,
                            onPasswordChange = { pdfPassword = it },
                            onToggleVisibility = { passwordVisible = !passwordVisible },
                            isProcessing = isProcessing
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterChip(
                            selected = targetImageFormatIsPng,
                            onClick = { targetImageFormatIsPng = true },
                            label = { Text("PNG (Lossless)") },
                            modifier = Modifier.testTag("format_png_chip")
                        )
                        FilterChip(
                            selected = !targetImageFormatIsPng,
                            onClick = { targetImageFormatIsPng = false },
                            label = { Text("JPEG (Compact)") },
                            modifier = Modifier.testTag("format_jpeg_chip")
                        )
                    }

                    Button(
                        onClick = {
                            conversionPdf?.let { pdf ->
                                val fmt = if (targetImageFormatIsPng) {
                                    Bitmap.CompressFormat.PNG
                                } else {
                                    Bitmap.CompressFormat.JPEG
                                }
                                viewModel.executePdfToImages(
                                    file = pdf,
                                    password = pdfPassword.takeIf { it.isNotEmpty() },
                                    format = fmt,
                                    outputPrefix = pdf.nameWithoutExtension
                                )
                            }
                        },
                        enabled = conversionPdf != null && !isProcessing && (!pdfRequiresPassword || pdfPassword.isNotEmpty()),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("execute_pdf_to_images_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Collections, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (targetImageFormatIsPng) "Export PDF Pages as PNG" else "Export PDF Pages as JPEG",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                ConversionMode.IMAGE_FORMAT -> {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Source Image",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = sourceImageName ?: "No image selected",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.testTag("selected_conversion_image_name")
                                )
                            }
                            OutlinedButton(
                                onClick = { singleImagePickerLauncher.launch("image/*") },
                                enabled = !isProcessing,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("pick_single_image_button")
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (sourceImageUri == null) "Select Image" else "Change")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterChip(
                            selected = convertImageToJpeg,
                            onClick = { convertImageToJpeg = true },
                            label = { Text("Convert to JPEG (.jpg)") },
                            modifier = Modifier.testTag("target_jpeg_chip")
                        )
                        FilterChip(
                            selected = !convertImageToJpeg,
                            onClick = { convertImageToJpeg = false },
                            label = { Text("Convert to PNG (.png)") },
                            modifier = Modifier.testTag("target_png_chip")
                        )
                    }

                    Button(
                        onClick = {
                            sourceImageUri?.let { uri ->
                                viewModel.executeConvertImageFormat(
                                    uri = uri,
                                    isToJpeg = convertImageToJpeg
                                )
                            }
                        },
                        enabled = sourceImageUri != null && !isProcessing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("execute_image_format_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (convertImageToJpeg) "Convert Image to JPEG" else "Convert Image to PNG",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                ConversionMode.PDF_TO_TEXT -> {
                    PdfSourcePickerCard(
                        selectedPdf = conversionPdf,
                        isProcessing = isProcessing,
                        onPickPdf = { pdfPickerLauncher.launch(arrayOf("application/pdf")) }
                    )

                    if (pdfRequiresPassword) {
                        PasswordFieldCard(
                            password = pdfPassword,
                            passwordVisible = passwordVisible,
                            onPasswordChange = { pdfPassword = it },
                            onToggleVisibility = { passwordVisible = !passwordVisible },
                            isProcessing = isProcessing
                        )
                    }

                    Button(
                        onClick = {
                            conversionPdf?.let { pdf ->
                                viewModel.executePdfToText(
                                    file = pdf,
                                    password = pdfPassword.takeIf { it.isNotEmpty() },
                                    outputName = "${pdf.nameWithoutExtension}_text.txt"
                                )
                            }
                        },
                        enabled = conversionPdf != null && !isProcessing && (!pdfRequiresPassword || pdfPassword.isNotEmpty()),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("execute_pdf_to_text_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Extract Text (.txt)", fontWeight = FontWeight.Bold)
                    }

                    if (!extractedText.isNullOrBlank()) {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("extracted_text_preview_card")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Extracted Text Preview",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = extractedText!!.take(1000),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                ConversionMode.TEXT_TO_PDF -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterChip(
                            selected = !useTypedTextMode,
                            onClick = { useTypedTextMode = false },
                            label = { Text("Select .txt File") },
                            modifier = Modifier.testTag("txt_file_mode_chip")
                        )
                        FilterChip(
                            selected = useTypedTextMode,
                            onClick = { useTypedTextMode = true },
                            label = { Text("Type / Paste Text") },
                            modifier = Modifier.testTag("typed_text_mode_chip")
                        )
                    }

                    if (!useTypedTextMode) {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Source Text File (.txt)",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = sourceTextName ?: "No .txt file selected",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.testTag("selected_txt_file_name")
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            txtFilePickerLauncher.launch(arrayOf("text/plain", "text/*"))
                                        },
                                        enabled = !isProcessing,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.testTag("pick_txt_file_button")
                                    ) {
                                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (sourceTextUri == null) "Select .txt" else "Change")
                                    }
                                }

                                if (!sourceTextPreview.isNullOrBlank()) {
                                    Text(
                                        text = sourceTextPreview!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 4
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = txtToPdfOutputName,
                            onValueChange = { txtToPdfOutputName = it },
                            label = { Text("Output PDF Filename") },
                            singleLine = true,
                            enabled = !isProcessing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("txt_to_pdf_name_input")
                        )

                        Button(
                            onClick = {
                                sourceTextUri?.let { uri ->
                                    viewModel.executeConvertTextFileToPdf(uri, txtToPdfOutputName)
                                }
                            },
                            enabled = sourceTextUri != null && !isProcessing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("execute_txt_file_to_pdf_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Convert .txt File to PDF", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedTextField(
                            value = typedTitle,
                            onValueChange = { typedTitle = it },
                            label = { Text("Document Title") },
                            singleLine = true,
                            enabled = !isProcessing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("convert_title_input")
                        )

                        OutlinedTextField(
                            value = typedBody,
                            onValueChange = { typedBody = it },
                            label = { Text("Text Content") },
                            minLines = 6,
                            maxLines = 12,
                            enabled = !isProcessing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("convert_body_input")
                        )

                        Button(
                            onClick = {
                                viewModel.executeCreateTextPdf(
                                    textContent = typedBody,
                                    outputName = typedTitle.ifBlank { "Converted_Text" }
                                )
                            },
                            enabled = typedBody.isNotBlank() && !isProcessing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("convert_text_to_pdf_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate PDF from Text", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Processing Progress Banner
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
                                text = processingMessage.ifBlank { "Converting..." },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        OutlinedButton(
                            onClick = { viewModel.resetProcessingState() },
                            modifier = Modifier.testTag("cancel_conversion_button")
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            }

            // Output Files Result Card
            if (outputFiles.isNotEmpty()) {
                ConversionOutputSection(
                    files = outputFiles,
                    resultSummary = conversionResultSummary,
                    layoutDisclosure = conversionLayoutDisclosure,
                    onOpenPdf = { pdfFile ->
                        viewModel.setSelectedPdfFile(pdfFile)
                        viewModel.navigateTo(ScreenRoute.PDF_VIEWER)
                    },
                    onShareFile = { file ->
                        try {
                            val shareUri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val mime = when (file.extension.lowercase()) {
                                "pdf" -> "application/pdf"
                                "png" -> "image/png"
                                "jpg", "jpeg" -> "image/jpeg"
                                "txt" -> "text/plain"
                                else -> "*/*"
                            }
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = mime
                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Converted File"))
                        } catch (e: Exception) {
                            viewModel.showNotification("Unable to share file")
                        }
                    },
                    onExportFile = { file ->
                        fileToExport = file
                        saveDocumentLauncher.launch(file.name)
                    }
                )
            }
        }
    }
}

@Composable
private fun PdfSourcePickerCard(
    selectedPdf: File?,
    isProcessing: Boolean,
    onPickPdf: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Source PDF Document",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = selectedPdf?.name ?: "No PDF file selected",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("selected_conversion_pdf_name")
                )
                if (selectedPdf != null) {
                    Text(
                        text = PdfEngine.formatFileSize(selectedPdf.length()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            OutlinedButton(
                onClick = onPickPdf,
                enabled = !isProcessing,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("select_conversion_pdf_button")
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (selectedPdf == null) "Select PDF" else "Change")
            }
        }
    }
}

@Composable
private fun PasswordFieldCard(
    password: String,
    passwordVisible: Boolean,
    onPasswordChange: (String) -> Unit,
    onToggleVisibility: () -> Unit,
    isProcessing: Boolean
) {
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text("PDF Open Password (Required)") },
        singleLine = true,
        enabled = !isProcessing,
        visualTransformation = if (passwordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("conversion_pdf_password_input")
    )
}

@Composable
private fun ConversionOutputSection(
    files: List<File>,
    resultSummary: String?,
    layoutDisclosure: String?,
    onOpenPdf: (File) -> Unit,
    onShareFile: (File) -> Unit,
    onExportFile: (File) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("conversion_output_card")
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
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Conversion Complete (${files.size} file${if (files.size > 1) "s" else ""})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (!resultSummary.isNullOrBlank()) {
                        Text(
                            text = resultSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("conversion_result_summary")
                        )
                    }
                }
            }

            if (!layoutDisclosure.isNullOrBlank()) {
                Text(
                    text = layoutDisclosure,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("conversion_layout_disclosure")
                )
            }

            files.take(10).forEach { file ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = PdfEngine.formatFileSize(file.length()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (file.extension.equals("pdf", ignoreCase = true)) {
                                OutlinedButton(
                                    onClick = { onOpenPdf(file) },
                                    modifier = Modifier.testTag("open_converted_pdf_button")
                                ) {
                                    Text("Open PDF")
                                }
                            }
                            IconButton(
                                onClick = { onShareFile(file) },
                                modifier = Modifier.testTag("share_converted_file_${file.name}")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share ${file.name}")
                            }
                            IconButton(
                                onClick = { onExportFile(file) },
                                modifier = Modifier.testTag("export_converted_file_${file.name}")
                            ) {
                                Icon(Icons.Default.Download, contentDescription = "Save ${file.name}")
                            }
                        }
                    }
                }
            }

            if (files.size > 10) {
                Text(
                    text = "+ ${files.size - 10} more files saved in app storage",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
