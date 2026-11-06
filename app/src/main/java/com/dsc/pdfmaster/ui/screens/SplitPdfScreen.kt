package com.dsc.pdfmaster.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsc.pdfmaster.pdfengine.PageRangeParser
import com.dsc.pdfmaster.pdfengine.PageRangeResult
import com.dsc.pdfmaster.pdfengine.PdfEngine
import com.dsc.pdfmaster.ui.PdfMasterViewModel
import com.dsc.pdfmaster.ui.theme.Dimens
import com.dsc.pdfmaster.ui.util.rememberWindowInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitPdfScreen(
    viewModel: PdfMasterViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val windowInfo = rememberWindowInfo()
    val currentPdf by viewModel.selectedPdfFile.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()

    var totalPages by remember { mutableIntStateOf(1) }
    var pageRangeInput by remember { mutableStateOf("1") }
    var outputNameInput by remember { mutableStateOf("Split_${System.currentTimeMillis() / 1000}.pdf") }

    val parsedRange = remember(pageRangeInput, totalPages, currentPdf) {
        if (currentPdf == null) {
            PageRangeResult.Error("No PDF selected")
        } else {
            PageRangeParser.parse(pageRangeInput, totalPages)
        }
    }
    val rangeError = (parsedRange as? PageRangeResult.Error)?.message
    val isRangeValid = parsedRange is PageRangeResult.Success
    val isOutputNameValid = outputNameInput.trim().isNotBlank()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importAndSelectPdf(it, openViewer = false)
        }
    }

    LaunchedEffect(currentPdf) {
        val file = currentPdf
        if (file != null && file.exists()) {
            withContext(Dispatchers.IO) {
                val meta = PdfEngine.getPdfMetadata(context, android.net.Uri.fromFile(file))
                totalPages = meta?.pageCount ?: 1
                pageRangeInput = if (totalPages > 1) "1-$totalPages" else "1"
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Split & Extract Pages", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("split_back_button")) {
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
                // Two-Pane Layout on Expanded
                Row(
                    modifier = contentModifier.padding(vertical = Dimens.Spacing16),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing24)
                ) {
                    // Left Pane: Selected File Preview
                    Column(modifier = Modifier.weight(1f)) {
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
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.width(Dimens.Spacing12))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentPdf?.name ?: "No PDF Selected",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = if (currentPdf != null) "Total Pages: $totalPages" else "Pick a file to split",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                                    modifier = Modifier.testTag("split_select_file_button"),
                                    shape = RoundedCornerShape(Dimens.CornerMedium)
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(Dimens.Spacing4))
                                    Text("Change", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Right Pane: Split Options & Execute
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "SPLIT CONFIGURATION",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )

                            Spacer(modifier = Modifier.height(Dimens.Spacing12))

                            OutlinedTextField(
                                value = pageRangeInput,
                                onValueChange = { pageRangeInput = it },
                                label = { Text("Page Range (e.g. 1-3, 5, 7-10)") },
                                singleLine = true,
                                isError = rangeError != null && pageRangeInput.isNotBlank(),
                                supportingText = {
                                    if (rangeError != null && pageRangeInput.isNotBlank()) {
                                        Text(text = rangeError, color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("page_range_input_field")
                            )

                            Spacer(modifier = Modifier.height(Dimens.Spacing12))

                            OutlinedTextField(
                                value = outputNameInput,
                                onValueChange = { outputNameInput = it },
                                label = { Text("Output PDF File Name") },
                                singleLine = true,
                                isError = !isOutputNameValid && currentPdf != null,
                                supportingText = {
                                    if (!isOutputNameValid && currentPdf != null) {
                                        Text("Please enter a valid output file name", color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("split_output_name_field")
                            )
                        }

                        Button(
                            onClick = {
                                val successPages = (parsedRange as? PageRangeResult.Success)?.pageNumbers
                                if (currentPdf != null && !successPages.isNullOrEmpty() && isOutputNameValid) {
                                    val indices = successPages.map { it - 1 }
                                    viewModel.executeSplitPdf(currentPdf!!, indices, outputNameInput.trim())
                                }
                            },
                            enabled = currentPdf != null && isRangeValid && isOutputNameValid && !isProcessing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(Dimens.ButtonHeight)
                                .testTag("execute_split_button"),
                            shape = RoundedCornerShape(Dimens.CornerMedium)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.CallSplit, contentDescription = null)
                            Spacer(modifier = Modifier.width(Dimens.Spacing8))
                            Text("Extract Selected Pages", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            } else {
                // Stacked Vertical Scroll Layout
                Column(
                    modifier = contentModifier
                        .padding(vertical = Dimens.Spacing16)
                        .verticalScroll(rememberScrollState())
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
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.width(Dimens.Spacing12))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentPdf?.name ?: "No PDF Selected",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = if (currentPdf != null) "Total Pages: $totalPages" else "Pick a file to split",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                                modifier = Modifier.testTag("split_select_file_button"),
                                shape = RoundedCornerShape(Dimens.CornerMedium)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(Dimens.Spacing4))
                                Text("Change", fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Dimens.Spacing24))

                    Text(
                        text = "SPLIT CONFIGURATION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(Dimens.Spacing12))

                    OutlinedTextField(
                        value = pageRangeInput,
                        onValueChange = { pageRangeInput = it },
                        label = { Text("Page Range (e.g. 1-3, 5, 7-10)") },
                        singleLine = true,
                        isError = rangeError != null && pageRangeInput.isNotBlank(),
                        supportingText = {
                            if (rangeError != null && pageRangeInput.isNotBlank()) {
                                Text(text = rangeError, color = MaterialTheme.colorScheme.error)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("page_range_input_field")
                    )

                    Spacer(modifier = Modifier.height(Dimens.Spacing12))

                    OutlinedTextField(
                        value = outputNameInput,
                        onValueChange = { outputNameInput = it },
                        label = { Text("Output PDF File Name") },
                        singleLine = true,
                        isError = !isOutputNameValid && currentPdf != null,
                        supportingText = {
                            if (!isOutputNameValid && currentPdf != null) {
                                Text("Please enter a valid output file name", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("split_output_name_field")
                    )

                    Spacer(modifier = Modifier.height(Dimens.Spacing32))

                    Button(
                        onClick = {
                            val successPages = (parsedRange as? PageRangeResult.Success)?.pageNumbers
                            if (currentPdf != null && !successPages.isNullOrEmpty() && isOutputNameValid) {
                                val indices = successPages.map { it - 1 }
                                viewModel.executeSplitPdf(currentPdf!!, indices, outputNameInput.trim())
                            }
                        },
                        enabled = currentPdf != null && isRangeValid && isOutputNameValid && !isProcessing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Dimens.ButtonHeight)
                            .testTag("execute_split_button"),
                        shape = RoundedCornerShape(Dimens.CornerMedium)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.CallSplit, contentDescription = null)
                        Spacer(modifier = Modifier.width(Dimens.Spacing8))
                        Text("Extract Selected Pages", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}
