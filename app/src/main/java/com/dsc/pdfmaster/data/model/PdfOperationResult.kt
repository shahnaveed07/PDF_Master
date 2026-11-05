package com.dsc.pdfmaster.data.model

import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.CancellationException
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale

sealed class PdfOperationError(open val userMessage: String) {
    data class CorruptedPdf(
        override val userMessage: String = "This PDF file appears to be corrupted or unreadable."
    ) : PdfOperationError(userMessage)

    data class WrongPassword(
        override val userMessage: String = "Incorrect password. Please check the password and try again."
    ) : PdfOperationError(userMessage)

    data class PasswordRequired(
        override val userMessage: String = "This PDF is password protected. Enter the password to continue."
    ) : PdfOperationError(userMessage)

    data class InsufficientStorage(
        override val userMessage: String = "Insufficient device storage space to complete this operation."
    ) : PdfOperationError(userMessage)

    data class RevokedPermission(
        override val userMessage: String = "Access permission to this file was revoked or expired. Please re-select the file."
    ) : PdfOperationError(userMessage)

    data class FileNotFound(
        override val userMessage: String = "File no longer available. It may have been moved or deleted."
    ) : PdfOperationError(userMessage)

    data class UnsupportedFormat(
        override val userMessage: String = "This file format or encryption type is not supported locally."
    ) : PdfOperationError(userMessage)

    data class Cancelled(
        override val userMessage: String = "Operation was cancelled. Your original files are untouched."
    ) : PdfOperationError(userMessage)

    data class Unknown(
        override val userMessage: String = "Operation could not be completed."
    ) : PdfOperationError(userMessage)

    companion object {
        fun fromThrowable(
            throwable: Throwable,
            fallbackMessage: String = "Operation could not be completed."
        ): PdfOperationError {
            var current: Throwable? = throwable
            while (current != null) {
                if (current is CancellationException) {
                    return Cancelled()
                }
                if (current is InvalidPasswordException) {
                    return WrongPassword()
                }
                if (current is SecurityException) {
                    return RevokedPermission()
                }
                if (current is FileNotFoundException) {
                    return FileNotFound()
                }
                val msg = current.message?.lowercase(Locale.US).orEmpty()
                if (current is IOException &&
                    (msg.contains("enospc") ||
                        msg.contains("no space left") ||
                        msg.contains("disk full") ||
                        msg.contains("out of space") ||
                        msg.contains("storage"))
                ) {
                    return InsufficientStorage()
                }
                if (msg.contains("permission denied") || msg.contains("eacces") || msg.contains("revoked")) {
                    return RevokedPermission()
                }
                if (msg.contains("password")) {
                    return WrongPassword()
                }
                if (msg.contains("corrupt") ||
                    msg.contains("end-of-file") ||
                    msg.contains("missing root object") ||
                    msg.contains("header") ||
                    msg.contains("xref") ||
                    msg.contains("damaged")
                ) {
                    return CorruptedPdf()
                }
                if (msg.contains("unsupported")) {
                    return UnsupportedFormat()
                }
                current = current.cause
            }
            return Unknown(fallbackMessage)
        }
    }
}

sealed class PdfOperationResult<out T> {
    data object Idle : PdfOperationResult<Nothing>()
    data class Loading(
        val message: String,
        val progressPercent: Int = 0
    ) : PdfOperationResult<Nothing>()

    data class Success<out T>(
        val data: T,
        val message: String = ""
    ) : PdfOperationResult<T>()

    data class Failure(
        val error: PdfOperationError
    ) : PdfOperationResult<Nothing>()
}
