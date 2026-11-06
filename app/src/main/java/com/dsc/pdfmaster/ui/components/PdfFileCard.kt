package com.dsc.pdfmaster.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsc.pdfmaster.data.database.RecentPdfEntity
import com.dsc.pdfmaster.pdfengine.PdfEngine
import com.dsc.pdfmaster.pdfengine.PdfThumbnailCache
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PdfFileCard(
    pdf: RecentPdfEntity,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onTogglePinned: (() -> Unit)? = null,
    onRemoveFromRecent: (() -> Unit)? = null,
    onFileInfo: (() -> Unit)? = null,
    isAvailable: Boolean = remember(pdf.filePath) {
        val f = File(pdf.filePath)
        f.exists() && f.isFile && f.length() > 0L
    },
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val formattedDate = remember(pdf.lastAccessedTimestamp) {
        SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(pdf.lastAccessedTimestamp))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = isAvailable) { onClick() }
            .testTag("pdf_file_card_${pdf.filePath.hashCode()}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isAvailable) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isAvailable) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isAvailable) Icons.Default.PictureAsPdf else Icons.Default.ErrorOutline,
                    contentDescription = if (isAvailable) "PDF Icon" else "File Unavailable",
                    tint = if (isAvailable) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    },
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (pdf.isPinned) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(14.dp)
                                .padding(end = 4.dp)
                        )
                    }
                    Text(
                        text = pdf.fileName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (isAvailable) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${pdf.pageCount} pages",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = " • ",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = PdfEngine.formatFileSize(pdf.fileSizeBytes),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (pdf.lastReadPage > 0) {
                            Text(
                                text = " • Last read p.${pdf.lastReadPage + 1}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = formattedDate,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                } else {
                    Text(
                        text = "File no longer available",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("stale_file_badge_${pdf.filePath.hashCode()}")
                    )
                    Text(
                        text = "Moved, deleted, or permission revoked",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!isAvailable) {
                OutlinedButton(
                    onClick = { (onRemoveFromRecent ?: onDelete).invoke() },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("remove_stale_file_button_${pdf.filePath.hashCode()}")
                ) {
                    Text("Remove", fontSize = 12.sp)
                }
            } else {
                if (onTogglePinned != null) {
                    IconButton(
                        onClick = onTogglePinned,
                        modifier = Modifier.testTag("pin_button_${pdf.filePath.hashCode()}")
                    ) {
                        Icon(
                            imageVector = if (pdf.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (pdf.isPinned) "Unpin" else "Pin",
                            tint = if (pdf.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.testTag("favorite_button_${pdf.filePath.hashCode()}")
                ) {
                    Icon(
                        imageVector = if (pdf.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Favorite",
                        tint = if (pdf.isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "File Options"
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (onTogglePinned != null) {
                            DropdownMenuItem(
                                text = { Text(if (pdf.isPinned) "Unpin from Top" else "Pin to Top") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (pdf.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onTogglePinned()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Share PDF") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onShare()
                            }
                        )
                        if (onFileInfo != null) {
                            DropdownMenuItem(
                                text = { Text("File Info") },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onFileInfo()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onRename()
                            }
                        )
                        if (onRemoveFromRecent != null) {
                            DropdownMenuItem(
                                text = { Text("Remove from Recent") },
                                leadingIcon = { Icon(Icons.Default.RemoveCircleOutline, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onRemoveFromRecent()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete File", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact Recent Document card variant optimized for horizontal carousels (e.g. Home screen).
 * Employs a clean layout with:
 * - Real first-page thumbnail caching (fallback to PDF icon)
 * - Sensible filename truncation without clipping
 * - Clear page count, file size, and last opened metadata
 * - Pinned and Favorite badges
 * - Single 3-dot overflow menu for full action access (open, pin, favorite, share, rename, info, delete)
 */
@Composable
fun CompactPdfCard(
    pdf: RecentPdfEntity,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onTogglePinned: (() -> Unit)? = null,
    onRemoveFromRecent: (() -> Unit)? = null,
    onFileInfo: (() -> Unit)? = null,
    isAvailable: Boolean = remember(pdf.filePath) {
        val f = File(pdf.filePath)
        f.exists() && f.isFile && f.length() > 0L
    },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var thumbnailBitmap by remember(pdf.filePath) {
        mutableStateOf<Bitmap?>(PdfThumbnailCache.getFromMemory(pdf.filePath, 0, 160))
    }

    LaunchedEffect(pdf.filePath, isAvailable) {
        if (isAvailable && thumbnailBitmap == null) {
            val file = File(pdf.filePath)
            if (file.exists() && file.isFile && file.length() > 0L) {
                thumbnailBitmap = PdfThumbnailCache.loadThumbnail(context, file, pageIndex = 0, targetWidth = 160)
            }
        }
    }

    val formattedDate = remember(pdf.lastAccessedTimestamp) {
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(pdf.lastAccessedTimestamp))
    }

    Card(
        modifier = modifier
            .width(220.dp)
            .clickable(enabled = isAvailable) { onClick() }
            .testTag("compact_pdf_card_${pdf.filePath.hashCode()}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isAvailable) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Top row: Thumbnail/Icon + Badges + Overflow Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isAvailable) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (thumbnailBitmap != null && !thumbnailBitmap!!.isRecycled) {
                        Image(
                            bitmap = thumbnailBitmap!!.asImageBitmap(),
                            contentDescription = "PDF Thumbnail",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = if (isAvailable) Icons.Default.PictureAsPdf else Icons.Default.ErrorOutline,
                            contentDescription = if (isAvailable) "PDF Icon" else "File Unavailable",
                            tint = if (isAvailable) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (pdf.isPinned) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (pdf.isFavorite) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Favorite",
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("compact_card_menu_${pdf.filePath.hashCode()}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More actions",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            if (isAvailable) {
                                DropdownMenuItem(
                                    text = { Text("Open") },
                                    leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onClick()
                                    }
                                )
                                if (onTogglePinned != null) {
                                    DropdownMenuItem(
                                        text = { Text(if (pdf.isPinned) "Unpin" else "Pin to Top") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (pdf.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            showMenu = false
                                            onTogglePinned()
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(if (pdf.isFavorite) "Remove Favorite" else "Add to Favorites") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (pdf.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                            contentDescription = null,
                                            tint = if (pdf.isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        onToggleFavorite()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Share") },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onShare()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onRename()
                                    }
                                )
                                if (onFileInfo != null) {
                                    DropdownMenuItem(
                                        text = { Text("File Info") },
                                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                        onClick = {
                                            showMenu = false
                                            onFileInfo()
                                        }
                                    )
                                }
                            }
                            if (onRemoveFromRecent != null) {
                                DropdownMenuItem(
                                    text = { Text("Remove from Recent") },
                                    leadingIcon = { Icon(Icons.Default.RemoveCircleOutline, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onRemoveFromRecent()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Delete File", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Document Filename (Clear, sensible truncation without clipping)
            Text(
                text = pdf.fileName,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Document Metadata
            if (isAvailable) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${pdf.pageCount} p • ${PdfEngine.formatFileSize(pdf.fileSizeBytes)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                    Text(
                        text = formattedDate,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1
                    )
                }
            } else {
                Text(
                    text = "File Unavailable",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

