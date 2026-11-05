package com.dsc.pdfmaster.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dsc.pdfmaster.data.model.PdfOperationError
import com.dsc.pdfmaster.data.repository.PdfRepository
import com.dsc.pdfmaster.pdfengine.PdfEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class SecurityUiState(
    val protectSourcePdf: File? = null,
    val protectSecurityState: PdfEngine.PdfSecurityState? = null,
    val protectRequiresCurrentPassword: Boolean = false,
    val selectedKeyLengthBits: Int = 256,
    val protectErrorMessage: String? = null,
    val protectError: PdfOperationError? = null,
    val protectResult: PdfEngine.ProtectResult.Success? = null,
    val unprotectSourcePdf: File? = null,
    val unprotectSecurityState: PdfEngine.PdfSecurityState? = null,
    val unprotectErrorMessage: String? = null,
    val unprotectError: PdfOperationError? = null,
    val unprotectResult: PdfEngine.UnprotectResult.Success? = null,
    val isProcessing: Boolean = false,
    val processingMessage: String = ""
)

@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val application: Application,
    private val repository: PdfRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SecurityUiState())
    val uiState: StateFlow<SecurityUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null

    fun setKeyLengthBits(bits: Int) {
        val normalized = if (bits == 128) 128 else 256
        _uiState.update {
            it.copy(
                selectedKeyLengthBits = normalized,
                protectErrorMessage = null,
                protectError = null,
                protectResult = null
            )
        }
    }

    fun clearProtectFeedback() {
        _uiState.update {
            it.copy(
                protectErrorMessage = null,
                protectError = null,
                protectResult = null
            )
        }
    }

    fun clearUnprotectFeedback() {
        _uiState.update {
            it.copy(
                unprotectErrorMessage = null,
                unprotectError = null,
                unprotectResult = null
            )
        }
    }

    fun selectProtectSourcePdf(file: File?) {
        if (file == null) {
            _uiState.update {
                it.copy(
                    protectSourcePdf = null,
                    protectSecurityState = null,
                    protectRequiresCurrentPassword = false,
                    protectErrorMessage = null,
                    protectError = null,
                    protectResult = null
                )
            }
            return
        }
        viewModelScope.launch {
            if (!file.exists() || file.length() == 0L || !PdfEngine.hasPdfHeader(file)) {
                val msg = "Selected file is not a valid PDF document."
                _uiState.update {
                    it.copy(
                        protectSourcePdf = null,
                        protectSecurityState = PdfEngine.PdfSecurityState.InvalidOrCorrupted(msg),
                        protectRequiresCurrentPassword = false,
                        protectErrorMessage = msg,
                        protectError = PdfOperationError.CorruptedPdf(msg),
                        protectResult = null
                    )
                }
                return@launch
            }
            val secState = PdfEngine.inspectPdfSecurity(application, file)
            val requiresCurr = secState is PdfEngine.PdfSecurityState.UserPasswordRequired ||
                secState is PdfEngine.PdfSecurityState.OwnerPasswordRestricted
            val errMsg = when (secState) {
                is PdfEngine.PdfSecurityState.InvalidOrCorrupted -> secState.message
                is PdfEngine.PdfSecurityState.UnsupportedEncryption -> secState.message
                else -> null
            }
            _uiState.update {
                it.copy(
                    protectSourcePdf = file,
                    protectSecurityState = secState,
                    protectRequiresCurrentPassword = requiresCurr,
                    protectErrorMessage = errMsg,
                    protectError = errMsg?.let { msg -> PdfOperationError.CorruptedPdf(msg) },
                    protectResult = null
                )
            }
        }
    }

    fun selectProtectSourcePdfFromUri(uri: Uri, onSelected: ((File?) -> Unit)? = null) {
        viewModelScope.launch {
            val file = repository.getOrCreateManagedFileFromUri(uri)
            if (file != null && file.exists() && PdfEngine.hasPdfHeader(file)) {
                repository.registerPdfFile(file)
                selectProtectSourcePdf(file)
                onSelected?.invoke(file)
            } else {
                val msg = "Selected file is not a valid PDF document."
                _uiState.update {
                    it.copy(
                        protectSourcePdf = null,
                        protectSecurityState = PdfEngine.PdfSecurityState.InvalidOrCorrupted(msg),
                        protectErrorMessage = msg,
                        protectError = PdfOperationError.CorruptedPdf(msg)
                    )
                }
                onSelected?.invoke(null)
            }
        }
    }

    fun executeProtectPdf(
        file: File? = _uiState.value.protectSourcePdf,
        password: String,
        outputName: String = "",
        confirmPassword: String = password,
        currentPassword: String? = null,
        ownerPassword: String? = null,
        confirmOwnerPassword: String? = ownerPassword,
        allowPrinting: Boolean = true,
        allowCopy: Boolean = true,
        keyLengthBits: Int = _uiState.value.selectedKeyLengthBits,
        replaceOriginal: Boolean = false,
        onComplete: ((PdfEngine.ProtectResult) -> Unit)? = null
    ) {
        if (_uiState.value.isProcessing) return
        val targetFile = file ?: _uiState.value.protectSourcePdf
        if (targetFile == null) {
            val msg = "Please select a PDF document to protect."
            _uiState.update {
                it.copy(
                    protectErrorMessage = msg,
                    protectError = PdfOperationError.FileNotFound(msg)
                )
            }
            return
        }

        val effectiveKeyBits = if (keyLengthBits == 128) 128 else 256
        activeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    selectedKeyLengthBits = effectiveKeyBits,
                    protectErrorMessage = null,
                    protectError = null,
                    protectResult = null,
                    processingMessage = "Applying $effectiveKeyBits-bit AES encryption & verifying..."
                )
            }
            try {
                val desiredOutputName = outputName.trim().ifBlank {
                    "${targetFile.nameWithoutExtension}_protected.pdf"
                }
                val result = repository.protectPdfDetailed(
                    pdfFile = targetFile,
                    password = password,
                    confirmPassword = confirmPassword,
                    ownerPassword = ownerPassword,
                    confirmOwnerPassword = confirmOwnerPassword,
                    currentPassword = currentPassword,
                    allowPrinting = allowPrinting,
                    allowCopy = allowCopy,
                    keyLengthBits = effectiveKeyBits,
                    outputName = desiredOutputName,
                    replaceOriginal = replaceOriginal
                )
                when (result) {
                    is PdfEngine.ProtectResult.Success -> {
                        _uiState.update {
                            it.copy(
                                isProcessing = false,
                                protectResult = result,
                                protectSourcePdf = if (replaceOriginal) result.file else it.protectSourcePdf,
                                protectSecurityState = if (replaceOriginal) {
                                    PdfEngine.PdfSecurityState.UserPasswordRequired
                                } else {
                                    it.protectSecurityState
                                },
                                protectRequiresCurrentPassword = if (replaceOriginal) {
                                    true
                                } else {
                                    it.protectRequiresCurrentPassword
                                }
                            )
                        }
                    }
                    is PdfEngine.ProtectResult.Error -> {
                        val reqCurr = result.error == PdfEngine.ProtectError.ALREADY_ENCRYPTED ||
                            result.error == PdfEngine.ProtectError.OWNER_PASSWORD_REQUIRED ||
                            result.error == PdfEngine.ProtectError.INCORRECT_CURRENT_PASSWORD
                        _uiState.update {
                            it.copy(
                                isProcessing = false,
                                protectRequiresCurrentPassword = if (reqCurr) true else it.protectRequiresCurrentPassword,
                                protectErrorMessage = result.message,
                                protectError = PdfOperationError.Unknown(result.message)
                            )
                        }
                    }
                }
                onComplete?.invoke(result)
            } catch (e: CancellationException) {
                val msg = "Encryption cancelled. Original file is unchanged."
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        protectErrorMessage = msg,
                        protectError = PdfOperationError.Cancelled(msg)
                    )
                }
            } catch (t: Throwable) {
                val err = PdfOperationError.fromThrowable(t, "Failed to encrypt PDF")
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        protectErrorMessage = err.userMessage,
                        protectError = err
                    )
                }
            }
        }
    }

    fun cancelActiveOperation() {
        activeJob?.cancel()
        activeJob = null
        _uiState.update { it.copy(isProcessing = false) }
    }
}
