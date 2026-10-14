package com.example.ui

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
import com.example.data.database.PdfDatabase
import com.example.data.repository.PdfRepository
import com.example.ui.components.PdfBottomNavigationBar
import com.example.ui.components.PdfTopAppBar
import com.example.ui.components.ProcessingDialog
import com.example.ui.screens.CompressPdfScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ImagesToPdfScreen
import com.example.ui.screens.ManagePagesScreen
import com.example.ui.screens.MergePdfScreen
import com.example.ui.screens.PasswordProtectScreen
import com.example.ui.screens.PdfViewerScreen
import com.example.ui.screens.RecentFilesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplitPdfScreen
import com.example.ui.theme.MyApplicationTheme

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

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (msg != null) {
            scope.launch {
                snackbarHostState.showSnackbar(msg)
            }
            viewModel.clearSnackbarMessage()
        }
    }

    MyApplicationTheme(darkTheme = isDarkMode) {
        Scaffold(
            topBar = {
                if (currentRoute == ScreenRoute.HOME || currentRoute == ScreenRoute.RECENT_FILES) {
                    PdfTopAppBar(
                        title = if (currentRoute == ScreenRoute.HOME) "PDF Master Studio" else "My Documents",
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
                            onBack = { viewModel.navigateTo(ScreenRoute.HOME) }
                        )
                        ScreenRoute.MERGE_PDF -> MergePdfScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.navigateTo(ScreenRoute.HOME) }
                        )
                        ScreenRoute.SPLIT_PDF -> SplitPdfScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.navigateTo(ScreenRoute.HOME) }
                        )
                        ScreenRoute.COMPRESS_PDF -> CompressPdfScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.navigateTo(ScreenRoute.HOME) }
                        )
                        ScreenRoute.IMAGES_TO_PDF -> ImagesToPdfScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.navigateTo(ScreenRoute.HOME) }
                        )
                        ScreenRoute.MANAGE_PAGES -> ManagePagesScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.navigateTo(ScreenRoute.HOME) }
                        )
                        ScreenRoute.PASSWORD_PROTECT -> PasswordProtectScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.navigateTo(ScreenRoute.HOME) }
                        )
                        ScreenRoute.SETTINGS -> SettingsScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.navigateTo(ScreenRoute.HOME) }
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
