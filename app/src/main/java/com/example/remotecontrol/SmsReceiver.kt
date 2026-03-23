package com.example.remotecontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

            for (message in messages) {
                val sender = message.originatingAddress ?: "Unknown"
                val body = message.messageBody ?: continue

                Log.d("SmsReceiver", "SMS keldi | Kimdan: $sender | Matn: $body")

                if (!body.trim().startsWith("#")) continue

                // Raqam bloklangan?
                if (!SecurityManager.isAllowed(context, sender)) {
                    Log.w("SmsReceiver", "Bloklangan raqam: $sender")
                    continue
                }

                // Buyruq va parolni ajrat
                val (command, password) = SecurityManager.parseCommand(body.trim())

                // Parolni tekshir
                if (!SecurityManager.checkPassword(context, password)) {
                    Log.w("SmsReceiver", "Noto'g'ri parol: $password")
                    CommandHandler(context).sendSms(sender, "🔐 Noto'g'ri parol!")
                    continue
                }

                Log.d("SmsReceiver", "Buyruq qabul qilindi: $command")
                // Raqamni eslab qolish
                SecurityManager.addCommandSender(context, sender)
                CommandLogger.log(context, sender, command)
                CommandHandler(context).execute(sender, command)
            }
        }
    }
}
