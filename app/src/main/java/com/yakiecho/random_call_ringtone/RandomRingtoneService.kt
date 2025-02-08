package com.yakiecho.random_call_ringtone

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import java.io.File
import kotlin.random.Random

class RandomRingtoneService : Service() {

    private lateinit var telephonyManager: TelephonyManager
    private lateinit var phoneStateListener: PhoneStateListener
    private lateinit var PrefManager: PrefManager
    private var selectedFolderPath: Uri? = null

    override fun onCreate() {
        PrefManager = PrefManager(this)
        selectedFolderPath = PrefManager.loadSavedFolderPath()

        Log.d(globallogtag+ randomringtoneservicelogtag, "Init service.")
        try {
            super.onCreate()
            // Инициализация канала уведомлений и уведомления
            createNotificationChannel()
            val notification = buildNotification()
            with(NotificationManagerCompat.from(this)) {
                if (ActivityCompat.checkSelfPermission(
                        this@RandomRingtoneService,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return
                }
                notify(1, notification)
            }
            startForeground(1, notification)
            Log.d(globallogtag+ randomringtoneservicelogtag, "Notification started.")
        } catch (e: Exception) {
            Log.e(globallogtag+ randomringtoneservicelogtag, "Error initializing service: ${e.message}")
            e.printStackTrace()
        }

        // Настраиваем TelephonyManager
        try {
            telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
            phoneStateListener = object : PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    super.onCallStateChanged(state, phoneNumber)
                    if (state == TelephonyManager.CALL_STATE_IDLE) {
                        Log.d(globallogtag+ randomringtoneservicelogtag, "Call ended, setting random ringtone.")
                        setRandomRingtone()
                    }
                }
            }
            // Регистрация слушателя состояния звонков
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
        } catch (e: Exception) {
            Log.e(globallogtag+ randomringtoneservicelogtag, "Error initializing TelephonyManager: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Используем START_STICKY для автоматического перезапуска сервиса, если он будет убит системой
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            super.onDestroy()
            // Отключаем слушателя при остановке сервиса
            stopForeground(true) // Останавливаем уведомление
            stopSelf() // Останавливаем сервис

            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        } catch (e: Exception) {
            Log.e(globallogtag+ randomringtoneservicelogtag, "Error during service destruction: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun setRandomRingtone() {
        Log.d(globallogtag+ randomringtoneservicelogtag, "Clicked with ${selectedFolderPath}")
        if (selectedFolderPath == null) {
            Log.e(globallogtag+ randomringtoneservicelogtag, "No folder selected for ringtones.")
            return
        }

        val randomRingtoneUri = getRandomRingtoneUri(selectedFolderPath!!)
        if (randomRingtoneUri != null) {
            try {
                // Добавляем файл в медиатеку и устанавливаем рингтон
                val newUri = addRingtoneToMediaStore(File(randomRingtoneUri.path!!))
                if (newUri != null) {
                    RingtoneManager.setActualDefaultRingtoneUri(
                        this,
                        RingtoneManager.TYPE_RINGTONE,
                        newUri
                    )
                    Log.d(globallogtag+ randomringtoneservicelogtag, "Random ringtone set: $newUri")
                } else {
                    Log.e(globallogtag+ randomringtoneservicelogtag, "Failed to add ringtone to MediaStore.")
                }
            } catch (e: Exception) {
                Log.e(globallogtag+ randomringtoneservicelogtag, "Error setting ringtone: ${e.message}")
                e.printStackTrace()
            }
        } else {
            Log.d(globallogtag+ randomringtoneservicelogtag, "No valid ringtone files found.")
        }
    }

    private fun getRandomRingtoneUri(folderUri: Uri): Uri? {
        try {
            val folderDocumentFile = DocumentFile.fromTreeUri(this, folderUri)
            if (folderDocumentFile == null || !folderDocumentFile.exists() || !folderDocumentFile.isDirectory) {
                Log.e(globallogtag+ randomringtoneservicelogtag, "Invalid folder Uri or not a directory.")
                return null
            }

            val soundFiles = folderDocumentFile.listFiles().filter { file ->
                file.name?.let { it.endsWith(".mp3") || it.endsWith(".wav") || it.endsWith(".ogg") } == true
            }

            if (soundFiles.isEmpty()) {
                Log.d(globallogtag+ randomringtoneservicelogtag, "No valid sound files found in folder.")
                return null
            }

            return soundFiles[Random.nextInt(soundFiles.size)].uri
        } catch (e: Exception) {
            Log.e(globallogtag+ randomringtoneservicelogtag, "Error getting random ringtone: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    private fun addRingtoneToMediaStore(file: File): Uri? {
        try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp3")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Music/Ringtones")
                put(MediaStore.Audio.AudioColumns.IS_RINGTONE, true)
            }

            val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            contentResolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                arrayOf(file.name),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    return Uri.withAppendedPath(collection, id.toString())
                }
            }

            val newUri = contentResolver.insert(collection, values) ?: return null
            contentResolver.openOutputStream(newUri)?.use { outputStream ->
                file.inputStream().use { inputStream -> inputStream.copyTo(outputStream) }
            }

            return newUri
        } catch (e: Exception) {
            Log.e(globallogtag+ randomringtoneservicelogtag, "Error adding ringtone to MediaStore: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Random Ringtone Service")
            .setContentText(getString(R.string.notification_text))
            .setOngoing(true)
            .setAutoCancel(true)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // замените на ваш значок
            .build()
    }

    private fun createNotificationChannel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Создаем канал для уведомлений
                val serviceChannel = NotificationChannel(
                    CHANNEL_ID,
                    "Random Ringtone Service", // Название канала
                    NotificationManager.IMPORTANCE_HIGH // Важность канала
                ).apply {
                    description = "Service that manages random ringtones."
                }

                // Получаем NotificationManager и создаем канал
                val manager = getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(serviceChannel)
            }
        } catch (e: Exception) {
            Log.e(globallogtag+ randomringtoneservicelogtag, "Error creating notification channel: ${e.message}")
            e.printStackTrace()
        }
    }

    companion object {
        const val CHANNEL_ID = "RandomRingtoneServiceChannel"
    }
}