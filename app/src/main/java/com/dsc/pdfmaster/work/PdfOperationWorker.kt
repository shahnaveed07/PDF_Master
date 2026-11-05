package com.dsc.pdfmaster.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.dsc.pdfmaster.R
import com.dsc.pdfmaster.data.model.PdfCompressionLevel
import com.dsc.pdfmaster.data.model.PdfCompressionMode
import com.dsc.pdfmaster.data.model.PdfOperationError
import com.dsc.pdfmaster.data.repository.PdfRepository
import com.dsc.pdfmaster.pdfengine.OfficeToPdfConverter
import com.dsc.pdfmaster.pdfengine.PdfEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class PdfWorkOperationType(val title: String) {
    MERGE("Merging PDFs"),
    COMPRESS("Compressing PDF"),
    CONVERT_PDF_TO_IMAGES("Converting PDF to Images"),
    CONVERT_PDF_TO_TEXT("Extracting PDF Text"),
    CONVERT_TXT_TO_PDF("Converting TXT to PDF"),
    CONVERT_IMAGE_FORMAT("Converting Image Format"),
    OFFICE_TO_PDF("Converting Office Document to PDF"),
    IMAGES_TO_PDF("Converting Images to PDF")
}

enum class PdfWorkState {
    ENQUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    val isFinished: Boolean
        get() = this == SUCCEEDED || this == FAILED || this == CANCELLED
}

data class PdfWorkInfo(
    val id: String,
    val operationType: PdfWorkOperationType,
    val state: PdfWorkState,
    val progressPercent: Int = 0,
    val message: String = "",
    val outputFiles: List<File> = emptyList(),
    val extractedText: String? = null,
    val resultSummary: String? = null,
    val layoutDisclosure: String? = null,
    val originalSizeBytes: Long = 0L,
    val compressedSizeBytes: Long = 0L,
    val error: PdfOperationError? = null
)

sealed class PdfWorkSpec {
    data class Merge(
        val inputFiles: List<File>,
        val outputFileName: String
    ) : PdfWorkSpec()

    data class Compress(
        val sourceFile: File,
        val level: PdfCompressionLevel,
        val mode: PdfCompressionMode,
        val outputFileName: String
    ) : PdfWorkSpec()

    data class ImagesToPdf(
        val imageUris: List<Uri>,
        val outputFileName: String
    ) : PdfWorkSpec()

    data class OfficeToPdf(
        val sourceUri: Uri,
        val expectedType: OfficeToPdfConverter.OfficeDocumentType,
        val outputFileName: String
    ) : PdfWorkSpec()

    data class PdfToImages(
        val sourceFile: File,
        val password: String?,
        val format: Bitmap.CompressFormat,
        val outputPrefix: String
    ) : PdfWorkSpec()

    data class PdfToText(
        val sourceFile: File,
        val password: String?,
        val outputFileName: String
    ) : PdfWorkSpec()

    data class TxtToPdf(
        val sourceUri: Uri,
        val outputFileName: String
    ) : PdfWorkSpec()

    data class ConvertImageFormat(
        val sourceUri: Uri,
        val targetFormat: Bitmap.CompressFormat,
        val outputFileName: String
    ) : PdfWorkSpec()
}

