package com.dsc.pdfmaster.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dsc.pdfmaster.R
import com.dsc.pdfmaster.di.hiltViewModel
import com.dsc.pdfmaster.ui.adaptive.rememberWindowSizeClass
import com.dsc.pdfmaster.ui.components.PdfBottomNavigationBar
import com.dsc.pdfmaster.ui.components.PdfNavigationDrawerContent
import com.dsc.pdfmaster.ui.components.PdfNavigationRail
import com.dsc.pdfmaster.ui.components.PdfTopAppBar
import com.dsc.pdfmaster.ui.components.ProcessingDialog
import com.dsc.pdfmaster.ui.navigation.AnnotatePdfRoute
import com.dsc.pdfmaster.ui.navigation.CompressPdfRoute
import com.dsc.pdfmaster.ui.navigation.ConvertPdfRoute
import com.dsc.pdfmaster.ui.navigation.CreatePdfRoute
import com.dsc.pdfmaster.ui.navigation.DocumentScannerRoute
import com.dsc.pdfmaster.ui.navigation.ExtractPagesRoute
import com.dsc.pdfmaster.ui.navigation.HomeRoute
import com.dsc.pdfmaster.ui.navigation.ImagesToPdfRoute
import com.dsc.pdfmaster.ui.navigation.ManagePagesRoute
import com.dsc.pdfmaster.ui.navigation.MergePdfRoute
import com.dsc.pdfmaster.ui.navigation.OcrPdfRoute
import com.dsc.pdfmaster.ui.navigation.PasswordProtectRoute
import com.dsc.pdfmaster.ui.navigation.PdfViewerRoute
import com.dsc.pdfmaster.ui.navigation.RecentFilesRoute
import com.dsc.pdfmaster.ui.navigation.RemovePasswordRoute
import com.dsc.pdfmaster.ui.navigation.SettingsRoute
import com.dsc.pdfmaster.ui.navigation.SignPdfRoute
import com.dsc.pdfmaster.ui.navigation.SplitPdfRoute
import com.dsc.pdfmaster.ui.navigation.WatermarkPdfRoute
import com.dsc.pdfmaster.ui.navigation.toAppRoute
import com.dsc.pdfmaster.ui.navigation.toScreenRoute
import com.dsc.pdfmaster.ui.screens.AnnotatePdfScreen
import com.dsc.pdfmaster.ui.screens.CompressPdfScreen
import com.dsc.pdfmaster.ui.screens.ConvertPdfScreen
import com.dsc.pdfmaster.ui.screens.CreatePdfScreen
import com.dsc.pdfmaster.ui.screens.DocumentScannerScreen
import com.dsc.pdfmaster.ui.screens.ExtractPdfScreen
import com.dsc.pdfmaster.ui.screens.HomeScreen
import com.dsc.pdfmaster.ui.screens.ImagesToPdfScreen
import com.dsc.pdfmaster.ui.screens.ManagePagesScreen
import com.dsc.pdfmaster.ui.screens.MergePdfScreen
import com.dsc.pdfmaster.ui.screens.OcrPdfScreen
import com.dsc.pdfmaster.ui.screens.OnboardingScreen
import com.dsc.pdfmaster.ui.screens.PasswordProtectScreen
import com.dsc.pdfmaster.ui.screens.PdfViewerScreen
import com.dsc.pdfmaster.ui.screens.RecentFilesScreen
import com.dsc.pdfmaster.ui.screens.RemovePasswordScreen
import com.dsc.pdfmaster.ui.screens.SettingsScreen
import com.dsc.pdfmaster.ui.screens.SignPdfScreen
import com.dsc.pdfmaster.ui.screens.SplitPdfScreen
import com.dsc.pdfmaster.ui.screens.WatermarkPdfScreen
import com.dsc.pdfmaster.ui.theme.PdfMasterTheme
import com.dsc.pdfmaster.ui.util.rememberWindowInfo
import kotlinx.coroutines.launch

