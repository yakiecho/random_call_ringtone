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
    private lateinit var PrefManager: PrefManager
    private var selectedFolderPath: Uri? = null
    private var isCallStateChecked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        PrefManager = PrefManager(this)
        selectedFolderPath = PrefManager.loadSavedFolderPath()

        val serviceIntent = Intent(this, RandomRingtoneService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        selectedFolderTextView = findViewById(R.id.textViewSelectedFolder)

        if (selectedFolderPath == null) {
            selectedFolderTextView.text = getString(R.string.selected_folder_not_avaible)

        } else {
            selectedFolderTextView.text =
                getString(R.string.selected_folder, selectedFolderPath)
        }

        buttonSetRandomRingtone = findViewById(R.id.buttonSetRandomRingtone)
        buttonSelectFolder = findViewById(R.id.buttonSelectFolder)

        buttonSelectFolder.setOnClickListener {
            onSelectFolderClick()
        }

        buttonSetRandomRingtone.setOnClickListener {
            onSetRandomRingtoneClick()
        }

        checkAndRequestPermissions()

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
                        Log.d(globallogtag+ mainactivitylogtag, "State on start is true")
                        isCallStateChecked = true  // Устанавливаем флаг, чтобы в следующий раз метод вызывался
                    }
                }
            }
        }

        // Регистрация слушателя
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    private val folderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                PrefManager.saveFolderPath(it)
                selectedFolderTextView.text = selectedFolderPath.toString()
            }
        }

    private fun onSelectFolderClick() {
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
        Log.d(globallogtag+ mainactivitylogtag, "Clicked with ${selectedFolderPath}")
        if (isWriteSettingsPermissionGranted()) {
            selectedFolderPath?.let { folderUri ->
                val randomRingtoneUri = getRandomRingtoneUri(folderUri)
                if (randomRingtoneUri != null) {
                    try {
                        Log.d(globallogtag+ mainactivitylogtag, "Setting ringtone: $randomRingtoneUri")

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
                Log.d(globallogtag+ mainactivitylogtag, "Not setted ahead")
                selectedFolderTextView.text = getString(R.string.selected_folder_not_setted_ahead)
            }
        } else {
            requestWriteSettingsPermission()
        }
    }

    private fun getRandomRingtoneUri(folderUri: Uri): Uri? {
        Log.d(globallogtag+ mainactivitylogtag, "File search init for folder: $folderUri")

        // Получаем DocumentFile для работы с Uri
        val folderDocumentFile = DocumentFile.fromTreeUri(this, folderUri)

        if (folderDocumentFile == null || !folderDocumentFile.exists() || !folderDocumentFile.isDirectory) {
            Log.d(globallogtag+ mainactivitylogtag, "Invalid folder Uri or not a directory: $folderUri")
            return null
        }

        // Получаем список всех файлов в директории
        val soundFiles = folderDocumentFile.listFiles().filter { file ->
            file.name?.let {
                it.endsWith(".mp3") || it.endsWith(".wav") || it.endsWith(".ogg")
            } == true
        }

        if (soundFiles.isEmpty()) {
            Log.d(globallogtag+ mainactivitylogtag, "No valid sound files found.")
            return null
        }

        // Выбираем случайный файл
        val randomFile = soundFiles[Random.nextInt(soundFiles.size)]
        Log.d(globallogtag+ mainactivitylogtag, "Selected random file: ${randomFile.name}")

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