@Singleton
class PdfWorkManager @Inject constructor(
    private val context: Context,
    private val repository: PdfRepository
) {
    private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentJob: Job? = null
    private var currentWorkId: String? = null

    private val _activeWorkInfo = MutableStateFlow<PdfWorkInfo?>(null)
    val activeWorkInfo: StateFlow<PdfWorkInfo?> = _activeWorkInfo.asStateFlow()

    init {
        ensureNotificationChannel()
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun enqueue(
        spec: PdfWorkSpec,
        onFinished: (suspend (PdfWorkInfo) -> Unit)? = null
    ): String {
        currentJob?.cancel()
        val workId = UUID.randomUUID().toString()
        currentWorkId = workId
        val opType = when (spec) {
            is PdfWorkSpec.Merge -> PdfWorkOperationType.MERGE
            is PdfWorkSpec.Compress -> PdfWorkOperationType.COMPRESS
            is PdfWorkSpec.ImagesToPdf -> PdfWorkOperationType.IMAGES_TO_PDF
            is PdfWorkSpec.OfficeToPdf -> PdfWorkOperationType.OFFICE_TO_PDF
            is PdfWorkSpec.PdfToImages -> PdfWorkOperationType.CONVERT_PDF_TO_IMAGES
            is PdfWorkSpec.PdfToText -> PdfWorkOperationType.CONVERT_PDF_TO_TEXT
            is PdfWorkSpec.TxtToPdf -> PdfWorkOperationType.CONVERT_TXT_TO_PDF
            is PdfWorkSpec.ConvertImageFormat -> PdfWorkOperationType.CONVERT_IMAGE_FORMAT
        }

        val initialInfo = PdfWorkInfo(
            id = workId,
            operationType = opType,
            state = PdfWorkState.RUNNING,
            progressPercent = 10,
            message = "${opType.title}..."
        )
        _activeWorkInfo.value = initialInfo
        postForegroundProgressNotification(initialInfo)

        currentJob = workerScope.launch {
            val worker = PdfOperationWorker(context, repository)
            try {
                val finalInfo = worker.execute(
                    workId = workId,
                    opType = opType,
                    spec = spec,
                    onProgress = { progress, message ->
                        val updated = PdfWorkInfo(
                            id = workId,
                            operationType = opType,
                            state = PdfWorkState.RUNNING,
                            progressPercent = progress.coerceIn(0, 99),
                            message = message
                        )
                        _activeWorkInfo.value = updated
                        postForegroundProgressNotification(updated)
                    }
                )
                _activeWorkInfo.value = finalInfo
                dismissNotification()
                onFinished?.invoke(finalInfo)
            } catch (e: CancellationException) {
                val cancelledInfo = PdfWorkInfo(
                    id = workId,
                    operationType = opType,
                    state = PdfWorkState.CANCELLED,
                    progressPercent = 0,
                    message = "${opType.title} cancelled",
                    error = PdfOperationError.Cancelled("${opType.title} was cancelled.")
                )
                _activeWorkInfo.value = cancelledInfo
                dismissNotification()
                onFinished?.invoke(cancelledInfo)
            } catch (t: Throwable) {
                val mappedError = PdfOperationError.fromThrowable(t, "${opType.title} failed.")
                val failedInfo = PdfWorkInfo(
                    id = workId,
                    operationType = opType,
                    state = PdfWorkState.FAILED,
                    progressPercent = 0,
                    message = mappedError.userMessage,
                    error = mappedError
                )
                _activeWorkInfo.value = failedInfo
                dismissNotification()
                onFinished?.invoke(failedInfo)
            }
        }
        return workId
    }

    fun cancelActiveWork(workId: String? = null) {
        if (workId == null || workId == currentWorkId) {
            currentJob?.cancel()
            currentJob = null
            dismissNotification()
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PDF Background Operations",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress and cancel controls for on-device PDF operations"
                setShowBadge(false)
            }
            manager?.createNotificationChannel(channel)
        }
    }

    private fun postForegroundProgressNotification(info: PdfWorkInfo) {
        if (!hasNotificationPermission()) return
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val cancelIntent = Intent(context, PdfWorkCancelReceiver::class.java).apply {
                action = ACTION_CANCEL_PDF_WORK
                putExtra(EXTRA_WORK_ID, info.id)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val cancelPendingIntent = PendingIntent.getBroadcast(
                context,
                NOTIFICATION_ID,
                cancelIntent,
                flags
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(info.operationType.title)
                .setContentText(info.message)
                .setProgress(100, info.progressPercent.coerceIn(0, 100), false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Cancel",
                    cancelPendingIntent
                )
                .build()

            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {
        }
    }

    private fun dismissNotification() {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {
        }
    }

    companion object {
        const val CHANNEL_ID = "pdf_master_operations_channel"
        const val NOTIFICATION_ID = 4102
        const val ACTION_CANCEL_PDF_WORK = "com.dsc.pdfmaster.action.CANCEL_PDF_WORK"
        const val EXTRA_WORK_ID = "extra_work_id"

        @android.annotation.SuppressLint("StaticFieldLeak")
        @Volatile
        var sharedInstance: PdfWorkManager? = null
    }
}

class PdfOperationWorker(
    private val context: Context,
    private val repository: PdfRepository
) {
    suspend fun execute(
        workId: String,
        opType: PdfWorkOperationType,
        spec: PdfWorkSpec,
        onProgress: (Int, String) -> Unit
    ): PdfWorkInfo {
        return when (spec) {
            is PdfWorkSpec.Merge -> {
                onProgress(20, "Merging ${spec.inputFiles.size} PDF files...")
                val result = repository.mergePdfs(spec.inputFiles, spec.outputFileName)
                if (result != null && result.exists()) {
                    PdfWorkInfo(
                        id = workId,
                        operationType = opType,
                        state = PdfWorkState.SUCCEEDED,
                        progressPercent = 100,
                        message = "Merged successfully into ${result.name}!",
                        outputFiles = listOf(result)
                    )
                } else {
                    PdfWorkInfo(
                        id = workId,
                        operationType = opType,
                        state = PdfWorkState.FAILED,
                        message = "Merge failed. Please verify the selected PDFs.",
                        error = PdfOperationError.CorruptedPdf("Merge failed. Please verify the selected PDFs.")
                    )
                }
            }

            is PdfWorkSpec.Compress -> {
                val originalSize = spec.sourceFile.length()
                onProgress(25, "Compressing PDF with ${spec.level.label} (${spec.mode.label})...")
                val resultFile = repository.compressPdf(
                    pdfFile = spec.sourceFile,
                    level = spec.level,
                    outputName = spec.outputFileName,
                    mode = spec.mode
                )
                if (resultFile != null && resultFile.exists()) {
                    val compressedSize = resultFile.length()
                    PdfWorkInfo(
                        id = workId,
                        operationType = opType,
                        state = PdfWorkState.SUCCEEDED,
                        progressPercent = 100,
                        message = "Compressed successfully!",
                        outputFiles = listOf(resultFile),
                        originalSizeBytes = originalSize,
                        compressedSizeBytes = compressedSize
                    )
                } else {
                    PdfWorkInfo(
                        id = workId,
                        operationType = opType,
                        state = PdfWorkState.FAILED,
                        message = "Compression failed.",
                        error = PdfOperationError.CorruptedPdf("Compression failed.")
                    )
                }
            }

            is PdfWorkSpec.ImagesToPdf -> {
                onProgress(25, "Converting ${spec.imageUris.size} images to PDF...")
                val result = repository.convertImagesToPdf(spec.imageUris, spec.outputFileName)
                if (result != null && result.exists()) {
                    PdfWorkInfo(
                        id = workId,
                        operationType = opType,
                        state = PdfWorkState.SUCCEEDED,
                        progressPercent = 100,
                        message = "Converted to PDF: ${result.name}",
                        outputFiles = listOf(result)
                    )
                } else {
                    PdfWorkInfo(
                        id = workId,
                        operationType = opType,
                        state = PdfWorkState.FAILED,
                        message = "Conversion failed. Check that all selected files are valid images.",
                        error = PdfOperationError.UnsupportedFormat("Conversion failed. Check that all selected files are valid images.")
                    )
                }
            }

            is PdfWorkSpec.OfficeToPdf -> {
                val label = spec.expectedType.label
                onProgress(30, "Converting $label to PDF...")
                when (
                    val result = repository.convertOfficeToPdfDetailed(
                        uri = spec.sourceUri,
                        expectedType = spec.expectedType,
                        outputName = spec.outputFileName
                    )
                ) {
                    is OfficeToPdfConverter.OfficeConversionResult.Success -> {
                        PdfWorkInfo(
                            id = workId,
                            operationType = opType,
                            state = PdfWorkState.SUCCEEDED,
                            progressPercent = 100,
                            message = "Converted $label to ${result.file.name} (${result.pageCount} pages)",
                            outputFiles = listOf(result.file),
                            resultSummary = "${result.pageCount} PDF page(s) • ${result.summary}",
                            layoutDisclosure = result.layoutDisclosure
                        )
                    }
                    is OfficeToPdfConverter.OfficeConversionResult.Error -> {
                        PdfWorkInfo(
                            id = workId,
                            operationType = opType,
                            state = PdfWorkState.FAILED,
                            message = result.message,
                            error = PdfOperationError.UnsupportedFormat(result.message)
                        )
                    }
                }
            }

            is PdfWorkSpec.PdfToImages -> {
                val formatLabel = if (spec.format == Bitmap.CompressFormat.PNG) "PNG" else "JPEG"
                onProgress(30, "Rendering PDF pages to $formatLabel images...")
                when (
                    val result = repository.convertPdfToImagesDetailed(
                        pdfFile = spec.sourceFile,
                        format = spec.format,
                        password = spec.password,
                        outputPrefix = spec.outputPrefix
                    )
                ) {
                    is PdfEngine.PdfToImagesResult.Success -> {
                        PdfWorkInfo(
                            id = workId,
                            operationType = opType,
                            state = PdfWorkState.SUCCEEDED,
                            progressPercent = 100,
                            message = "Exported ${result.files.size} $formatLabel image(s) (${result.files.first().name})",
                            outputFiles = result.files
                        )
                    }
                    is PdfEngine.PdfToImagesResult.Error -> {
                        val err = if (result.isPasswordProtected) {
                            PdfOperationError.WrongPassword(result.message)
                        } else {
                            PdfOperationError.CorruptedPdf(result.message)
                        }
                        PdfWorkInfo(
                            id = workId,
                            operationType = opType,
                            state = PdfWorkState.FAILED,
                            message = result.message,
                            error = err
                        )
                    }
                }
            }

            is PdfWorkSpec.PdfToText -> {
                onProgress(30, "Extracting text content from PDF...")
                when (
                    val result = repository.extractTextFromPdfDetailed(
                        pdfFile = spec.sourceFile,
                        password = spec.password,
                        outputName = spec.outputFileName
                    )
                ) {
                    is PdfEngine.TextExtractResult.Success -> {
                        PdfWorkInfo(
                            id = workId,
                            operationType = opType,
                            state = PdfWorkState.SUCCEEDED,
                            progressPercent = 100,
                            message = "Text extracted to ${result.file.name}",
                            outputFiles = listOf(result.file),
                            extractedText = result.extractedText
                        )
                    }
                    is PdfEngine.TextExtractResult.Error -> {
                        val err = if (result.isPasswordProtected) {
                            PdfOperationError.WrongPassword(result.message)
                        } else {
                            PdfOperationError.CorruptedPdf(result.message)
                        }
                        PdfWorkInfo(
                            id = workId,
                            operationType = opType,
                            state = PdfWorkState.FAILED,
                            message = result.message,
                            error = err
                        )
                    }
                }
            }

            is PdfWorkSpec.TxtToPdf -> {
                onProgress(30, "Converting TXT file to PDF...")
                val pdfFile = repository.convertTextFileToPdf(spec.sourceUri, spec.outputFileName)
                if (pdfFile != null && pdfFile.exists()) {
                    PdfWorkInfo(
                        id = workId,
                        operationType = opType,
                        state = PdfWorkState.SUCCEEDED,
                        progressPercent = 100,
                        message = "TXT converted to PDF: ${pdfFile.name}",
                        outputFiles = listOf(pdfFile)
                    )
                } else {
                    val msg = "Failed to convert TXT file to PDF. Ensure the file contains valid text."
                    PdfWorkInfo(
                        id = workId,
                        operationType = opType,
                        state = PdfWorkState.FAILED,
                        message = msg,
                        error = PdfOperationError.UnsupportedFormat(msg)
                    )
                }
            }

            is PdfWorkSpec.ConvertImageFormat -> {
                val ext = if (spec.targetFormat == Bitmap.CompressFormat.JPEG) "JPG" else "PNG"
                onProgress(30, "Converting image to $ext...")
                when (
                    val result = repository.convertImageFormatDetailed(
                        imageUri = spec.sourceUri,
                        targetFormat = spec.targetFormat,
                        outputName = spec.outputFileName
                    )
                ) {
                    is PdfEngine.ImageConversionResult.Success -> {
                        PdfWorkInfo(
                            id = workId,
                            operationType = opType,
                            state = PdfWorkState.SUCCEEDED,
                            progressPercent = 100,
                            message = "Image converted to ${result.file.name} (${result.width}x${result.height})",
                            outputFiles = listOf(result.file)
                        )
                    }
                    is PdfEngine.ImageConversionResult.Error -> {
                        PdfWorkInfo(
                            id = workId,
                            operationType = opType,
                            state = PdfWorkState.FAILED,
                            message = result.message,
                            error = PdfOperationError.UnsupportedFormat(result.message)
                        )
                    }
                }
            }
        }
    }
}
