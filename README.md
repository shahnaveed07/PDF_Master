# PDF Master

[![Android CI](https://github.com/shahnaveed07/PDF_Master/actions/workflows/android.yml/badge.svg)](https://github.com/shahnaveed07/PDF_Master/actions/workflows/android.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-black.svg)](LICENSE)

> **Powerful PDF tools. Simple. Private.**  
> Built by **DarkSkullCorporation**

PDF Master is a modern, high-performance Android utility suite designed for fast, confidential, and seamless PDF document workflows. Built entirely with Kotlin and Jetpack Compose, the application processes all documents directly on-device using native Android framework graphics and document APIs—ensuring complete user privacy with zero cloud uploads and zero internet dependencies.

---

## Key Features

- **Merge PDFs**: Combine multiple PDF documents into a single organized file with customizable order.
- **Split PDF**: Extract specific page ranges or isolate individual pages into standalone PDF documents.
- **Compress PDF**: Optimize file size across multiple compression levels (High Quality, Balanced, Smaller File) with before/after size comparisons.
- **Images to PDF**: Convert photos and scanned images directly into high-quality PDF documents.
- **Create PDF**: Generate documents from scratch in 3 creation modes: Text PDF, Image PDF, and Hybrid (Text + Image) PDF.
- **Document Converter**: Local conversion suite supporting PDF to Images (PNG/JPG), PDF to Text (TXT), Image Format Conversion (PNG <-> JPG), and Text to PDF.
- **Office to PDF Converter (beta)**: Offline conversion of OpenXML Word (`.docx`), Excel (`.xlsx`), and PowerPoint (`.pptx`) documents to PDF on-device.
- **Password Protection & Unprotect**: Apply 256-bit AES encryption (with separate Open and Owner password support) to confidential documents, and safely remove passwords from protected PDFs.
- **Manage & Reorder Pages**: Visual grid interface to reorder pages, rotate pages (90°/180°/270°), and remove unnecessary pages.
- **PDF Viewer**: Smooth multi-page document reader with lazy page loading, page count, zoom indicators, and password unlock overlay.
- **Recent Files History**: Built-in document history powered by local Room persistence (`filePath` PK deduplicated schema) for instant access to recent outputs.
- **Clean Monochrome Design**: Professional high-contrast monochrome design system (Black, White, Neutral Greys) with light mode, dark mode, and system default support.

---

## Screenshots

> _Screenshots placeholder — UI previews for Home Dashboard, PDF Viewer, Document Converter, and Password Protection screens will be added here._

| Home Dashboard | PDF Reader | Protect PDF | Document Converter |
| :---: | :---: | :---: | :---: |
| _Coming soon_ | _Coming soon_ | _Coming soon_ | _Coming soon_ |

---

## Privacy & Security Architecture

- **Zero Cloud Uploads**: Documents never leave device storage or local memory.
- **Zero Tracking / Zero Analytics**: No external telemetry, tracking SDKs, or background server synchronization.
- **Zero Unnecessary Permissions**: Does not request camera, contacts, location, or broad storage permissions. File access is managed via the secure Android System Document Picker.
- **Explicit Cloud Backup Exclusions**: Document storage directories (`imported_pdfs`, `output_pdfs`, `output_images`, `output_texts`, `managed_pdfs`) are explicitly excluded from Android cloud backup and device transfer rules.
- **Native Android Framework & PDFBox Engine**: Utilizes native `android.graphics.pdf.PdfRenderer`, `android.graphics.pdf.PdfDocument`, and `PDFBox-Android` with strict bitmap memory recycling and file descriptor management.

---

## Technology Stack

- **Language**: Kotlin 2.0+
- **UI Framework**: Jetpack Compose with Material Design 3 (M3)
- **Architecture**: MVVM (Model-View-ViewModel) + Unidirectional Data Flow (UDF)
- **Local Database**: Room (SQLite) with Kotlin Symbol Processing (KSP), exported schema history (`app/schemas/`), and Flow reactivity (`version = 2` schema)
- **Async Concurrency**: Kotlin Coroutines (`Dispatchers.IO`, `Dispatchers.Main`) & `StateFlow`
- **Image Loading**: Coil Compose for document and picker image previews
- **Rendering & Manipulation**: PDFBox-Android, native Android `PdfRenderer`, `PdfDocument`, and `Canvas`
- **Testing & Quality**: Robolectric local JVM testing, Roborazzi screenshot verification, Android Lint, and Detekt static analysis

---

## Application Details

- **Application ID**: `com.dsc.pdfmaster`
- **Namespace**: `com.dsc.pdfmaster`
- **Min SDK**: 24 (Android 7.0 Nougat)
- **Target / Compile SDK**: 36 (Android 16)

---

## Project Structure

```
com.dsc.pdfmaster
├── MainActivity.kt                      // Single Activity entry point
├── data/                                // Data & persistence layer
│   ├── database/
│   │   ├── RecentPdfEntity.kt           // Room database entity (filePath PK)
│   │   ├── RecentPdfDao.kt              // Reactive DAO with Flow queries
│   │   └── PdfDatabase.kt               // Room database instance (MIGRATION_1_2)
│   ├── model/
│   │   ├── PdfTool.kt                   // Tool item models
│   │   ├── PdfCompressionLevel.kt       // Compression presets
│   │   └── PdfPageItem.kt               // Page index, bitmap, and rotation model
│   └── repository/
│       └── PdfRepository.kt             // Repository mediating DB & file system
├── pdfengine/
│   ├── PdfEngine.kt                     // On-device PDF manipulation engine
│   └── OfficeToPdfConverter.kt          // Local OpenXML (.docx, .xlsx, .pptx) converter (beta)
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
    │   ├── CreatePdfScreen.kt
    │   ├── ConvertPdfScreen.kt
    │   ├── ExtractPdfScreen.kt
    │   ├── MergePdfScreen.kt
    │   ├── ImagesToPdfScreen.kt
    │   ├── CompressPdfScreen.kt
    │   ├── ManagePagesScreen.kt
    │   ├── SplitPdfScreen.kt
    │   ├── PasswordProtectScreen.kt
    │   ├── RemovePasswordScreen.kt
    │   ├── RecentFilesScreen.kt
    │   └── SettingsScreen.kt
    └── theme/                           // Monochrome design system
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
./gradlew assembleDebug
```

To run JVM and Robolectric unit tests:
```bash
./gradlew testDebugUnitTest
```

To run Android Lint and Detekt checks:
```bash
./gradlew lint detekt
```

---

## Roadmap

- [ ] Enhanced PDF Viewer capabilities (in-document text search, table of contents / outline navigation, page bookmarks)
- [ ] Expanded Office-to-PDF layout fidelity (complex nested tables, custom embedded fonts, vector drawing shapes)
- [ ] Page annotations, watermarking, and page numbering tools
- [ ] Batch document operations and background queue progress notifications
- [ ] PDF metadata inspector and sanitizer

---

## Contributing

Contributions are welcome! To contribute to PDF Master:

1. Fork the repository and create a feature branch from `main` (`git checkout -b feat/your-feature`).
2. Keep all document processing strictly **on-device and offline**—do not introduce network dependencies, analytics, or unnecessary permissions.
3. Follow the existing MVVM + Repository architecture and monochrome Material 3 design system.
4. Add unit/Robolectric tests for new engine or ViewModel functionality and verify that `./gradlew assembleDebug testDebugUnitTest lint detekt` passes.
5. Open a Pull Request with a clear summary of your changes.

---

## Release Notes & Configuration

- **ProGuard / R8**: R8 minification (`isMinifyEnabled = true`) and resource shrinking (`isShrinkResources = true`) are enabled for release builds, with keep rules configured in `app/proguard-rules.pro` for Room database schemas and PDFBox-Android internals.
- **Signing**: Configure your release keystore via environment variables or Gradle properties when generating signed release APKs / AABs.
- **Permissions**: Fully compliant with Google Play Store policies with zero dangerous permissions declared.

---

## License

Licensed under the [MIT License](LICENSE).  
Copyright © 2026 DarkSkullCorporation.
