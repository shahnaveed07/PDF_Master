package com.dsc.pdfmaster.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsc.pdfmaster.data.model.PageNumberFormat
import com.dsc.pdfmaster.data.model.PageNumberPosition
import com.dsc.pdfmaster.data.model.WatermarkPosition
import com.dsc.pdfmaster.di.hiltViewModel
import com.dsc.pdfmaster.ui.PdfMasterViewModel
import com.dsc.pdfmaster.ui.viewmodel.WatermarkPdfViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WatermarkPdfScreen(
    viewModel: PdfMasterViewModel,
    onBack: () -> Unit,
    watermarkViewModel: WatermarkPdfViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val selectedGlobalPdf by viewModel.selectedPdfFile.collectAsState()
    val isExplicitlySelected by viewModel.isPdfExplicitlySelected.collectAsState()
    val uiState by watermarkViewModel.uiState.collectAsState()

    var outputFileName by remember(uiState.sourceFile) {
        val base = uiState.sourceFile?.nameWithoutExtension ?: "Document"
        mutableStateOf("${base}_watermarked.pdf")
    }

    LaunchedEffect(selectedGlobalPdf, isExplicitlySelected) {
        if (uiState.sourceFile == null && isExplicitlySelected && selectedGlobalPdf != null) {
            watermarkViewModel.setSourcePdf(selectedGlobalPdf)
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { watermarkViewModel.selectSourcePdfFromUri(it) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Watermark & Page Numbers", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("watermark_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Source PDF Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = uiState.sourceFile?.name ?: "No PDF Selected",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Configure text watermark and page numbering",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                        modifier = Modifier.testTag("watermark_select_pdf_button")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Select PDF", fontSize = 12.sp)
                    }
                }
            }

            // Text Watermark Configuration Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BrandingWatermark, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("TEXT WATERMARK", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Switch(
                            checked = uiState.watermarkConfig.enabled,
                            onCheckedChange = { watermarkViewModel.updateWatermarkEnabled(it) },
                            modifier = Modifier.testTag("watermark_enable_switch")
                        )
                    }

                    if (uiState.watermarkConfig.enabled) {
                        OutlinedTextField(
                            value = uiState.watermarkConfig.text,
                            onValueChange = { watermarkViewModel.updateWatermarkText(it) },
                            label = { Text("Watermark Text") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("watermark_text_input")
                        )

                        Text(
                            text = "Opacity: ${(uiState.watermarkConfig.opacity * 100).roundToInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Slider(
                            value = uiState.watermarkConfig.opacity,
                            onValueChange = { watermarkViewModel.updateWatermarkOpacity(it) },
                            valueRange = 0.05f..1.0f,
                            modifier = Modifier.testTag("watermark_opacity_slider")
                        )

                        Text(
                            text = "Rotation: ${uiState.watermarkConfig.rotationDegrees.roundToInt()}°",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Slider(
                            value = uiState.watermarkConfig.rotationDegrees,
                            onValueChange = { watermarkViewModel.updateWatermarkRotation(it) },
                            valueRange = -90f..90f,
                            modifier = Modifier.testTag("watermark_rotation_slider")
                        )

                        Text("Watermark Position", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            WatermarkPosition.entries.forEach { pos ->
                                FilterChip(
                                    selected = uiState.watermarkConfig.position == pos,
                                    onClick = { watermarkViewModel.updateWatermarkPosition(pos) },
                                    label = { Text(pos.label) },
                                    modifier = Modifier.testTag("watermark_pos_${pos.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            }

            // Page Numbers Configuration Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FormatListNumbered, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("PAGE NUMBERS", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Switch(
                            checked = uiState.pageNumberConfig.enabled,
                            onCheckedChange = { watermarkViewModel.updatePageNumbersEnabled(it) },
                            modifier = Modifier.testTag("page_numbers_enable_switch")
                        )
                    }

                    if (uiState.pageNumberConfig.enabled) {
                        Text("Page Number Format", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PageNumberFormat.entries.forEach { fmt ->
                                FilterChip(
                                    selected = uiState.pageNumberConfig.format == fmt,
                                    onClick = { watermarkViewModel.updatePageNumberFormat(fmt) },
                                    label = { Text(fmt.label) },
                                    modifier = Modifier.testTag("pagenum_format_${fmt.name.lowercase()}")
                                )
                            }
                        }

                        Text("Page Number Position", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PageNumberPosition.entries.forEach { pos ->
                                FilterChip(
                                    selected = uiState.pageNumberConfig.position == pos,
                                    onClick = { watermarkViewModel.updatePageNumberPosition(pos) },
                                    label = { Text(pos.label) },
                                    modifier = Modifier.testTag("pagenum_pos_${pos.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = outputFileName,
                onValueChange = { outputFileName = it },
                label = { Text("Output PDF Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    watermarkViewModel.applyWatermarkAndPageNumbers(outputFileName) { savedFile ->
                        viewModel.openPdfInViewer(savedFile)
                    }
                },
                enabled = uiState.sourceFile != null &&
                    (uiState.watermarkConfig.enabled || uiState.pageNumberConfig.enabled) &&
                    !uiState.isProcessing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("apply_watermark_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Apply Watermark & Page Numbers", fontWeight = FontWeight.Bold)
            }
        }
    }
}
