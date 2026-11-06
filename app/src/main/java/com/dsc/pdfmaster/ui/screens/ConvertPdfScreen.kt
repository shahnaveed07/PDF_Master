package com.dsc.pdfmaster.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EditNote
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dsc.pdfmaster.pdfengine.OfficeToPdfConverter
import com.dsc.pdfmaster.pdfengine.PdfEngine
import com.dsc.pdfmaster.ui.PdfMasterViewModel
import com.dsc.pdfmaster.ui.ScreenRoute
import com.dsc.pdfmaster.ui.theme.Dimens
import com.dsc.pdfmaster.ui.util.rememberWindowInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class ConversionMode(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tag: String,
    val category: String,
    val isSupported: Boolean = true,
    val officeFormat: OfficeToPdfConverter.OfficeDocumentType? = null
) {
    WORD_TO_PDF(
        title = "Word to PDF (.docx)",
        subtitle = "Convert Word documents (headings, paragraphs, lists, tables, images) to PDF",
        icon = Icons.Default.Description,
        tag = "word_to_pdf",
        category = "OFFICE TO PDF",
        officeFormat = OfficeToPdfConverter.OfficeDocumentType.WORD_DOCX
    ),
    EXCEL_TO_PDF(
        title = "Excel to PDF (.xlsx)",
        subtitle = "Convert Excel workbooks and sheets to structured multi-page PDF tables",
        icon = Icons.Default.TableChart,
        tag = "excel_to_pdf",
        category = "OFFICE TO PDF",
        officeFormat = OfficeToPdfConverter.OfficeDocumentType.EXCEL_XLSX
    ),
    PPT_TO_PDF(
        title = "PowerPoint to PDF (.pptx)",
        subtitle = "Convert PowerPoint presentation slides, shapes, tables, and images to PDF",
        icon = Icons.Default.Slideshow,
        tag = "ppt_to_pdf",
        category = "OFFICE TO PDF",
        officeFormat = OfficeToPdfConverter.OfficeDocumentType.POWERPOINT_PPTX
    ),
    PDF_TO_IMAGES(
        title = "PDF to Images (PNG / JPG)",
        subtitle = "Render every page of a PDF file into high-resolution PNG or JPEG images",
        icon = Icons.Default.Collections,
        tag = "pdf_to_images",
        category = "PDF CONVERSION"
    ),
    PDF_TO_TEXT(
        title = "PDF to Text (.txt)",
        subtitle = "Extract selectable text from all pages of a PDF into a plain-text file",
        icon = Icons.AutoMirrored.Filled.Notes,
        tag = "pdf_to_text",
        category = "PDF CONVERSION"
    ),
    IMAGES_TO_PDF(
        title = "Images to PDF (JPG / PNG)",
        subtitle = "Combine one or more photos into a single multi-page PDF document",
        icon = Icons.Default.PictureAsPdf,
        tag = "images_to_pdf",
        category = "IMAGE CONVERSION"
    ),
    IMAGE_FORMAT(
        title = "PNG ↔ JPEG Image Converter",
        subtitle = "Convert image files between lossless PNG and compressed JPEG formats",
        icon = Icons.Default.SwapHoriz,
        tag = "image_format",
        category = "IMAGE CONVERSION"
    ),
    TXT_TO_PDF(
        title = "TXT File to PDF",
        subtitle = "Convert a plain-text document into a formatted multi-page PDF",
        icon = Icons.Default.Description,
        tag = "txt_to_pdf",
        category = "TEXT CONVERSION"
    ),
    TYPED_TEXT_TO_PDF(
        title = "Type Text to PDF",
        subtitle = "Type or paste formatted text directly to create a PDF",
        icon = Icons.Default.EditNote,
        tag = "typed_text_to_pdf",
        category = "TEXT CONVERSION"
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConvertPdfScreen(
    viewModel: PdfMasterViewModel,
    onBack: () -> Unit = { viewModel.navigateBack() }
) {
    val windowInfo = rememberWindowInfo()
    var activeWorkflowMode by rememberSaveable { mutableStateOf<ConversionMode?>(null) }

    BackHandler {
        if (activeWorkflowMode != null) {
            activeWorkflowMode = null
        } else {
            onBack()
        }
    }

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

    // Options for PDF -> Images
    var targetImageFormatIsPng by rememberSaveable { mutableStateOf(true) }
    var pdfPassword by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    // Options for Image Format conversion
    var convertImageToJpeg by rememberSaveable { mutableStateOf(true) }

    // Options for Images -> PDF
    var imagesToPdfOutputName by rememberSaveable { mutableStateOf("Converted_Images.pdf") }

    // Options for Text -> PDF
    var typedTitle by rememberSaveable { mutableStateOf("Converted Document") }
    var typedBody by rememberSaveable { mutableStateOf("") }
    var txtToPdfOutputName by rememberSaveable { mutableStateOf("Text_Document.pdf") }

    val currentMode = activeWorkflowMode ?: ConversionMode.WORD_TO_PDF

    var officeOutputName by rememberSaveable(sourceOfficeName, currentMode) {
        val base = sourceOfficeName?.substringBeforeLast('.') ?: when (currentMode) {
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
        uri?.let {
            viewModel.clearBatchConvertSelection()
            viewModel.selectConversionPdfFromUri(it)
        }
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
            viewModel.selectConversionOfficeFileFromUri(it, currentMode.officeFormat)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Convert Files & PDF", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (activeWorkflowMode != null) {
                                activeWorkflowMode = null
                            } else {
                                onBack()
                            }
                        },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Dimens.Spacing16),
            contentAlignment = Alignment.TopCenter
        ) {
            val contentModifier = Modifier
                .fillMaxSize()
                .widthIn(max = Dimens.MaxContentWidth)

            if (activeWorkflowMode == null) {
                // ==================== DASHBOARD VIEW ====================
                Column(
                    modifier = contentModifier
                        .padding(vertical = Dimens.Spacing16)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Dimens.Spacing16)
                ) {
                    Text(
                        text = "Convert your files",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Choose a format and convert files directly on your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val categories = ConversionMode.entries.groupBy { it.category }
                    categories.forEach { (categoryName, modeList) ->
                        Text(
                            text = categoryName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(top = Dimens.Spacing8)
                        )

                        val cols = if (windowInfo.isExpanded) 3 else if (windowInfo.isMedium) 2 else 1
                        modeList.chunked(cols).forEach { rowModes ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing12)
                            ) {
                                rowModes.forEach { mode ->
                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(Dimens.CornerMedium))
                                            .clickable {
                                                pdfPassword = ""
                                                viewModel.clearConversionState()
                                                if (mode.officeFormat != null) {
                                                    viewModel.resetConversionSession()
                                                }
                                                activeWorkflowMode = mode
                                            }
                                            .testTag("option_card_${mode.tag}"),
                                        shape = RoundedCornerShape(Dimens.CornerMedium),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(Dimens.Spacing16),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(Dimens.IconContainerSize)
                                                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(Dimens.CornerSmall)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = mode.icon,
                                                    contentDescription = mode.title,
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(Dimens.Spacing12))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = mode.title,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                                Text(
                                                    text = mode.subtitle,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                                val missing = cols - rowModes.size
                                repeat(missing) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            } else {
                // ==================== WORKFLOW FORM VIEW ====================
                val mode = activeWorkflowMode!!

                Column(
                    modifier = contentModifier
                        .padding(vertical = Dimens.Spacing16)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Dimens.Spacing16)
                ) {
                    // Active Mode Header Card with Change Format Button
                    Card(
                        shape = RoundedCornerShape(Dimens.CornerMedium),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(Dimens.Spacing16),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(Dimens.CornerSmall),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    mode.icon,
                                    contentDescription = null,
                                    modifier = Modifier.padding(12.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(Dimens.Spacing12))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = mode.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = { activeWorkflowMode = null },
                                modifier = Modifier.testTag("change_format_button"),
                                shape = RoundedCornerShape(Dimens.CornerSmall)
                            ) {
                                Text("Change Format", fontSize = 11.sp)
                            }
                        }
                    }

                    // Error Message Banner
                    if (!errorMessage.isNullOrBlank()) {
                        Card(
                            shape = RoundedCornerShape(Dimens.CornerSmall),
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

                    // Mode-Specific Input & Controls Form
                    when (mode) {
                        ConversionMode.WORD_TO_PDF,
                        ConversionMode.EXCEL_TO_PDF,
                        ConversionMode.PPT_TO_PDF -> {
                            val expectedFormat = mode.officeFormat!!
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

                            Card(
                                shape = RoundedCornerShape(Dimens.CornerSmall),
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

                            Card(
                                shape = RoundedCornerShape(Dimens.CornerMedium),
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
                                        }
                                        OutlinedButton(
                                            onClick = { officeFilePickerLauncher.launch(pickerMimeTypes) },
                                            enabled = !isProcessing,
                                            shape = RoundedCornerShape(Dimens.CornerSmall),
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
                                    .height(Dimens.ButtonHeight)
                                    .testTag("execute_convert_office_to_pdf_button"),
                                shape = RoundedCornerShape(Dimens.CornerMedium)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Convert ${expectedFormat.label} to PDF", fontWeight = FontWeight.Bold)
                            }
                        }

                        ConversionMode.PDF_TO_IMAGES -> {
                            Card(
                                shape = RoundedCornerShape(Dimens.CornerMedium),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Selected PDF Document",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = conversionPdf?.name ?: "No PDF selected",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.testTag("selected_conversion_pdf_name")
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                                        enabled = !isProcessing,
                                        shape = RoundedCornerShape(Dimens.CornerSmall),
                                        modifier = Modifier.testTag("select_conversion_pdf_button")
                                    ) {
                                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (conversionPdf == null) "Select PDF" else "Change")
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FilterChip(
                                    selected = targetImageFormatIsPng,
                                    onClick = { targetImageFormatIsPng = true },
                                    label = { Text("Lossless PNG") },
                                    modifier = Modifier.testTag("format_png_chip")
                                )
                                FilterChip(
                                    selected = !targetImageFormatIsPng,
                                    onClick = { targetImageFormatIsPng = false },
                                    label = { Text("Compressed JPEG") },
                                    modifier = Modifier.testTag("format_jpeg_chip")
                                )
                            }

                            Button(
                                onClick = {
                                    conversionPdf?.let { pdf ->
                                        val imgFormat = if (targetImageFormatIsPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                                        viewModel.executePdfToImages(
                                            file = pdf,
                                            password = pdfPassword.ifBlank { null },
                                            format = imgFormat
                                        )
                                    }
                                },
                                enabled = conversionPdf != null && !isProcessing,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(Dimens.ButtonHeight)
                                    .testTag("execute_pdf_to_images_button"),
                                shape = RoundedCornerShape(Dimens.CornerMedium)
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Render PDF Pages to Images", fontWeight = FontWeight.Bold)
                            }
                        }

                        ConversionMode.PDF_TO_TEXT -> {
                            Card(
                                shape = RoundedCornerShape(Dimens.CornerMedium),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
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
                                            text = conversionPdf?.name ?: "No PDF selected",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.testTag("selected_conversion_pdf_name")
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                                        enabled = !isProcessing,
                                        shape = RoundedCornerShape(Dimens.CornerSmall),
                                        modifier = Modifier.testTag("select_conversion_pdf_button")
                                    ) {
                                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (conversionPdf == null) "Select PDF" else "Change")
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    conversionPdf?.let { pdf ->
                                        viewModel.executePdfToText(
                                            file = pdf,
                                            password = pdfPassword.ifBlank { null }
                                        )
                                    }
                                },
                                enabled = conversionPdf != null && !isProcessing,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(Dimens.ButtonHeight)
                                    .testTag("execute_pdf_to_text_button"),
                                shape = RoundedCornerShape(Dimens.CornerMedium)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Extract Text to TXT File", fontWeight = FontWeight.Bold)
                            }
                        }

                        ConversionMode.IMAGES_TO_PDF -> {
                            Card(
                                shape = RoundedCornerShape(Dimens.CornerMedium),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Selected Photo Count",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "${selectedImageUris.size} Images Selected",
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                multiPhotoPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            },
                                            enabled = !isProcessing,
                                            shape = RoundedCornerShape(Dimens.CornerSmall),
                                            modifier = Modifier.testTag("pick_images_for_pdf_button")
                                        ) {
                                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Add Photos")
                                        }
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
                                    if (selectedImageUris.isNotEmpty()) {
                                        viewModel.executeImagesToPdf(imagesToPdfOutputName)
                                    }
                                },
                                enabled = selectedImageUris.isNotEmpty() && !isProcessing,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(Dimens.ButtonHeight)
                                    .testTag("execute_images_to_pdf_button"),
                                shape = RoundedCornerShape(Dimens.CornerMedium)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Convert Images to PDF", fontWeight = FontWeight.Bold)
                            }
                        }

                        ConversionMode.IMAGE_FORMAT -> {
                            Card(
                                shape = RoundedCornerShape(Dimens.CornerMedium),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
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
                                        shape = RoundedCornerShape(Dimens.CornerSmall),
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
                                    .height(Dimens.ButtonHeight)
                                    .testTag("execute_image_format_button"),
                                shape = RoundedCornerShape(Dimens.CornerMedium)
                            ) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Convert Image Format", fontWeight = FontWeight.Bold)
                            }
                        }

                        ConversionMode.TXT_TO_PDF -> {
                            Card(
                                shape = RoundedCornerShape(Dimens.CornerMedium),
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
                                            shape = RoundedCornerShape(Dimens.CornerSmall),
                                            modifier = Modifier.testTag("pick_txt_file_button")
                                        ) {
                                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(if (sourceTextUri == null) "Select .txt" else "Change")
                                        }
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
                                        viewModel.executeConvertTextFileToPdf(
                                            uri = uri,
                                            outputName = txtToPdfOutputName
                                        )
                                    }
                                },
                                enabled = sourceTextUri != null && !isProcessing,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(Dimens.ButtonHeight)
                                    .testTag("execute_txt_file_to_pdf_button"),
                                shape = RoundedCornerShape(Dimens.CornerMedium)
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Convert .txt File to PDF", fontWeight = FontWeight.Bold)
                            }
                        }

                        ConversionMode.TYPED_TEXT_TO_PDF -> {
                            OutlinedTextField(
                                value = typedTitle,
                                onValueChange = { typedTitle = it },
                                label = { Text("Document Header Title") },
                                singleLine = true,
                                enabled = !isProcessing,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("convert_title_input")
                            )

                            OutlinedTextField(
                                value = typedBody,
                                onValueChange = { typedBody = it },
                                label = { Text("Document Content Text") },
                                enabled = !isProcessing,
                                minLines = 8,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("convert_body_input")
                            )

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
                                    if (typedBody.isNotBlank()) {
                                        viewModel.executeCreateTextPdf(
                                            textContent = "${typedTitle}\n\n${typedBody}",
                                            outputName = txtToPdfOutputName
                                        )
                                    }
                                },
                                enabled = typedBody.isNotBlank() && !isProcessing,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(Dimens.ButtonHeight)
                                    .testTag("convert_text_to_pdf_button"),
                                shape = RoundedCornerShape(Dimens.CornerMedium)
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Generate PDF from Typed Text", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Conversion Results View
                    if (outputFiles.isNotEmpty()) {
                        ConversionResultCard(
                            outputFiles = outputFiles,
                            summary = conversionResultSummary,
                            layoutDisclosure = conversionLayoutDisclosure,
                            onViewPdf = { file ->
                                viewModel.openPdfInViewer(file)
                            },
                            onShareFile = { file ->
                                val mimeType = if (file.name.endsWith(".pdf", ignoreCase = true)) {
                                    "application/pdf"
                                } else if (file.name.endsWith(".png", ignoreCase = true)) {
                                    "image/png"
                                } else if (file.name.endsWith(".jpg", ignoreCase = true) || file.name.endsWith(".jpeg", ignoreCase = true)) {
                                    "image/jpeg"
                                } else {
                                    "*/*"
                                }
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = mimeType
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share converted file"))
                            },
                            onReset = {
                                viewModel.clearConversionState()
                            }
                        )
                    }

                    // Extracted Text Results View
                    if (!extractedText.isNullOrBlank()) {
                        ExtractedTextResultCard(
                            extractedText = extractedText!!,
                            context = context
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversionResultCard(
    outputFiles: List<File>,
    summary: String?,
    layoutDisclosure: String?,
    onViewPdf: (File) -> Unit,
    onShareFile: (File) -> Unit,
    onReset: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(Dimens.CornerMedium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("conversion_result_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Conversion Successful",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!summary.isNullOrBlank()) {
                        Text(
                            text = summary,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("conversion_result_summary")
                        )
                    }
                }
            }

            if (!layoutDisclosure.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(Dimens.CornerSmall),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = layoutDisclosure,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            outputFiles.forEach { file ->
                Card(
                    shape = RoundedCornerShape(Dimens.CornerSmall),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
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
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = PdfEngine.formatFileSize(file.length()),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (file.name.endsWith(".pdf", ignoreCase = true)) {
                                Button(
                                    onClick = { onViewPdf(file) },
                                    shape = RoundedCornerShape(Dimens.CornerSmall),
                                    modifier = Modifier.testTag("view_converted_pdf_button")
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("View PDF", fontSize = 12.sp)
                                }
                            }
                            OutlinedButton(
                                onClick = { onShareFile(file) },
                                shape = RoundedCornerShape(Dimens.CornerSmall),
                                modifier = Modifier.testTag("share_converted_file_button")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onReset,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("convert_another_file_button"),
                shape = RoundedCornerShape(Dimens.CornerMedium)
            ) {
                Text("Convert Another File")
            }
        }
    }
}

@Composable
private fun ExtractedTextResultCard(
    extractedText: String,
    context: Context
) {
    Card(
        shape = RoundedCornerShape(Dimens.CornerMedium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("extracted_text_result_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Extracted Text",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Extracted PDF Text", extractedText)
                        clipboard.setPrimaryClip(clip)
                    },
                    shape = RoundedCornerShape(Dimens.CornerSmall),
                    modifier = Modifier.testTag("copy_extracted_text_button")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy All", fontSize = 12.sp)
                }
            }

            Surface(
                shape = RoundedCornerShape(Dimens.CornerSmall),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = extractedText,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(10.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

