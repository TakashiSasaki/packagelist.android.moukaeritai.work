package com.example

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PackageListUiState(
    val packageNames: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val isExporting: Boolean = false,
    val errorMessage: String? = null,
    val lastExportMessage: String? = null,
    val snapshot: PackageSnapshot? = null
)

class PackageListViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PackageListUiState())
    val uiState: StateFlow<PackageListUiState> = _uiState.asStateFlow()

    fun loadPackages(context: Context) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null, lastExportMessage = null) }
        viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.Default) {
                    PackageListRepository.getVisiblePackageNames(context)
                }
                val snapshot = PackageListRepository.createSnapshot(context, list)
                _uiState.update {
                    it.copy(
                        packageNames = list,
                        snapshot = snapshot,
                        isLoading = false,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to load visible packages.\n${e.javaClass.name}: ${e.message}"
                    )
                }
            }
        }
    }

    fun exportCsv(context: Context, uri: Uri) {
        val currentSnapshot = _uiState.value.snapshot ?: return
        _uiState.update { it.copy(isExporting = true, lastExportMessage = null, errorMessage = null) }
        viewModelScope.launch {
            try {
                val csvContent = withContext(Dispatchers.Default) {
                    generateCsvData(currentSnapshot)
                }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(csvContent.toByteArray(Charsets.UTF_8))
                    } ?: throw Exception("Failed to open output stream for selected location.")
                }
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        lastExportMessage = "Exported"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        errorMessage = "Failed to export CSV.\n${e.javaClass.name}: ${e.message}"
                    )
                }
            }
        }
    }

    private fun generateCsvData(snapshot: PackageSnapshot): String {
        val sb = java.lang.StringBuilder()
        sb.append("generated_at_epoch_millis,generated_at_iso8601,app_package_name,app_version_name,android_sdk_int,android_release,target_sdk_version,query_all_packages_granted,visible_package_count,package_name\n")

        val count = snapshot.packageNames.size
        val ver = snapshot.appVersionName ?: ""
        val queryAll = snapshot.queryAllPackagesGranted.toString()

        for (pkgName in snapshot.packageNames) {
            sb.append(snapshot.generatedAtEpochMillis).append(",")
            sb.append(escapeCsvField(snapshot.generatedAtIso8601)).append(",")
            sb.append(escapeCsvField(snapshot.appPackageName)).append(",")
            sb.append(escapeCsvField(ver)).append(",")
            sb.append(snapshot.androidSdkInt).append(",")
            sb.append(escapeCsvField(snapshot.androidRelease)).append(",")
            sb.append(snapshot.targetSdkVersion).append(",")
            sb.append(queryAll).append(",")
            sb.append(count).append(",")
            sb.append(escapeCsvField(pkgName)).append("\n")
        }
        return sb.toString()
    }

    private fun escapeCsvField(field: String): String {
        if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            return "\"" + field.replace("\"", "\"\"") + "\""
        }
        return field
    }

    fun getDefaultFileName(): String {
        val currentSnapshot = _uiState.value.snapshot ?: return "visible_packages.csv"
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val timeStr = sdf.format(Date(currentSnapshot.generatedAtEpochMillis))
        val visibility = if (currentSnapshot.queryAllPackagesGranted) "broad" else "limited"
        return "visible_packages_${timeStr}_sdk${currentSnapshot.androidSdkInt}_target${currentSnapshot.targetSdkVersion}_$visibility.csv"
    }

    fun clearExportMessage() {
        _uiState.update { it.copy(lastExportMessage = null) }
    }
}
