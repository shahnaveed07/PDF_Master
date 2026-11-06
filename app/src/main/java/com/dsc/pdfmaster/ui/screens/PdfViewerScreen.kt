package com.dsc.pdfmaster.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.dsc.pdfmaster.R
import com.dsc.pdfmaster.pdfengine.PdfAdvancedToolsEngine
import com.dsc.pdfmaster.pdfengine.PdfEngine
import com.dsc.pdfmaster.ui.PdfMasterViewModel
import com.dsc.pdfmaster.ui.ScreenRoute
import com.dsc.pdfmaster.ui.theme.Dimens
import com.dsc.pdfmaster.ui.util.FormFactorPreviews
import com.dsc.pdfmaster.ui.util.rememberWindowInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    viewModel: PdfMasterViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val windowInfo = rememberWindowInfo()
    val currentPdfFile by viewModel.selectedPdfFile.collectAsState()
    val isNightMode by viewModel.viewerNightMode.collectAsState()
    val searchMatches by viewModel.viewerSearchMatches.collectAsState()
    val bookmarks by viewModel.viewerBookmarks.collectAsState()
    val scope = rememberCoroutineScope()

    var pageCount by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var isPasswordProtected by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var verifiedPassword by remember { mutableStateOf<String?>(null) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showFileInfoDialog by remember { mutableStateOf(false) }
    var showBookmarksDialog by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQueryInput by remember { mutableStateOf("") }
    var activeSearchMatchIndex by remember { mutableIntStateOf(0) }
    var newBookmarkTitle by remember { mutableStateOf("") }
    var renameInputText by remember { mutableStateOf("") }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val displayTargetWidth = remember(configuration.screenWidthDp, density.density) {
        with(density) { configuration.screenWidthDp.dp.roundToPx() }.coerceIn(360, 1080)
    }
    val lazyListState = rememberLazyListState()

    val nightInvertColorFilter = remember(isNightMode) {
        if (isNightMode) {
            ColorFilter.colorMatrix(ColorMatrix(PdfAdvancedToolsEngine.createNightInvertColorMatrix()))
        } else {
            null
        }
    }

    LaunchedEffect(currentPdfFile, verifiedPassword) {
        val file = currentPdfFile
        if (file != null && file.exists()) {
            isLoading = true
            withContext(Dispatchers.IO) {
                try {
                    val isEncrypted = PdfEngine.checkPasswordProtected(context, file)
                    isPasswordProtected = isEncrypted && verifiedPassword == null

                    if (!isPasswordProtected) {
                        val meta = PdfEngine.getPdfMetadata(context, android.net.Uri.fromFile(file), verifiedPassword)
                        val loadedCount = meta?.pageCount ?: 0
                        pageCount = loadedCount
                        val savedLastPage = viewModel.getLastReadPage(file.absolutePath)
                        viewModel.loadViewerBookmarks(file, verifiedPassword)
                        withContext(Dispatchers.Main) {
                            if (loadedCount > 0 && savedLastPage in 1 until loadedCount) {
                                lazyListState.scrollToItem(savedLastPage)
                            }
                        }
                    }
                } catch (_: Exception) {
                    pageCount = 0
                } finally {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                    }
                }
            }
        } else {
            pageCount = 0
            isLoading = false
        }
    }

    // Persist last-read page as the user scrolls
    LaunchedEffect(lazyListState, currentPdfFile, pageCount) {
        val file = currentPdfFile ?: return@LaunchedEffect
        snapshotFlow { lazyListState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { pageIdx ->
                if (pageCount > 0 && pageIdx in 0 until pageCount) {
                    viewModel.updateLastReadPage(file.absolutePath, pageIdx)
                }
            }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Custom Viewer Header
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = currentPdfFile?.name ?: "PDF Reader",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    if (pageCount > 0) {
                        Text(
                            text = "$pageCount Total Pages • ${if (isNightMode) "Night Invert" else "100% Crisp Render"}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("viewer_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                if (!isPasswordProtected && pageCount > 0) {
                    IconButton(
                        onClick = {
                            showSearchBar = !showSearchBar
                            if (!showSearchBar) {
                                searchQueryInput = ""
                                viewModel.clearViewerSearch()
                            }
                        },
                        modifier = Modifier.testTag("viewer_search_toggle_button")
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search in PDF")
                    }

                    IconButton(
                        onClick = {
                            currentPdfFile?.let { viewModel.loadViewerBookmarks(it, verifiedPassword) }
                            newBookmarkTitle = "Page ${lazyListState.firstVisibleItemIndex + 1}"
                            showBookmarksDialog = true
                        },
                        modifier = Modifier.testTag("viewer_bookmarks_button")
                    ) {
                        Icon(Icons.Default.Bookmark, contentDescription = "Bookmarks & Outline")
                    }

                    IconButton(
                        onClick = { viewModel.toggleViewerNightMode() },
                        modifier = Modifier.testTag("viewer_night_mode_button")
                    ) {
                        Icon(
                            imageVector = if (isNightMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Night Invert Mode"
                        )
                    }
                }

                IconButton(
                    onClick = { showOverflowMenu = true },
                    modifier = Modifier.testTag("viewer_overflow_button")
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }

                DropdownMenu(
                    expanded = showOverflowMenu,
                    onDismissRequest = { showOverflowMenu = false }
                ) {
                    val currentPageIdx = lazyListState.firstVisibleItemIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))

                    if (!isPasswordProtected && pageCount > 0 && currentPdfFile != null) {
                        DropdownMenuItem(
                            text = { Text("Extract Current Page (Page ${currentPageIdx + 1})") },
                            leadingIcon = { Icon(Icons.Default.ContentCut, contentDescription = null) },
                            modifier = Modifier.testTag("viewer_menu_extract_current_page"),
                            onClick = {
                                showOverflowMenu = false
                                val file = currentPdfFile!!
                                val outName = "${file.nameWithoutExtension}_page_${currentPageIdx + 1}.pdf"
                                viewModel.executeExtractPages(
                                    file = file,
                                    pageIndices = listOf(currentPageIdx),
                                    outputName = outName,
                                    password = verifiedPassword
                                )
                            }
                        )

                        if (pageCount > 1) {
                            DropdownMenuItem(
                                text = { Text("Delete Current Page (Page ${currentPageIdx + 1})") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                modifier = Modifier.testTag("viewer_menu_delete_current_page"),
                                onClick = {
                                    showOverflowMenu = false
                                    val file = currentPdfFile!!
                                    viewModel.deleteCurrentPageFromViewer(
                                        file = file,
                                        pageIndexToDelete = currentPageIdx,
                                        password = verifiedPassword
                                    )
                                }
                            )
                        }

                        DropdownMenuItem(
                            text = { Text("Sign PDF") },
                            leadingIcon = { Icon(Icons.Default.Draw, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                viewModel.navigateTo(ScreenRoute.SIGN_PDF)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Annotate PDF") },
                            leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                viewModel.navigateTo(ScreenRoute.ANNOTATE_PDF)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Watermark & Page Numbers") },
                            leadingIcon = { Icon(Icons.Default.FormatColorText, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                viewModel.navigateTo(ScreenRoute.WATERMARK_PDF)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("OCR Searchable PDF") },
                            leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                viewModel.navigateTo(ScreenRoute.OCR_PDF)
                            }
                        )
                    }

                    if (isPasswordProtected || verifiedPassword != null) {
                        DropdownMenuItem(
                            text = { Text("Remove Password") },
                            leadingIcon = { Icon(Icons.Default.LockOpen, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                viewModel.navigateTo(ScreenRoute.REMOVE_PASSWORD)
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Protect PDF") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                viewModel.navigateTo(ScreenRoute.PASSWORD_PROTECT)
                            }
                        )
                    }

                    DropdownMenuItem(
                        text = { Text("Compress PDF") },
                        leadingIcon = { Icon(Icons.Default.Compress, contentDescription = null) },
                        onClick = {
                            showOverflowMenu = false
                            viewModel.navigateTo(ScreenRoute.COMPRESS_PDF)
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            showOverflowMenu = false
                            currentPdfFile?.let { sharePdfFile(context, it, context.getString(R.string.share_pdf_chooser)) }
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Print") },
                        leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) },
                        onClick = {
                            showOverflowMenu = false
                            currentPdfFile?.let { printPdfFile(context, it) }
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            showOverflowMenu = false
                            renameInputText = currentPdfFile?.name?.removeSuffix(".pdf") ?: ""
                            showRenameDialog = true
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("File Info") },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                        onClick = {
                            showOverflowMenu = false
                            showFileInfoDialog = true
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Manage Pages") },
                        leadingIcon = { Icon(Icons.Default.SwapVert, contentDescription = null) },
                        onClick = {
                            showOverflowMenu = false
                            currentPdfFile?.let { viewModel.loadPagesForPdf(it) }
                            viewModel.navigateTo(ScreenRoute.MANAGE_PAGES)
                        }
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        // In-Document Search Bar
        if (showSearchBar && currentPdfFile != null && !isPasswordProtected) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("viewer_search_bar")
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQueryInput,
                            onValueChange = { q ->
                                searchQueryInput = q
                                activeSearchMatchIndex = 0
                                currentPdfFile?.let { file ->
                                    viewModel.searchInPdf(file, q, verifiedPassword)
                                }
                            },
                            placeholder = { Text("Search text in document...", fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("viewer_search_input")
                        )

                        if (searchMatches.isNotEmpty()) {
                            Text(
                                text = "${activeSearchMatchIndex + 1}/${searchMatches.size}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("viewer_search_match_counter")
                            )
                            IconButton(
                                onClick = {
                                    if (searchMatches.isNotEmpty()) {
                                        activeSearchMatchIndex =
                                            (activeSearchMatchIndex - 1 + searchMatches.size) % searchMatches.size
                                        val targetPage = searchMatches[activeSearchMatchIndex].pageIndex
                                        scope.launch {
                                            lazyListState.animateScrollToItem(targetPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0)))
                                        }
                                    }
                                },
                                modifier = Modifier.testTag("viewer_search_prev_button")
                            ) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Match")
                            }
                            IconButton(
                                onClick = {
                                    if (searchMatches.isNotEmpty()) {
                                        activeSearchMatchIndex = (activeSearchMatchIndex + 1) % searchMatches.size
                                        val targetPage = searchMatches[activeSearchMatchIndex].pageIndex
                                        scope.launch {
                                            lazyListState.animateScrollToItem(targetPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0)))
                                        }
                                    }
                                },
                                modifier = Modifier.testTag("viewer_search_next_button")
                            ) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Next Match")
                            }
                        }

                        IconButton(
                            onClick = {
                                showSearchBar = false
                                searchQueryInput = ""
                                viewModel.clearViewerSearch()
                            }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close Search")
                        }
                    }

                    if (searchMatches.isNotEmpty() && activeSearchMatchIndex in searchMatches.indices) {
                        val activeMatch = searchMatches[activeSearchMatchIndex]
                        LaunchedEffect(activeSearchMatchIndex, searchMatches.size) {
                            val targetPage = activeMatch.pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                            lazyListState.scrollToItem(targetPage)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Page ${activeMatch.pageIndex + 1}: ${activeMatch.snippet}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    } else if (searchQueryInput.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "No matches found for \"$searchQueryInput\"",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (currentPdfFile == null || !currentPdfFile!!.exists()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No PDF File Loaded",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Select a PDF from Dashboard or Recent Files to read.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = { viewModel.navigateTo(ScreenRoute.HOME) }) {
                        Text("Go to Dashboard")
                    }
                }
            }
        } else if (isPasswordProtected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Password Protected PDF",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "This document is encrypted. Enter the password to unlock.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = {
                                passwordInput = it
                                passwordError = null
                            },
                            label = { Text("Enter Password") },
                            singleLine = true,
                            isError = passwordError != null,
                            supportingText = {
                                passwordError?.let { err ->
                                    Text(text = err, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                                }
                            },
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle Visibility"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("viewer_password_field")
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (passwordInput.isNotBlank() && currentPdfFile != null) {
                                    scope.launch(Dispatchers.IO) {
                                        val isValid = PdfEngine.verifyPdfPassword(context, currentPdfFile!!, passwordInput)
                                        withContext(Dispatchers.Main) {
                                            if (isValid) {
                                                verifiedPassword = passwordInput
                                                passwordError = null
                                            } else {
                                                passwordError = "Incorrect password. Please try again."
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = passwordInput.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("unlock_pdf_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.LockOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Unlock Document", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else if (isLoading && pageCount == 0) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Rendering pages in HD quality...", fontSize = 13.sp)
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .background(if (isNightMode) Color(0xFF0A0A0A) else MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    if (windowInfo.isExpanded && pageCount > 0) {
                        // Collapsible Thumbnail Sidebar on Expanded Width Screens
                        Surface(
                            modifier = Modifier
                                .width(130.dp)
                                .fillMaxHeight(),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 4.dp
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(count = pageCount, key = { "thumb_$it" }) { idx ->
                                    val file = currentPdfFile
                                    val fileVersionKey = file?.let { "${it.absolutePath}:${it.lastModified()}:${it.length()}" }
                                    val thumbBmp by produceState<Bitmap?>(
                                        initialValue = file?.let { viewModel.bitmapCache.get(it, idx, 160) },
                                        key1 = fileVersionKey,
                                        key2 = idx
                                    ) {
                                        if (value == null && file != null) {
                                            value = withContext(Dispatchers.IO) {
                                                PdfEngine.renderPageBitmap(
                                                    context = context,
                                                    pdfFile = file,
                                                    pageIndex = idx,
                                                    targetWidth = 160,
                                                    password = verifiedPassword,
                                                    useCache = true,
                                                    bitmapCache = viewModel.bitmapCache
                                                )
                                            }
                                        }
                                    }

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                scope.launch { lazyListState.scrollToItem(idx) }
                                            },
                                        shape = RoundedCornerShape(Dimens.CornerSmall),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (lazyListState.firstVisibleItemIndex == idx) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            }
                                        )
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(4.dp)
                                        ) {
                                            val tb = thumbBmp
                                            if (tb != null && !tb.isRecycled) {
                                                Image(
                                                    bitmap = tb.asImageBitmap(),
                                                    contentDescription = "Thumbnail ${idx + 1}",
                                                    colorFilter = nightInvertColorFilter,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(110.dp)
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(110.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                                }
                                            }
                                            Text(
                                                text = "${idx + 1}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = Dimens.MaxContentWidth)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            items(count = pageCount, key = { index -> index }) { index ->
                                val file = currentPdfFile
                                val fileVersionKey = file?.let { "${it.absolutePath}:${it.lastModified()}:${it.length()}" }
                                val bitmap by produceState<Bitmap?>(
                                    initialValue = file?.let {
                                        viewModel.bitmapCache.get(it, index, displayTargetWidth)
                                    },
                                    key1 = fileVersionKey,
                                    key2 = index,
                                    key3 = verifiedPassword
                                ) {
                                    if (value == null && file != null) {
                                        value = withContext(Dispatchers.IO) {
                                            PdfEngine.renderPageBitmap(
                                                context = context,
                                                pdfFile = file,
                                                pageIndex = index,
                                                targetWidth = displayTargetWidth,
                                                password = verifiedPassword,
                                                useCache = true,
                                                bitmapCache = viewModel.bitmapCache
                                            )
                                        }
                                    }
                                }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("pdf_page_card_$index"),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isNightMode) Color(0xFF141414) else Color.White
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        val bmp = bitmap
                                        if (bmp != null && !bmp.isRecycled) {
                                            Image(
                                                bitmap = bmp.asImageBitmap(),
                                                contentDescription = "PDF Page ${index + 1}",
                                                colorFilter = nightInvertColorFilter,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(300.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                            }
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(if (isNightMode) Color(0xFF1F1F1F) else Color(0xFFF0F0F0))
                                                .padding(vertical = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Page ${index + 1} of $pageCount",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isNightMode) Color.LightGray else Color.DarkGray
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Page Indicator Float Badge
                        val firstVisibleItem = lazyListState.firstVisibleItemIndex
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White,
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp)
                        ) {
                            Text(
                                text = "${firstVisibleItem + 1} / $pageCount",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Bookmarks & Document Outline Dialog
    if (showBookmarksDialog && currentPdfFile != null) {
        val currentPageIndex = lazyListState.firstVisibleItemIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        AlertDialog(
            onDismissRequest = { showBookmarksDialog = false },
            title = { Text("Bookmarks & Outline", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newBookmarkTitle,
                            onValueChange = { newBookmarkTitle = it },
                            label = { Text("Bookmark Page ${currentPageIndex + 1}") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("bookmark_title_input")
                        )
                        IconButton(
                            onClick = {
                                val title = newBookmarkTitle.ifBlank { "Page ${currentPageIndex + 1}" }
                                viewModel.addViewerBookmark(
                                    file = currentPdfFile!!,
                                    title = title,
                                    pageIndex = currentPageIndex,
                                    password = verifiedPassword
                                )
                            },
                            modifier = Modifier.testTag("add_bookmark_button")
                        ) {
                            Icon(Icons.Default.BookmarkAdd, contentDescription = "Save Bookmark")
                        }
                    }

                    if (bookmarks.isEmpty()) {
                        Text(
                            text = "No bookmarks or outline entries found. Add one above for the current page.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.height(220.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(bookmarks) { bm ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showBookmarksDialog = false
                                            scope.launch {
                                                lazyListState.scrollToItem(
                                                    bm.pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                                                )
                                            }
                                        }
                                        .testTag("bookmark_item_${bm.pageIndex}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = bm.title,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = "Page ${bm.pageIndex + 1}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                viewModel.removeViewerBookmark(
                                                    file = currentPdfFile!!,
                                                    pageIndex = bm.pageIndex,
                                                    password = verifiedPassword
                                                )
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Remove Bookmark",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showBookmarksDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Rename Dialog
    if (showRenameDialog && currentPdfFile != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename PDF File", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter a new file name:", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = renameInputText,
                        onValueChange = { renameInputText = it },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rename_input_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInputText.isNotBlank()) {
                            viewModel.renamePdf(currentPdfFile!!, renameInputText.trim())
                            showRenameDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_rename_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // File Info Dialog
    if (showFileInfoDialog && currentPdfFile != null) {
        val file = currentPdfFile!!
        val formattedDate = remember(file.lastModified()) {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(file.lastModified()))
        }
        AlertDialog(
            onDismissRequest = { showFileInfoDialog = false },
            title = { Text("File Information", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Name: ${file.name}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Size: ${PdfEngine.formatFileSize(file.length())}", fontSize = 13.sp)
                    Text("Pages: $pageCount", fontSize = 13.sp)
                    Text("Path: ${file.absolutePath}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Modified: $formattedDate", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(onClick = { showFileInfoDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@FormFactorPreviews
@Composable
private fun PdfViewerScreenPreview() {
    com.dsc.pdfmaster.ui.theme.PdfMasterTheme {
        PdfViewerScreen(
            viewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
            onBack = {}
        )
    }
}

private fun sharePdfFile(
    context: Context,
    file: File,
    chooserTitle: String
) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun printPdfFile(
    context: Context,
    file: File
) {
    try {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? android.print.PrintManager
        val printAdapter = object : android.print.PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: android.print.PrintAttributes?,
                newAttributes: android.print.PrintAttributes?,
                cancellationSignal: android.os.CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: android.os.Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = android.print.PrintDocumentInfo.Builder(file.name)
                    .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out android.print.PageRange>?,
                destination: android.os.ParcelFileDescriptor?,
                cancellationSignal: android.os.CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                try {
                    file.inputStream().use { input ->
                        android.os.ParcelFileDescriptor.AutoCloseOutputStream(destination).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.message)
                }
            }
        }
        printManager?.print("PDF_Master_Print_${file.name}", printAdapter, null)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
