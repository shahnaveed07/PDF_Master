@file:OptIn(
    kotlinx.serialization.ExperimentalSerializationApi::class,
    kotlinx.serialization.InternalSerializationApi::class
)

package com.dsc.pdfmaster.ui.navigation

import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.dsc.pdfmaster.ui.ScreenRoute
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure

private class RouteObjectSerializer<T : Any>(
    serialName: String,
    private val objectInstance: T
) : KSerializer<T> {
    override val descriptor: SerialDescriptor =
        buildSerialDescriptor(serialName, StructureKind.OBJECT)

    override fun serialize(encoder: Encoder, value: T) {
        encoder.encodeStructure(descriptor) {}
    }

    override fun deserialize(decoder: Decoder): T {
        decoder.decodeStructure(descriptor) {
            while (true) {
                val index = decodeElementIndex(descriptor)
                if (index == CompositeDecoder.DECODE_DONE) break
            }
        }
        return objectInstance
    }
}

sealed interface AppRoute {
    val screenRoute: ScreenRoute
}

@Serializable
data object HomeRoute : AppRoute {
    override val screenRoute: ScreenRoute = ScreenRoute.HOME
    private val kSerializer: KSerializer<HomeRoute> =
        RouteObjectSerializer("com.dsc.pdfmaster.ui.navigation.HomeRoute", this)
    fun serializer(): KSerializer<HomeRoute> = kSerializer
}

@Serializable
data object RecentFilesRoute : AppRoute {
    override val screenRoute: ScreenRoute = ScreenRoute.RECENT_FILES
    private val kSerializer: KSerializer<RecentFilesRoute> =
        RouteObjectSerializer("com.dsc.pdfmaster.ui.navigation.RecentFilesRoute", this)
    fun serializer(): KSerializer<RecentFilesRoute> = kSerializer
}

@Serializable
data object PdfViewerRoute : AppRoute {
    override val screenRoute: ScreenRoute = ScreenRoute.PDF_VIEWER
    private val kSerializer: KSerializer<PdfViewerRoute> =
        RouteObjectSerializer("com.dsc.pdfmaster.ui.navigation.PdfViewerRoute", this)
    fun serializer(): KSerializer<PdfViewerRoute> = kSerializer
}

@Serializable
data object CreatePdfRoute : AppRoute {
    override val screenRoute: ScreenRoute = ScreenRoute.CREATE_PDF
    private val kSerializer: KSerializer<CreatePdfRoute> =
        RouteObjectSerializer("com.dsc.pdfmaster.ui.navigation.CreatePdfRoute", this)
    fun serializer(): KSerializer<CreatePdfRoute> = kSerializer
}

@Serializable
data object ConvertPdfRoute : AppRoute {
    override val screenRoute: ScreenRoute = ScreenRoute.CONVERT_PDF
    private val kSerializer: KSerializer<ConvertPdfRoute> =
        RouteObjectSerializer("com.dsc.pdfmaster.ui.navigation.ConvertPdfRoute", this)
    fun serializer(): KSerializer<ConvertPdfRoute> = kSerializer
}

@Serializable
data object ExtractPagesRoute : AppRoute {
    override val screenRoute: ScreenRoute = ScreenRoute.EXTRACT_PAGES
    private val kSerializer: KSerializer<ExtractPagesRoute> =
        RouteObjectSerializer("com.dsc.pdfmaster.ui.navigation.ExtractPagesRoute", this)
    fun serializer(): KSerializer<ExtractPagesRoute> = kSerializer
}

@Serializable
data object MergePdfRoute : AppRoute {
    override val screenRoute: ScreenRoute = ScreenRoute.MERGE_PDF
    private val kSerializer: KSerializer<MergePdfRoute> =
        RouteObjectSerializer("com.dsc.pdfmaster.ui.navigation.MergePdfRoute", this)
    fun serializer(): KSerializer<MergePdfRoute> = kSerializer
}

@Serializable
data object SplitPdfRoute : AppRoute {
    override val screenRoute: ScreenRoute = ScreenRoute.SPLIT_PDF
    private val kSerializer: KSerializer<SplitPdfRoute> =
        RouteObjectSerializer("com.dsc.pdfmaster.ui.navigation.SplitPdfRoute", this)
    fun serializer(): KSerializer<SplitPdfRoute> = kSerializer
}

