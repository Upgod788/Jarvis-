package com.example.update

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class UpdateManifest(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val minimumSupportedVersionCode: Int,
    val downloadUrl: String,
    val releaseNotes: List<String>,
    val mandatory: Boolean,
    val sha256: String,
    val fileSize: Long,
    val channel: String = "stable",
    val publishedAt: String = "",
    val releaseId: String = ""
) {
    val changelog: List<String> get() = releaseNotes

    fun toJson(): String {
        val json = JSONObject()
        json.put("latestVersionCode", latestVersionCode)
        json.put("latestVersionName", latestVersionName)
        json.put("minimumSupportedVersionCode", minimumSupportedVersionCode)
        json.put("downloadUrl", downloadUrl)
        val notesArray = JSONArray()
        for (note in releaseNotes) {
            notesArray.put(note)
        }
        json.put("releaseNotes", notesArray)
        json.put("mandatory", mandatory)
        json.put("sha256", sha256)
        json.put("fileSize", fileSize)
        json.put("channel", channel)
        json.put("publishedAt", publishedAt)
        json.put("releaseId", releaseId)
        return json.toString()
    }

    companion object {
        @Throws(JSONException::class)
        fun fromJson(jsonStr: String): UpdateManifest {
            val json = JSONObject(jsonStr)
            val notesList = mutableListOf<String>()
            if (json.has("releaseNotes")) {
                val array = json.getJSONArray("releaseNotes")
                for (i in 0 until array.length()) {
                    notesList.add(array.getString(i))
                }
            }
            return UpdateManifest(
                latestVersionCode = json.optInt("latestVersionCode", 1),
                latestVersionName = json.optString("latestVersionName", "1.0.0"),
                minimumSupportedVersionCode = json.optInt("minimumSupportedVersionCode", 1),
                downloadUrl = json.optString("downloadUrl", ""),
                releaseNotes = notesList,
                mandatory = json.optBoolean("mandatory", false),
                sha256 = json.optString("sha256", ""),
                fileSize = json.optLong("fileSize", 0L),
                channel = json.optString("channel", "stable"),
                publishedAt = json.optString("publishedAt", ""),
                releaseId = json.optString("releaseId", "")
            )
        }
    }
}
