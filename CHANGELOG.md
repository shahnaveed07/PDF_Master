# Changelog — PDF Master

All notable changes to **PDF Master** (`com.dsc.pdfmaster`) are documented in this file.

---

## [1.0.0] — 2026-11-05

### Step 1 & Core Engine Audit — Protect PDF Encryption & Offline Office Conversions
- **PDF Encryption & Security (`PdfEngine`, `PdfRepository`, `PasswordProtectScreen`, `RemovePasswordScreen`)**:
  - Audited `com.tom-roush:pdfbox-android:2.0.27.0` security handlers (`StandardProtectionPolicy`, `AccessPermission`, `PDEncryption`) and implemented verified **128-bit and 256-bit AES** (`preferAES = true`) password protection.
  - Added support for distinct User (Open) and Owner (Permissions) passwords, in-place atomic file encryption with rollback safety, and detailed `ProtectResult` / `UnprotectResult` sealed outcomes.
  - Prevented silent pre-population of sample PDFs in security and conversion workflows.
- **Offline Office-to-PDF Conversion (`OfficeToPdfConverter`, `ConvertPdfScreen`)**:
  - Implemented local, 100% offline conversion for **DOCX (Word)**, **XLSX (Excel)**, and **PPTX (PowerPoint)** documents into multi-page PDFs without cloud uploads or network permissions.

---

### Phase 1 — Repository Hygiene & Build Setup
- **Git & Workspace Hygiene (`.gitignore`)**:
  - Excluded `.idea/`, `.aistudio/`, `assets/.aistudio/`, `public/assets/aistudio/`, `local.properties`, `build/`, `.gradle/`, and `*.keystore`.
- **Gradle Version Catalog (`gradle/libs.versions.toml`, `app/build.gradle.kts`)**:
  - Centralized all library and plugin dependencies in `gradle/libs.versions.toml`.
- **Release Optimization (`app/build.gradle.kts`, `app/proguard-rules.pro`)**:
  - Enabled R8 code minification (`isMinifyEnabled = true`) and resource shrinking (`isShrinkResources = true`) for release builds while preserving Room and PDFBox-Android keep rules.
- **Room Schema Export & Migration Testing (`app/schemas/`, `DatabaseMigrationTest.kt`)**:
  - Configured Room KSP schema export (`room.schemaLocation`) and added automated migration tests for `MIGRATION_1_2` and `MIGRATION_2_3`.
- **CI/CD, Static Analysis & Automation**:
  - Added GitHub Actions workflow (`.github/workflows/android.yml`) running JDK 17, `assembleDebug`, `testDebugUnitTest`, `lint`, and uploading debug APK artifacts.
  - Added Detekt static analysis configuration (`config/detekt/detekt.yml`) and Gradle verification tasks (`detekt`, `ktlintCheck`).
  - Added Dependabot configuration (`.github/dependabot.yml`) for Gradle and GitHub Actions.
  - Updated `README.md` (Android 16 / SDK 36 badge, Screenshots, Roadmap, Contributing, Office-to-PDF beta note) and added MIT `LICENSE` (`DarkSkullCorporation`).

---

