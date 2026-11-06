package com.dsc.pdfmaster.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsc.pdfmaster.R
import com.dsc.pdfmaster.data.database.RecentPdfEntity
import com.dsc.pdfmaster.pdfengine.PdfEngine
import com.dsc.pdfmaster.ui.PdfMasterViewModel
import com.dsc.pdfmaster.ui.ScreenRoute
import com.dsc.pdfmaster.ui.adaptive.WindowSizeClass
import com.dsc.pdfmaster.ui.adaptive.rememberWindowSizeClass
import com.dsc.pdfmaster.ui.components.CompactPdfCard
import com.dsc.pdfmaster.ui.components.PdfFileCard
import com.dsc.pdfmaster.ui.theme.Dimens
import com.dsc.pdfmaster.ui.util.FormFactorPreviews
import java.io.File

private data class ToolSection(
    val titleRes: Int,
    val tools: List<QuickActionItem>
)

private data class QuickActionItem(
    val titleRes: Int,
    val subtitleRes: Int,
    val icon: ImageVector,
    val tag: String,
    val onClick: () -> Unit
)

@Composable
fun HomeScreen(
    viewModel: PdfMasterViewModel,
    onOpenPdfViewer: (File) -> Unit,
    modifier: Modifier = Modifier,
    windowSizeClass: WindowSizeClass = rememberWindowSizeClass()
) {
    val context = LocalContext.current
    val recentList by viewModel.recentPdfs.collectAsState()
    val favoriteList by viewModel.pinnedOrFavoritePdfs.collectAsState()

    var fileToRename by remember { mutableStateOf<RecentPdfEntity?>(null) }
    var fileToDelete by remember { mutableStateOf<RecentPdfEntity?>(null) }
    var fileInfoToShow by remember { mutableStateOf<RecentPdfEntity?>(null) }
    var renameInputText by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importAndSelectPdf(it, openViewer = true)
        }
    }

    val organizeTools = listOf(
        QuickActionItem(
            titleRes = R.string.tool_open_pdf_title,
            subtitleRes = R.string.tool_open_pdf_subtitle,
            icon = Icons.Default.FolderOpen,
            tag = "action_open_pdf",
            onClick = { filePickerLauncher.launch(arrayOf("application/pdf")) }
        ),
        QuickActionItem(
            titleRes = R.string.tool_merge_title,
            subtitleRes = R.string.tool_merge_subtitle,
            icon = Icons.AutoMirrored.Filled.CallMerge,
            tag = "action_merge_pdf",
            onClick = { viewModel.navigateTo(ScreenRoute.MERGE_PDF) }
        ),
        QuickActionItem(
            titleRes = R.string.tool_split_title,
            subtitleRes = R.string.tool_split_subtitle,
            icon = Icons.AutoMirrored.Filled.CallSplit,
            tag = "action_split_pdf",
            onClick = { viewModel.navigateTo(ScreenRoute.SPLIT_PDF) }
        ),
        QuickActionItem(
            titleRes = R.string.tool_manage_pages_title,
            subtitleRes = R.string.tool_manage_pages_subtitle,
            icon = Icons.Default.Layers,
            tag = "action_manage_pages",
            onClick = { viewModel.navigateTo(ScreenRoute.MANAGE_PAGES) }
        )
    )

    val optimizeTools = listOf(
        QuickActionItem(
            titleRes = R.string.tool_compress_title,
            subtitleRes = R.string.tool_compress_subtitle,
            icon = Icons.Default.Compress,
            tag = "action_compress_pdf",
            onClick = { viewModel.navigateTo(ScreenRoute.COMPRESS_PDF) }
        )
    )

    val createConvertTools = listOf(
        QuickActionItem(
            titleRes = R.string.tool_images_to_pdf_title,
            subtitleRes = R.string.tool_images_to_pdf_subtitle,
            icon = Icons.Default.Image,
            tag = "action_images_to_pdf",
            onClick = { viewModel.navigateTo(ScreenRoute.IMAGES_TO_PDF) }
        ),
        QuickActionItem(
            titleRes = R.string.tool_create_pdf_title,
            subtitleRes = R.string.tool_create_pdf_subtitle,
            icon = Icons.Default.AddCircleOutline,
            tag = "action_create_pdf",
            onClick = { viewModel.navigateTo(ScreenRoute.CREATE_PDF) }
        ),
        QuickActionItem(
            titleRes = R.string.tool_convert_title,
            subtitleRes = R.string.tool_convert_subtitle,
            icon = Icons.Default.Transform,
            tag = "action_convert_pdf",
            onClick = { viewModel.navigateTo(ScreenRoute.CONVERT_PDF) }
        ),
        QuickActionItem(
            titleRes = R.string.tool_office_to_pdf_title,
            subtitleRes = R.string.tool_office_to_pdf_subtitle,
            icon = Icons.Default.Description,
            tag = "action_office_to_pdf",
            onClick = { viewModel.navigateTo(ScreenRoute.CONVERT_PDF) }
        ),
        QuickActionItem(
            titleRes = R.string.tool_scanner_title,
            subtitleRes = R.string.tool_scanner_subtitle,
            icon = Icons.Default.CameraAlt,
            tag = "action_document_scanner",
            onClick = { viewModel.navigateTo(ScreenRoute.DOCUMENT_SCANNER) }
        ),
        QuickActionItem(
            titleRes = R.string.tool_ocr_title,
            subtitleRes = R.string.tool_ocr_subtitle,
            icon = Icons.Default.TextFields,
            tag = "action_ocr_pdf",
            onClick = { viewModel.navigateTo(ScreenRoute.OCR_PDF) }
        )
    )

    val securityTools = listOf(
        QuickActionItem(
            titleRes = R.string.tool_protect_title,
            subtitleRes = R.string.tool_protect_subtitle,
            icon = Icons.Default.Lock,
            tag = "action_protect_pdf",
            onClick = { viewModel.navigateTo(ScreenRoute.PASSWORD_PROTECT) }
        ),
        QuickActionItem(
            titleRes = R.string.tool_unlock_title,
            subtitleRes = R.string.tool_unlock_subtitle,
            icon = Icons.Default.LockOpen,
            tag = "action_remove_password",
            onClick = { viewModel.navigateTo(ScreenRoute.REMOVE_PASSWORD) }
        ),
        QuickActionItem(
            titleRes = R.string.tool_sign_title,
            subtitleRes = R.string.tool_sign_subtitle,
            icon = Icons.Default.Draw,
            tag = "action_sign_pdf",
            onClick = { viewModel.navigateTo(ScreenRoute.SIGN_PDF) }
        ),
        QuickActionItem(
            titleRes = R.string.tool_annotate_title,
            subtitleRes = R.string.tool_annotate_subtitle,
            icon = Icons.Default.EditNote,
            tag = "action_annotate_pdf",
            onClick = { viewModel.navigateTo(ScreenRoute.ANNOTATE_PDF) }
        ),
        QuickActionItem(
            titleRes = R.string.tool_watermark_title,
            subtitleRes = R.string.tool_watermark_subtitle,
            icon = Icons.Default.FormatColorText,
            tag = "action_watermark_pdf",
            onClick = { viewModel.navigateTo(ScreenRoute.WATERMARK_PDF) }
        )
    )

    val sections = listOf(
        ToolSection(R.string.section_organize_tools, organizeTools),
        ToolSection(R.string.section_optimize_tools, optimizeTools),
        ToolSection(R.string.section_create_convert_tools, createConvertTools),
        ToolSection(R.string.section_security_tools, securityTools)
    )

    val unavailableMessage = stringResource(R.string.msg_file_unavailable)
    val shareChooserTitle = stringResource(R.string.share_pdf_chooser)

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = Dimens.MaxContentWidth)
                .padding(horizontal = Dimens.Spacing16)
                .testTag("home_screen_list"),
            contentPadding = PaddingValues(top = Dimens.Spacing16, bottom = Dimens.Spacing24)
        ) {
            // Horizontal Recent Files Carousel (at Top)
            if (recentList.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.section_recent_documents),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.2.sp,
                            modifier = Modifier.semantics { heading() }
                        )
                        TextButton(
                            onClick = { viewModel.navigateTo(ScreenRoute.RECENT_FILES) },
                            modifier = Modifier
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .testTag("see_all_recent_button")
                        ) {
                            Text(
                                text = stringResource(R.string.action_see_all),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Dimens.Spacing8))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing12),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(recentList.take(6), key = { "top_recent_${it.filePath}" }) { pdf ->
                            val available = viewModel.isPdfAvailable(pdf)
                            CompactPdfCard(
                                pdf = pdf,
                                isAvailable = available,
                                onClick = {
                                    if (available) {
                                        onOpenPdfViewer(File(pdf.filePath))
                                    } else {
                                        viewModel.showNotification(unavailableMessage)
                                    }
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(pdf.filePath, pdf.isFavorite) },
                                onTogglePinned = { viewModel.togglePinned(pdf.filePath, pdf.isPinned) },
                                onShare = { sharePdfFile(context, File(pdf.filePath), shareChooserTitle) },
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

                    Spacer(modifier = Modifier.height(Dimens.Spacing24))
                }
            }

            // Favorites Carousel (if available)
            if (favoriteList.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = stringResource(R.string.section_favorites),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.section_favorites),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.2.sp,
                            modifier = Modifier.semantics { heading() }
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing12),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(favoriteList, key = { "fav_${it.filePath}" }) { fav ->
                            val available = viewModel.isPdfAvailable(fav)
                            CompactPdfCard(
                                pdf = fav,
                                isAvailable = available,
                                onClick = {
                                    if (available) {
                                        onOpenPdfViewer(File(fav.filePath))
                                    } else {
                                        viewModel.showNotification(unavailableMessage)
                                    }
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(fav.filePath, fav.isFavorite) },
                                onTogglePinned = { viewModel.togglePinned(fav.filePath, fav.isPinned) },
                                onShare = { sharePdfFile(context, File(fav.filePath), shareChooserTitle) },
                                onRename = {
                                    fileToRename = fav
                                    renameInputText = fav.fileName.removeSuffix(".pdf")
                                },
                                onDelete = { fileToDelete = fav },
                                onRemoveFromRecent = { viewModel.removePdfFromRecent(fav.filePath) },
                                onFileInfo = { fileInfoToShow = fav }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Dimens.Spacing24))
                }
            }

            // Categorized Tool Sections
            sections.forEachIndexed { index, section ->
                item(key = "section_${section.titleRes}") {
                    val columns = windowSizeClass.quickActionGridColumns.coerceIn(2, 4)
                    Text(
                        text = stringResource(section.titleRes),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier
                            .padding(bottom = Dimens.Spacing12)
                            .semantics { heading() }
                            .then(if (index == 0) Modifier.testTag("quick_actions_header") else Modifier)
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(Dimens.Spacing12),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = Dimens.Spacing24)
                            .then(if (index == 0) Modifier.testTag("quick_actions_grid_${columns}_cols") else Modifier)
                    ) {
                        section.tools.chunked(columns).forEach { rowItems ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing12),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                rowItems.forEach { tool ->
                                    HomeScreenToolCard(
                                        title = stringResource(tool.titleRes),
                                        subtitle = stringResource(tool.subtitleRes),
                                        icon = tool.icon,
                                        onClick = tool.onClick,
                                        tag = tool.tag,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                val emptySlots = columns - rowItems.size
                                repeat(emptySlots) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
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
                            .testTag("home_rename_input_field")
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
                        .testTag("home_confirm_rename_button")
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { fileToRename = null },
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Delete Dialog
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
                        .testTag("home_confirm_delete_button")
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
                    Text("Size: ${PdfEngine.formatFileSize(pdfEntity.fileSizeBytes)}", fontSize = 13.sp)
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
}

@Composable
private fun HomeScreenToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(Dimens.CornerToolCard))
            .clickable { onClick() }
            .testTag(tag),
        shape = RoundedCornerShape(Dimens.CornerToolCard),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.Spacing16),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(Dimens.IconContainerSize)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(Dimens.CornerSmall)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(Dimens.Spacing12))
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(Dimens.Spacing4))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@FormFactorPreviews
@Composable
private fun HomeScreenPreview() {
    com.dsc.pdfmaster.ui.theme.PdfMasterTheme {
        HomeScreen(
            viewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
            onOpenPdfViewer = {}
        )
    }
}

private fun sharePdfFile(
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
