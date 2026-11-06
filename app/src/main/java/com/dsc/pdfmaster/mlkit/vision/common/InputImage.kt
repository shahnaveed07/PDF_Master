package com.dsc.pdfmaster.mlkit.vision.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * On-device ML Kit InputImage representation for bundled offline OCR processing.
 */
class InputImage private constructor(
    val bitmap: Bitmap?,
    val rotationDegrees: Int,
    val width: Int,
    val height: Int,
    val sourceUri: Uri? = null
) {
    companion object {
        @JvmStatic
        fun fromBitmap(bitmap: Bitmap, rotationDegrees: Int): InputImage {
            return InputImage(
                bitmap = bitmap,
                rotationDegrees = rotationDegrees,
                width = bitmap.width,
                height = bitmap.height,
                sourceUri = null
            )
        }

        @JvmStatic
        fun fromFilePath(context: Context, imageUri: Uri): InputImage {
            val bmp = decodeBitmapFromUri(context, imageUri)
            return InputImage(
                bitmap = bmp,
                rotationDegrees = 0,
                width = bmp?.width ?: 0,
                height = bmp?.height ?: 0,
                sourceUri = imageUri
            )
        }

        private fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? {
            return try {
                if (uri.scheme == "file") {
                    val path = uri.path ?: return null
                    BitmapFactory.decodeFile(File(path).absolutePath)
                } else {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
