package com.example.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import com.example.permissions.PermissionManager

data class ContactMatch(
    val id: String,
    val name: String,
    val phoneNumber: String
)

class CallContactTool : Tool {
    override val name = "CallContactTool"
    override val description = "Looks up a contact in the phone directory and initiates a phone call or dialer."
    override val parameters = listOf(
        ToolParameter(name = "contactName", type = "string", description = "The name of the contact to call"),
        ToolParameter(name = "phoneNumber", type = "string", description = "Direct phone number if provided", required = false)
    )
    override val requiredPermissions = listOf(
        Manifest.permission.READ_CONTACTS
    )
    override val riskLevel = RiskLevel.MEDIUM
    override val requiresConfirmation = true

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val directNumber = params["phoneNumber"]?.toString()?.trim()
        val contactName = params["contactName"]?.toString()?.trim() ?: ""

        if (directNumber != null && directNumber.isNotBlank()) {
            return makeCall(context, directNumber, contactName.ifBlank { directNumber })
        }

        if (contactName.isBlank()) {
            return ToolResult.error("Please specify who you want to call.")
        }

        if (!PermissionManager.hasPermission(context, Manifest.permission.READ_CONTACTS)) {
            return ToolResult.error(
                message = "Contacts permission is required to find \"$contactName\".",
                errorCode = "PERMISSION_DENIED",
                data = mapOf("permission" to Manifest.permission.READ_CONTACTS)
            )
        }

        val matches = searchContacts(context, contactName)
        return when {
            matches.isEmpty() -> {
                ToolResult.error(
                    message = "I couldn't find any contact named \"$contactName\" in your phone.",
                    errorCode = "CONTACT_NOT_FOUND"
                )
            }
            matches.size == 1 -> {
                val match = matches.first()
                makeCall(context, match.phoneNumber, match.name)
            }
            else -> {
                val namesWithNumbers = matches.take(3).joinToString(", ") { "${it.name} (${it.phoneNumber})" }
                ToolResult.error(
                    message = "I found multiple contacts matching \"$contactName\": $namesWithNumbers. Which one would you like to call?",
                    errorCode = "AMBIGUOUS_CONTACTS",
                    data = mapOf("matches" to matches.map { mapOf("name" to it.name, "number" to it.phoneNumber) })
                )
            }
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
            // Content provider query failure
        }
        return results
    }

    private fun makeCall(context: Context, phoneNumber: String, displayName: String): ToolResult {
        return try {
            val hasCallPermission = PermissionManager.hasPermission(context, Manifest.permission.CALL_PHONE)
            val intent = if (hasCallPermission) {
                Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
            ToolResult.ok(
                message = if (hasCallPermission) "Calling $displayName ($phoneNumber)..." else "Opening dialer for $displayName ($phoneNumber)...",
                data = mapOf("phoneNumber" to phoneNumber, "name" to displayName)
            )
        } catch (e: Exception) {
            ToolResult.error("Unable to initiate call: ${e.localizedMessage}")
        }
    }
}