@Serializable
data object CompressPdfRoute : AppRoute {
    override val screenRoute: ScreenRoute = ScreenRoute.COMPRESS_PDF
    private val kSerializer: KSerializer<CompressPdfRoute> =
        RouteObjectSerializer("com.dsc.pdfmaster.ui.navigation.CompressPdfRoute", this)
    fun serializer(): KSerializer<CompressPdfRoute> = kSerializer
}

@Serializable
data object ImagesToPdfRoute : AppRoute {
    override val screenRoute: ScreenRoute = ScreenRoute.IMAGES_TO_PDF
    private val kSerializer: KSerializer<ImagesToPdfRoute> =
        RouteObjectSerializer("com.dsc.pdfmaster.ui.navigation.ImagesToPdfRoute", this)
    fun serializer(): KSerializer<ImagesToPdfRoute> = kSerializer
}

@Serializable
data object ManagePagesRoute : AppRoute {
    override val screenRoute: ScreenRoute = ScreenRoute.MANAGE_PAGES
    private val kSerializer: KSerializer<ManagePagesRoute> =
        RouteObjectSerializer("com.dsc.pdfmaster.ui.navigation.ManagePagesRoute", this)
    fun serializer(): KSerializer<ManagePagesRoute> = kSerializer
}

@Serializable
data object PasswordProtectRoute : AppRoute {
    override val screenRoute: ScreenRoute = ScreenRoute.PASSWORD_PROTECT
    private val kSerializer: KSerializer<PasswordProtectRoute> =
        RouteObjectSerializer("com.dsc.pdfmaster.ui.navigation.PasswordProtectRoute", this)
    fun serializer(): KSerializer<PasswordProtectRoute> = kSerializer
}

@Serializable
data object RemovePasswordRoute : AppRoute {
    override val screenRoute: ScreenRoute = ScreenRoute.REMOVE_PASSWORD
    private val kSerializer: KSerializer<RemovePasswordRoute> =
        RouteObjectSerializer("com.dsc.pdfmaster.ui.navigation.RemovePasswordRoute", this)
    fun serializer(): KSerializer<RemovePasswordRoute> = kSerializer
}

@Serializable
data object SignPdfRoute : AppRoute {
    override val screenRoute: ScreenRoute = ScreenRoute.SIGN_PDF
    private val kSerializer: KSerializer<SignPdfRoute> =
        RouteObjectSerializer("com.dsc.pdfmaster.ui.navigation.SignPdfRoute", this)
    fun serializer(): KSerializer<SignPdfRoute> = kSerializer
}

@Serializable
data object AnnotatePdfRoute : AppRoute {
    override val screenRoute: ScreenRoute = ScreenRoute.ANNOTATE_PDF
    private val kSerializer: KSerializer<AnnotatePdfRoute> =
        RouteObjectSerializer("com.dsc.pdfmaster.ui.navigation.AnnotatePdfRoute", this)
    fun serializer(): KSerializer<AnnotatePdfRoute> = kSerializer
}

@Serializable
data object WatermarkPdfRoute : AppRoute {
    override val screenRoute: ScreenRoute = ScreenRoute.WATERMARK_PDF
    private val kSerializer: KSerializer<WatermarkPdfRoute> =
        RouteObjectSerializer("com.dsc.pdfmaster.ui.navigation.WatermarkPdfRoute", this)
    fun serializer(): KSerializer<WatermarkPdfRoute> = kSerializer
}

@Serializable
data object OcrPdfRoute : AppRoute {
    override val screenRoute: ScreenRoute = ScreenRoute.OCR_PDF
    private val kSerializer: KSerializer<OcrPdfRoute> =
        RouteObjectSerializer("com.dsc.pdfmaster.ui.navigation.OcrPdfRoute", this)
    fun serializer(): KSerializer<OcrPdfRoute> = kSerializer
}

