package com.example.smstracker

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class SMSReceiver : BroadcastReceiver() {
    val TAG = "SMSReceiver"

    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

        val subscriptionID = intent.getIntExtra("subscription", -1)
        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList

        val subscriptionInfo = activeSubscriptions?.find { it.subscriptionId == subscriptionID }

        var simSlotIndex = subscriptionInfo?.simSlotIndex ?: 0

        messages.forEach { message ->
            val sender = message.displayOriginatingAddress
            val body = message.messageBody

            // Handle the received SMS here
            Log.d(TAG, "Received SMS from $sender: $body")
            sendToServer(context, sender ?: "Unknown", body, simSlotIndex)
        }
    }

    private fun sendToServer(
        context: Context,
        sender: String,
        message: String,
        simSlotIndex: Int,
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val requestQueue = Volley.newRequestQueue(context)
            //val url = "http://20.90.208.205:3000/sms"
            val url = "${Global.baseURL}/sms"

            val accountName = if (simSlotIndex == 0) {
                DataStoreManager().getFirstAccount(context)
            } else {
                DataStoreManager().getSecondAccount(context)
            }

            val json = JSONObject().apply {
                put("accountName", accountName)
                put("sender", sender)
                put("message", message)
            }

            var attempt = 0
            var success = false

            while (attempt < 5 && !success) {
                val latch = kotlinx.coroutines.CompletableDeferred<Boolean>()

                val request = object : StringRequest(
                    Method.POST, url,
                    Response.Listener {
                        Log.d(TAG, "Attempt $attempt: Success")
                        success = true
                        latch.complete(true)
                    },
                    Response.ErrorListener { error ->
                        Log.e(TAG, "Attempt $attempt failed: ${error.message}")
                        latch.complete(false)
                    }
                ) {
                    override fun getBody(): ByteArray = json.toString().toByteArray(Charsets.UTF_8)
                    override fun getBodyContentType(): String = "application/json; charset=utf-8"
                }

                requestQueue.add(request)
                success = latch.await()

                if (!success) {
                    attempt++
                    kotlinx.coroutines.delay(1000L * attempt) // exponential backoff (1s, 2s, 3s...)
                }
            }

            if (!success) {
                Log.e(TAG, "Failed to send SMS after 5 attempts.")
            }
        }
    }

}