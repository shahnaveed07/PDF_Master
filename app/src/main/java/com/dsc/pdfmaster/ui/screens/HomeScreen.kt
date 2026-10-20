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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsc.pdfmaster.ui.PdfMasterViewModel
import com.dsc.pdfmaster.ui.ScreenRoute
import com.dsc.pdfmaster.ui.components.PdfFileCard
import java.io.File

@Composable
fun HomeScreen(
    viewModel: PdfMasterViewModel,
    onOpenPdfViewer: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val recentList by viewModel.recentPdfs.collectAsState()
    val favoriteList by viewModel.favoritePdfs.collectAsState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importAndSelectPdf(it, openViewer = true)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        // Quick Action Grid Title
        item {
            Text(
                text = "QUICK ACTIONS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Quick Action Grid (2x2)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    QuickActionCard(
                        title = "Open PDF",
                        subtitle = "Select file",
                        icon = Icons.Default.FolderOpen,
                        onClick = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                        tag = "action_open_pdf",
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionCard(
                        title = "Create PDF",
                        subtitle = "Text / Images",
                        icon = Icons.Default.AddCircleOutline,
                        onClick = { viewModel.navigateTo(ScreenRoute.CREATE_PDF) },
                        tag = "action_create_pdf",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    QuickActionCard(
                        title = "Convert",
                        subtitle = "Office / Images / TXT",
                        icon = Icons.Default.Transform,
                        onClick = { viewModel.navigateTo(ScreenRoute.CONVERT_PDF) },
                        tag = "action_convert_pdf",
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionCard(
                        title = "Compress",
                        subtitle = "Reduce size",
                        icon = Icons.Default.Compress,
                        onClick = { viewModel.navigateTo(ScreenRoute.COMPRESS_PDF) },
                        tag = "action_compress_pdf",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    QuickActionCard(
                        title = "Protect PDF",
                        subtitle = "256-bit AES lock",
                        icon = Icons.Default.Lock,
                        onClick = { viewModel.navigateTo(ScreenRoute.PASSWORD_PROTECT) },
                        tag = "action_protect_pdf",
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionCard(
                        title = "Unlock PDF",
                        subtitle = "Remove password",
                        icon = Icons.Default.LockOpen,
                        onClick = { viewModel.navigateTo(ScreenRoute.REMOVE_PASSWORD) },
                        tag = "action_remove_password",
                        modifier = Modifier.weight(1f)
                    )
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
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "FAVORITES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.2.sp
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(favoriteList) { fav ->
                        PdfFileCard(
                            pdf = fav,
                            onClick = { onOpenPdfViewer(File(fav.filePath)) },
                            onToggleFavorite = { viewModel.toggleFavorite(fav.filePath, fav.isFavorite) },
                            onShare = { sharePdfFile(context, File(fav.filePath)) },
                            onRename = { viewModel.renamePdf(File(fav.filePath), "Renamed_${fav.fileName}") },
                            onDelete = { viewModel.deletePdf(fav.filePath) },
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
                    text = "RECENT DOCUMENTS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.2.sp
                )
                TextButton(
                    onClick = { viewModel.navigateTo(ScreenRoute.RECENT_FILES) },
                    modifier = Modifier.testTag("see_all_recent_button")
                ) {
                    Text("See All", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (recentList.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
                            text = "No recent PDFs. Open or create a PDF to get started.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(recentList.take(3)) { pdf ->
                PdfFileCard(
                    pdf = pdf,
                    onClick = { onOpenPdfViewer(File(pdf.filePath)) },
                    onToggleFavorite = { viewModel.toggleFavorite(pdf.filePath, pdf.isFavorite) },
                    onShare = { sharePdfFile(context, File(pdf.filePath)) },
                    onRename = { viewModel.renamePdf(File(pdf.filePath), "Renamed_${pdf.fileName}") },
                    onDelete = { viewModel.deletePdf(pdf.filePath) },
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
                    contentDescription = null,
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

private fun sharePdfFile(context: android.content.Context, file: File) {
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
        context.startActivity(Intent.createChooser(intent, "Share PDF via"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