@Composable
fun PdfMasterApp(
    viewModel: PdfMasterViewModel = hiltViewModel(),
    incomingIntent: android.content.Intent? = null,
    onIntentConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val destinationRoute = navBackStackEntry?.destination.toScreenRoute()
    val windowSizeClass = rememberWindowSizeClass()
    val windowInfo = rememberWindowInfo()

    val currentRoute by viewModel.currentRoute.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val isDynamicColorEnabled by viewModel.isDynamicColorEnabled.collectAsState()
    val showOnboarding by viewModel.showOnboarding.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val processingMessage by viewModel.processingMessage.collectAsState()
    val activeWorkInfo by viewModel.activeWorkInfo.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(incomingIntent) {
        if (incomingIntent != null) {
            viewModel.handleIncomingIntent(incomingIntent)
            onIntentConsumed()
        }
    }

    var hasRequestedNotificationPermission by rememberSaveable { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        // If denied, operations still run on-device and report progress in-app gracefully.
    }

    LaunchedEffect(isProcessing, activeWorkInfo) {
        val workRunning = isProcessing || (activeWorkInfo != null && !activeWorkInfo!!.state.isFinished)
        if (workRunning && !hasRequestedNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                hasRequestedNotificationPermission = true
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Synchronize top-level navigation requests emitted from ViewModel
    LaunchedEffect(Unit) {
        viewModel.navigationEvents.collect { route ->
            val currentNavScreen = navController.currentDestination.toScreenRoute()
            if (currentNavScreen != route) {
                val targetAppRoute = route.toAppRoute()
                if (route == ScreenRoute.HOME) {
                    val popped = navController.popBackStack(HomeRoute, inclusive = false)
                    if (!popped) {
                        navController.navigate(HomeRoute) {
                            launchSingleTop = true
                        }
                    }
                } else if (route in listOf(ScreenRoute.RECENT_FILES, ScreenRoute.SETTINGS)) {
                    navController.navigate(targetAppRoute) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                } else {
                    navController.navigate(targetAppRoute) {
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    // Inform ViewModel when destination changes so screen-specific state resets execute
    LaunchedEffect(destinationRoute) {
        if (destinationRoute != null) {
            viewModel.updateCurrentRouteFromNav(destinationRoute)
        }
    }

    val handleBack: () -> Unit = {
        if (!navController.popBackStack()) {
            viewModel.updateCurrentRouteFromNav(ScreenRoute.HOME)
        }
    }

    BackHandler(enabled = destinationRoute != null && destinationRoute != ScreenRoute.HOME) {
        handleBack()
    }

    val navigateToTab: (ScreenRoute) -> Unit = { route ->
        viewModel.updateCurrentRouteFromNav(route)
        val targetAppRoute = route.toAppRoute()
        if (route == ScreenRoute.HOME) {
            val popped = navController.popBackStack(HomeRoute, inclusive = false)
            if (!popped) {
                navController.navigate(HomeRoute) {
                    launchSingleTop = true
                }
            }
        } else {
            navController.navigate(targetAppRoute) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
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

    val effectiveRoute = destinationRoute ?: currentRoute
    val showNav = effectiveRoute in listOf(ScreenRoute.HOME, ScreenRoute.RECENT_FILES, ScreenRoute.SETTINGS)

    PdfMasterTheme(
        darkTheme = isDarkMode,
        dynamicColor = isDynamicColorEnabled
    ) {
        if (showOnboarding) {
            OnboardingScreen(
                onFinishOnboarding = { viewModel.completeOnboarding() }
            )
            return@PdfMasterTheme
        }

        @Composable
        fun AppScaffoldContent() {
            Scaffold(
                topBar = {
                    if (effectiveRoute == ScreenRoute.HOME || effectiveRoute == ScreenRoute.RECENT_FILES) {
                        PdfTopAppBar(
                            title = if (effectiveRoute == ScreenRoute.HOME) {
                                stringResource(R.string.app_name)
                            } else {
                                stringResource(R.string.nav_recent_pdfs)
                            },
                            searchQuery = searchQuery,
                            onSearchQueryChange = { viewModel.setSearchQuery(it) },
                            isDarkMode = isDarkMode,
                            onToggleDarkMode = { viewModel.toggleDarkMode(it) }
                        )
                    }
                },
                bottomBar = {
                    if (showNav && windowInfo.isCompact) {
                        PdfBottomNavigationBar(
                            currentRoute = effectiveRoute,
                            onNavigate = { route -> navigateToTab(route) }
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
                    NavHost(
                        navController = navController,
                        startDestination = HomeRoute,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable<HomeRoute> {
                            HomeScreen(
                                viewModel = viewModel,
                                onOpenPdfViewer = { file -> viewModel.openPdfInViewer(file) },
                                windowSizeClass = windowSizeClass
                            )
                        }
                        composable<RecentFilesRoute> {
                            RecentFilesScreen(
                                viewModel = viewModel,
                                onOpenPdfViewer = { file -> viewModel.openPdfInViewer(file) },
                                windowSizeClass = windowSizeClass
                            )
                        }
                        composable<PdfViewerRoute> {
                            PdfViewerScreen(
                                viewModel = viewModel,
                                onBack = { handleBack() }
                            )
                        }
                        composable<CreatePdfRoute> {
                            CreatePdfScreen(
                                viewModel = viewModel,
                                onBack = { handleBack() }
                            )
                        }
                        composable<ConvertPdfRoute> {
                            ConvertPdfScreen(
                                viewModel = viewModel,
                                onBack = { handleBack() }
                            )
                        }
                        composable<RemovePasswordRoute> {
                            RemovePasswordScreen(
                                viewModel = viewModel,
                                onBack = { handleBack() }
                            )
                        }
                        composable<ExtractPagesRoute> {
                            ExtractPdfScreen(
                                viewModel = viewModel,
                                onBack = { handleBack() }
                            )
                        }
                        composable<MergePdfRoute> {
                            MergePdfScreen(
                                viewModel = viewModel,
                                onBack = { handleBack() }
                            )
                        }
                        composable<SplitPdfRoute> {
                            SplitPdfScreen(
                                viewModel = viewModel,
                                onBack = { handleBack() }
                            )
                        }
                        composable<CompressPdfRoute> {
                            CompressPdfScreen(
                                viewModel = viewModel,
                                onBack = { handleBack() }
                            )
                        }
                        composable<ImagesToPdfRoute> {
                            ImagesToPdfScreen(
                                viewModel = viewModel,
                                onBack = { handleBack() }
                            )
                        }
                        composable<ManagePagesRoute> {
                            ManagePagesScreen(
                                viewModel = viewModel,
                                onBack = { handleBack() }
                            )
                        }
                        composable<PasswordProtectRoute> {
                            PasswordProtectScreen(
                                viewModel = viewModel,
                                onBack = { handleBack() }
                            )
                        }
                        composable<SignPdfRoute> {
                            SignPdfScreen(
                                viewModel = viewModel,
                                onBack = { handleBack() }
                            )
                        }
                        composable<AnnotatePdfRoute> {
                            AnnotatePdfScreen(
                                viewModel = viewModel,
                                onBack = { handleBack() }
                            )
                        }
                        composable<WatermarkPdfRoute> {
                            WatermarkPdfScreen(
                                viewModel = viewModel,
                                onBack = { handleBack() }
                            )
                        }
                        composable<OcrPdfRoute> {
                            OcrPdfScreen(
                                viewModel = viewModel,
                                onBack = { handleBack() }
                            )
                        }
                        composable<DocumentScannerRoute> {
                            DocumentScannerScreen(
                                viewModel = viewModel,
                                onBack = { handleBack() }
                            )
                        }
                        composable<SettingsRoute> {
                            SettingsScreen(
                                viewModel = viewModel,
                                onBack = { handleBack() }
                            )
                        }
                    }

                    if (isProcessing && effectiveRoute != ScreenRoute.HOME) {
                        ProcessingDialog(
                            message = processingMessage,
                            onDismissRequest = { viewModel.resetProcessingState() },
                            onCancelWork = { viewModel.cancelActiveOperation() }
                        )
                    }
                }
            }
        }

        if (showNav && windowInfo.isExpanded) {
            PermanentNavigationDrawer(
                drawerContent = {
                    PdfNavigationDrawerContent(
                        currentRoute = effectiveRoute,
                        onNavigate = { route -> navigateToTab(route) }
                    )
                }
            ) {
                AppScaffoldContent()
            }
        } else if (showNav && windowInfo.isMedium) {
            Row(modifier = Modifier.fillMaxSize()) {
                PdfNavigationRail(
                    currentRoute = effectiveRoute,
                    onNavigate = { route -> navigateToTab(route) }
                )
                Box(modifier = Modifier.weight(1f)) {
                    AppScaffoldContent()
                }
            }
        } else {
            AppScaffoldContent()
        }
    }
}
