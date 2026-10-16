package com.dsc.pdfmaster.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsc.pdfmaster.pdfengine.PdfEngine
import com.dsc.pdfmaster.ui.PdfMasterViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitPdfScreen(
    viewModel: PdfMasterViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentPdf by viewModel.selectedPdfFile.collectAsState()

    var totalPages by remember { mutableIntStateOf(1) }
    var pageRangeInput by remember { mutableStateOf("1") }
    var outputNameInput by remember { mutableStateOf("Split_${System.currentTimeMillis() / 1000}.pdf") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importAndSelectPdf(it, openViewer = false)
        }
    }

    LaunchedEffect(currentPdf) {
        val file = currentPdf
        if (file != null && file.exists()) {
            withContext(Dispatchers.IO) {
                val meta = PdfEngine.getPdfMetadata(context, android.net.Uri.fromFile(file))
                totalPages = meta?.pageCount ?: 1
                pageRangeInput = if (totalPages > 1) "1-$totalPages" else "1"
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Split & Extract Pages", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("split_back_button")) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Document Selection Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentPdf?.name ?: "No PDF Selected",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = if (currentPdf != null) "Total Pages: $totalPages" else "Pick a file to split",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                        modifier = Modifier.testTag("split_select_file_button")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Change", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "SPLIT CONFIGURATION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = pageRangeInput,
                onValueChange = { pageRangeInput = it },
                label = { Text("Page Range (e.g. 1-3, 5, 7-10)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("page_range_input_field")
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = outputNameInput,
                onValueChange = { outputNameInput = it },
                label = { Text("Output PDF File Name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("split_output_name_field")
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    val indices = parsePageRange(pageRangeInput, totalPages)
                    if (currentPdf != null) {
                        viewModel.executeSplitPdf(currentPdf!!, indices, outputNameInput.trim())
                    }
                },
                enabled = currentPdf != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("execute_split_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.CallSplit, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Extract Selected Pages", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

// Helper to parse page range input string like "1-3, 5, 7-10" into 0-indexed page integers
private fun parsePageRange(rangeStr: String, maxPages: Int): List<Int> {
    val result = mutableSetOf<Int>()
    try {
        val parts = rangeStr.split(",")
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.contains("-")) {
                val bounds = trimmed.split("-")
                val start = bounds[0].toIntOrNull() ?: 1
                val end = bounds[1].toIntOrNull() ?: maxPages
                for (p in start..end) {
                    if (p in 1..maxPages) {
                        result.add(p - 1)
                    }
                }
            } else {
                val p = trimmed.toIntOrNull()
                if (p != null && p in 1..maxPages) {
                    result.add(p - 1)
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return result.sorted()
}
