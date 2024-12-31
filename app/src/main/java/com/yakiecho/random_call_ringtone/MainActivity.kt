package com.yakiecho.random_call_ringtone

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import java.io.File
import kotlin.random.Random
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.documentfile.provider.DocumentFile

class MainActivity : AppCompatActivity() {

    private lateinit var selectedFolderTextView: TextView
    private lateinit var buttonSetRandomRingtone: Button
    private lateinit var buttonSelectFolder: Button
    private lateinit var telephonyManager: TelephonyManager
    private lateinit var phoneStateListener: PhoneStateListener
    private var selectedFolderPath: Uri? = null  // Изменили на Uri
    private var isCallStateChecked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val serviceIntent = Intent(this, RandomRingtoneService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        selectedFolderTextView = findViewById(R.id.textViewSelectedFolder)
        buttonSetRandomRingtone = findViewById(R.id.buttonSetRandomRingtone)
        buttonSelectFolder = findViewById(R.id.buttonSelectFolder)

        buttonSelectFolder.setOnClickListener {
            onSelectFolderClick()
        }

        buttonSetRandomRingtone.setOnClickListener {
            onSetRandomRingtoneClick()
        }

        checkAndRequestPermissions()

        loadSavedFolderPath()

        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

        phoneStateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                super.onCallStateChanged(state, phoneNumber)
                when (state) {
                    TelephonyManager.CALL_STATE_IDLE -> {
                        // Проверяем, был ли уже вызван этот метод после запуска приложения
                        if (isCallStateChecked) {
                            onSetRandomRingtoneClick()
                        }
                    }
                    else -> {
                        isCallStateChecked = true  // Устанавливаем флаг, чтобы в следующий раз метод вызывался
                    }
                }
            }
        }

        // Регистрация слушателя
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    private fun loadSavedFolderPath() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val folderUriString = preferences.getString("selected_folder_path", null)

        Log.d("RingtoneReceiver", "Loaded pref path: $folderUriString")

        if (folderUriString != null) {
            try {
                selectedFolderPath = Uri.parse(folderUriString)

                // Восстанавливаем сохраненные разрешения
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                try {
                    contentResolver.takePersistableUriPermission(selectedFolderPath!!, takeFlags)
                } catch (e: SecurityException) {
                    Log.e("RingtoneReceiver", "Unable to take persistable permission: ${e.message}")
                }

                // Проверяем доступность папки
                val folderDocumentFile = DocumentFile.fromTreeUri(this, selectedFolderPath!!)
                val folderExistBool = folderDocumentFile?.exists() == true
                val pathIsFolder = folderDocumentFile?.isDirectory == true

                Log.d("RingtoneReceiver", "folderExistBool: $folderExistBool, pathIsFolder: $pathIsFolder")

                if (folderExistBool && pathIsFolder) {
                    selectedFolderTextView.text =
                        getString(R.string.selected_folder, folderDocumentFile?.uri)
                } else {
                    selectedFolderTextView.text = getString(R.string.selected_folder_not_avaible)
                    selectedFolderPath = null
                }
            } catch (e: Exception) {
                Log.e("RingtoneReceiver", "Exception while loading folder: ${e.message}")
                selectedFolderTextView.text = getString(R.string.selected_folder_error)
                selectedFolderPath = null
            }
        } else {
            selectedFolderTextView.text = getString(R.string.selected_folder_not_setted_ahead)
        }
    }

    private fun saveFolderPath(uri: Uri) {
        try {
            val preferences = PreferenceManager.getDefaultSharedPreferences(this)
            preferences.edit().putString("selected_folder_path", uri.toString()).apply()

            Log.d("RingtoneReceiver", "Saved pref path: $uri")

            selectedFolderPath = uri

            // Сохраняем разрешения на доступ
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, takeFlags)

            // Проверяем доступность папки
            val folderDocumentFile = DocumentFile.fromTreeUri(this, uri)
            val folderExistBool = folderDocumentFile?.exists() == true
            val pathIsFolder = folderDocumentFile?.isDirectory == true

            Log.d("RingtoneReceiver", "folderExistBool: $folderExistBool, pathIsFolder: $pathIsFolder")

            if (folderExistBool && pathIsFolder) {
                selectedFolderTextView.text = getString(R.string.selected_folder, folderDocumentFile?.uri)
            } else {
                selectedFolderTextView.text = getString(R.string.selected_folder_error_cannot_save)
            }
        } catch (e: SecurityException) {
            Log.e("RingtoneReceiver", "SecurityException: ${e.message}")
            selectedFolderTextView.text =
                getString(R.string.selected_folder_error_access_to_folder_forbiden)
        } catch (e: Exception) {
            Log.e("RingtoneReceiver", "Exception: ${e.message}")
            selectedFolderTextView.text = getString(R.string.selected_folder_error_while_save)
        }
    }


    private val folderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                saveFolderPath(it)
            }
        }

    fun onSelectFolderClick() {
        folderPickerLauncher.launch(null)
    }

    private fun addRingtoneToMediaStore(file: File): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name) // Отображаемое имя файла
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp3") // MIME-тип (замените, если нужно)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Music/Ringtones") // Путь для рингтонов
            put(MediaStore.Audio.AudioColumns.IS_RINGTONE, true)
            put(MediaStore.Audio.AudioColumns.IS_NOTIFICATION, false)
            put(MediaStore.Audio.AudioColumns.IS_ALARM, false)
            put(MediaStore.Audio.AudioColumns.IS_MUSIC, false)
        }

        // Указываем медиатеку для аудиофайлов
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        // Проверяем, возможно, файл уже существует
        contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf(file.name),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                // Если файл уже есть, возвращаем его Uri
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                return Uri.withAppendedPath(collection, id.toString())
            }
        }

        // Вставляем файл в медиатеку
        val newUri = contentResolver.insert(collection, values) ?: return null

        // Копируем содержимое файла в медиатеку
        contentResolver.openOutputStream(newUri)?.use { outputStream ->
            file.inputStream().use { inputStream ->
                inputStream.copyTo(outputStream)
            }
        }

        return newUri
    }

    fun onSetRandomRingtoneClick() {
        Log.d("RingtoneReceiver", "Clicked")
        if (isWriteSettingsPermissionGranted()) {
            selectedFolderPath?.let { folderUri ->
                val randomRingtoneUri = getRandomRingtoneUri(folderUri)
                if (randomRingtoneUri != null) {
                    try {
                        Log.d("RingtoneReceiver", "Setting ringtone: $randomRingtoneUri")

                        // Добавление в медиатеку
                        val newUri = addRingtoneToMediaStore(File(randomRingtoneUri.path!!))
                        if (newUri != null) {
                            // Установка рингтона
                            RingtoneManager.setActualDefaultRingtoneUri(
                                this,
                                RingtoneManager.TYPE_RINGTONE,
                                newUri
                            )
                            selectedFolderTextView.text = getString(R.string.random_ringtone_setted)
                        } else {
                            selectedFolderTextView.text =
                                getString(R.string.cannot_add_ringtone_to_mediastore)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        selectedFolderTextView.text =
                            getString(R.string.error_setting_ringtone_to_mediastore, e.message)
                    }
                } else {
                    selectedFolderTextView.text =
                        getString(R.string.theres_no_audio_files_in_folder)
                }
            } ?: run {
                selectedFolderTextView.text = getString(R.string.selected_folder_not_setted_ahead)
            }
        } else {
            requestWriteSettingsPermission()
        }
    }

    private fun getRandomRingtoneUri(folderUri: Uri): Uri? {
        Log.d("RingtoneReceiver", "File search init for folder: $folderUri")

        // Получаем DocumentFile для работы с Uri
        val folderDocumentFile = DocumentFile.fromTreeUri(this, folderUri)

        if (folderDocumentFile == null || !folderDocumentFile.exists() || !folderDocumentFile.isDirectory) {
            Log.d("RingtoneReceiver", "Invalid folder Uri or not a directory: $folderUri")
            return null
        }

        // Получаем список всех файлов в директории
        val soundFiles = folderDocumentFile.listFiles().filter { file ->
            file.name?.let {
                it.endsWith(".mp3") || it.endsWith(".wav") || it.endsWith(".ogg")
            } == true
        }

        if (soundFiles.isEmpty()) {
            Log.d("RingtoneReceiver", "No valid sound files found.")
            return null
        }

        // Выбираем случайный файл
        val randomFile = soundFiles[Random.nextInt(soundFiles.size)]
        Log.d("RingtoneReceiver", "Selected random file: ${randomFile.name}")

        // Возвращаем Uri этого файла
        return randomFile.uri
    }


    private fun isWriteSettingsPermissionGranted(): Boolean {
        return Settings.System.canWrite(this)
    }

    private fun requestWriteSettingsPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
        startActivityForResult(intent, REQUEST_WRITE_SETTINGS_PERMISSION)
    }

    // Используем REQUEST_WRITE_SETTINGS_PERMISSION для кода возврата, чтобы обработать разрешение
    private val REQUEST_WRITE_SETTINGS_PERMISSION = 1001

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_WRITE_SETTINGS_PERMISSION) {
            if (isWriteSettingsPermissionGranted()) {
                onSetRandomRingtoneClick()  // Повторно пробуем установить рингтон
            } else {
                selectedFolderTextView.text = getString(R.string.access_to_WSP_not_given)
            }
        }
    }


    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                selectedFolderTextView.text = getString(R.string.access_to_mediastore_not_given)
            }
        }

    private fun checkPhoneStatePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_PHONE_STATE), 1)
        }
    }


    private fun checkAndRequestPermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        checkPhoneStatePermission()

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(permission)
        }
    }
}
