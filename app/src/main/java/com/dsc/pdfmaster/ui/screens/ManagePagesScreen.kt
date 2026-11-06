package com.dsc.pdfmaster.ui.screens

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsc.pdfmaster.ui.PdfMasterViewModel
import com.dsc.pdfmaster.ui.theme.Dimens
import com.dsc.pdfmaster.ui.util.rememberWindowInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagePagesScreen(
    viewModel: PdfMasterViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val windowInfo = rememberWindowInfo()
    val pageItems by viewModel.pdfPagesList.collectAsState()
    val currentPdf by viewModel.selectedPdfFile.collectAsState()
    var outputNameInput by remember { mutableStateOf("Organized_${System.currentTimeMillis() / 1000}.pdf") }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Organize & Rotate Pages", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("${pageItems.size} Pages Loaded", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("manage_pages_back_button")) {
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
                // Two-Pane Side-by-Side Layout
                Row(
                    modifier = contentModifier.padding(vertical = Dimens.Spacing16),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing24)
                ) {
                    // Left Pane: Page Thumbnails Grid
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "TAP TO DELETE, ROTATE ICON TO ROTATE, ARROWS TO REORDER",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.8.sp
                        )

                        Spacer(modifier = Modifier.height(Dimens.Spacing8))

                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 120.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(bottom = Dimens.Spacing12),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing12),
                            verticalArrangement = Arrangement.spacedBy(Dimens.Spacing12)
                        ) {
                            itemsIndexed(pageItems) { index, item ->
                                PageThumbnailCard(
                                    item = item,
                                    canMoveUp = index > 0,
                                    canMoveDown = index < pageItems.size - 1,
                                    onMoveUp = { viewModel.movePageUp(index) },
                                    onMoveDown = { viewModel.movePageDown(index) },
                                    onToggleDelete = { viewModel.togglePageSelectionForDelete(item.pageIndex) },
                                    onRotate = { viewModel.rotatePage(item.pageIndex) }
                                )
                            }
                        }
                    }

                    // Right Pane: Output Options & Action Button
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "EXPORT OPTIONS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )

                            Spacer(modifier = Modifier.height(Dimens.Spacing12))

                            OutlinedTextField(
                                value = outputNameInput,
                                onValueChange = { outputNameInput = it },
                                label = { Text("Output File Name") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("manage_pages_filename_input")
                            )
                        }

                        Spacer(modifier = Modifier.height(Dimens.Spacing24))

                        Button(
                            onClick = {
                                if (currentPdf != null) {
                                    viewModel.executeSaveOrganizedPages(currentPdf!!, outputNameInput.trim())
                                }
                            },
                            enabled = pageItems.any { !it.isSelected } && currentPdf != null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(Dimens.ButtonHeight)
                                .testTag("save_organized_pages_button"),
                            shape = RoundedCornerShape(Dimens.CornerMedium)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(Dimens.Spacing8))
                            Text("Save Updated PDF", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            } else {
                // Stacked Vertical Layout
                Column(
                    modifier = contentModifier.padding(vertical = Dimens.Spacing16)
                ) {
                    OutlinedTextField(
                        value = outputNameInput,
                        onValueChange = { outputNameInput = it },
                        label = { Text("Output File Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manage_pages_filename_input")
                    )

                    Spacer(modifier = Modifier.height(Dimens.Spacing12))

                    Text(
                        text = "TAP TO SELECT FOR DELETION, USE ROTATE ICON TO ROTATE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.8.sp
                    )

                    Spacer(modifier = Modifier.height(Dimens.Spacing8))

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = Dimens.Spacing12),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing12),
                        verticalArrangement = Arrangement.spacedBy(Dimens.Spacing12)
                    ) {
                        itemsIndexed(pageItems) { index, item ->
                            PageThumbnailCard(
                                item = item,
                                canMoveUp = index > 0,
                                canMoveDown = index < pageItems.size - 1,
                                onMoveUp = { viewModel.movePageUp(index) },
                                onMoveDown = { viewModel.movePageDown(index) },
                                onToggleDelete = { viewModel.togglePageSelectionForDelete(item.pageIndex) },
                                onRotate = { viewModel.rotatePage(item.pageIndex) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Dimens.Spacing12))

                    Button(
                        onClick = {
                            if (currentPdf != null) {
                                viewModel.executeSaveOrganizedPages(currentPdf!!, outputNameInput.trim())
                            }
                        },
                        enabled = pageItems.any { !it.isSelected } && currentPdf != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Dimens.ButtonHeight)
                            .testTag("save_organized_pages_button"),
                        shape = RoundedCornerShape(Dimens.CornerMedium)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(Dimens.Spacing8))
                        Text("Save Updated PDF", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PageThumbnailCard(
    item: com.dsc.pdfmaster.data.model.PdfPageItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleDelete: () -> Unit,
    onRotate: () -> Unit
) {
    val isMarkedForDelete = item.isSelected

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .border(
                width = if (isMarkedForDelete) 3.dp else 1.dp,
                color = if (isMarkedForDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(Dimens.CornerSmall)
            )
            .clickable { onToggleDelete() }
            .testTag("page_item_${item.pageIndex}"),
        shape = RoundedCornerShape(Dimens.CornerSmall),
        colors = CardDefaults.cardColors(
            containerColor = if (isMarkedForDelete) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (item.bitmap != null) {
                Image(
                    bitmap = item.bitmap.asImageBitmap(),
                    contentDescription = "Page ${item.displayPageNumber}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                )
            }

            // Page Number Badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "P. ${item.displayPageNumber}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Rotate Action Button
            IconButton(
                onClick = onRotate,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.RotateRight,
                    contentDescription = "Rotate",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Move Left / Move Right Buttons for page reordering
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (canMoveUp) {
                    IconButton(
                        onClick = onMoveUp,
                        modifier = Modifier
                            .size(28.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Move Left",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(28.dp))
                }

                if (canMoveDown) {
                    IconButton(
                        onClick = onMoveDown,
                        modifier = Modifier
                            .size(28.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Move Right",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(28.dp))
                }
            }

            // Delete Indicator Badge
            if (isMarkedForDelete) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(MaterialTheme.colorScheme.error, CircleShape)
                        .padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Marked for delete",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}
