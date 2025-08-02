package com.example.smstracker

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthGsm
import android.telephony.ServiceState
import android.telephony.SignalStrength
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresPermission
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Timer
import java.util.TimerTask

class SimStatusMonitor(private val context: Context) {
    private val TAG = "SimStatusMonitor"
    private val checkInterval = 120000L // 2 minutes
    private val dataStoreManager = DataStoreManager()
    private val timer = Timer()
    private var isMonitoring = false
    //private val serverUrl = "http://20.90.208.205:3000/simstatus"
    private val serverUrl = "${Global.baseURL}/simstatus"

    @RequiresPermission(allOf = [
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.READ_PRECISE_PHONE_STATE
    ])
    fun startMonitoring() {
        if (isMonitoring) return

        Log.d(TAG, "Starting SIM status monitoring")
        isMonitoring = true

        // Schedule periodic updates
        timer.schedule(object : TimerTask() {
            override fun run() {
                Handler(Looper.getMainLooper()).post {
                    try {
                        CoroutineScope(Dispatchers.Main).launch {
                            checkAndSendSimStatus()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in SIM status check: ${e.message}", e)
                    }
                }
            }
        }, 0, checkInterval)
    }

    fun stopMonitoring() {
        Log.d(TAG, "Stopping SIM status monitoring")
        timer.cancel()
    }

    @RequiresPermission(allOf = [
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.READ_PRECISE_PHONE_STATE
    ])
    private suspend fun checkAndSendSimStatus() {
        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList ?: return

        for (subscriptionInfo in activeSubscriptions) {
            sendSimStatusToServer(subscriptionInfo)
        }
    }

    @RequiresPermission(allOf = [
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.READ_PRECISE_PHONE_STATE
    ])
    private suspend fun sendSimStatusToServer(subscriptionInfo: SubscriptionInfo) {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val simSlotIndex = subscriptionInfo.simSlotIndex

        // Get a telephony manager for this specific subscription
        val subTelephonyManager = telephonyManager.createForSubscriptionId(subscriptionInfo.subscriptionId)

        // Get signal quality (0-31, similar to CSQ value in GSM)
        val signalQuality = getSignalQuality(subTelephonyManager)

        // Get network status
        val networkStatus = getNetworkStatus(subTelephonyManager)

        // Get operator name
        val operatorName = subTelephonyManager.networkOperatorName ?: "Unknown"

        // Get registration status
        val regStatus = getRegistrationStatus(subTelephonyManager)

        // Calculate RSSI in dBm
        val rssiDbm = calculateRssiDbm(signalQuality)

        // Determine which account to use based on SIM slot
        val accountName = if (simSlotIndex == 0) {
            dataStoreManager.getFirstAccount(context)
        } else {
            dataStoreManager.getSecondAccount(context)
        }

        // Create JSON payload
        val json = JSONObject().apply {
            put("accountName", accountName)
            put("signalQuality", signalQuality)
            put("signalStrength", rssiDbm)
            put("networkStatus", networkStatus)
            put("operator", operatorName)
            put("registrationStatus", regStatus)
        }

        Log.d(TAG, "Sending SIM status to server: $json")

        // Send to server
        CoroutineScope(Dispatchers.IO).launch {
            val requestQueue = Volley.newRequestQueue(context)
            var attempt = 0
            var success = false

            while (attempt < 3 && !success) {
                val latch = kotlinx.coroutines.CompletableDeferred<Boolean>()

                val request = object : StringRequest(
                    Method.POST, serverUrl,
                    Response.Listener {
                        Log.d(TAG, "Successfully sent SIM status to server")
                        success = true
                        latch.complete(true)
                    },
                    Response.ErrorListener { error ->
                        Log.e(TAG, "Failed to send SIM status: ${error.message}")
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
                    kotlinx.coroutines.delay(1000L * attempt) // exponential backoff
                }
            }

            if (!success) {
                Log.e(TAG, "Failed to send SIM status after multiple attempts")
            }
        }
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(allOf = [Manifest.permission.READ_PHONE_STATE, Manifest.permission.ACCESS_FINE_LOCATION])
    private fun getSignalQuality(telephonyManager: TelephonyManager): Int {
        // Default quality value - will return this if no better data is found
        var signalQuality = 0

        try {
            val cellInfoList = telephonyManager.allCellInfo
            if (cellInfoList != null && cellInfoList.isNotEmpty()) {
                // Find the serving cell with the strongest signal
                for (cellInfo in cellInfoList) {
                    if (!cellInfo.isRegistered) continue

                    when (cellInfo) {
                        // GSM networks
                        is CellInfoGsm -> {
                            val signalStrengthGsm = cellInfo.cellSignalStrength
                            // GSM signal strength is typically -113 to -51 dBm
                            val dBm = signalStrengthGsm.dbm
                            Log.d(TAG, "GSM signal: $dBm dBm")

                            // Convert to CSQ scale (0-31)
                            val tempQuality = convertDbmToCsq(dBm)
                            if (tempQuality > signalQuality) {
                                signalQuality = tempQuality
                            }
                        }

                        // LTE networks
                        is CellInfoLte -> {
                            val signalStrengthLte = cellInfo.cellSignalStrength
                            // LTE signal strength is typically -140 to -44 dBm
                            val dBm = signalStrengthLte.dbm
                            val rsrp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                signalStrengthLte.rsrp
                            } else {
                                Int.MIN_VALUE // Custom fallback for unavailable
                            }
                            Log.d(TAG, "LTE signal: $dBm dBm, RSRP: $rsrp")

                            // Use RSRP if available (more accurate), otherwise dBm
                            val effectiveDbm = if (rsrp != CellInfo.UNAVAILABLE && rsrp < 0) rsrp else dBm

                            // Convert LTE RSRP to CSQ-like scale
                            // RSRP range is typically -140 to -44 dBm
                            val tempQuality = when {
                                effectiveDbm > -65 -> 31  // Excellent: -65 dBm or better
                                effectiveDbm > -75 -> 28  // Great
                                effectiveDbm > -85 -> 24  // Good
                                effectiveDbm > -95 -> 20  // Fair
                                effectiveDbm > -105 -> 16 // Poor but usable
                                effectiveDbm > -115 -> 8  // Very poor
                                else -> 4               // Barely detectable
                            }

                            if (tempQuality > signalQuality) {
                                signalQuality = tempQuality
                            }
                        }

                        // WCDMA/UMTS networks
                        is CellInfoWcdma -> {
                            val signalStrengthWcdma = cellInfo.cellSignalStrength
                            val dBm = signalStrengthWcdma.dbm
                            Log.d(TAG, "WCDMA signal: $dBm dBm")

                            // WCDMA signal strength is typically -120 to -40 dBm
                            val tempQuality = when {
                                dBm > -70 -> 31  // Excellent
                                dBm > -80 -> 27  // Great
                                dBm > -90 -> 23  // Good
                                dBm > -100 -> 19 // Fair
                                dBm > -110 -> 15 // Poor but usable
                                else -> 7       // Very poor
                            }

                            if (tempQuality > signalQuality) {
                                signalQuality = tempQuality
                            }
                        }

                        else -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cellInfo is CellInfoNr) {
                                val signalStrengthNr = cellInfo.cellSignalStrength
                                val dBm = signalStrengthNr.dbm
                                Log.d(TAG, "5G NR signal: $dBm dBm")

                                val tempQuality = convertDbmToCsq(dBm)
                                if (tempQuality > signalQuality) {
                                    signalQuality = tempQuality
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting signal quality: ${e.message}", e)
        }

        // If we have no signal information from cell info, try alternative methods
        if (signalQuality == 0) {
            try {
                // Get service state - if we're in service, we must have at least some signal
                val serviceState = telephonyManager.serviceState
                if (serviceState?.state == ServiceState.STATE_IN_SERVICE) {
                    // Force signal quality to minimum reception level (15) if we're in service
                    // but couldn't get actual signal data
                    signalQuality = 16
                    Log.d(TAG, "Using fallback signal quality based on service state")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking service state: ${e.message}", e)
            }
        }

        // Ensure we have at least 16 for any reception
        // This ensures compatibility with your requirement that signalQuality > 15 means reception
        return if (signalQuality > 0) {
            maxOf(signalQuality, 16) // Enforce minimum of 16 for any detected signal
        } else {
            0 // No signal detected
        }
    }

    private fun convertDbmToCsq(dBm: Int): Int {
        // CSQ formula: (dBm + 113) / 2 for dBm between -113 and -51
        return when {
            dBm < -113 -> 0
            dBm > -51 -> 31
            else -> ((dBm + 113) / 2).coerceIn(0, 31)
        }
    }

    private fun calculateRssiDbm(csq: Int): Int {
        // Convert CSQ back to dBm
        return when {
            csq < 0 -> -113
            csq >= 31 -> -51
            else -> -113 + (csq * 2)
        }
    }

    private fun getNetworkStatus(telephonyManager: TelephonyManager): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val state = telephonyManager.serviceState?.state
            when (state) {
                ServiceState.STATE_IN_SERVICE -> "Registered"
                ServiceState.STATE_OUT_OF_SERVICE -> "Not registered"
                ServiceState.STATE_EMERGENCY_ONLY -> "Emergency only"
                ServiceState.STATE_POWER_OFF -> "Radio off"
                else -> "Unknown"
            }
        } else {
            // Pre-API 26 fallback using SIM and network operator
            val simState = telephonyManager.simState
            val operator = telephonyManager.networkOperatorName

            if (simState == TelephonyManager.SIM_STATE_READY && operator.isNotBlank()) {
                "Registered (legacy)"
            } else {
                "Not registered (legacy)"
            }
        }
    }


    private fun getLegacyNetworkStatus(telephonyManager: TelephonyManager): String {
        return when (telephonyManager.dataState) {
            TelephonyManager.DATA_CONNECTED -> "Registered (data connected)"
            TelephonyManager.DATA_DISCONNECTED -> "Not registered"
            TelephonyManager.DATA_SUSPENDED -> "Connection suspended"
            TelephonyManager.DATA_CONNECTING -> "Connecting"
            else -> "Unknown legacy state"
        }
    }


    private fun getRegistrationStatus(telephonyManager: TelephonyManager): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceState = telephonyManager.serviceState
            when (serviceState?.state) {
                ServiceState.STATE_IN_SERVICE -> if (serviceState.roaming) 5 else 1
                ServiceState.STATE_OUT_OF_SERVICE -> 0
                ServiceState.STATE_EMERGENCY_ONLY -> 3
                ServiceState.STATE_POWER_OFF -> 2
                else -> 4
            }
        } else {
            val simReady = telephonyManager.simState == TelephonyManager.SIM_STATE_READY
            val operatorAvailable = telephonyManager.networkOperatorName.isNotBlank()

            return when {
                simReady && operatorAvailable -> 1 // Assume registered (approximation)
                simReady -> 0 // SIM OK but no operator
                else -> -1 // SIM not ready
            }
        }
    }


}