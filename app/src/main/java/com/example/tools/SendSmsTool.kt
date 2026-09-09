package com.example.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.SmsManager
import com.example.permissions.PermissionManager

class SendSmsTool : Tool {
    override val name = "SendSmsTool"
    override val description = "Prepares or sends an SMS text message to a contact or phone number."
    override val parameters = listOf(
        ToolParameter(name = "recipient", type = "string", description = "Contact name or phone number"),
        ToolParameter(name = "message", type = "string", description = "The text content of the SMS"),
        ToolParameter(name = "platform", type = "string", description = "'sms' or 'whatsapp'", required = false)
    )
    override val requiredPermissions = listOf(Manifest.permission.SEND_SMS)
    override val riskLevel = RiskLevel.MEDIUM
    override val requiresConfirmation = true

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val recipient = params["recipient"]?.toString()?.trim()
            ?: return ToolResult.error("Recipient is required to send a message.")
        val message = params["message"]?.toString()?.trim()
            ?: return ToolResult.error("Message text is empty.")
        val platform = params["platform"]?.toString()?.lowercase() ?: "sms"

        if (platform == "whatsapp") {
            return openWhatsAppMessage(context, recipient, message)
        }

        // Check if recipient is a phone number or contact name
        val isNumeric = recipient.matches(Regex("^[+0-9\\-\\s()]+$"))
        val (phoneNumber, displayName) = if (isNumeric) {
            Pair(recipient, recipient)
        } else {
            resolveContactNumber(context, recipient)
                ?: return ToolResult.error("Could not find phone number for \"$recipient\". Please check contacts.")
        }

        // Try direct SMS if permission granted, otherwise launch default SMS app with prefilled text
        return if (PermissionManager.hasPermission(context, Manifest.permission.SEND_SMS)) {
            try {
                val smsManager: SmsManager = context.getSystemService(SmsManager::class.java)
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
                ToolResult.ok("Message sent to $displayName ($phoneNumber).")
            } catch (e: Exception) {
                // If direct SMS fails due to carrier/carrier restrictions, fall back to SMS intent
                launchSmsApp(context, phoneNumber, message, displayName)
            }
        } else {
            launchSmsApp(context, phoneNumber, message, displayName)
        }
    }

    private fun launchSmsApp(context: Context, phoneNumber: String, message: String, displayName: String): ToolResult {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$phoneNumber")
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolResult.ok("Opening messaging app with message to $displayName.")
        } catch (e: Exception) {
            ToolResult.error("Unable to open SMS app: ${e.localizedMessage}")
        }
    }

    private fun openWhatsAppMessage(context: Context, recipient: String, message: String): ToolResult {
        return try {
            val isInstalled = try {
                context.packageManager.getPackageInfo("com.whatsapp", 0)
                true
            } catch (e: Exception) {
                false
            }

            if (!isInstalled) {
                return ToolResult.error("WhatsApp isn't installed on this phone.")
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://api.whatsapp.com/send?text=" + Uri.encode(message))
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolResult.ok("Opened WhatsApp with your prepared message.")
        } catch (e: Exception) {
            ToolResult.error("Could not open WhatsApp: ${e.localizedMessage}")
        }
    }

    private fun resolveContactNumber(context: Context, contactName: String): Pair<String, String>? {
        if (!PermissionManager.hasPermission(context, Manifest.permission.READ_CONTACTS)) {
            return null
        }
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$contactName%")

        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) else contactName
                    val number = if (numIndex >= 0) cursor.getString(numIndex) else ""
                    if (number.isNotBlank()) {
                        return Pair(number, name)
                    }
                }
            }
        } catch (e: Exception) {
            // Error querying contacts
        }
        return null
    }
}
