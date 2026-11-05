package com.dsc.pdfmaster.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsc.pdfmaster.R
import com.dsc.pdfmaster.ui.PdfMasterViewModel
import com.dsc.pdfmaster.ui.ScreenRoute
import com.dsc.pdfmaster.ui.adaptive.WindowSizeClass
import com.dsc.pdfmaster.ui.adaptive.rememberWindowSizeClass
import com.dsc.pdfmaster.ui.components.PdfFileCard
import java.io.File

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

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importAndSelectPdf(it, openViewer = true)
        }
    }

    val quickActions = listOf(
        QuickActionItem(
            titleRes = R.string.tool_open_pdf_title,
            subtitleRes = R.string.tool_open_pdf_subtitle,
            icon = Icons.Default.FolderOpen,
            tag = "action_open_pdf",
            onClick = { filePickerLauncher.launch(arrayOf("application/pdf")) }
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
            titleRes = R.string.tool_compress_title,
            subtitleRes = R.string.tool_compress_subtitle,
            icon = Icons.Default.Compress,
            tag = "action_compress_pdf",
            onClick = { viewModel.navigateTo(ScreenRoute.COMPRESS_PDF) }
        ),
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
        ),
        QuickActionItem(
            titleRes = R.string.tool_ocr_title,
            subtitleRes = R.string.tool_ocr_subtitle,
            icon = Icons.Default.TextFields,
            tag = "action_ocr_pdf",
            onClick = { viewModel.navigateTo(ScreenRoute.OCR_PDF) }
        ),
        QuickActionItem(
            titleRes = R.string.tool_scanner_title,
            subtitleRes = R.string.tool_scanner_subtitle,
            icon = Icons.Default.CameraAlt,
            tag = "action_document_scanner",
            onClick = { viewModel.navigateTo(ScreenRoute.DOCUMENT_SCANNER) }
        ),
        QuickActionItem(
            titleRes = R.string.tool_merge_title,
            subtitleRes = R.string.tool_merge_subtitle,
            icon = Icons.AutoMirrored.Filled.CallMerge,
            tag = "action_merge_pdf",
            onClick = { viewModel.navigateTo(ScreenRoute.MERGE_PDF) }
        )
    )

    val columns = windowSizeClass.quickActionGridColumns.coerceIn(2, 4)
    val unavailableMessage = stringResource(R.string.msg_file_unavailable)
    val shareChooserTitle = stringResource(R.string.share_pdf_chooser)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("home_screen_list"),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        // Quick Action Grid Title
        item {
            Text(
                text = stringResource(R.string.section_quick_actions),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.2.sp,
                modifier = Modifier
                    .semantics { heading() }
                    .testTag("quick_actions_header")
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Adaptive Quick Action Grid (2 columns on Compact, 3 on Medium, 4 on Expanded)
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.testTag("quick_actions_grid_${columns}_cols")
            ) {
                quickActions.chunked(columns).forEach { rowItems ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowItems.forEach { action ->
                            QuickActionCard(
                                title = stringResource(action.titleRes),
                                subtitle = stringResource(action.subtitleRes),
                                icon = action.icon,
                                onClick = action.onClick,
                                tag = action.tag,
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

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Favorites Carousel (Displayed only when non-empty)
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(favoriteList, key = { "fav_${it.filePath}" }) { fav ->
                        val available = viewModel.isPdfAvailable(fav)
                        PdfFileCard(
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
                            onRename = { viewModel.renamePdf(File(fav.filePath), "Renamed_${fav.fileName}") },
                            onDelete = { viewModel.deletePdf(fav.filePath) },
                            onRemoveFromRecent = { viewModel.removePdfFromRecent(fav.filePath) },
                            modifier = Modifier.width(280.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Latest 3 Recent Documents
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
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (recentList.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_empty_recent_card"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.empty_recent_home),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(recentList.take(3), key = { "recent_${it.filePath}" }) { pdf ->
                val available = viewModel.isPdfAvailable(pdf)
                PdfFileCard(
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
                    onRename = { viewModel.renamePdf(File(pdf.filePath), "Renamed_${pdf.fileName}") },
                    onDelete = { viewModel.deletePdf(pdf.filePath) },
                    onRemoveFromRecent = { viewModel.removePdfFromRecent(pdf.filePath) },
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickActionCard(
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
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .testTag(tag),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