@Serializable
data object DocumentScannerRoute : AppRoute {
    override val screenRoute: ScreenRoute = ScreenRoute.DOCUMENT_SCANNER
    private val kSerializer: KSerializer<DocumentScannerRoute> =
        RouteObjectSerializer("com.dsc.pdfmaster.ui.navigation.DocumentScannerRoute", this)
    fun serializer(): KSerializer<DocumentScannerRoute> = kSerializer
}

@Serializable
data object SettingsRoute : AppRoute {
    override val screenRoute: ScreenRoute = ScreenRoute.SETTINGS
    private val kSerializer: KSerializer<SettingsRoute> =
        RouteObjectSerializer("com.dsc.pdfmaster.ui.navigation.SettingsRoute", this)
    fun serializer(): KSerializer<SettingsRoute> = kSerializer
}

fun ScreenRoute.toAppRoute(): AppRoute = when (this) {
    ScreenRoute.HOME -> HomeRoute
    ScreenRoute.RECENT_FILES -> RecentFilesRoute
    ScreenRoute.PDF_VIEWER -> PdfViewerRoute
    ScreenRoute.CREATE_PDF -> CreatePdfRoute
    ScreenRoute.CONVERT_PDF -> ConvertPdfRoute
    ScreenRoute.EXTRACT_PAGES -> ExtractPagesRoute
    ScreenRoute.MERGE_PDF -> MergePdfRoute
    ScreenRoute.SPLIT_PDF -> SplitPdfRoute
    ScreenRoute.COMPRESS_PDF -> CompressPdfRoute
    ScreenRoute.IMAGES_TO_PDF -> ImagesToPdfRoute
    ScreenRoute.MANAGE_PAGES -> ManagePagesRoute
    ScreenRoute.PASSWORD_PROTECT -> PasswordProtectRoute
    ScreenRoute.REMOVE_PASSWORD -> RemovePasswordRoute
    ScreenRoute.SIGN_PDF -> SignPdfRoute
    ScreenRoute.ANNOTATE_PDF -> AnnotatePdfRoute
    ScreenRoute.WATERMARK_PDF -> WatermarkPdfRoute
    ScreenRoute.OCR_PDF -> OcrPdfRoute
    ScreenRoute.DOCUMENT_SCANNER -> DocumentScannerRoute
    ScreenRoute.SETTINGS -> SettingsRoute
}

fun NavDestination?.toScreenRoute(): ScreenRoute? {
    if (this == null) return null
    return when {
        hasRoute<HomeRoute>() -> ScreenRoute.HOME
        hasRoute<RecentFilesRoute>() -> ScreenRoute.RECENT_FILES
        hasRoute<PdfViewerRoute>() -> ScreenRoute.PDF_VIEWER
        hasRoute<CreatePdfRoute>() -> ScreenRoute.CREATE_PDF
        hasRoute<ConvertPdfRoute>() -> ScreenRoute.CONVERT_PDF
        hasRoute<ExtractPagesRoute>() -> ScreenRoute.EXTRACT_PAGES
        hasRoute<MergePdfRoute>() -> ScreenRoute.MERGE_PDF
        hasRoute<SplitPdfRoute>() -> ScreenRoute.SPLIT_PDF
        hasRoute<CompressPdfRoute>() -> ScreenRoute.COMPRESS_PDF
        hasRoute<ImagesToPdfRoute>() -> ScreenRoute.IMAGES_TO_PDF
        hasRoute<ManagePagesRoute>() -> ScreenRoute.MANAGE_PAGES
        hasRoute<PasswordProtectRoute>() -> ScreenRoute.PASSWORD_PROTECT
        hasRoute<RemovePasswordRoute>() -> ScreenRoute.REMOVE_PASSWORD
        hasRoute<SignPdfRoute>() -> ScreenRoute.SIGN_PDF
        hasRoute<AnnotatePdfRoute>() -> ScreenRoute.ANNOTATE_PDF
        hasRoute<WatermarkPdfRoute>() -> ScreenRoute.WATERMARK_PDF
        hasRoute<OcrPdfRoute>() -> ScreenRoute.OCR_PDF
        hasRoute<DocumentScannerRoute>() -> ScreenRoute.DOCUMENT_SCANNER
        hasRoute<SettingsRoute>() -> ScreenRoute.SETTINGS
        else -> null
    }
}
