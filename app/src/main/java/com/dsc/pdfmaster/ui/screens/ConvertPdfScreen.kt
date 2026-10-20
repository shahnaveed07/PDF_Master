package com.dsc.pdfmaster.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.dsc.pdfmaster.pdfengine.PdfEngine
import com.dsc.pdfmaster.ui.PdfMasterViewModel
import com.dsc.pdfmaster.ui.ScreenRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

enum class ActiveConversionMode(
    val title: String,
    val description: String
) {
    PDF_TO_PNG("PDF to PNG Images", "Render every page of the selected PDF to PNG image files"),
    PDF_TO_JPEG("PDF to JPEG Images", "Render every page of the selected PDF to JPEG image files"),
    PDF_TO_TXT("PDF to Plain Text (.TXT)", "Extract text content from the selected PDF into a UTF-8 .txt file"),
    PNG_TO_JPEG("PNG to JPEG Converter", "Convert a PNG image to JPEG (flattening transparency onto white)"),
    JPEG_TO_PNG("JPEG to PNG Converter", "Convert a JPEG image to lossless PNG format"),
    TXT_FILE_TO_PDF("TXT File to PDF", "Convert a plain-text (.txt) file into a multi-page A4 PDF document")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConvertPdfScreen(
    viewModel: PdfMasterViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isProcessing by viewModel.isProcessing.collectAsState()
    val sourcePdf by viewModel.conversionSourcePdf.collectAsState()
    val pdfRequiresPassword by viewModel.conversionPdfRequiresPassword.collectAsState()
    val sourceImageUri by viewModel.conversionSourceImageUri.collectAsState()
    val sourceImageName by viewModel.conversionSourceImageName.collectAsState()
    val sourceTextUri by viewModel.conversionSourceTextUri.collectAsState()
    val sourceTextName by viewModel.conversionSourceTextName.collectAsState()
    val sourceTextPreview by viewModel.conversionSourceTextPreview.collectAsState()
    val outputFiles by viewModel.conversionOutputFiles.collectAsState()
    val extractedText by viewModel.conversionExtractedText.collectAsState()
    val errorMessage by viewModel.conversionErrorMessage.collectAsState()

    var activeMode by remember { mutableStateOf<ActiveConversionMode?>(null) }
    var pdfPasswordInput by remember { mutableStateOf("") }
    var outputNameInput by remember { mutableStateOf("") }
    var pendingExportFile by remember { mutableStateOf<File?>(null) }

    // Update default output name whenever explicit input file or mode changes
    LaunchedEffect(activeMode, sourcePdf, sourceImageName, sourceTextName) {
        outputNameInput = when (activeMode) {
            ActiveConversionMode.PDF_TO_PNG,
            ActiveConversionMode.PDF_TO_JPEG -> sourcePdf?.nameWithoutExtension ?: "document"
            ActiveConversionMode.PDF_TO_TXT -> "${sourcePdf?.nameWithoutExtension ?: "document"}_text.txt"
            ActiveConversionMode.PNG_TO_JPEG -> "${(sourceImageName ?: "image").substringBeforeLast('.')}_converted.jpg"
            ActiveConversionMode.JPEG_TO_PNG -> "${(sourceImageName ?: "image").substringBeforeLast('.')}_converted.png"
            ActiveConversionMode.TXT_FILE_TO_PDF -> "${(sourceTextName ?: "notes").substringBeforeLast('.')}.pdf"
            null -> ""
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            pdfPasswordInput = ""
            viewModel.selectConversionPdfFromUri(it)
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.selectConversionImageFromUri(it)
        }
    }

    val txtPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.selectConversionTextFileFromUri(it)
        }
    }

    val exportDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { destUri ->
        val fileToExport = pendingExportFile
        if (destUri != null && fileToExport != null && fileToExport.exists()) {
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(destUri)?.use { out ->
                        fileToExport.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    fun selectMode(mode: ActiveConversionMode, launchPickerImmediately: Boolean = true) {
        if (activeMode != mode) {
            viewModel.resetConversionSession()
            pdfPasswordInput = ""
            activeMode = mode
        }
        if (launchPickerImmediately) {
            when (mode) {
                ActiveConversionMode.PDF_TO_PNG,
                ActiveConversionMode.PDF_TO_JPEG,
                ActiveConversionMode.PDF_TO_TXT -> pdfPickerLauncher.launch(arrayOf("application/pdf"))
                ActiveConversionMode.PNG_TO_JPEG -> imagePickerLauncher.launch("image/png")
                ActiveConversionMode.JPEG_TO_PNG -> imagePickerLauncher.launch("image/jpeg")
                ActiveConversionMode.TXT_FILE_TO_PDF -> txtPickerLauncher.launch(arrayOf("text/plain"))
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Document Converter", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("convert_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Active Conversion Session Workspace
            val currentMode = activeMode
            if (currentMode != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("active_conversion_workspace"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentMode.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = currentMode.description,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    activeMode = null
                                    viewModel.resetConversionSession()
                                },
                                modifier = Modifier.testTag("close_conversion_workspace_button")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close conversion workspace")
                            }
                        }

                        if (currentMode == ActiveConversionMode.PDF_TO_PNG || currentMode == ActiveConversionMode.PDF_TO_JPEG) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = currentMode == ActiveConversionMode.PDF_TO_PNG,
                                    onClick = { activeMode = ActiveConversionMode.PDF_TO_PNG },
                                    label = { Text("PNG Format") },
                                    modifier = Modifier.testTag("format_chip_png")
                                )
                                FilterChip(
                                    selected = currentMode == ActiveConversionMode.PDF_TO_JPEG,
                                    onClick = { activeMode = ActiveConversionMode.PDF_TO_JPEG },
                                    label = { Text("JPEG Format") },
                                    modifier = Modifier.testTag("format_chip_jpeg")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val selectedInputLabel = when (currentMode) {
                            ActiveConversionMode.PDF_TO_PNG,
                            ActiveConversionMode.PDF_TO_JPEG,
                            ActiveConversionMode.PDF_TO_TXT -> sourcePdf?.let {
                                "${it.name} (${PdfEngine.formatFileSize(it.length())})"
                            } ?: "No PDF selected — choose a PDF file"
                            ActiveConversionMode.PNG_TO_JPEG,
                            ActiveConversionMode.JPEG_TO_PNG -> sourceImageName ?: "No image selected — choose an image file"
                            ActiveConversionMode.TXT_FILE_TO_PDF -> sourceTextName ?: "No TXT file selected — choose a .txt file"
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "SOURCE FILE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = selectedInputLabel,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.testTag("conversion_selected_source_text")
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    when (currentMode) {
                                        ActiveConversionMode.PDF_TO_PNG,
                                        ActiveConversionMode.PDF_TO_JPEG,
                                        ActiveConversionMode.PDF_TO_TXT -> pdfPickerLauncher.launch(arrayOf("application/pdf"))
                                        ActiveConversionMode.PNG_TO_JPEG -> imagePickerLauncher.launch("image/png")
                                        ActiveConversionMode.JPEG_TO_PNG -> imagePickerLauncher.launch("image/jpeg")
                                        ActiveConversionMode.TXT_FILE_TO_PDF -> txtPickerLauncher.launch(arrayOf("text/plain"))
                                    }
                                },
                                enabled = !isProcessing,
                                modifier = Modifier.testTag("conversion_select_input_button")
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Select File", fontSize = 12.sp)
                            }
                        }

                        if (sourceTextPreview != null && currentMode == ActiveConversionMode.TXT_FILE_TO_PDF) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Preview: ${sourceTextPreview!!.take(160)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        val isPdfMode = currentMode in listOf(
                            ActiveConversionMode.PDF_TO_PNG,
                            ActiveConversionMode.PDF_TO_JPEG,
                            ActiveConversionMode.PDF_TO_TXT
                        )

                        if (isPdfMode && pdfRequiresPassword) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = pdfPasswordInput,
                                onValueChange = { pdfPasswordInput = it },
                                label = { Text("PDF Password (Required for Encrypted PDF)") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("conversion_pdf_password_field")
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = outputNameInput,
                            onValueChange = { outputNameInput = it },
                            label = {
                                Text(
                                    if (currentMode == ActiveConversionMode.PDF_TO_PNG || currentMode == ActiveConversionMode.PDF_TO_JPEG) {
                                        "Output Image Prefix (e.g. report -> report_page_1)"
                                    } else {
                                        "Output Filename"
                                    }
                                )
                            },
                            singleLine = true,
                            enabled = !isProcessing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("conversion_output_name_field")
                        )

                        if (errorMessage != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("conversion_error_card"),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = errorMessage!!,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }

                        val hasValidInput = when (currentMode) {
                            ActiveConversionMode.PDF_TO_PNG,
                            ActiveConversionMode.PDF_TO_JPEG,
                            ActiveConversionMode.PDF_TO_TXT -> sourcePdf != null && (!pdfRequiresPassword || pdfPasswordInput.isNotEmpty())
                            ActiveConversionMode.PNG_TO_JPEG,
                            ActiveConversionMode.JPEG_TO_PNG -> sourceImageUri != null
                            ActiveConversionMode.TXT_FILE_TO_PDF -> sourceTextUri != null
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                when (currentMode) {
                                    ActiveConversionMode.PDF_TO_PNG -> {
                                        val pdf = sourcePdf ?: return@Button
                                        viewModel.executePdfToImages(
                                            file = pdf,
                                            password = pdfPasswordInput.takeIf { it.isNotEmpty() },
                                            format = Bitmap.CompressFormat.PNG,
                                            outputPrefix = outputNameInput.trim().ifBlank { pdf.nameWithoutExtension }
                                        )
                                    }
                                    ActiveConversionMode.PDF_TO_JPEG -> {
                                        val pdf = sourcePdf ?: return@Button
                                        viewModel.executePdfToImages(
                                            file = pdf,
                                            password = pdfPasswordInput.takeIf { it.isNotEmpty() },
                                            format = Bitmap.CompressFormat.JPEG,
                                            outputPrefix = outputNameInput.trim().ifBlank { pdf.nameWithoutExtension }
                                        )
                                    }
                                    ActiveConversionMode.PDF_TO_TXT -> {
                                        val pdf = sourcePdf ?: return@Button
                                        viewModel.executePdfToText(
                                            file = pdf,
                                            password = pdfPasswordInput.takeIf { it.isNotEmpty() },
                                            outputName = outputNameInput.trim().ifBlank { "${pdf.nameWithoutExtension}_text.txt" }
                                        )
                                    }
                                    ActiveConversionMode.PNG_TO_JPEG -> {
                                        val uri = sourceImageUri ?: return@Button
                                        viewModel.executeConvertImageFormat(
                                            uri = uri,
                                            isToJpeg = true,
                                            outputName = outputNameInput.trim()
                                        )
                                    }
                                    ActiveConversionMode.JPEG_TO_PNG -> {
                                        val uri = sourceImageUri ?: return@Button
                                        viewModel.executeConvertImageFormat(
                                            uri = uri,
                                            isToJpeg = false,
                                            outputName = outputNameInput.trim()
                                        )
                                    }
                                    ActiveConversionMode.TXT_FILE_TO_PDF -> {
                                        val uri = sourceTextUri ?: return@Button
                                        viewModel.executeConvertTextFileToPdf(
                                            uri = uri,
                                            outputName = outputNameInput.trim()
                                        )
                                    }
                                }
                            },
                            enabled = hasValidInput && !isProcessing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("execute_conversion_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Transform, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Convert Now", fontWeight = FontWeight.Bold)
                        }

                        if (outputFiles.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("conversion_result_card"),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Conversion Complete (${outputFiles.size} file${if (outputFiles.size > 1) "s" else ""})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    val firstFile = outputFiles.first()
                                    val totalBytes = outputFiles.sumOf { it.length() }
                                    Text(
                                        text = if (outputFiles.size == 1) {
                                            "Saved: ${firstFile.name} (${PdfEngine.formatFileSize(totalBytes)})"
                                        } else {
                                            "Saved: ${firstFile.name} ... ${outputFiles.last().name} (${PdfEngine.formatFileSize(totalBytes)} total)"
                                        },
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    if (!extractedText.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = extractedText!!.take(300),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { shareOutputFiles(context, outputFiles) },
                                            modifier = Modifier.testTag("share_conversion_output_button")
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Share", fontSize = 12.sp)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                pendingExportFile = firstFile
                                                exportDocumentLauncher.launch(firstFile.name)
                                            },
                                            modifier = Modifier.testTag("export_conversion_output_button")
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Save As", fontSize = 12.sp)
                                        }
                                        if (firstFile.name.endsWith(".pdf", ignoreCase = true)) {
                                            Button(
                                                onClick = { viewModel.openPdfInViewer(firstFile) },
                                                modifier = Modifier.testTag("open_converted_pdf_button")
                                            ) {
                                                Text("Open PDF", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Category 1: PDF
            Text(
                text = "PDF",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            ConversionCard(
                title = "Images to PDF",
                subtitle = "Combine JPG/PNG photos into a validated multi-page PDF document",
                icon = Icons.Default.Image,
                isEnabled = true,
                onClick = {
                    viewModel.clearSelectedImagesForPdf()
                    viewModel.navigateTo(ScreenRoute.IMAGES_TO_PDF)
                },
                tag = "convert_images_to_pdf"
            )

            Spacer(modifier = Modifier.height(10.dp))

            ConversionCard(
                title = "Text to PDF",
                subtitle = "Compose and generate a formatted multi-page PDF from text",
                icon = Icons.Default.Description,
                isEnabled = true,
                onClick = { viewModel.navigateTo(ScreenRoute.CREATE_PDF) },
                tag = "convert_text_to_pdf"
            )

            Spacer(modifier = Modifier.height(10.dp))

            ConversionCard(
                title = "Extract PDF Text (PDF to TXT)",
                subtitle = "Extract plain UTF-8 text from an explicitly selected PDF document",
                icon = Icons.Default.Description,
                isEnabled = true,
                onClick = { selectMode(ActiveConversionMode.PDF_TO_TXT) },
                tag = "convert_pdf_to_text"
            )

            Spacer(modifier = Modifier.height(10.dp))

            ConversionCard(
                title = "PDF to PNG",
                subtitle = "Render all pages of a selected PDF into high-resolution PNG images",
                icon = Icons.Default.PictureAsPdf,
                isEnabled = true,
                onClick = { selectMode(ActiveConversionMode.PDF_TO_PNG) },
                tag = "convert_pdf_to_png"
            )

            Spacer(modifier = Modifier.height(10.dp))

            ConversionCard(
                title = "PDF to JPEG",
                subtitle = "Render all pages of a selected PDF into compressed JPEG images",
                icon = Icons.Default.PictureAsPdf,
                isEnabled = true,
                onClick = { selectMode(ActiveConversionMode.PDF_TO_JPEG) },
                tag = "convert_pdf_to_jpeg"
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Category 2: Images
            Text(
                text = "IMAGES",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            ConversionCard(
                title = "PNG to JPEG",
                subtitle = "Convert PNG images to JPEG locally with white alpha compositing",
                icon = Icons.Default.Transform,
                isEnabled = true,
                onClick = { selectMode(ActiveConversionMode.PNG_TO_JPEG) },
                tag = "convert_image_format"
            )

            Spacer(modifier = Modifier.height(10.dp))

            ConversionCard(
                title = "JPEG to PNG",
                subtitle = "Convert JPEG photos into lossless PNG image files locally",
                icon = Icons.Default.Transform,
                isEnabled = true,
                onClick = { selectMode(ActiveConversionMode.JPEG_TO_PNG) },
                tag = "convert_jpeg_to_png"
            )

            Spacer(modifier = Modifier.height(10.dp))

            ConversionCard(
                title = "PDF Pages to PNG / JPEG",
                subtitle = "Render PDF pages to genuine PNG or JPEG image files",
                icon = Icons.Default.PictureAsPdf,
                isEnabled = true,
                onClick = { selectMode(ActiveConversionMode.PDF_TO_PNG) },
                tag = "convert_pdf_to_images"
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Category 3: Office
            Text(
                text = "OFFICE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            ConversionCard(
                title = "Word (DOC / DOCX) to PDF",
                subtitle = "Unavailable locally — Faithful Word layout & font rendering is not supported offline",
                icon = Icons.Default.Description,
                isEnabled = false,
                onClick = {},
                tag = "convert_word_to_pdf"
            )

            Spacer(modifier = Modifier.height(10.dp))

            ConversionCard(
                title = "Excel (XLS / XLSX) to PDF",
                subtitle = "Unavailable locally — Spreadsheet pagination & formula rendering is not supported offline",
                icon = Icons.Default.TableChart,
                isEnabled = false,
                onClick = {},
                tag = "convert_excel_to_pdf"
            )

            Spacer(modifier = Modifier.height(10.dp))

            ConversionCard(
                title = "PowerPoint (PPT / PPTX) to PDF",
                subtitle = "Unavailable locally — Slide vector & theme rendering is not supported offline",
                icon = Icons.Default.Slideshow,
                isEnabled = false,
                onClick = {},
                tag = "convert_pptx_to_pdf"
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Category 4: Other
            Text(
                text = "OTHER",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            ConversionCard(
                title = "Plain Text (.TXT) File to PDF",
                subtitle = "Import a .txt document and convert it to a formatted A4 PDF locally",
                icon = Icons.Default.Description,
                isEnabled = true,
                onClick = { selectMode(ActiveConversionMode.TXT_FILE_TO_PDF) },
                tag = "convert_txt_file_to_pdf"
            )

            Spacer(modifier = Modifier.height(10.dp))

            ConversionCard(
                title = "HTML to PDF",
                subtitle = "Unavailable locally — Full CSS/DOM paged layout rendering is not supported offline",
                icon = Icons.Default.Code,
                isEnabled = false,
                onClick = {},
                tag = "convert_html_to_pdf"
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun shareOutputFiles(context: Context, files: List<File>) {
    if (files.isEmpty()) return
    try {
        val uris = ArrayList(
            files.map { file ->
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }
        )
        val firstName = files.first().name.lowercase()
        val mimeType = when {
            firstName.endsWith(".pdf") -> "application/pdf"
            firstName.endsWith(".png") -> "image/png"
            firstName.endsWith(".jpg") || firstName.endsWith(".jpeg") -> "image/jpeg"
            firstName.endsWith(".txt") -> "text/plain"
            else -> "*/*"
        }

        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uris.first())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mimeType
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        context.startActivity(Intent.createChooser(intent, "Share converted output"))
    } catch (_: Exception) {
    }
}

@Composable
private fun ConversionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isEnabled: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    val containerColor = if (isEnabled) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }
    val contentAlpha = if (isEnabled) 1.0f else 0.5f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isEnabled) Modifier.clickable { onClick() } else Modifier)
            .testTag(tag),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isEnabled) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                )
            }
        }
    }
}
