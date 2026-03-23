package com.example.remotecontrol

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.IBinder
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log

class BatteryMonitorService : Service() {

    private var receiver: BroadcastReceiver? = null
    private var lowSmsSent = false
    private var wasCharging = false
    private var fullSmsSent = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val prefs = getSharedPreferences("rc_auto_sms", Context.MODE_PRIVATE)
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val percent = if (scale > 0) (level * 100 / scale) else return
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                 status == BatteryManager.BATTERY_STATUS_FULL
                val phone = prefs.getString("alert_phone", "") ?: ""
                if (phone.isEmpty()) return

                // ── QUVVAT TO'LDI ─────────────────────────────────────
                if (prefs.getBoolean("battery_full_enabled", false)) {
                    if (isCharging && percent >= 100 && !fullSmsSent) {
                        fullSmsSent = true
                        sendSms(phone, "🔋 Quvvat to'liq zaryadlandi! (100%)")
                        Log.d("BatteryMonitor", "Full SMS yuborildi")
                    }
                    // Quvvat manbai uzilsa — reset
                    if (!isCharging) {
                        fullSmsSent = false
                    }
                }

                // ── QUVVAT KAM (aynan 19%) ─────────────────────────────
                if (prefs.getBoolean("battery_low_enabled", false)) {
                    if (!isCharging && percent == 19 && !lowSmsSent) {
                        lowSmsSent = true
                        sendSms(phone, "🪫 Diqqat! Telefon quvvati kam: $percent%")
                        Log.d("BatteryMonitor", "Low SMS yuborildi: $percent%")
                    }
                    // Zaryadlanish boshlansa — reset (keyingi safar yana yuborish uchun)
                    if (isCharging) {
                        lowSmsSent = false
                    }
                }

                wasCharging = isCharging
            }
        }
        registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    private fun sendSms(phone: String, message: String) {
        try {
            val prefs = getSharedPreferences("rc_auto_sms", Context.MODE_PRIVATE)
            val simSlot = prefs.getInt("sim_slot", 0)
            val sm = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val subs = sm.activeSubscriptionInfoList
            val smsManager = if (subs != null && subs.size > simSlot)
                SmsManager.getSmsManagerForSubscriptionId(subs[simSlot].subscriptionId)
            else SmsManager.getDefault()
            smsManager.sendTextMessage(phone, null, message, null, null)
        } catch (e: Exception) {
            Log.e("BatteryMonitor", "SMS xato: ${e.message}")
        }
    }

    override fun onDestroy() {
        receiver?.let { unregisterReceiver(it) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
