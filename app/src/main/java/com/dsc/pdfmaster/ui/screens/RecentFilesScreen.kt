package com.dsc.pdfmaster.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Description
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsc.pdfmaster.data.database.RecentPdfEntity
import com.dsc.pdfmaster.ui.PdfMasterViewModel
import com.dsc.pdfmaster.ui.components.PdfFileCard
import java.io.File

@Composable
fun RecentFilesScreen(
    viewModel: PdfMasterViewModel,
    onOpenPdfViewer: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val recentPdfs by viewModel.recentPdfs.collectAsState()
    val favoritePdfs by viewModel.favoritePdfs.collectAsState()

    var selectedTabIndex by remember { mutableStateOf(0) }
    var fileToRename by remember { mutableStateOf<RecentPdfEntity?>(null) }
    var fileToDelete by remember { mutableStateOf<RecentPdfEntity?>(null) }
    var fileInfoToShow by remember { mutableStateOf<RecentPdfEntity?>(null) }
    var renameInputText by remember { mutableStateOf("") }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    val displayList = if (selectedTabIndex == 0) recentPdfs else favoritePdfs

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
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
                onClick = { selectedTabIndex = 0 },
                modifier = Modifier.testTag("tab_all_documents")
            ) {
                Text(
                    text = "All Files (${recentPdfs.size})",
                    modifier = Modifier.padding(vertical = 12.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                modifier = Modifier.testTag("tab_starred_documents")
            ) {
                Text(
                    text = "Starred (${favoritePdfs.size})",
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
                text = if (selectedTabIndex == 0) "RECENT PDF HISTORY" else "FAVORITE DOCUMENTS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )

            if (selectedTabIndex == 0 && recentPdfs.isNotEmpty()) {
                TextButton(
                    onClick = { showClearConfirmDialog = true },
                    modifier = Modifier.testTag("clear_history_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ClearAll,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text("Clear List", fontSize = 12.sp)
                }
            }
        }

        if (displayList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (selectedTabIndex == 0) "No recent documents found" else "No starred PDFs yet",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(displayList) { pdf ->
                    PdfFileCard(
                        pdf = pdf,
                        onClick = { onOpenPdfViewer(File(pdf.filePath)) },
                        onToggleFavorite = { viewModel.toggleFavorite(pdf.filePath, pdf.isFavorite) },
                        onShare = { sharePdf(context, File(pdf.filePath)) },
                        onRename = {
                            fileToRename = pdf
                            renameInputText = pdf.fileName.removeSuffix(".pdf")
                        },
                        onDelete = { fileToDelete = pdf },
                        onRemoveFromRecent = { viewModel.removePdfFromRecent(pdf.filePath) },
                        onFileInfo = { fileInfoToShow = pdf }
                    )
                }
            }
        }
    }

    // Rename Dialog
    fileToRename?.let { pdfEntity ->
        AlertDialog(
            onDismissRequest = { fileToRename = null },
            title = { Text("Rename PDF File", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter a new file name:", fontSize = 13.sp)
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
                    modifier = Modifier.testTag("confirm_rename_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    fileToDelete?.let { pdfEntity ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Delete PDF File?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete '${pdfEntity.fileName}' from disk storage? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePdf(pdfEntity.filePath)
                        fileToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { fileToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // File Info Dialog
    fileInfoToShow?.let { pdfEntity ->
        val formattedDate = remember(pdfEntity.lastAccessedTimestamp) {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(pdfEntity.lastAccessedTimestamp))
        }
        AlertDialog(
            onDismissRequest = { fileInfoToShow = null },
            title = { Text("File Information", fontWeight = FontWeight.Bold) },
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
                Button(onClick = { fileInfoToShow = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Clear History Dialog
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear Recent PDFs?", fontWeight = FontWeight.Bold) },
            text = { Text("This will remove files from history records. Physical files on storage will remain intact.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearHistory()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun sharePdf(context: android.content.Context, file: File) {
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
        context.startActivity(Intent.createChooser(intent, "Share PDF"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
