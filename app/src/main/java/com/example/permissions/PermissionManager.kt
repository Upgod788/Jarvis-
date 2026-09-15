package com.example.permissions

import android.content.Context
import androidx.core.content.ContextCompat

object PermissionManager {
    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun hasAllPermissions(context: Context, permissions: List<String>): Boolean {
        return permissions.all { hasPermission(context, it) }
    }

    fun getMissingPermissions(context: Context, permissions: List<String>): List<String> {
        return permissions.filter { !hasPermission(context, it) }
    }

    fun getPermissionLabel(permission: String): String {
        return when (permission) {
            android.Manifest.permission.RECORD_AUDIO -> "Microphone"
            android.Manifest.permission.CALL_PHONE -> "Phone Calls"
            android.Manifest.permission.SEND_SMS -> "Send SMS"
            android.Manifest.permission.READ_CONTACTS -> "Contacts"
            android.Manifest.permission.CAMERA -> "Camera"
            else -> permission.substringAfterLast(".")
        }
    }
}
