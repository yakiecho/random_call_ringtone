package com.yakiecho.random_call_ringtone

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.preference.PreferenceManager
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import org.json.JSONObject

class PrefManager(private val context: Context) {

    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val keyJsonPrefs = "saved_json"

    private val defaultJson = JSONObject(
        """
        {
            "path": "none",
            "playlists": "none",
            "g": 0
        }
        """
    )

    fun loadJson(): JSONObject {
        val jsonString = preferences.getString(keyJsonPrefs, defaultJson.toString())
        return try {
            val loadedJson = JSONObject(jsonString)
            ensureDefaultStyle(loadedJson)
        } catch (e: Exception) {
            Log.e(globallogtag+prefmanagerogtag, "Error loading JSON: ${e.message}")
            ensureDefaultStyle(JSONObject())
        }
    }

    fun saveJson(newData: JSONObject): Boolean {
        return try {
            val currentJson = loadJson()

            // Обновляем только указанные поля
            newData.keys().forEach { key ->
                if (defaultJson.has(key)) {
                    currentJson.put(key, newData.get(key))
                }
            }

            // Сохраняем полный JSON с соблюдением шаблона
            preferences.edit().putString(keyJsonPrefs, currentJson.toString()).apply()
            Log.d(globallogtag+prefmanagerogtag, "Saved JSON: $currentJson")
            true
        } catch (e: Exception) {
            Log.e(globallogtag+prefmanagerogtag, "Error saving JSON: ${e.message}")
            false
        }
    }

    // Гарантирует, что JSON соответствует шаблону
    private fun ensureDefaultStyle(json: JSONObject): JSONObject {
        defaultJson.keys().forEach { key ->
            if (!json.has(key)) {
                json.put(key, defaultJson.get(key))
            }
        }
        return json
    }

    fun loadSavedFolderPath(): Uri? {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val folderUriString = preferences.getString("selected_folder_path", null)

        Log.d(globallogtag+prefmanagerogtag, "Loaded pref path: $folderUriString")

        return if (folderUriString != null) {
            try {
                val folderUri = Uri.parse(folderUriString)

                // Restore persistable permissions
                val takeFlags =
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                try {
                    context.contentResolver.takePersistableUriPermission(folderUri, takeFlags)
                } catch (e: SecurityException) {
                    Log.e(globallogtag+prefmanagerogtag, "Unable to take persistable permission: ${e.message}")
                }

                // Verify folder accessibility
                val folderDocumentFile = DocumentFile.fromTreeUri(context, folderUri)
                val folderExistBool = folderDocumentFile?.exists() == true
                val pathIsFolder = folderDocumentFile?.isDirectory == true

                Log.d(globallogtag+prefmanagerogtag, "folderExistBool: $folderExistBool, pathIsFolder: $pathIsFolder")

                if (folderExistBool && pathIsFolder) {
                    return folderUri
                } else {
                    return null
                }
            } catch (e: Exception) {
                Log.e(globallogtag+prefmanagerogtag, "Exception while loading folder: ${e.message}")
                return null
            }
        } else {
            return null
        }
    }

    fun saveFolderPath(uri: Uri): Boolean {
        return try {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            preferences.edit().putString("selected_folder_path", uri.toString()).apply()

            Log.d(globallogtag+prefmanagerogtag, "Saved pref path: $uri")

            // Save persistable permissions
            val takeFlags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)

            // Verify folder accessibility
            val folderDocumentFile = DocumentFile.fromTreeUri(context, uri)
            val folderExistBool = folderDocumentFile?.exists() == true
            val pathIsFolder = folderDocumentFile?.isDirectory == true

            Log.d(globallogtag+prefmanagerogtag, "folderExistBool: $folderExistBool, pathIsFolder: $pathIsFolder")

            return folderExistBool && pathIsFolder
        } catch (e: SecurityException) {
            Log.e(globallogtag+prefmanagerogtag, "SecurityException: ${e.message}")
            return false
        } catch (e: Exception) {
            Log.e(globallogtag+prefmanagerogtag, "Exception: ${e.message}")
            return false
        }
    }
}
