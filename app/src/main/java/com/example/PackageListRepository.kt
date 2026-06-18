package com.example

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PackageSnapshot(
    val generatedAtEpochMillis: Long,
    val generatedAtIso8601: String,
    val appPackageName: String,
    val appVersionName: String?,
    val androidSdkInt: Int,
    val androidRelease: String,
    val targetSdkVersion: Int,
    val queryAllPackagesGranted: Boolean,
    val packageNames: List<String>
)

object PackageListRepository {

    fun getVisiblePackageNames(context: Context): List<String> {
        val pm = context.packageManager
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(0)
        }
        return packages.map { it.packageName }
            .distinct()
            .sorted()
    }

    fun createSnapshot(context: Context, packageNames: List<String>): PackageSnapshot {
        val epoch = System.currentTimeMillis()
        val iso8601 = formatEpochToIso8601(epoch)
        val appPkg = context.packageName
        
        val appVer = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(appPkg, PackageManager.PackageInfoFlags.of(0)).versionName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(appPkg, 0).versionName
            }
        } catch (e: Exception) {
            "1.0"
        }

        val sdkInt = Build.VERSION.SDK_INT
        val release = Build.VERSION.RELEASE
        val targetSdk = context.applicationInfo.targetSdkVersion
        
        val queryAllPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.checkSelfPermission("android.permission.QUERY_ALL_PACKAGES") == PackageManager.PERMISSION_GRANTED
        } else {
            false
        }

        return PackageSnapshot(
            generatedAtEpochMillis = epoch,
            generatedAtIso8601 = iso8601,
            appPackageName = appPkg,
            appVersionName = appVer,
            androidSdkInt = sdkInt,
            androidRelease = release,
            targetSdkVersion = targetSdk,
            queryAllPackagesGranted = queryAllPackages,
            packageNames = packageNames
        )
    }

    private fun formatEpochToIso8601(epochMillis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        return sdf.format(Date(epochMillis))
    }
}
