package com.dsc.pdfmaster.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsc.pdfmaster.R
import com.dsc.pdfmaster.data.database.RecentPdfEntity
import com.dsc.pdfmaster.ui.PdfMasterViewModel
import com.dsc.pdfmaster.ui.adaptive.WindowSizeClass
import com.dsc.pdfmaster.ui.adaptive.rememberWindowSizeClass
import com.dsc.pdfmaster.ui.components.PdfFileCard
import java.io.File

@Composable
fun RecentFilesScreen(
    viewModel: PdfMasterViewModel,
    onOpenPdfViewer: (File) -> Unit,
    modifier: Modifier = Modifier,
    windowSizeClass: WindowSizeClass = rememberWindowSizeClass()
) {
    val context = LocalContext.current
    val recentPdfs by viewModel.recentPdfs.collectAsState()
    val pinnedOrFavoritePdfs by viewModel.pinnedOrFavoritePdfs.collectAsState()
    val selectedPdfFile by viewModel.selectedPdfFile.collectAsState()

    var selectedTabIndex by remember { mutableStateOf(0) }
    var fileToRename by remember { mutableStateOf<RecentPdfEntity?>(null) }
    var fileToDelete by remember { mutableStateOf<RecentPdfEntity?>(null) }
    var fileInfoToShow by remember { mutableStateOf<RecentPdfEntity?>(null) }
    var renameInputText by remember { mutableStateOf("") }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    val displayList = if (selectedTabIndex == 0) recentPdfs else pinnedOrFavoritePdfs
    val isTwoPane = windowSizeClass.isTwoPaneSupported
    val unavailableMessage = stringResource(R.string.msg_file_unavailable)
    val shareTitle = stringResource(R.string.share_pdf_chooser)

    val handleOpenFile: (File) -> Unit = { file ->
        if (isTwoPane) {
            viewModel.setSelectedPdfFile(file)
        } else {
            onOpenPdfViewer(file)
        }
    }

    if (isTwoPane) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .testTag("recent_files_two_pane_layout")
        ) {
            RecentFilesListPane(
                viewModel = viewModel,
                recentPdfs = recentPdfs,
                pinnedOrFavoritePdfs = pinnedOrFavoritePdfs,
                displayList = displayList,
                selectedTabIndex = selectedTabIndex,
                onSelectTab = { selectedTabIndex = it },
                onShowClearConfirm = { showClearConfirmDialog = true },
                onOpenFile = handleOpenFile,
                onShareFile = { sharePdf(context, it, shareTitle) },
                onRequestRename = {
                    fileToRename = it
                    renameInputText = it.fileName.removeSuffix(".pdf")
                },
                onRequestDelete = { fileToDelete = it },
                onRequestInfo = { fileInfoToShow = it },
                unavailableMessage = unavailableMessage,
                modifier = Modifier
                    .weight(0.45f)
                    .fillMaxHeight()
                    .testTag("recent_files_list_pane")
            )

            VerticalDivider(modifier = Modifier.fillMaxHeight())

            Box(
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .testTag("recent_files_viewer_pane")
            ) {
                if (selectedPdfFile != null && selectedPdfFile!!.exists()) {
                    PdfViewerScreen(
                        viewModel = viewModel,
                        onBack = {}
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = stringResource(R.string.nav_reader),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.two_pane_viewer_placeholder),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    } else {
        RecentFilesListPane(
            viewModel = viewModel,
            recentPdfs = recentPdfs,
            pinnedOrFavoritePdfs = pinnedOrFavoritePdfs,
            displayList = displayList,
            selectedTabIndex = selectedTabIndex,
            onSelectTab = { selectedTabIndex = it },
            onShowClearConfirm = { showClearConfirmDialog = true },
            onOpenFile = handleOpenFile,
            onShareFile = { sharePdf(context, it, shareTitle) },
            onRequestRename = {
                fileToRename = it
                renameInputText = it.fileName.removeSuffix(".pdf")
            },
            onRequestDelete = { fileToDelete = it },
            onRequestInfo = { fileInfoToShow = it },
            unavailableMessage = unavailableMessage,
            modifier = modifier.fillMaxSize()
        )
    }

    // Rename Dialog
    fileToRename?.let { pdfEntity ->
        AlertDialog(
            onDismissRequest = { fileToRename = null },
            title = {
                Text(
                    text = stringResource(R.string.dialog_rename_title),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )
            },
            text = {
                Column {
                    Text(stringResource(R.string.dialog_rename_prompt), fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = renameInputText,
                        onValueChange = { renameInputText = it },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rename_input_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInputText.isNotBlank()) {
                            viewModel.renamePdf(File(pdfEntity.filePath), renameInputText.trim())
                            fileToRename = null
                        }
                    },
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("confirm_rename_button")
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { fileToRename = null },
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Delete Confirmation Dialog
    fileToDelete?.let { pdfEntity ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = {
                Text(
                    text = stringResource(R.string.dialog_delete_title),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )
            },
            text = {
                Text(stringResource(R.string.dialog_delete_message, pdfEntity.fileName))
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePdf(pdfEntity.filePath)
                        fileToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("confirm_delete_button")
                ) {
                    Text(stringResource(R.string.action_delete_permanently))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { fileToDelete = null },
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // File Info Dialog
    fileInfoToShow?.let { pdfEntity ->
        val formattedDate = remember(pdfEntity.lastAccessedTimestamp) {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date(pdfEntity.lastAccessedTimestamp))
        }
        AlertDialog(
            onDismissRequest = { fileInfoToShow = null },
            title = {
                Text(
                    text = stringResource(R.string.dialog_file_info_title),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Name: ${pdfEntity.fileName}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Size: ${com.dsc.pdfmaster.pdfengine.PdfEngine.formatFileSize(pdfEntity.fileSizeBytes)}", fontSize = 13.sp)
                    Text("Pages: ${pdfEntity.pageCount}", fontSize = 13.sp)
                    Text("Path: ${pdfEntity.filePath}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Last Opened: $formattedDate", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(
                    onClick = { fileInfoToShow = null },
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    // Clear History Dialog
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.dialog_clear_recent_title),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )
            },
            text = { Text(stringResource(R.string.dialog_clear_recent_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearHistory()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Text(stringResource(R.string.action_clear_all))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showClearConfirmDialog = false },
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun RecentFilesListPane(
    viewModel: PdfMasterViewModel,
    recentPdfs: List<RecentPdfEntity>,
    pinnedOrFavoritePdfs: List<RecentPdfEntity>,
    displayList: List<RecentPdfEntity>,
    selectedTabIndex: Int,
    onSelectTab: (Int) -> Unit,
    onShowClearConfirm: () -> Unit,
    onOpenFile: (File) -> Unit,
    onShareFile: (File) -> Unit,
    onRequestRename: (RecentPdfEntity) -> Unit,
    onRequestDelete: (RecentPdfEntity) -> Unit,
    onRequestInfo: (RecentPdfEntity) -> Unit,
    unavailableMessage: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp)
    ) {
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { onSelectTab(0) },
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("tab_all_documents")
            ) {
                Text(
                    text = stringResource(R.string.tab_all_files, recentPdfs.size),
                    modifier = Modifier.padding(vertical = 12.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { onSelectTab(1) },
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("tab_starred_documents")
            ) {
                Text(
                    text = stringResource(R.string.tab_favorites_pinned, pinnedOrFavoritePdfs.size),
                    modifier = Modifier.padding(vertical = 12.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (selectedTabIndex == 0) {
                    stringResource(R.string.header_recent_history)
                } else {
                    stringResource(R.string.header_favorites_pinned_docs)
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp,
                modifier = Modifier.semantics { heading() }
            )

            if (selectedTabIndex == 0 && recentPdfs.isNotEmpty()) {
                TextButton(
                    onClick = onShowClearConfirm,
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("clear_history_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ClearAll,
                        contentDescription = stringResource(R.string.action_clear_list),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(stringResource(R.string.action_clear_list), fontSize = 12.sp)
                }
            }
        }

        val pinnedPaths = remember(pinnedOrFavoritePdfs) { pinnedOrFavoritePdfs.map { it.filePath }.toSet() }
        val unpinnedRecentPdfs = remember(recentPdfs, pinnedPaths) {
            recentPdfs.filterNot { it.filePath in pinnedPaths }
        }
        val searchQuery by viewModel.searchQuery.collectAsState()

        if (displayList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = stringResource(R.string.empty_recent_files),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (searchQuery.isNotBlank()) {
                        Text(
                            text = "No matching documents",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "No documents match \"$searchQuery\"",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { viewModel.setSearchQuery("") },
                            modifier = Modifier.testTag("clear_search_filter_button")
                        ) {
                            Text("Clear Search")
                        }
                    } else {
                        Text(
                            text = if (selectedTabIndex == 0) {
                                stringResource(R.string.empty_recent_files)
                            } else {
                                stringResource(R.string.empty_starred_files)
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (selectedTabIndex == 0) {
                                stringResource(R.string.empty_recent_files_hint)
                            } else {
                                stringResource(R.string.empty_starred_files_hint)
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .testTag("recent_files_lazy_column"),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (selectedTabIndex == 0) {
                    if (pinnedOrFavoritePdfs.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.header_favorites_pinned),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp,
                                modifier = Modifier
                                    .padding(bottom = 4.dp)
                                    .semantics { heading() }
                                    .testTag("favorites_pinned_section_header")
                            )
                        }
                        items(pinnedOrFavoritePdfs, key = { "pinned_${it.filePath}" }) { pdf ->
                            val available = viewModel.isPdfAvailable(pdf)
                            PdfFileCard(
                                pdf = pdf,
                                isAvailable = available,
                                onClick = {
                                    if (available) {
                                        onOpenFile(File(pdf.filePath))
                                    } else {
                                        viewModel.showNotification(unavailableMessage)
                                    }
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(pdf.filePath, pdf.isFavorite) },
                                onTogglePinned = { viewModel.togglePinned(pdf.filePath, pdf.isPinned) },
                                onShare = { onShareFile(File(pdf.filePath)) },
                                onRename = { onRequestRename(pdf) },
                                onDelete = { onRequestDelete(pdf) },
                                onRemoveFromRecent = { viewModel.removePdfFromRecent(pdf.filePath) },
                                onFileInfo = { onRequestInfo(pdf) }
                            )
                        }
                        if (unpinnedRecentPdfs.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.header_all_recent_files),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier
                                        .padding(bottom = 4.dp)
                                        .semantics { heading() }
                                )
                            }
                            items(unpinnedRecentPdfs, key = { "recent_${it.filePath}" }) { pdf ->
                                val available = viewModel.isPdfAvailable(pdf)
                                PdfFileCard(
                                    pdf = pdf,
                                    isAvailable = available,
                                    onClick = {
                                        if (available) {
                                            onOpenFile(File(pdf.filePath))
                                        } else {
                                            viewModel.showNotification(unavailableMessage)
                                        }
                                    },
                                    onToggleFavorite = { viewModel.toggleFavorite(pdf.filePath, pdf.isFavorite) },
                                    onTogglePinned = { viewModel.togglePinned(pdf.filePath, pdf.isPinned) },
                                    onShare = { onShareFile(File(pdf.filePath)) },
                                    onRename = { onRequestRename(pdf) },
                                    onDelete = { onRequestDelete(pdf) },
                                    onRemoveFromRecent = { viewModel.removePdfFromRecent(pdf.filePath) },
                                    onFileInfo = { onRequestInfo(pdf) }
                                )
                            }
                        }
                    } else {
                        items(recentPdfs, key = { "recent_${it.filePath}" }) { pdf ->
                            val available = viewModel.isPdfAvailable(pdf)
                            PdfFileCard(
                                pdf = pdf,
                                isAvailable = available,
                                onClick = {
                                    if (available) {
                                        onOpenFile(File(pdf.filePath))
                                    } else {
                                        viewModel.showNotification(unavailableMessage)
                                    }
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(pdf.filePath, pdf.isFavorite) },
                                onTogglePinned = { viewModel.togglePinned(pdf.filePath, pdf.isPinned) },
                                onShare = { onShareFile(File(pdf.filePath)) },
                                onRename = { onRequestRename(pdf) },
                                onDelete = { onRequestDelete(pdf) },
                                onRemoveFromRecent = { viewModel.removePdfFromRecent(pdf.filePath) },
                                onFileInfo = { onRequestInfo(pdf) }
                            )
                        }
                    }
                } else {
                    items(pinnedOrFavoritePdfs, key = { "fav_${it.filePath}" }) { pdf ->
                        val available = viewModel.isPdfAvailable(pdf)
                        PdfFileCard(
                            pdf = pdf,
                            isAvailable = available,
                            onClick = {
                                if (available) {
                                    onOpenFile(File(pdf.filePath))
                                } else {
                                    viewModel.showNotification(unavailableMessage)
                                }
                            },
                            onToggleFavorite = { viewModel.toggleFavorite(pdf.filePath, pdf.isFavorite) },
                            onTogglePinned = { viewModel.togglePinned(pdf.filePath, pdf.isPinned) },
                            onShare = { onShareFile(File(pdf.filePath)) },
                            onRename = { onRequestRename(pdf) },
                            onDelete = { onRequestDelete(pdf) },
                            onRemoveFromRecent = { viewModel.removePdfFromRecent(pdf.filePath) },
                            onFileInfo = { onRequestInfo(pdf) }
                        )
                    }
                }
            }
        }
    }
}

private fun sharePdf(
    context: android.content.Context,
    file: File,
    chooserTitle: String
) {
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
