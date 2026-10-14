# PDF Master

> **Powerful PDF tools. Simple. Private.**  
> Built by **DarkSkullCorporation**

PDF Master is a modern, high-performance Android utility suite designed for fast, confidential, and seamless PDF document workflows. Built entirely with Kotlin and Jetpack Compose, the application processes all documents directly on-device using native Android framework graphics and document APIs—ensuring complete user privacy with zero cloud uploads and zero internet dependencies.

---

## Key Features

- **Merge PDFs**: Combine multiple PDF documents into a single organized file with customizable order.
- **Split PDF**: Extract specific page ranges or isolate individual pages into standalone PDF documents.
- **Compress PDF**: Optimize file size across multiple compression levels (Low, Medium, High) using intelligent vector and bitmap downsampling.
- **Images to PDF**: Convert photos and scanned images directly into high-quality A4 PDF documents.
- **Manage & Reorder Pages**: Visual grid interface to reorder pages, rotate pages (90°/180°/270°), and remove unnecessary pages.
- **PDF Viewer**: Smooth multi-page document reader powered by native `PdfRenderer` with page count and zoom indicators.
- **Password Protection**: Apply standard encryption and password security to confidential documents.
- **Recent Files History**: Built-in document history powered by local Room persistence for instant access to recent outputs.
- **Dark-First Modern UI**: Professional DarkSkullCorporation visual identity featuring high-contrast tiered dark surfaces (`#000000`, `#0A0A0D`), electric cyan accents (`#00E5FF`), and purple highlights (`#8B5CF6`).

---

## Privacy & Security Architecture

- **Zero Cloud Uploads**: Documents never leave device storage or local memory.
- **Zero Tracking / Zero Analytics**: No external telemetry, tracking SDKs, or background server synchronization.
- **Zero Unnecessary Permissions**: Does not request camera, contacts, location, or broad storage permissions. File access is managed via the secure Android System Document Picker.
- **Native Android Framework Engine**: Utilizes native `android.graphics.pdf.PdfRenderer` and `android.graphics.pdf.PdfDocument` with strict bitmap memory recycling and file descriptor management.

---

## Technology Stack

- **Language**: Kotlin 2.0+
- **UI Framework**: Jetpack Compose with Material Design 3 (M3)
- **Architecture**: MVVM (Model-View-ViewModel) + Unidirectional Data Flow (UDF)
- **Local Database**: Room (SQLite) with Kotlin Symbol Processing (KSP) and Flow reactivity
- **Async Concurrency**: Kotlin Coroutines (`Dispatchers.IO`, `Dispatchers.Main`) & `StateFlow`
- **Image Loading**: Coil Compose for document and picker image previews
- **Rendering & Manipulation**: Native Android `PdfRenderer`, `PdfDocument`, and `Canvas`
- **Testing**: Robolectric local JVM testing and Roborazzi screenshot verification

---

## Application Details

- **Application ID**: `com.dsc.pdfmastertool`
- **Namespace**: `com.dsc.pdfmastertool`
- **Min SDK**: 24 (Android 7.0 Nougat)
- **Target / Compile SDK**: 36 (Android 15+)

---

## Project Structure

```
com.dsc.pdfmastertool
├── MainActivity.kt                      // Single Activity entry point
├── data/                                // Data & persistence layer
│   ├── database/
│   │   ├── RecentPdfEntity.kt           // Room database entity
│   │   ├── RecentPdfDao.kt              // Reactive DAO with Flow queries
│   │   └── PdfDatabase.kt               // Room database instance
│   ├── model/
│   │   ├── PdfTool.kt                   // Tool item models
│   │   ├── PdfCompressionLevel.kt       // Compression presets
│   │   └── PdfPageItem.kt               // Page index, bitmap, and rotation model
│   └── repository/
│       └── PdfRepository.kt             // Repository mediating DB & file system
├── pdfengine/
│   └── PdfEngine.kt                     // Native on-device PDF manipulation engine
└── ui/
    ├── PdfMasterApp.kt                  // Root scaffold and route coordinator
    ├── PdfMasterViewModel.kt            // State management and async operations
    ├── components/                      // Reusable UI components
    │   ├── PdfBottomNavigationBar.kt
    │   ├── PdfTopAppBar.kt
    │   ├── PdfFileCard.kt
    │   ├── ToolCard.kt
    │   └── ProcessingDialog.kt
    ├── screens/                         // Feature screens
    │   ├── HomeScreen.kt
    │   ├── PdfViewerScreen.kt
    │   ├── MergePdfScreen.kt
    │   ├── ImagesToPdfScreen.kt
    │   ├── CompressPdfScreen.kt
    │   ├── ManagePagesScreen.kt
    │   ├── SplitPdfScreen.kt
    │   ├── PasswordProtectScreen.kt
    │   ├── RecentFilesScreen.kt
    │   └── SettingsScreen.kt
    └── theme/                           // DarkSkullCorporation theme & styling
        ├── Color.kt
        ├── Type.kt
        └── Theme.kt
```

---

## Building Locally

### Prerequisites
- Android Studio Ladybug | 2024.2+ or Android SDK command-line tools
- JDK 17 or higher
- Android SDK platforms up to API 36

### Build Commands

To assemble the debug APK:
```bash
gradle :app:assembleDebug
```

To run JVM and Robolectric unit tests:
```bash
gradle :app:testDebugUnitTest
```

---

## Release Notes & Configuration

- **ProGuard / R8**: ProGuard rules are configured in `app/proguard-rules.pro` to preserve Room database schemas and native Android graphics bindings.
- **Signing**: Configure your release keystore via environment variables or Gradle properties when generating signed release APKs / AABs.
- **Permissions**: Fully compliant with Google Play Store policies with zero dangerous permissions declared.

---

© DarkSkullCorporation. All rights reserved.
