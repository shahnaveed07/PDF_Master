package com.dsc.pdfmaster.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsc.pdfmaster.ui.PdfMasterViewModel
import com.dsc.pdfmaster.ui.ScreenRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConvertPdfScreen(
    viewModel: PdfMasterViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentPdf by viewModel.selectedPdfFile.collectAsState()

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importAndSelectPdf(it, openViewer = false)
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.executeConvertImageFormat(it, isToJpeg = true)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Document Converter", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("convert_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Category: PDF Conversions
            Text(
                text = "PDF CONVERSIONS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            ConversionCard(
                title = "Images to PDF",
                subtitle = "Combine JPG/PNG photos into a clean PDF document",
                icon = Icons.Default.Image,
                isEnabled = true,
                onClick = { viewModel.navigateTo(ScreenRoute.IMAGES_TO_PDF) },
                tag = "convert_images_to_pdf"
            )

            Spacer(modifier = Modifier.height(10.dp))

            ConversionCard(
                title = "Text File to PDF",
                subtitle = "Generate formatted PDF document from plain text",
                icon = Icons.Default.Description,
                isEnabled = true,
                onClick = { viewModel.navigateTo(ScreenRoute.CREATE_PDF) },
                tag = "convert_text_to_pdf"
            )

            Spacer(modifier = Modifier.height(10.dp))

            ConversionCard(
                title = "Extract PDF Text (PDF to TXT)",
                subtitle = "Extract plain text from selected PDF document",
                icon = Icons.Default.Description,
                isEnabled = true,
                onClick = {
                    if (currentPdf != null) {
                        viewModel.executePdfToText(currentPdf!!)
                    } else {
                        pdfPickerLauncher.launch(arrayOf("application/pdf"))
                    }
                },
                tag = "convert_pdf_to_text"
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Category: Image Conversions
            Text(
                text = "IMAGE CONVERSIONS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            ConversionCard(
                title = "Extract PDF Pages to PNG Images",
                subtitle = "Save all PDF pages as PNG image files",
                icon = Icons.Default.PictureAsPdf,
                isEnabled = true,
                onClick = { viewModel.navigateTo(ScreenRoute.EXTRACT_PAGES) },
                tag = "convert_pdf_to_images"
            )

            Spacer(modifier = Modifier.height(10.dp))

            ConversionCard(
                title = "Image Format Converter (PNG to JPG)",
                subtitle = "Convert PNG photos to compressed JPG images locally",
                icon = Icons.Default.Transform,
                isEnabled = true,
                onClick = { imagePickerLauncher.launch("image/*") },
                tag = "convert_image_format"
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Category: Office Formats
            Text(
                text = "OFFICE FORMATS (REQUIRES SERVER / CLOUD)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            ConversionCard(
                title = "Word (DOCX) to PDF",
                subtitle = "Requires online cloud or server parser",
                icon = Icons.Default.Description,
                isEnabled = false,
                onClick = {},
                tag = "convert_word_to_pdf"
            )

            Spacer(modifier = Modifier.height(10.dp))

            ConversionCard(
                title = "Excel (XLSX) to PDF",
                subtitle = "Requires spreadsheet rendering engine",
                icon = Icons.Default.TableChart,
                isEnabled = false,
                onClick = {},
                tag = "convert_excel_to_pdf"
            )

            Spacer(modifier = Modifier.height(10.dp))

            ConversionCard(
                title = "PowerPoint (PPTX) to PDF",
                subtitle = "Requires presentation slide parser",
                icon = Icons.Default.Slideshow,
                isEnabled = false,
                onClick = {},
                tag = "convert_pptx_to_pdf"
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ConversionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isEnabled: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    val containerColor = if (isEnabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val contentAlpha = if (isEnabled) 1.0f else 0.5f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isEnabled) { onClick() }
            .testTag(tag),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                )
            }
        }
    }
}
