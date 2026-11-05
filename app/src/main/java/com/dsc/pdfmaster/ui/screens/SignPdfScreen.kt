package com.dsc.pdfmaster.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsc.pdfmaster.data.model.NormalizedPoint
import com.dsc.pdfmaster.data.model.SignatureStroke
import com.dsc.pdfmaster.di.hiltViewModel
import com.dsc.pdfmaster.ui.PdfMasterViewModel
import com.dsc.pdfmaster.ui.viewmodel.SignPdfViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignPdfScreen(
    viewModel: PdfMasterViewModel,
    onBack: () -> Unit,
    signViewModel: SignPdfViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val selectedGlobalPdf by viewModel.selectedPdfFile.collectAsState()
    val isExplicitlySelected by viewModel.isPdfExplicitlySelected.collectAsState()
    val uiState by signViewModel.uiState.collectAsState()

    val drawnStrokes = remember { mutableStateListOf<SignatureStroke>() }
    val currentStrokePoints = remember { mutableStateListOf<NormalizedPoint>() }
    var signatureName by remember { mutableStateOf("Signature") }
    var outputFileName by remember(uiState.sourceFile) {
        val base = uiState.sourceFile?.nameWithoutExtension ?: "Document"
        mutableStateOf("${base}_signed.pdf")
    }

    LaunchedEffect(selectedGlobalPdf, isExplicitlySelected) {
        if (uiState.sourceFile == null && isExplicitlySelected && selectedGlobalPdf != null) {
            signViewModel.setSourcePdf(selectedGlobalPdf)
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { signViewModel.selectSourcePdfFromUri(it) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Sign PDF", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("sign_back_button")) {
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
            // 1. Source PDF Selector Card
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
                            text = if (uiState.pageCount > 0) "${uiState.pageCount} pages" else "Select a PDF to sign",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                        modifier = Modifier.testTag("sign_select_pdf_button")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Select PDF", fontSize = 12.sp)
                    }
                }
            }

            // 2. Signature Drawing Pad
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("DRAW NEW SIGNATURE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                drawnStrokes.clear()
                                currentStrokePoints.clear()
                            },
                            modifier = Modifier.testTag("clear_signature_canvas_button")
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear", fontSize = 12.sp)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                            .testTag("signature_drawing_canvas")
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        currentStrokePoints.clear()
                                        val nx = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        val ny = (offset.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        currentStrokePoints.add(NormalizedPoint(nx, ny))
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val pos = change.position
                                        val nx = (pos.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        val ny = (pos.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        currentStrokePoints.add(NormalizedPoint(nx, ny))
                                    },
                                    onDragEnd = {
                                        if (currentStrokePoints.isNotEmpty()) {
                                            drawnStrokes.add(SignatureStroke(currentStrokePoints.toList(), 6f))
                                            currentStrokePoints.clear()
                                        }
                                    }
                                )
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val drawStrokeList = drawnStrokes + if (currentStrokePoints.isNotEmpty()) {
                                listOf(SignatureStroke(currentStrokePoints.toList(), 6f))
                            } else {
                                emptyList()
                            }
                            drawStrokeList.forEach { stroke ->
                                if (stroke.points.size == 1) {
                                    val pt = stroke.points.first()
                                    drawCircle(
                                        color = Color.Black,
                                        radius = 3f,
                                        center = Offset(pt.x * size.width, pt.y * size.height)
                                    )
                                } else if (stroke.points.size > 1) {
                                    val path = Path().apply {
                                        val first = stroke.points.first()
                                        moveTo(first.x * size.width, first.y * size.height)
                                        for (i in 1 until stroke.points.size) {
                                            val pt = stroke.points[i]
                                            lineTo(pt.x * size.width, pt.y * size.height)
                                        }
                                    }
                                    drawPath(
                                        path = path,
                                        color = Color.Black,
                                        style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                    )
                                }
                            }
                        }
                        if (drawnStrokes.isEmpty() && currentStrokePoints.isEmpty()) {
                            Text(
                                text = "Sign here with your finger or stylus",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = signatureName,
                            onValueChange = { signatureName = it },
                            label = { Text("Signature Label") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                signViewModel.saveDrawnSignature(drawnStrokes.toList(), signatureName)
                                drawnStrokes.clear()
                            },
                            enabled = drawnStrokes.isNotEmpty(),
                            modifier = Modifier.testTag("save_signature_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save for Reuse")
                        }
                    }
                }
            }

            // 3. Saved Signatures for Reuse
            if (uiState.savedSignatures.isNotEmpty()) {
                Text(
                    text = "SAVED SIGNATURES (TAP TO SELECT)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(uiState.savedSignatures, key = { it.id }) { sig ->
                        val isSelected = uiState.selectedSignature?.id == sig.id
                        val sigBmp = remember(sig.file.absolutePath) {
                            BitmapFactory.decodeFile(sig.file.absolutePath)
                        }
                        Card(
                            modifier = Modifier
                                .width(150.dp)
                                .clickable { signViewModel.selectSignature(sig) }
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(10.dp)
                                ),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (sigBmp != null) {
                                    Image(
                                        bitmap = sigBmp.asImageBitmap(),
                                        contentDescription = sig.name,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = sig.name,
                                        fontSize = 11.sp,
                                        color = Color.Black,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { signViewModel.deleteSignature(sig) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete Signature",
                                            tint = Color.DarkGray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Page Placement, Drag & Resize Preview
            if (uiState.sourceFile != null && uiState.pageCount > 0) {
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
                            Text(
                                text = "PLACE & DRAG ON PAGE ${uiState.selectedPageIndex + 1} / ${uiState.pageCount}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Row {
                                IconButton(
                                    onClick = { signViewModel.selectPage(uiState.selectedPageIndex - 1) },
                                    enabled = uiState.selectedPageIndex > 0
                                ) {
                                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Page")
                                }
                                IconButton(
                                    onClick = { signViewModel.selectPage(uiState.selectedPageIndex + 1) },
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
                                .testTag("signature_page_placement_box")
                        ) {
                            val density = LocalDensity.current
                            val boxWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
                            val boxHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)

                            uiState.pagePreviewBitmap?.let { bmp ->
                                if (!bmp.isRecycled) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "PDF Page Preview",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.FillBounds
                                    )
                                }
                            }

                            uiState.selectedSignature?.let { selectedSig ->
                                val sigBmp = remember(selectedSig.file.absolutePath) {
                                    BitmapFactory.decodeFile(selectedSig.file.absolutePath)
                                }
                                val sigW = maxWidth * uiState.widthFraction
                                val sigH = maxHeight * uiState.heightFraction
                                val offsetX = (uiState.normalizedX * boxWidthPx).roundToInt()
                                val offsetY = (uiState.normalizedY * boxHeightPx).roundToInt()

                                Box(
                                    modifier = Modifier
                                        .offset { IntOffset(offsetX, offsetY) }
                                        .size(sigW, sigH)
                                        .border(1.5.dp, Color.Black, RoundedCornerShape(4.dp))
                                        .background(Color.White.copy(alpha = 0.35f))
                                        .pointerInput(boxWidthPx, boxHeightPx) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                val newNx = uiState.normalizedX + (dragAmount.x / boxWidthPx)
                                                val newNy = uiState.normalizedY + (dragAmount.y / boxHeightPx)
                                                signViewModel.updatePlacementPosition(newNx, newNy)
                                            }
                                        }
                                ) {
                                    if (sigBmp != null) {
                                        Image(
                                            bitmap = sigBmp.asImageBitmap(),
                                            contentDescription = "Placed Signature",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = "Signature Size: ${(uiState.widthFraction * 100).roundToInt()}% of page width",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Slider(
                            value = uiState.widthFraction,
                            onValueChange = { signViewModel.updatePlacementScale(it) },
                            valueRange = 0.15f..0.75f,
                            modifier = Modifier.testTag("signature_size_slider")
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = uiState.applyToAllPages,
                                onCheckedChange = { signViewModel.setApplyToAllPages(it) }
                            )
                            Text("Apply signature at this position on all pages", fontSize = 13.sp)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = outputFileName,
                onValueChange = { outputFileName = it },
                label = { Text("Output Signed PDF Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    signViewModel.flattenAndSaveSignedPdf(outputFileName) { signedFile ->
                        viewModel.openPdfInViewer(signedFile)
                    }
                },
                enabled = uiState.sourceFile != null && uiState.selectedSignature != null && !uiState.isProcessing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("flatten_signature_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Flatten Signature & Save PDF", fontWeight = FontWeight.Bold)
            }
        }
    }
}
