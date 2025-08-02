package com.example.smstracker;

import android.Manifest;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.RequiresPermission;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.smstracker.ui.theme.DataStoreManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Timer;
import java.util.TimerTask;

public class SimStatusMonitor {
    private static final String TAG = "SimStatusMonitor";
    private final Context context;
    private final TelephonyManager telephonyManager;
    private final DataStoreManager dataStoreManager;
    private final Timer timer = new Timer();
    private PhoneStateListener phoneStateListener;
    private int lastSignalQuality = -1; // Cache the last known signal quality (ASU)

    private static final long CHECK_INTERVAL = 120000L; // 2 minutes
    private final String serverUrl = "http://192.168.1.132:3000" + "/simstatus";

    @RequiresPermission(allOf = {
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_COARSE_LOCATION // Coarse is sufficient for cell tower info
    })
    public SimStatusMonitor(Context context) {
        this.context = context;
        this.telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        // Correctly initialize DataStoreManager with the context
        this.dataStoreManager = new DataStoreManager(context);
    }

    @RequiresPermission(allOf = {
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_COARSE_LOCATION
    })
    public void startMonitoring() {
        Log.d(TAG, "Starting SIM status monitoring");

        // Use PhoneStateListener for real-time signal strength updates on older APIs
        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onSignalStrengthsChanged(SignalStrength signalStrength) {
                super.onSignalStrengthsChanged(signalStrength);
                if (signalStrength.isGsm()) {
                    int gsmSignalStrength = signalStrength.getGsmSignalStrength();
                    // The value 99 means unknown or not detectable
                    lastSignalQuality = (gsmSignalStrength == 99) ? 0 : gsmSignalStrength;
                } else {
                    // Fallback for non-GSM networks, less precise
                    lastSignalQuality = signalStrength.getEvdoDbm(); // Example for CDMA/EVDO
                }
                Log.d(TAG, "PhoneStateListener updated signal quality (ASU): " + lastSignalQuality);
            }
        };

        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);

        // Schedule the periodic task to send data to the server
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                // Run on a background thread to perform network operations
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        checkAndSendSimStatus();
                    }
                }).start();
            }
        }, 0, CHECK_INTERVAL);
    }

    public void stopMonitoring() {
        Log.d(TAG, "Stopping SIM status monitoring");
        timer.cancel();
        if (phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
    }

    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    private void checkAndSendSimStatus() {
        // On API 19, we work with the single, default TelephonyManager instance
        if (telephonyManager.getSimState() != TelephonyManager.SIM_STATE_READY) {
            Log.w(TAG, "SIM not ready, skipping status send.");
            return;
        }
        sendSimStatusToServer();
    }

    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    private void sendSimStatusToServer() {
        // Use the cached signal quality from the listener
        int signalQuality = lastSignalQuality;
        if (signalQuality < 0) { // If listener hasn't run yet
            signalQuality = 0;
        }

        // Get other details
        String operatorName = telephonyManager.getNetworkOperatorName();
        if (operatorName == null || operatorName.isEmpty()) {
            operatorName = "Unknown";
        }

        int regStatus = getRegistrationStatus();
        String networkStatus = getNetworkStatus(regStatus);
        int rssiDbm = calculateRssiDbm(signalQuality); // Convert ASU to dBm
        String accountName = dataStoreManager.getFirstAccount(); // Assuming primary SIM maps to first account

        // Create JSON payload
        final JSONObject json = new JSONObject();
        try {
            json.put("accountName", accountName);
            json.put("signalQuality", signalQuality); // This is ASU (0-31 or 99)
            json.put("signalStrength", rssiDbm);
            json.put("networkStatus", networkStatus);
            json.put("operator", operatorName);
            json.put("registrationStatus", regStatus);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating JSON payload", e);
            return;
        }

        Log.d(TAG, "Sending SIM status to server: " + json.toString());

        // Send to server using Volley
        RequestQueue requestQueue = Volley.newRequestQueue(context);
        StringRequest stringRequest = new StringRequest(Request.Method.POST, serverUrl,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Log.d(TAG, "Successfully sent SIM status to server. Response: " + response);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e(TAG, "Failed to send SIM status", error);
                    }
                }) {
            @Override
            public byte[] getBody() {
                return json.toString().getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public String getBodyContentType() {
                return "application/json; charset=utf-8";
            }
        };

        requestQueue.add(stringRequest);
    }

    private int calculateRssiDbm(int asu) {
        // Formula to convert ASU (Arbitrary Strength Unit) to dBm for GSM
        if (asu <= 0 || asu == 99) {
            return -114; // Use a value indicating no/unknown signal
        }
        if (asu >= 31) {
            return -51;
        }
        return -113 + (asu * 2);
    }

    private String getNetworkStatus(int registrationStatus) {
        switch (registrationStatus) {
            case 1: // Registered Home
            case 5: // Registered Roaming
                return "Registered";
            case 0:
                return "Not registered";
            case 3:
                return "Emergency only";
            case 2:
                return "Radio off";
            default:
                return "Unknown";
        }
    }

    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    private int getRegistrationStatus() {
        // Use modern API if available
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ServiceState serviceState = telephonyManager.getServiceState();
            if (serviceState != null) {
                switch (serviceState.getState()) {
                    case ServiceState.STATE_IN_SERVICE:
                        return serviceState.getRoaming() ? 5 : 1; // 5 for roaming, 1 for home
                    case ServiceState.STATE_OUT_OF_SERVICE:
                        return 0;
                    case ServiceState.STATE_EMERGENCY_ONLY:
                        return 3;
                    case ServiceState.STATE_POWER_OFF:
                        return 2;
                    default:
                        return 4; // Unknown
                }
            }
        }

        // Legacy fallback for older devices
        if (telephonyManager.getSimState() == TelephonyManager.SIM_STATE_READY &&
                telephonyManager.getNetworkOperatorName() != null &&
                !telephonyManager.getNetworkOperatorName().isEmpty()) {
            return telephonyManager.isNetworkRoaming() ? 5 : 1;
        } else {
            return 0; // Not registered
        }
    }
}