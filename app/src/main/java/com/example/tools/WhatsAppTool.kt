package com.example.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import com.example.permissions.PermissionManager

class WhatsAppTool : Tool {
    override val name = "WhatsAppTool"
    override val description = "Automates WhatsApp actions including opening chats, composing messages to contacts, and sending message drafts."
    override val parameters = listOf(
        ToolParameter(name = "action", type = "string", description = "Action to perform: 'open', 'message', or 'chat'"),
        ToolParameter(name = "contactName", type = "string", description = "Target contact name (e.g. Rahul)", required = false),
        ToolParameter(name = "message", type = "string", description = "Text message to send", required = false),
        ToolParameter(name = "phoneNumber", type = "string", description = "Target phone number if known", required = false)
    )
    override val requiredPermissions = listOf(
        Manifest.permission.READ_CONTACTS
    )
    override val riskLevel = RiskLevel.MEDIUM
    override val requiresConfirmation = true

    companion object {
        const val WHATSAPP_PKG = "com.whatsapp"
        const val WHATSAPP_BUSINESS_PKG = "com.whatsapp.w4b"
    }

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val pm = context.packageManager
        val isWhatsAppInstalled = isPackageInstalled(pm, WHATSAPP_PKG) || isPackageInstalled(pm, WHATSAPP_BUSINESS_PKG)
        val targetPkg = if (isPackageInstalled(pm, WHATSAPP_PKG)) WHATSAPP_PKG else WHATSAPP_BUSINESS_PKG

        val action = params["action"]?.toString()?.trim()?.lowercase() ?: "open"
        val contactName = params["contactName"]?.toString()?.trim() ?: ""
        val message = params["message"]?.toString()?.trim() ?: ""
        var phoneNumber = params["phoneNumber"]?.toString()?.trim() ?: ""

        // Case 1: Simply opening WhatsApp
        if (contactName.isBlank() && message.isBlank() && action == "open") {
            if (!isWhatsAppInstalled) {
                return ToolResult.error(
                    message = "WhatsApp is not installed on this device.",
                    errorCode = "APP_NOT_INSTALLED"
                )
            }
            val launchIntent = pm.getLaunchIntentForPackage(targetPkg)
            return if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                ToolResult.ok("WhatsApp opened.")
            } else {
                ToolResult.error("Unable to launch WhatsApp.")
            }
        }

        // Case 2: Resolving Contact
        if (phoneNumber.isBlank() && contactName.isNotBlank()) {
            if (!PermissionManager.hasPermission(context, Manifest.permission.READ_CONTACTS)) {
                return ToolResult.error(
                    message = "Contacts permission is required to find \"$contactName\" for WhatsApp.",
                    errorCode = "PERMISSION_DENIED",
                    data = mapOf("permission" to Manifest.permission.READ_CONTACTS)
                )
            }

            val matches = searchContacts(context, contactName)
            when {
                matches.isEmpty() -> {
                    return ToolResult.error(
                        message = "I couldn't find \"$contactName\" in your contacts.",
                        errorCode = "CONTACT_NOT_FOUND"
                    )
                }
                matches.size > 1 -> {
                    val summary = matches.take(3).joinToString(", ") { "${it.name} (${it.phoneNumber})" }
                    return ToolResult.requiresUserAction(
                        message = "I found multiple contacts named $contactName: $summary. Which one do you mean?",
                        data = mapOf("matches" to matches.map { mapOf("name" to it.name, "number" to it.phoneNumber) })
                    )
                }
                else -> {
                    phoneNumber = matches.first().phoneNumber
                }
            }
        }

        // Clean phone number (strip spaces, hyphens, parentheses)
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")

        if (!isWhatsAppInstalled) {
            // Can open web / api.whatsapp.com fallback in browser
            return if (cleanNumber.isNotBlank()) {
                val encodedMsg = Uri.encode(message)
                val webUri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber&text=$encodedMsg")
                val intent = Intent(Intent.ACTION_VIEW, webUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ToolResult.partial(
                    message = "WhatsApp app is not installed. Opening WhatsApp Web for $cleanNumber with message ready.",
                    data = mapOf("contact" to contactName, "number" to cleanNumber)
                )
            } else {
                ToolResult.error("WhatsApp is not installed on this device.", errorCode = "APP_NOT_INSTALLED")
            }
        }

        // Open chat with message prepared
        return try {
            if (cleanNumber.isNotBlank()) {
                val encodedMsg = Uri.encode(message)
                val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber&text=$encodedMsg")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage(targetPkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                val targetDisplay = if (contactName.isNotBlank()) contactName else cleanNumber
                ToolResult.partial(
                    message = "WhatsApp is open for $targetDisplay and the message is ready. Please tap Send.",
                    data = mapOf("recipient" to targetDisplay, "message" to message)
                )
            } else {
                // Share intent targeted to WhatsApp
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    setPackage(targetPkg)
                    putExtra(Intent.EXTRA_TEXT, message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(sendIntent)
                ToolResult.partial(
                    message = "WhatsApp is open with your message ready. Select contact to send.",
                    data = mapOf("message" to message)
                )
            }
        } catch (e: Exception) {
            ToolResult.error("Failed to open WhatsApp: ${e.localizedMessage}")
        }
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun searchContacts(context: Context, query: String): List<ContactMatch> {
        val results = mutableListOf<ContactMatch>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (cursor.moveToNext() && results.size < 5) {
                    val id = if (idIndex >= 0) cursor.getString(idIndex) else ""
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) else query
                    val number = if (numIndex >= 0) cursor.getString(numIndex) else ""
                    if (number.isNotBlank()) {
                        results.add(ContactMatch(id = id, name = name, phoneNumber = number))
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore query failure
        }
        return results
    }
}
