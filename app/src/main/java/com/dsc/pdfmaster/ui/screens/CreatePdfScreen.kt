package com.dsc.pdfmaster.ui.screens

import android.net.Uri
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
fun CreatePdfScreen(
    viewModel: PdfMasterViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val windowInfo = rememberWindowInfo()
    var selectedTab by remember { mutableStateOf(0) } // 0: Text, 1: Image, 2: Hybrid
    var textInput by remember { mutableStateOf("") }
    var outputNameInput by remember { mutableStateOf("Created_Doc_${System.currentTimeMillis() / 1000}.pdf") }

    val selectedImageUris by viewModel.selectedImageUris.collectAsState()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addImagesForPdf(uris)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Create PDF Document", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("create_pdf_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Text PDF", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Image PDF", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Hybrid", fontWeight = FontWeight.Bold) }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Dimens.Spacing16),
            contentAlignment = Alignment.TopCenter
        ) {
            val contentModifier = Modifier
                .fillMaxSize()
                .widthIn(max = Dimens.MaxContentWidth)
                .padding(vertical = Dimens.Spacing16)

            if (windowInfo.isExpanded) {
                Row(
                    modifier = contentModifier,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing24)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        CreateTabLeftContent(
                            selectedTab = selectedTab,
                            textInput = textInput,
                            onTextInputChange = { textInput = it },
                            selectedImageUris = selectedImageUris,
                            onPickImages = { imagePickerLauncher.launch("image/*") },
                            onRemoveImage = { viewModel.removeImageForPdf(it) }
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "DOCUMENT NAME",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(Dimens.Spacing12))
                            OutlinedTextField(
                                value = outputNameInput,
                                onValueChange = { outputNameInput = it },
                                label = { Text("Output Document Name") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("create_pdf_name_input")
                            )
                        }

                        Spacer(modifier = Modifier.height(Dimens.Spacing24))

                        CreateTabActionButton(
                            selectedTab = selectedTab,
                            textInput = textInput,
                            selectedImageUris = selectedImageUris,
                            outputNameInput = outputNameInput,
                            viewModel = viewModel
                        )
                    }
                }
            } else {
                Column(
                    modifier = contentModifier.verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = outputNameInput,
                        onValueChange = { outputNameInput = it },
                        label = { Text("Output Document Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_pdf_name_input")
                    )

                    Spacer(modifier = Modifier.height(Dimens.Spacing16))

                    CreateTabLeftContent(
                        selectedTab = selectedTab,
                        textInput = textInput,
                        onTextInputChange = { textInput = it },
                        selectedImageUris = selectedImageUris,
                        onPickImages = { imagePickerLauncher.launch("image/*") },
                        onRemoveImage = { viewModel.removeImageForPdf(it) }
                    )

                    Spacer(modifier = Modifier.height(Dimens.Spacing24))

                    CreateTabActionButton(
                        selectedTab = selectedTab,
                        textInput = textInput,
                        selectedImageUris = selectedImageUris,
                        outputNameInput = outputNameInput,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateTabLeftContent(
    selectedTab: Int,
    textInput: String,
    onTextInputChange: (String) -> Unit,
    selectedImageUris: List<Uri>,
    onPickImages: () -> Unit,
    onRemoveImage: (Uri) -> Unit
) {
    when (selectedTab) {
        0 -> {
            OutlinedTextField(
                value = textInput,
                onValueChange = onTextInputChange,
                label = { Text("Enter Document Text Content") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .testTag("text_pdf_content_input"),
                minLines = 8
            )
        }
        1 -> {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPickImages() }
                    .testTag("pick_images_button"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(Dimens.CornerMedium)
            ) {
                Row(
                    modifier = Modifier.padding(Dimens.Spacing16),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(Dimens.Spacing12))
                    Text("Select Photos (${selectedImageUris.size} chosen)", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(Dimens.Spacing12))

            if (selectedImageUris.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(100.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing8),
                    verticalArrangement = Arrangement.spacedBy(Dimens.Spacing8)
                ) {
                    itemsIndexed(selectedImageUris) { _, uri ->
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(Dimens.CornerSmall))
                        ) {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            IconButton(
                                onClick = { onRemoveImage(uri) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove image", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No images selected yet.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        2 -> {
            OutlinedTextField(
                value = textInput,
                onValueChange = onTextInputChange,
                label = { Text("Introductory Text / Title") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                maxLines = 4
            )

            Spacer(modifier = Modifier.height(Dimens.Spacing12))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPickImages() },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(Dimens.CornerMedium)
            ) {
                Row(
                    modifier = Modifier.padding(Dimens.Spacing16),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(Dimens.Spacing12))
                    Text("Attach Images (${selectedImageUris.size} selected)", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CreateTabActionButton(
    selectedTab: Int,
    textInput: String,
    selectedImageUris: List<Uri>,
    outputNameInput: String,
    viewModel: PdfMasterViewModel
) {
    when (selectedTab) {
        0 -> {
            Button(
                onClick = {
                    if (textInput.isNotBlank()) {
                        viewModel.executeCreateTextPdf(textInput, outputNameInput.trim())
                    }
                },
                enabled = textInput.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.ButtonHeight)
                    .testTag("generate_text_pdf_button"),
                shape = RoundedCornerShape(Dimens.CornerMedium)
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                Spacer(modifier = Modifier.width(Dimens.Spacing8))
                Text("Generate Text PDF", fontWeight = FontWeight.Bold)
            }
        }
        1 -> {
            Button(
                onClick = {
                    if (selectedImageUris.isNotEmpty()) {
                        viewModel.executeImagesToPdf(outputNameInput.trim())
                    }
                },
                enabled = selectedImageUris.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.ButtonHeight)
                    .testTag("generate_image_pdf_button"),
                shape = RoundedCornerShape(Dimens.CornerMedium)
            ) {
                Icon(Icons.Default.Image, contentDescription = null)
                Spacer(modifier = Modifier.width(Dimens.Spacing8))
                Text("Convert Images to PDF", fontWeight = FontWeight.Bold)
            }
        }
        2 -> {
            Button(
                onClick = {
                    if (textInput.isNotBlank() || selectedImageUris.isNotEmpty()) {
                        if (selectedImageUris.isNotEmpty()) {
                            viewModel.executeImagesToPdf(outputNameInput.trim())
                        } else {
                            viewModel.executeCreateTextPdf(textInput, outputNameInput.trim())
                        }
                    }
                },
                enabled = textInput.isNotBlank() || selectedImageUris.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.ButtonHeight)
                    .testTag("generate_hybrid_pdf_button"),
                shape = RoundedCornerShape(Dimens.CornerMedium)
            ) {
                Icon(Icons.Default.Description, contentDescription = null)
                Spacer(modifier = Modifier.width(Dimens.Spacing8))
                Text("Generate Hybrid PDF", fontWeight = FontWeight.Bold)
            }
        }
    }
}
