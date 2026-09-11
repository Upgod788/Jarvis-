package com.example.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale

sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val reason: String) : ValidationResult
}

object UpdateSecurityValidator {

    fun isHttpsUrl(url: String): Boolean {
        val trimmed = url.trim()
        return trimmed.startsWith("https://", ignoreCase = true)
    }

    fun calculateSha256(file: File): String {
        if (!file.exists() || file.length() == 0L) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val hashBytes = digest.digest()
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyChecksum(file: File, expectedHash: String): Boolean {
        if (expectedHash.isBlank()) return false
        val computed = calculateSha256(file)
        return computed.equals(expectedHash.trim(), ignoreCase = true)
    }

    @Suppress("DEPRECATION")
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
        val packageInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(
                    file.absolutePath,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
                )
            } else {
                pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNATURES)
            }
        } catch (e: Exception) {
            null
        }

        if (packageInfo == null) {
            return ValidationResult.Invalid("The downloaded file is not a valid Android APK archive or is corrupted.")
        }

        // Validate package name
        val apkPackage = packageInfo.packageName
        if (apkPackage != null && apkPackage != expectedPackageName) {
            return ValidationResult.Invalid(
                "Package mismatch: expected $expectedPackageName but found $apkPackage. Update rejected."
            )
        }

        // Prevent downgrade attack
        val newVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode
        }

        if (newVersionCode < currentVersionCode) {
            return ValidationResult.Invalid(
                "Version downgrade rejected: new version ($newVersionCode) is older than installed version ($currentVersionCode)."
            )
        }

        return ValidationResult.Valid
    }
}
