package com.dsc.pdfmaster.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.dsc.pdfmaster.pdfengine.PageRangeParser
import com.dsc.pdfmaster.pdfengine.PageRangeResult
import com.dsc.pdfmaster.pdfengine.PdfEngine
import com.dsc.pdfmaster.pdfengine.PdfThumbnailCache
import com.dsc.pdfmaster.ui.PdfMasterViewModel
import com.dsc.pdfmaster.ui.theme.DscPrimaryCyan
import com.dsc.pdfmaster.ui.theme.DscSecondaryPurple
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtractPdfScreen(
    viewModel: PdfMasterViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentPdf by viewModel.selectedPdfFile.collectAsState()
    val extractionResult by viewModel.extractionResult.collectAsState()

    var totalPages by remember { mutableIntStateOf(0) }
    var selectedPageIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var rangeInputText by remember { mutableStateOf("") }
    var rangeErrorMessage by remember { mutableStateOf<String?>(null) }
    var outputNameInput by remember { mutableStateOf("") }

    // Password handling
    var isPasswordProtected by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var isUnlocking by remember { mutableStateOf(false) }

    // Save/Export launcher
    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        destUri?.let { uri ->
            val result = extractionResult
            if (result != null && result.outputFile.exists()) {
                scope.launch(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { outStream ->
                            result.outputFile.inputStream().use { inStream ->
                                inStream.copyTo(outStream)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    // Document Picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.resetExtractionResult()
            viewModel.importAndSelectPdf(it, openViewer = false)
        }
    }

    // Load Document Metadata & Check Password Protection
    LaunchedEffect(currentPdf) {
        val file = currentPdf
        if (file != null && file.exists()) {
            withContext(Dispatchers.IO) {
                val isEncrypted = PdfEngine.checkPasswordProtected(context, file)
                if (isEncrypted) {
                    withContext(Dispatchers.Main) {
                        isPasswordProtected = true
                        totalPages = 0
                        selectedPageIndices = emptySet()
                    }
                } else {
                    val meta = PdfEngine.getPdfMetadata(context, Uri.fromFile(file))
                    val count = meta?.pageCount ?: 1
                    val defaultBase = file.nameWithoutExtension.removeSuffix(".pdf")
                    val defaultOutName = "${defaultBase}_extracted.pdf"

                    withContext(Dispatchers.Main) {
                        isPasswordProtected = false
                        passwordError = null
                        totalPages = count
                        outputNameInput = defaultOutName
                        // Default to first page selected or empty
                        selectedPageIndices = if (count > 0) setOf(0) else emptySet()
                        rangeInputText = if (count > 0) "1" else ""
                        rangeErrorMessage = null
                    }
                }
            }
        } else {
            totalPages = 0
            selectedPageIndices = emptySet()
            rangeInputText = ""
            outputNameInput = "Extracted_Document.pdf"
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Extract Pages", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    if (currentPdf != null && totalPages > 0) {
                        Text(
                            text = "${currentPdf!!.name} • $totalPages Pages",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        if (extractionResult != null) {
                            viewModel.resetExtractionResult()
                        } else {
                            onBack()
                        }
                    },
                    modifier = Modifier.testTag("extract_back_button")
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        // If extraction is complete, show the Result State (Requirement 11)
        if (extractionResult != null) {
            val result = extractionResult!!
            ExtractionResultView(
                result = result,
                onOpen = { viewModel.openPdfInViewer(result.outputFile) },
                onShare = { sharePdf(context, result.outputFile) },
                onExport = { exportFileLauncher.launch(result.outputFile.name) },
                onDone = {
                    viewModel.resetExtractionResult()
                    onBack()
                },
                onExtractMore = { viewModel.resetExtractionResult() }
            )
        } else if (isPasswordProtected) {
            // Password Protection State (Requirement 16)
            PasswordUnlockView(
                currentFile = currentPdf,
                passwordInput = passwordInput,
                onPasswordChange = { passwordInput = it },
                passwordError = passwordError,
                isUnlocking = isUnlocking,
                onChangeFile = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                onUnlock = {
                    if (currentPdf != null && passwordInput.isNotBlank()) {
                        isUnlocking = true
                        passwordError = null
                        scope.launch(Dispatchers.IO) {
                            try {
                                com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
                                val doc = com.tom_roush.pdfbox.pdmodel.PDDocument.load(currentPdf, passwordInput)
                                val count = doc.numberOfPages
                                doc.close()

                                val defaultBase = currentPdf!!.nameWithoutExtension.removeSuffix(".pdf")
                                withContext(Dispatchers.Main) {
                                    totalPages = count
                                    isPasswordProtected = false
                                    isUnlocking = false
                                    outputNameInput = "${defaultBase}_extracted.pdf"
                                    selectedPageIndices = if (count > 0) setOf(0) else emptySet()
                                    rangeInputText = if (count > 0) "1" else ""
                                    rangeErrorMessage = null
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    isUnlocking = false
                                    passwordError = "Incorrect password. Please try again."
                                }
                            }
                        }
                    }
                }
            )
        } else {
            // Main Extract Pages Configuration & Selection UI
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Source File Card
                SourcePdfCard(
                    file = currentPdf,
                    totalPages = totalPages,
                    onPickFile = { filePickerLauncher.launch(arrayOf("application/pdf")) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Page Range Input Card (Requirement 5 & 19)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PAGE RANGE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "${selectedPageIndices.size} of $totalPages selected",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selectedPageIndices.isNotEmpty()) DscPrimaryCyan else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = rangeInputText,
                            onValueChange = { newText ->
                                rangeInputText = newText
                                if (newText.isBlank()) {
                                    selectedPageIndices = emptySet()
                                    rangeErrorMessage = null
                                } else {
                                    when (val parseRes = PageRangeParser.parse(newText, totalPages.coerceAtLeast(1))) {
                                        is PageRangeResult.Success -> {
                                            selectedPageIndices = parseRes.pageNumbers.map { it - 1 }.toSet()
                                            rangeErrorMessage = null
                                        }
                                        is PageRangeResult.Error -> {
                                            rangeErrorMessage = parseRes.message
                                        }
                                    }
                                }
                            },
                            placeholder = { Text("e.g. 1-3, 5, 8-10") },
                            singleLine = true,
                            isError = rangeErrorMessage != null,
                            trailingIcon = {
                                if (rangeInputText.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            rangeInputText = ""
                                            selectedPageIndices = emptySet()
                                            rangeErrorMessage = null
                                        }
                                    ) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear range input", modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("extract_range_input"),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DscPrimaryCyan,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            )
                        )

                        AnimatedVisibility(visible = rangeErrorMessage != null) {
                            Text(
                                text = rangeErrorMessage ?: "",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Selection Action Chips (Select All, Clear, Invert) (Requirement 6)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AssistChip(
                                onClick = {
                                    val all = (0 until totalPages).toSet()
                                    selectedPageIndices = all
                                    rangeInputText = if (totalPages > 1) "1-$totalPages" else "1"
                                    rangeErrorMessage = null
                                },
                                label = { Text("Select All", fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.SelectAll, contentDescription = null, modifier = Modifier.size(14.dp))
                                },
                                modifier = Modifier.testTag("extract_select_all_button")
                            )

                            AssistChip(
                                onClick = {
                                    selectedPageIndices = emptySet()
                                    rangeInputText = ""
                                    rangeErrorMessage = null
                                },
                                label = { Text("Clear", fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp))
                                },
                                modifier = Modifier.testTag("extract_clear_button")
                            )

                            AssistChip(
                                onClick = {
                                    val all = (0 until totalPages).toSet()
                                    val inverted = all - selectedPageIndices
                                    selectedPageIndices = inverted
                                    val pageNums = inverted.map { it + 1 }.sorted()
                                    rangeInputText = PageRangeParser.formatToRangeString(pageNums)
                                    rangeErrorMessage = null
                                },
                                label = { Text("Invert", fontSize = 11.sp) },
                                modifier = Modifier.testTag("extract_invert_button")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Output File Name Field (Requirement 10)
                OutlinedTextField(
                    value = outputNameInput,
                    onValueChange = { outputNameInput = it },
                    label = { Text("Output PDF Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("extract_filename_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DscPrimaryCyan
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Page Thumbnails Grid (Requirement 4, 13, 18)
                Text(
                    text = "SELECT PAGES VISUALLY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                val pageIndicesList = remember(totalPages) { (0 until totalPages).toList() }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(pageIndicesList, key = { it }) { index ->
                        val isSelected = selectedPageIndices.contains(index)
                        PageThumbnailItem(
                            file = currentPdf,
                            pageIndex = index,
                            isSelected = isSelected,
                            onToggle = {
                                val newSet = if (isSelected) {
                                    selectedPageIndices - index
                                } else {
                                    selectedPageIndices + index
                                }
                                selectedPageIndices = newSet
                                val sortedPages = newSet.map { it + 1 }.sorted()
                                rangeInputText = PageRangeParser.formatToRangeString(sortedPages)
                                rangeErrorMessage = null
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Sticky Extract Button
                val isExtractEnabled = currentPdf != null &&
                        selectedPageIndices.isNotEmpty() &&
                        rangeErrorMessage == null &&
                        outputNameInput.isNotBlank()

                Button(
                    onClick = {
                        if (currentPdf != null && isExtractEnabled) {
                            val sortedIndices = selectedPageIndices.toList().sorted()
                            val cleanName = if (outputNameInput.endsWith(".pdf", ignoreCase = true)) {
                                outputNameInput
                            } else {
                                "$outputNameInput.pdf"
                            }
                            viewModel.executeExtractPages(
                                file = currentPdf!!,
                                pageIndices = sortedIndices,
                                outputName = cleanName,
                                password = passwordInput.ifBlank { null }
                            )
                        }
                    },
                    enabled = isExtractEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(bottom = 6.dp)
                        .testTag("execute_extract_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DscPrimaryCyan,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedPageIndices.isEmpty()) {
                            "Select Pages to Extract"
                        } else {
                            "Extract ${selectedPageIndices.size} ${if (selectedPageIndices.size == 1) "Page" else "Pages"}"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SourcePdfCard(
    file: File?,
    totalPages: Int,
    onPickFile: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(DscPrimaryCyan.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = DscPrimaryCyan,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file?.name ?: "No PDF Selected",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (file != null) {
                        "$totalPages ${if (totalPages == 1) "Page" else "Pages"} • ${PdfEngine.formatFileSize(file.length())}"
                    } else {
                        "Pick a document from storage"
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = onPickFile,
                modifier = Modifier.testTag("extract_select_file_button"),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Change", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun PageThumbnailItem(
    file: File?,
    pageIndex: Int,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val context = LocalContext.current
    var thumbnailBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(file, pageIndex) {
        if (file != null && file.exists()) {
            val cached = PdfThumbnailCache.getFromMemory(file.absolutePath, pageIndex, 240)
            if (cached != null) {
                thumbnailBitmap = cached
                isLoading = false
            } else {
                isLoading = true
                val loaded = PdfThumbnailCache.loadThumbnail(context, file, pageIndex, 240)
                thumbnailBitmap = loaded
                isLoading = false
            }
        } else {
            isLoading = false
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) DscPrimaryCyan else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onToggle() }
            .testTag("extract_page_card_$pageIndex"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) DscPrimaryCyan.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (thumbnailBitmap != null) {
                Image(
                    bitmap = thumbnailBitmap!!.asImageBitmap(),
                    contentDescription = "Thumbnail for Page ${pageIndex + 1}",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 28.dp),
                    contentScale = ContentScale.Fit
                )
            } else if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Checkbox Indicator Badge (Requirement 18)
            if (isSelected) {
                Surface(
                    shape = CircleShape,
                    color = DscPrimaryCyan,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Bottom Page Number Strip
            Surface(
                color = if (isSelected) DscPrimaryCyan else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                Text(
                    text = "Page ${pageIndex + 1}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ExtractionResultView(
    result: com.dsc.pdfmaster.ui.ExtractionResultState,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
    onDone: () -> Unit,
    onExtractMore: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(DscPrimaryCyan.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.TaskAlt,
                contentDescription = null,
                tint = DscPrimaryCyan,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Pages Extracted Successfully!",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Your extracted document is ready and preserved in 100% original PDF quality.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Result Details Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = DscPrimaryCyan,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = result.outputFile.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${result.pageCount} ${if (result.pageCount == 1) "Page" else "Pages"} • ${PdfEngine.formatFileSize(result.fileSizeBytes)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Saved to Recent Files automatically",
                        fontSize = 11.sp,
                        color = DscPrimaryCyan,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action Buttons (Requirement 11)
        Button(
            onClick = onOpen,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("result_open_pdf_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = DscPrimaryCyan,
                contentColor = Color.Black
            )
        ) {
            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Open Extracted PDF", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onShare,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("result_share_pdf_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            OutlinedButton(
                onClick = onExport,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("result_export_pdf_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Export As...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("result_done_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Text("Done / Return to Home", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun PasswordUnlockView(
    currentFile: File?,
    passwordInput: String,
    onPasswordChange: (String) -> Unit,
    passwordError: String?,
    isUnlocking: Boolean,
    onChangeFile: () -> Unit,
    onUnlock: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(DscSecondaryPurple.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = DscSecondaryPurple,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "This PDF is Password Protected",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Enter the document password to unlock its pages and proceed with extraction.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = passwordInput,
            onValueChange = onPasswordChange,
            label = { Text("Document Password") },
            singleLine = true,
            isError = passwordError != null,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("extract_password_input"),
            shape = RoundedCornerShape(12.dp)
        )

        if (passwordError != null) {
            Text(
                text = passwordError,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onUnlock,
            enabled = passwordInput.isNotBlank() && !isUnlocking,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("extract_unlock_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = DscSecondaryPurple,
                contentColor = Color.White
            )
        ) {
            if (isUnlocking) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
            } else {
                Text("Unlock PDF", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = onChangeFile,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Select Another PDF", fontSize = 13.sp)
        }
    }
}

private fun getOriginalFileName(context: Context, uri: Uri): String {
    var name: String? = null
    if (uri.scheme == "content") {
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1) {
                        name = it.getString(nameIdx)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    if (name == null) {
        name = uri.lastPathSegment
    }
    return name ?: "document.pdf"
}

private fun sharePdf(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Extracted PDF via"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
