package com.example.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object UpdateSecurityValidator {
    fun isHttpsUrl(url: String): Boolean {
        return url.trim().startsWith("https://", ignoreCase = true)
    }

    fun calculateSha256(file: File): String {
        if (!file.exists() || file.length() == 0L) {
            return ""
        }
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = byteBufferPool()
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val hashBytes = digest.digest()
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun byteBufferPool(): ByteArray = ByteArray(8192)

    fun verifyChecksum(file: File, expectedHash: String): Boolean {
        if (expectedHash.isBlank()) return false
        val computed = calculateSha256(file)
        return computed.equals(expectedHash.trim(), ignoreCase = true)
    }

    fun validateApkArchive(
        context: Context,
        file: File,
        expectedPackageName: String = context.packageName,
        currentVersionCode: Int = 1
    ): ValidationResult {
        if (!file.exists() || file.length() == 0L) {
            return ValidationResult.Invalid("Downloaded APK package is empty or does not exist.")
        }
        val pm = context.packageManager
        val packageInfo: PackageInfo? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNATURES)
            }
        } catch (e: Exception) {
            null
        }

        if (packageInfo == null) {
            return ValidationResult.Invalid("The downloaded file is not a valid Android APK archive or is corrupted.")
        }

        val apkPackage = packageInfo.packageName ?: ""
        if (apkPackage != expectedPackageName) {
            return ValidationResult.Invalid("Package mismatch: expected $expectedPackageName but found $apkPackage. Update rejected.")
        }

        val newVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode
        }

        if (newVersionCode < currentVersionCode) {
            return ValidationResult.Invalid("Version downgrade rejected: new version ($newVersionCode) is older than installed version ($currentVersionCode).")
        }

        return ValidationResult.Valid
    }
}
