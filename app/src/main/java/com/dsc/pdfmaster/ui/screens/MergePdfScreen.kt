package com.dsc.pdfmaster.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsc.pdfmaster.R
import com.dsc.pdfmaster.pdfengine.PdfEngine
import com.dsc.pdfmaster.ui.PdfMasterViewModel
import com.dsc.pdfmaster.ui.theme.Dimens
import com.dsc.pdfmaster.ui.util.FormFactorPreviews
import com.dsc.pdfmaster.ui.util.rememberWindowInfo
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergePdfScreen(
    viewModel: PdfMasterViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val windowInfo = rememberWindowInfo()
    val mergeFiles by viewModel.selectedFilesForMerge.collectAsState()
    var outputFileNameInput by remember { mutableStateOf("Merged_Document_${System.currentTimeMillis() / 1000}.pdf") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val fileList = mutableListOf<File>()
            for (uri in uris) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val temp = File.createTempFile("merge_input_", ".pdf", context.cacheDir)
                        val fos = FileOutputStream(temp)
                        inputStream.copyTo(fos)
                        inputStream.close()
                        fos.close()
                        fileList.add(temp)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            viewModel.addFilesForMerge(fileList)
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.merge_screen_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.semantics { heading() }
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("merge_back_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_navigate_back)
                    )
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
                // Two-Pane side-by-side layout for Expanded width screens (tablets / desktop)
                Row(
                    modifier = contentModifier.padding(vertical = Dimens.Spacing16),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing24)
                ) {
                    // Left Pane: File list / Empty state
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.merge_selected_files_count, mergeFiles.size),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp,
                                modifier = Modifier.semantics { heading() }
                            )

                            OutlinedButton(
                                onClick = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                                modifier = Modifier
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .testTag("add_pdf_files_button"),
                                shape = RoundedCornerShape(Dimens.CornerMedium)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = stringResource(R.string.merge_add_pdfs),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.merge_add_pdfs), fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(Dimens.Spacing12))

                        if (mergeFiles.isEmpty()) {
                            MergeEmptyStateCard(modifier = Modifier.weight(1f))
                        } else {
                            MergeFileList(
                                mergeFiles = mergeFiles,
                                onRemoveFile = { viewModel.removeFileFromMerge(it) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Right Pane: Options & Action Button
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "MERGE OPTIONS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(Dimens.Spacing12))
                            OutlinedTextField(
                                value = outputFileNameInput,
                                onValueChange = { outputFileNameInput = it },
                                label = { Text(stringResource(R.string.merge_output_name_label)) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("merge_filename_input")
                            )
                        }

                        Button(
                            onClick = { viewModel.executeMergePdfs(outputFileNameInput.trim()) },
                            enabled = mergeFiles.size >= 2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(Dimens.ButtonHeight)
                                .testTag("execute_merge_button"),
                            shape = RoundedCornerShape(Dimens.CornerMedium)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.CallMerge,
                                contentDescription = stringResource(R.string.merge_screen_title)
                            )
                            Spacer(modifier = Modifier.width(Dimens.Spacing8))
                            Text(
                                text = stringResource(R.string.merge_execute_button, mergeFiles.size),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            } else {
                // Compact / Medium Stacked Vertical Layout
                Column(
                    modifier = contentModifier.padding(vertical = Dimens.Spacing16)
                ) {
                    OutlinedTextField(
                        value = outputFileNameInput,
                        onValueChange = { outputFileNameInput = it },
                        label = { Text(stringResource(R.string.merge_output_name_label)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("merge_filename_input")
                    )

                    Spacer(modifier = Modifier.height(Dimens.Spacing16))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.merge_selected_files_count, mergeFiles.size),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp,
                            modifier = Modifier.semantics { heading() }
                        )

                        OutlinedButton(
                            onClick = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                            modifier = Modifier
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .testTag("add_pdf_files_button"),
                            shape = RoundedCornerShape(Dimens.CornerMedium)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.merge_add_pdfs),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.merge_add_pdfs), fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(Dimens.Spacing12))

                    if (mergeFiles.isEmpty()) {
                        MergeEmptyStateCard(modifier = Modifier.weight(1f))
                    } else {
                        MergeFileList(
                            mergeFiles = mergeFiles,
                            onRemoveFile = { viewModel.removeFileFromMerge(it) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(Dimens.Spacing12))

                    Button(
                        onClick = { viewModel.executeMergePdfs(outputFileNameInput.trim()) },
                        enabled = mergeFiles.size >= 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Dimens.ButtonHeight)
                            .testTag("execute_merge_button"),
                        shape = RoundedCornerShape(Dimens.CornerMedium)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.CallMerge,
                            contentDescription = stringResource(R.string.merge_screen_title)
                        )
                        Spacer(modifier = Modifier.width(Dimens.Spacing8))
                        Text(
                            text = stringResource(R.string.merge_execute_button, mergeFiles.size),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MergeEmptyStateCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Dimens.CornerMedium))
            .testTag("merge_empty_state"),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.CallMerge,
                contentDescription = stringResource(R.string.merge_screen_title),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(Dimens.Spacing12))
            Text(
                text = stringResource(R.string.merge_empty_title),
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(Dimens.Spacing4))
            Text(
                text = stringResource(R.string.merge_empty_subtitle),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MergeFileList(
    mergeFiles: List<File>,
    onRemoveFile: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = Dimens.Spacing8),
        verticalArrangement = Arrangement.spacedBy(Dimens.Spacing8)
    ) {
        itemsIndexed(mergeFiles) { index, file ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("merge_item_$index"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(Dimens.CornerSmall)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimens.Spacing12),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(Dimens.Spacing8)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.width(Dimens.Spacing12))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = file.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                        Text(
                            text = PdfEngine.formatFileSize(file.length()),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { onRemoveFile(file) },
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("remove_merge_item_$index")
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.cd_remove_file)
                        )
                    }
                }
            }
        }
    }
}

@FormFactorPreviews
@Composable
private fun MergePdfScreenPreview() {
    com.dsc.pdfmaster.ui.theme.PdfMasterTheme {
        MergePdfScreen(
            viewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
            onBack = {}
        )
    }
}
