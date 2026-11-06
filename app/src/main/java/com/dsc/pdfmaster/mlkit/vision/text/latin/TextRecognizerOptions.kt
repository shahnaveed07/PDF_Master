package com.dsc.pdfmaster.mlkit.vision.text.latin

/**
 * Options for the bundled Latin on-device ML Kit TextRecognizer.
 * Bundled model works 100% offline with no runtime model download required.
 */
class TextRecognizerOptions private constructor(
    val isBundledOfflineModel: Boolean = true
) {
    class Builder {
        fun build(): TextRecognizerOptions = TextRecognizerOptions(isBundledOfflineModel = true)
    }

    companion object {
        @JvmField
        val DEFAULT_OPTIONS: TextRecognizerOptions = Builder().build()

        /**
         * Approximate APK size impact of bundling the Latin ML Kit text recognition model:
         * ~4.6 MB compressed APK download size (~16.2 MB uncompressed native model assets).
         */
        const val BUNDLED_MODEL_COMPRESSED_SIZE_MB: Double = 4.6
        const val BUNDLED_MODEL_UNCOMPRESSED_SIZE_MB: Double = 16.2
    }
}
