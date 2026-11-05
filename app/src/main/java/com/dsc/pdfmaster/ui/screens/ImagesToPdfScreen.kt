package com.dsc.pdfmaster.ui.screens

import android.net.Uri
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dsc.pdfmaster.ui.PdfMasterViewModel
import com.dsc.pdfmaster.ui.theme.Dimens
import com.dsc.pdfmaster.ui.util.rememberWindowInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagesToPdfScreen(
    viewModel: PdfMasterViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val windowInfo = rememberWindowInfo()
    val selectedUris by viewModel.selectedImageUris.collectAsState()
    var outputNameInput by remember { mutableStateOf("Photos_Doc_${System.currentTimeMillis() / 1000}.pdf") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addImagesForPdf(uris)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Images to PDF", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("images_to_pdf_back_button")) {
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
                // Two-Pane Layout
                Row(
                    modifier = contentModifier.padding(vertical = Dimens.Spacing16),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing24)
                ) {
                    // Left Pane: Image Preview Grid / Empty
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SELECTED IMAGES (${selectedUris.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )

                            OutlinedButton(
                                onClick = { imagePickerLauncher.launch("image/*") },
                                modifier = Modifier.testTag("add_photos_button"),
                                shape = RoundedCornerShape(Dimens.CornerMedium)
                            ) {
                                Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Photos", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(Dimens.Spacing12))

                        if (selectedUris.isEmpty()) {
                            ImagesEmptyStateCard(modifier = Modifier.weight(1f))
                        } else {
                            ImagesGrid(
                                selectedUris = selectedUris,
                                onRemoveImage = { viewModel.removeImageForPdf(it) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Right Pane: Options & Action
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "DOCUMENT CONFIGURATION",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(Dimens.Spacing12))
                            OutlinedTextField(
                                value = outputNameInput,
                                onValueChange = { outputNameInput = it },
                                label = { Text("Output PDF Document Name") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("images_to_pdf_name_input")
                            )
                        }

                        Button(
                            onClick = { viewModel.executeImagesToPdf(outputNameInput.trim()) },
                            enabled = selectedUris.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(Dimens.ButtonHeight)
                                .testTag("execute_images_to_pdf_button"),
                            shape = RoundedCornerShape(Dimens.CornerMedium)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                            Spacer(modifier = Modifier.width(Dimens.Spacing8))
                            Text("Convert ${selectedUris.size} Images to PDF", fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
                        label = { Text("Output PDF Document Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("images_to_pdf_name_input")
                    )

                    Spacer(modifier = Modifier.height(Dimens.Spacing16))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SELECTED IMAGES (${selectedUris.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )

                        OutlinedButton(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier.testTag("add_photos_button"),
                            shape = RoundedCornerShape(Dimens.CornerMedium)
                        ) {
                            Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Photos", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(Dimens.Spacing12))

                    if (selectedUris.isEmpty()) {
                        ImagesEmptyStateCard(modifier = Modifier.weight(1f))
                    } else {
                        ImagesGrid(
                            selectedUris = selectedUris,
                            onRemoveImage = { viewModel.removeImageForPdf(it) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(Dimens.Spacing12))

                    Button(
                        onClick = { viewModel.executeImagesToPdf(outputNameInput.trim()) },
                        enabled = selectedUris.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Dimens.ButtonHeight)
                            .testTag("execute_images_to_pdf_button"),
                        shape = RoundedCornerShape(Dimens.CornerMedium)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                        Spacer(modifier = Modifier.width(Dimens.Spacing8))
                        Text("Convert ${selectedUris.size} Images to PDF", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ImagesEmptyStateCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Dimens.CornerMedium)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(Dimens.Spacing12))
            Text("No photos selected", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(Dimens.Spacing4))
            Text(
                "Tap 'Add Photos' to pick JPG/PNG images to convert.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ImagesGrid(
    selectedUris: List<Uri>,
    onRemoveImage: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(110.dp),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = Dimens.Spacing8),
        horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing8),
        verticalArrangement = Arrangement.spacedBy(Dimens.Spacing8)
    ) {
        itemsIndexed(selectedUris) { index, uri ->
            Box(
                modifier = Modifier
                    .height(120.dp)
                    .clip(RoundedCornerShape(Dimens.CornerSmall))
                    .testTag("image_preview_$index")
            ) {
                AsyncImage(
                    model = uri,
                    contentDescription = "Image ${index + 1}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = { onRemoveImage(uri) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("${index + 1}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}