### Phase 2 — Architecture & Reliability
- **Dependency Injection (`di/HiltAnnotations.kt`, `di/AppModule.kt`, `PdfMasterApplication.kt`)**:
  - Introduced Hilt-compatible dependency injection (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`, `@Module`, `@InstallIn`, `hiltViewModel()`) for `PdfDatabase`, `RecentPdfDao`, `PdfRepository`, `PdfWorkManager`, and `PdfPageBitmapCache`.
- **Per-Feature ViewModels (`ui/viewmodel/*`)**:
  - Modularized state management into dedicated feature ViewModels (`MergeViewModel`, `SplitViewModel`, `CompressViewModel`, `ConvertViewModel`, `PagesViewModel`, `SecurityViewModel`, `ViewerViewModel`, `RecentViewModel`) with immutable `UiState` `StateFlow`s.
- **Type-Safe Navigation Compose (`ui/navigation/AppRoutes.kt`, `ui/PdfMasterApp.kt`)**:
  - Migrated navigation to Jetpack Navigation Compose using `@Serializable` type-safe route objects.
- **Background Processing (`work/PdfWorkManager.kt`, `work/PdfWorkCancelReceiver.kt`)**:
  - Added foreground notification progress reporting and cancellation support for long-running PDF tasks, handling `POST_NOTIFICATIONS` permission denial gracefully.
- **Large PDF Memory Management (`pdfengine/PdfPageBitmapCache.kt`, `PdfEngine.kt`)**:
  - Implemented display-sized `Bitmap` rendering (`inSampleSize` / scaled `PdfRenderer.Page.render`), an `LruCache` for page bitmaps, and strict `ParcelFileDescriptor` / `PdfRenderer` closure, verified with a 300-page PDF test.
- **Persistable URI Permissions & Typed Error States (`data/model/PdfOperationResult.kt`)**:
  - Added persistable URI permission handling, stale file detection (`File no longer available` badge + remove action), and sealed `PdfOperationResult` / `PdfOperationError` types (`CorruptedPdf`, `InvalidPassword`, `InsufficientStorage`, `PermissionRevoked`).
- **Dual-Mode Compression (`PdfCompressionMode`)**:
  - Added PDFBox **Image Recompression mode** (downscales and re-encodes embedded images while keeping text selectable) as default alongside **Maximum Compression (Rasterize)** mode with before/after size reporting.

---

### Phase 3 — New Features
- **Open-With & Share (`AndroidManifest.xml`, `MainActivity.kt`, `PdfAdvancedToolsEngine.parseIncomingIntent`)**:
  - Added intent filters for `ACTION_VIEW` (`application/pdf`), `ACTION_SEND`, and `ACTION_SEND_MULTIPLE` (`application/pdf`, `image/*`) routing directly to Viewer, Merge, or Images-to-PDF.
- **Sign PDF (`SignPdfScreen.kt`, `SignPdfViewModel.kt`)**:
  - Freehand signature canvas, local reusable signature library, drag/resize page placement, and flattening into PDF pages.
- **Annotate PDF (`AnnotatePdfScreen.kt`, `AnnotatePdfViewModel.kt`)**:
  - Highlight, freehand drawing, and text box annotations with full Undo/Redo (`AnnotationHistoryStack`) saved into the output PDF.
- **Watermark & Page Numbers (`WatermarkPdfScreen.kt`, `WatermarkPdfViewModel.kt`)**:
  - Custom text watermarks (opacity, rotation, position including tiled) and formatted page numbering (`Page X of Y`, `X / Y`, `X`, `- X -`).
- **Viewer Upgrades (`PdfViewerScreen.kt`, `ViewerViewModel.kt`)**:
  - In-document text search with match navigation, document outline/bookmarks dialog, per-file last-read page restoration, Night/Invert mode toggle, and single-page extract/delete from the viewer menu.
- **On-Device Offline OCR (`OcrPdfScreen.kt`, `OcrViewModel.kt`, `com.google.mlkit.vision.text.*`)**:
  - Bundled offline ML Kit text recognition (~4.6 MB compressed / ~16.2 MB uncompressed APK impact) generating searchable PDFs with an aligned invisible text layer (`RenderingMode.NEITHER`).
- **Document Scanner (`DocumentScannerScreen.kt`, `DocumentScannerViewModel.kt`, `GmsDocumentScanning.kt`)**:
  - ML Kit Document Scanner integration requiring zero `CAMERA` permission in the app, with page filters (`Original`, `Auto Enhance`, `Grayscale`, `B&W`), rotation, and PDF export.
- **Batch Support (`CompressPdfScreen.kt`, `ConvertPdfScreen.kt`)**:
  - Multi-file batch selection and combined `BatchProgressState` progress view for Batch Compress and Batch Convert.
- **Favorites / Pinned Section (`RecentFilesScreen.kt`, `PdfFileCard.kt`, `PdfDatabase` v3)**:
  - Added `isPinned` and `lastReadPage` columns (`MIGRATION_2_3`) and a dedicated `FAVORITES / PINNED` section in Recent Files.

---

### Phase 4 — Polish, Accessibility, Adaptive UI & Localization
- **Accessibility**:
  - Added `contentDescription` to icons/images, enforced 48dp minimum interactive touch targets (`sizeIn(minWidth = 48.dp, minHeight = 48.dp)`), added `Modifier.semantics { heading() }` on section headers, and verified rendering at **200% font scale**.
- **Adaptive Layouts (`ui/adaptive/WindowSizeClass.kt`)**:
  - Implemented `WindowSizeClass` (`Compact`, `Medium`, `Expanded`) with adaptive 2/3/4-column Quick Action grids on `HomeScreen` and a **two-pane Recent Files + PDF Viewer** split layout on tablets and foldables.
- **Theme (`ui/theme/Theme.kt`, `SettingsScreen.kt`)**:
  - Preserved the signature monochrome theme as default and added an optional **Material You Dynamic Color** toggle in Settings.
- **Localization & RTL (`res/values*/strings.xml`)**:
  - Externalized UI strings to `strings.xml` and added translations for **Hindi (`hi`)**, **Urdu (`ur`)**, **Arabic (`ar`)**, **Spanish (`es`)**, and **French (`fr`)** with full RTL layout and `Icons.AutoMirrored` support.
- **Onboarding & Empty States (`OnboardingScreen.kt`)**:
  - Added a 3-step first-run onboarding guide (with replay option in Settings) and descriptive empty states across tools.
- **Comprehensive Testing & Roborazzi Baselines (`Phase4PolishAndCoverageTest.kt`, `GreetingScreenshotTest.kt`)**:
  - Added unit tests for `PdfEngine` round trips, `PdfRepository`, ViewModels with fake dependencies, Compose UI tests for `HomeScreen`, `MergePdfScreen`, `OnboardingScreen`, and two-pane `RecentFilesScreen`, and updated Roborazzi screenshot baselines.
