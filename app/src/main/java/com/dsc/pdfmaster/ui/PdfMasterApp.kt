package com.dsc.pdfmaster.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.dsc.pdfmaster.data.database.PdfDatabase
import com.dsc.pdfmaster.data.repository.PdfRepository
import com.dsc.pdfmaster.ui.components.PdfBottomNavigationBar
import com.dsc.pdfmaster.ui.components.PdfTopAppBar
import com.dsc.pdfmaster.ui.components.ProcessingDialog
import com.dsc.pdfmaster.ui.screens.CompressPdfScreen
import com.dsc.pdfmaster.ui.screens.ExtractPdfScreen
import com.dsc.pdfmaster.ui.screens.HomeScreen
import com.dsc.pdfmaster.ui.screens.ImagesToPdfScreen
import com.dsc.pdfmaster.ui.screens.ManagePagesScreen
import com.dsc.pdfmaster.ui.screens.MergePdfScreen
import com.dsc.pdfmaster.ui.screens.PasswordProtectScreen
import com.dsc.pdfmaster.ui.screens.PdfViewerScreen
import com.dsc.pdfmaster.ui.screens.RecentFilesScreen
import com.dsc.pdfmaster.ui.screens.SettingsScreen
import com.dsc.pdfmaster.ui.screens.SplitPdfScreen
import com.dsc.pdfmaster.ui.theme.PdfMasterTheme

@Composable
fun PdfMasterApp(
    viewModel: PdfMasterViewModel
) {
    val currentRoute by viewModel.currentRoute.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val processingMessage by viewModel.processingMessage.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = currentRoute != ScreenRoute.HOME) {
        viewModel.navigateBack()
    }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (msg != null) {
            scope.launch {
                snackbarHostState.showSnackbar(msg)
            }
            viewModel.clearSnackbarMessage()
        }
    }

    PdfMasterTheme(darkTheme = isDarkMode) {
        Scaffold(
            topBar = {
                if (currentRoute == ScreenRoute.HOME || currentRoute == ScreenRoute.RECENT_FILES) {
                    PdfTopAppBar(
                        title = if (currentRoute == ScreenRoute.HOME) "PDF Master" else "Recent Files",
                        searchQuery = searchQuery,
                        onSearchQueryChange = { viewModel.setSearchQuery(it) },
                        isDarkMode = isDarkMode,
                        onToggleDarkMode = { viewModel.toggleDarkMode(it) }
                    )
                }
            },
            bottomBar = {
                if (currentRoute in listOf(ScreenRoute.HOME, ScreenRoute.RECENT_FILES, ScreenRoute.PDF_VIEWER, ScreenRoute.SETTINGS)) {
                    PdfBottomNavigationBar(
                        currentRoute = currentRoute,
                        onNavigate = { route -> viewModel.navigateTo(route) }
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Crossfade(targetState = currentRoute, label = "screen_transition") { route ->
                    when (route) {
                        ScreenRoute.HOME -> HomeScreen(
                            viewModel = viewModel,
                            onOpenPdfViewer = { file -> viewModel.openPdfInViewer(file) }
                        )
                        ScreenRoute.RECENT_FILES -> RecentFilesScreen(
                            viewModel = viewModel,
                            onOpenPdfViewer = { file -> viewModel.openPdfInViewer(file) }
                        )
                        ScreenRoute.PDF_VIEWER -> PdfViewerScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.navigateBack() }
                        )
                        ScreenRoute.EXTRACT_PAGES -> ExtractPdfScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.navigateBack() }
                        )
                        ScreenRoute.MERGE_PDF -> MergePdfScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.navigateBack() }
                        )
                        ScreenRoute.SPLIT_PDF -> SplitPdfScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.navigateBack() }
                        )
                        ScreenRoute.COMPRESS_PDF -> CompressPdfScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.navigateBack() }
                        )
                        ScreenRoute.IMAGES_TO_PDF -> ImagesToPdfScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.navigateBack() }
                        )
                        ScreenRoute.MANAGE_PAGES -> ManagePagesScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.navigateBack() }
                        )
                        ScreenRoute.PASSWORD_PROTECT -> PasswordProtectScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.navigateBack() }
                        )
                        ScreenRoute.SETTINGS -> SettingsScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.navigateBack() }
                        )
                    }
                }

                if (isProcessing && currentRoute != ScreenRoute.HOME) {
                    ProcessingDialog(
                        message = processingMessage,
                        onDismissRequest = { viewModel.resetProcessingState() }
                    )
                }
            }
        }
    }
}
