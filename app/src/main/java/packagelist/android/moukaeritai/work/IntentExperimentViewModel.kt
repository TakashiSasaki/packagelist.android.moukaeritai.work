package packagelist.android.moukaeritai.work

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class ExportStatus {
    IDLE,
    RUNNING,
    READY,
    INTERNAL_SAVED,
    EXPORTING,
    EXPORTED,
    FAILED
}

data class ExportState(
    val status: ExportStatus = ExportStatus.IDLE,
    val progressStage: String = "Idle",
    val fileName: String? = null,
    val internalPath: String? = null,
    val jsonByteSize: Long = 0,
    val savedAtTimestamp: Long? = null,
    val internalSaveSuccess: Boolean = false,
    val externalExportSuccess: Boolean = false,
    val lastError: String? = null,
    val summaryMetrics: SurfaceDiagnosticSummary? = null,
    val validationStatus: String = "NOT_RUN",
    val validationErrorCount: Int = 0,
    val validationErrorSummary: String? = null,
    val catalogCandidateCount: Int = 0,
    val schemaVersion: Int = 5,
    val isInternalFileAvailable: Boolean = false
)

class IntentExperimentViewModel : ViewModel() {
    private val _state = MutableStateFlow(ExportState())
    val state: StateFlow<ExportState> = _state.asStateFlow()

    private var reportJson: String? = null

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val reportAdapter = moshi.adapter(IntentSurfaceReport::class.java).indent("  ")

    fun runDiagnostics(context: Context, andSave: Boolean, onPromptSave: ((String) -> Unit)? = null) {
        val currentState = _state.value
        if (currentState.status == ExportStatus.RUNNING || currentState.status == ExportStatus.EXPORTING) return
        
        _state.update { 
            ExportState(
                status = ExportStatus.RUNNING,
                progressStage = "Starting diagnostics..."
            )
        }
        reportJson = null

        val datetimePart = java.text.SimpleDateFormat("yyyyMMdd-HHmmss'Z'", java.util.Locale.US).apply { 
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date())
        val runIdShort = (10000..99999).random().toString()
        val runId = "$datetimePart-$runIdShort"

        val runner = IntentSurfaceDiscoveryRunner(context.applicationContext)
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val slug = Build.DEVICE.replace(Regex("[^a-zA-Z0-9]"), "-").lowercase()
                val fName = "MOUKAERITAI_INTENT_SURFACE__${datetimePart}__sdk${Build.VERSION.SDK_INT}__${slug}__${runIdShort}.json"
                _state.update { it.copy(fileName = fName) }

                val report = runner.runDiscovery(runId, fName) { stage ->
                    _state.update { it.copy(progressStage = stage) }
                }

                _state.update { it.copy(progressStage = "Semantic Validation...") }
                val validator = IntentSurfaceReportSemanticValidator()
                val validationResult = validator.validate(report)
                
                val catalogCount = report.intent_invocation_catalog?.candidate_count ?: 0

                _state.update { it.copy(progressStage = "Formatting JSON...") }
                val jsonStr = reportAdapter.toJson(report)
                val jsonBytes = jsonStr.toByteArray(Charsets.UTF_8)
                reportJson = jsonStr
                
                val valStatusStr = if (validationResult.isValid) "VALID" else "INVALID"

                if (!validationResult.isValid) {
                    _state.update { 
                        it.copy(
                            status = ExportStatus.FAILED,
                            lastError = "Validation Failed: " + validationResult.errors.firstOrNull(),
                            progressStage = "Failed during semantic validation.",
                            validationStatus = valStatusStr,
                            validationErrorCount = validationResult.errors.size,
                            validationErrorSummary = validationResult.errors.joinToString("\n"),
                            catalogCandidateCount = catalogCount,
                            schemaVersion = report.schema_version
                        )
                    }
                    return@launch
                }

                // INTERNAL SAVE - Requirement 1
                try {
                    val dir = File(context.filesDir, "intent_surface_reports")
                    if (!dir.exists()) dir.mkdirs()
                    val file = File(dir, fName)
                    file.writeBytes(jsonBytes)
                    
                    _state.update { 
                        it.copy(
                            status = ExportStatus.INTERNAL_SAVED,
                            progressStage = "Saved internally.",
                            internalPath = file.absolutePath,
                            jsonByteSize = jsonBytes.size.toLong(),
                            savedAtTimestamp = System.currentTimeMillis(),
                            internalSaveSuccess = true,
                            isInternalFileAvailable = true,
                            summaryMetrics = report.summary,
                            validationStatus = valStatusStr,
                            validationErrorCount = validationResult.errors.size,
                            validationErrorSummary = if (validationResult.isValid) null else validationResult.errors.joinToString("\n"),
                            catalogCandidateCount = catalogCount,
                            schemaVersion = report.schema_version
                        )
                    }
                    if (andSave) {
                        withContext(Dispatchers.Main) {
                            onPromptSave?.invoke(fName)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Diagnostics", "Internal save failed", e)
                    _state.update {
                        it.copy(
                            status = ExportStatus.FAILED,
                            lastError = "Internal Save Error: ${e.message}",
                            progressStage = "Failed to save internally.",
                            jsonByteSize = jsonBytes.size.toLong(),
                            internalSaveSuccess = false,
                            isInternalFileAvailable = false,
                            summaryMetrics = report.summary,
                            validationStatus = valStatusStr,
                            validationErrorCount = validationResult.errors.size,
                            validationErrorSummary = if (validationResult.isValid) null else validationResult.errors.joinToString("\n"),
                            catalogCandidateCount = catalogCount,
                            schemaVersion = report.schema_version
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("Diagnostics", "Run failed", e)
                _state.update { 
                    it.copy(
                        status = ExportStatus.FAILED,
                        lastError = "Error: ${e.message}",
                        progressStage = "Failed during generation."
                    )
                }
            }
        }
    }

    fun exportLastInternalReportToUri(context: Context, uri: Uri) {
        val path = _state.value.internalPath
        if (path == null) {
             _state.update { 
                 it.copy(status = ExportStatus.FAILED, lastError = "No internal file path available", progressStage = "Failed to export.") 
             }
             return
        }
        val file = File(path)
        if (!file.exists()) {
             _state.update { 
                 it.copy(status = ExportStatus.FAILED, lastError = "Internal save file not found", progressStage = "Failed to export.") 
             }
             return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(status = ExportStatus.EXPORTING, progressStage = "Saving to file...") }
            try {
                val outputStream = context.contentResolver.openOutputStream(uri)
                if (outputStream == null) {
                    _state.update { 
                        it.copy(
                            status = ExportStatus.FAILED,
                            lastError = "Save Error: Cannot open output stream",
                            progressStage = "Failed to write external file.",
                            externalExportSuccess = false
                        )
                    }
                    return@launch
                }
                
                outputStream.use { out ->
                    file.inputStream().use { input ->
                        input.copyTo(out)
                    }
                }
                
                _state.update { 
                    it.copy(
                        status = ExportStatus.EXPORTED,
                        progressStage = "Exported successfully.",
                        externalExportSuccess = true
                    )
                }
            } catch (e: Exception) {
                Log.e("Diagnostics", "Save failed", e)
                _state.update { 
                    it.copy(
                        status = ExportStatus.FAILED,
                        lastError = "Export Error: ${e.message}",
                        progressStage = "Failed during file write.",
                        externalExportSuccess = false
                    )
                }
            }
        }
    }

    fun saveReportToFile(context: Context, uri: Uri) {
        exportLastInternalReportToUri(context, uri)
    }
}


