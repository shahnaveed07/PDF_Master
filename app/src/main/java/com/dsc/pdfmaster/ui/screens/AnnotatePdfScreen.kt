package com.dsc.pdfmaster.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsc.pdfmaster.data.model.AnnotationToolType
import com.dsc.pdfmaster.data.model.NormalizedPoint
import com.dsc.pdfmaster.data.model.PdfAnnotationItem
import com.dsc.pdfmaster.di.hiltViewModel
import com.dsc.pdfmaster.ui.PdfMasterViewModel
import com.dsc.pdfmaster.ui.viewmodel.AnnotatePdfViewModel
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotatePdfScreen(
    viewModel: PdfMasterViewModel,
    onBack: () -> Unit,
    annotateViewModel: AnnotatePdfViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val selectedGlobalPdf by viewModel.selectedPdfFile.collectAsState()
    val isExplicitlySelected by viewModel.isPdfExplicitlySelected.collectAsState()
    val uiState by annotateViewModel.uiState.collectAsState()

    val activeFreehandPoints = remember { mutableStateListOf<NormalizedPoint>() }
    var highlightDragStart by remember { mutableStateOf<NormalizedPoint?>(null) }
    var highlightDragEnd by remember { mutableStateOf<NormalizedPoint?>(null) }

    var pendingTextBoxCoord by remember { mutableStateOf<NormalizedPoint?>(null) }
    var textBoxInput by remember { mutableStateOf("") }

    var outputFileName by remember(uiState.sourceFile) {
        val base = uiState.sourceFile?.nameWithoutExtension ?: "Document"
        mutableStateOf("${base}_annotated.pdf")
    }

    LaunchedEffect(selectedGlobalPdf, isExplicitlySelected) {
        if (uiState.sourceFile == null && isExplicitlySelected && selectedGlobalPdf != null) {
            annotateViewModel.setSourcePdf(selectedGlobalPdf)
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { annotateViewModel.selectSourcePdfFromUri(it) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Annotate PDF", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("annotate_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(
                    onClick = { annotateViewModel.undo() },
                    enabled = uiState.canUndo,
                    modifier = Modifier.testTag("annotate_undo_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                }
                IconButton(
                    onClick = { annotateViewModel.redo() },
                    enabled = uiState.canRedo,
                    modifier = Modifier.testTag("annotate_redo_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                }
                IconButton(
                    onClick = { annotateViewModel.clearCurrentPageAnnotations() },
                    enabled = uiState.currentPageAnnotations.isNotEmpty(),
                    modifier = Modifier.testTag("annotate_clear_page_button")
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Page Annotations")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                            text = "${uiState.history.applied.size} annotation(s) applied",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                        modifier = Modifier.testTag("annotate_select_pdf_button")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Select PDF", fontSize = 12.sp)
                    }
                }
            }

            // Tool Selector Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.selectedTool == AnnotationToolType.HIGHLIGHT,
                    onClick = { annotateViewModel.setTool(AnnotationToolType.HIGHLIGHT) },
                    label = { Text("Highlight") },
                    leadingIcon = { Icon(Icons.Default.FormatColorFill, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("tool_highlight_chip")
                )
                FilterChip(
                    selected = uiState.selectedTool == AnnotationToolType.FREEHAND,
                    onClick = { annotateViewModel.setTool(AnnotationToolType.FREEHAND) },
                    label = { Text("Freehand") },
                    leadingIcon = { Icon(Icons.Default.Brush, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("tool_freehand_chip")
                )
                FilterChip(
                    selected = uiState.selectedTool == AnnotationToolType.TEXT_BOX,
                    onClick = { annotateViewModel.setTool(AnnotationToolType.TEXT_BOX) },
                    label = { Text("Text Box") },
                    leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("tool_textbox_chip")
                )
            }

            if (uiState.sourceFile != null && uiState.pageCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Page ${uiState.selectedPageIndex + 1} of ${uiState.pageCount} (${uiState.selectedTool.label})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Row {
                        IconButton(
                            onClick = { annotateViewModel.selectPage(uiState.selectedPageIndex - 1) },
                            enabled = uiState.selectedPageIndex > 0
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Page")
                        }
                        IconButton(
                            onClick = { annotateViewModel.selectPage(uiState.selectedPageIndex + 1) },
                            enabled = uiState.selectedPageIndex < uiState.pageCount - 1
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next Page")
                        }
                    }
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.707f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .testTag("annotate_page_canvas")
                        .pointerInput(uiState.selectedTool) {
                            if (uiState.selectedTool == AnnotationToolType.TEXT_BOX) {
                                detectTapGestures { offset ->
                                    val nx = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0.05f, 0.90f)
                                    val ny = (offset.y / size.height.coerceAtLeast(1)).coerceIn(0.05f, 0.90f)
                                    pendingTextBoxCoord = NormalizedPoint(nx, ny)
                                    textBoxInput = ""
                                }
                            } else {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val nx = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        val ny = (offset.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        val pt = NormalizedPoint(nx, ny)
                                        if (uiState.selectedTool == AnnotationToolType.HIGHLIGHT) {
                                            highlightDragStart = pt
                                            highlightDragEnd = pt
                                        } else {
                                            activeFreehandPoints.clear()
                                            activeFreehandPoints.add(pt)
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val pos = change.position
                                        val nx = (pos.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        val ny = (pos.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        val pt = NormalizedPoint(nx, ny)
                                        if (uiState.selectedTool == AnnotationToolType.HIGHLIGHT) {
                                            highlightDragEnd = pt
                                        } else {
                                            activeFreehandPoints.add(pt)
                                        }
                                    },
                                    onDragEnd = {
                                        if (uiState.selectedTool == AnnotationToolType.HIGHLIGHT) {
                                            val s = highlightDragStart
                                            val e = highlightDragEnd
                                            if (s != null && e != null) {
                                                annotateViewModel.addHighlight(s.x, s.y, e.x, e.y)
                                            }
                                            highlightDragStart = null
                                            highlightDragEnd = null
                                        } else if (activeFreehandPoints.isNotEmpty()) {
                                            annotateViewModel.addFreehandStroke(activeFreehandPoints.toList())
                                            activeFreehandPoints.clear()
                                        }
                                    }
                                )
                            }
                        }
                ) {
                    uiState.pagePreviewBitmap?.let { bmp ->
                        if (!bmp.isRecycled) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Annotated Page Preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.FillBounds
                            )
                        }
                    }

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        uiState.currentPageAnnotations.forEach { item ->
                            when (item) {
                                is PdfAnnotationItem.Highlight -> {
                                    val left = min(item.startX, item.endX) * size.width
                                    val top = min(item.startY, item.endY) * size.height
                                    val w = (max(item.startX, item.endX) - min(item.startX, item.endX)) * size.width
                                    val h = (max(item.startY, item.endY) - min(item.startY, item.endY)) * size.height
                                    drawRect(
                                        color = Color.DarkGray.copy(alpha = item.opacity),
                                        topLeft = Offset(left, top),
                                        size = Size(w.coerceAtLeast(14f), h.coerceAtLeast(14f))
                                    )
                                }
                                is PdfAnnotationItem.Freehand -> {
                                    if (item.points.size > 1) {
                                        val path = Path().apply {
                                            val f = item.points.first()
                                            moveTo(f.x * size.width, f.y * size.height)
                                            for (i in 1 until item.points.size) {
                                                val p = item.points[i]
                                                lineTo(p.x * size.width, p.y * size.height)
                                            }
                                        }
                                        drawPath(
                                            path = path,
                                            color = Color.Black,
                                            style = Stroke(width = 5f, cap = StrokeCap.Round)
                                        )
                                    }
                                }
                                is PdfAnnotationItem.TextBox -> {
                                    drawRect(
                                        color = Color.Black.copy(alpha = 0.85f),
                                        topLeft = Offset(item.normalizedX * size.width, item.normalizedY * size.height - 14f),
                                        size = Size((item.text.length * 8f).coerceAtLeast(36f), 20f),
                                        style = Stroke(width = 2f)
                                    )
                                }
                            }
                        }

                        // Active drag preview
                        val hs = highlightDragStart
                        val he = highlightDragEnd
                        if (hs != null && he != null) {
                            drawRect(
                                color = Color.DarkGray.copy(alpha = 0.35f),
                                topLeft = Offset(min(hs.x, he.x) * size.width, min(hs.y, he.y) * size.height),
                                size = Size(
                                    ((max(hs.x, he.x) - min(hs.x, he.x)) * size.width).coerceAtLeast(10f),
                                    ((max(hs.y, he.y) - min(hs.y, he.y)) * size.height).coerceAtLeast(10f)
                                )
                            )
                        }

                        if (activeFreehandPoints.size > 1) {
                            val path = Path().apply {
                                val f = activeFreehandPoints.first()
                                moveTo(f.x * size.width, f.y * size.height)
                                for (i in 1 until activeFreehandPoints.size) {
                                    val p = activeFreehandPoints[i]
                                    lineTo(p.x * size.width, p.y * size.height)
                                }
                            }
                            drawPath(path, Color.Black, style = Stroke(width = 5f, cap = StrokeCap.Round))
                        }
                    }
                }
            }

            OutlinedTextField(
                value = outputFileName,
                onValueChange = { outputFileName = it },
                label = { Text("Output Annotated PDF Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    annotateViewModel.saveAnnotatedPdf(outputFileName) { savedFile ->
                        viewModel.openPdfInViewer(savedFile)
                    }
                },
                enabled = uiState.sourceFile != null && uiState.history.applied.isNotEmpty() && !uiState.isProcessing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_annotated_pdf_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Annotations into PDF", fontWeight = FontWeight.Bold)
            }
        }
    }

    pendingTextBoxCoord?.let { coord ->
        AlertDialog(
            onDismissRequest = { pendingTextBoxCoord = null },
            title = { Text("Add Text Annotation", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = textBoxInput,
                    onValueChange = { textBoxInput = it },
                    label = { Text("Enter annotation text") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("annotation_textbox_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (textBoxInput.isNotBlank()) {
                            annotateViewModel.addTextBox(textBoxInput, coord.x, coord.y)
                        }
                        pendingTextBoxCoord = null
                    },
                    modifier = Modifier.testTag("confirm_textbox_button")
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingTextBoxCoord = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
