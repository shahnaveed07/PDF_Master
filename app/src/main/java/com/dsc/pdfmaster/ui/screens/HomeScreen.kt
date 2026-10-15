package com.dsc.pdfmaster.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsc.pdfmaster.data.database.RecentPdfEntity
import com.dsc.pdfmaster.data.model.PdfTool
import com.dsc.pdfmaster.data.model.pdfToolsList
import com.dsc.pdfmaster.ui.PdfMasterViewModel
import com.dsc.pdfmaster.ui.ScreenRoute
import com.dsc.pdfmaster.ui.components.PdfFileCard
import com.dsc.pdfmaster.ui.components.ToolCard
import java.io.File
import java.io.FileOutputStream

@Composable
fun HomeScreen(
    viewModel: PdfMasterViewModel,
    onOpenPdfViewer: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val recentList by viewModel.recentPdfs.collectAsState()
    val favoriteList by viewModel.favoritePdfs.collectAsState()

    // File picker launcher for opening arbitrary PDF files
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val tempFile = File.createTempFile("picked_doc_", ".pdf", context.cacheDir)
                    val fos = FileOutputStream(tempFile)
                    inputStream.copyTo(fos)
                    inputStream.close()
                    fos.close()
                    viewModel.openPdfInViewer(tempFile)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
    ) {
        // Hero Studio Banner
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hero_banner_card"),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "PDF MASTER",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.85f),
                                    letterSpacing = 1.2.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Powerful PDF tools. Simple. Private.",
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Merge, split, compress, or convert documents with on-device processing.",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("open_file_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Open PDF",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            Button(
                                onClick = { viewModel.navigateTo(ScreenRoute.RECENT_FILES) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.2f),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("view_recent_button")
                            ) {
                                Text(
                                    text = "My Documents (${recentList.size})",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Popular Core Tools Grid
        item {
            Text(
                text = "FEATURED UTILITIES",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        val featuredTools = pdfToolsList.filter { it.isPopular }
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (featuredTools.size >= 1) {
                        ToolCard(
                            tool = featuredTools[0],
                            onClick = { viewModel.launchTool(featuredTools[0]) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (featuredTools.size >= 2) {
                        ToolCard(
                            tool = featuredTools[1],
                            onClick = { viewModel.launchTool(featuredTools[1]) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (featuredTools.size >= 3) {
                        ToolCard(
                            tool = featuredTools[2],
                            onClick = { viewModel.launchTool(featuredTools[2]) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (featuredTools.size >= 4) {
                        ToolCard(
                            tool = featuredTools[3],
                            onClick = { viewModel.launchTool(featuredTools[3]) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Favorite Documents Carousel (if any)
        if (favoriteList.isNotEmpty()) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "STARRED DOCUMENTS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

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

        // All Tools List
        item {
            Text(
                text = "ALL PDF TOOLS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        val otherTools = pdfToolsList.filter { !it.isPopular }
        items(otherTools) { tool ->
            ToolCard(
                tool = tool,
                onClick = { viewModel.launchTool(tool) },
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        // Recent Files Preview Header
        if (recentList.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECENT DOCUMENTS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Button(
                        onClick = { viewModel.navigateTo(ScreenRoute.RECENT_FILES) },
                        colors = ButtonDefaults.textButtonColors()
                    ) {
                        Text("See All", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

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
