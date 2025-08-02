package com.example.smstracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.smstracker.ui.theme.DataStoreManager;

import org.json.JSONException;
import org.json.JSONObject;

public class SMSReceiver extends BroadcastReceiver {
    private static final String TAG = "SMSReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            return;
        }

        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        // --- Dual SIM handling for older APIs ---
        // Before API 22, there was no standard way. Manufacturers used different extras.
        // We check for common ones. Default to 0 (first SIM).
        int simSlotIndex = 0;
        if (bundle.containsKey("slot")) {
            simSlotIndex = bundle.getInt("slot", 0);
        } else if (bundle.containsKey("simId")) {
            simSlotIndex = bundle.getInt("simId", 0);
        } else if (bundle.containsKey("slot_id")) {
            simSlotIndex = bundle.getInt("slot_id", 0);
        }
        Log.d(TAG, "Detected SIM slot: " + simSlotIndex);


        Object[] pdus = (Object[]) bundle.get("pdus");
        if (pdus == null) return;

        for (Object pdu : pdus) {
            SmsMessage message;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                String format = bundle.getString("format");
                message = SmsMessage.createFromPdu((byte[]) pdu, format);
            } else {
                message = SmsMessage.createFromPdu((byte[]) pdu);
            }

            String sender = message.getDisplayOriginatingAddress();
            String body = message.getMessageBody();

            Log.d(TAG, "Received SMS from " + sender + " on SIM " + simSlotIndex + ": " + body);
            sendToServer(context, sender, body, simSlotIndex);
        }
    }

    private void sendToServer(final Context context, final String sender, final String message, final int simSlotIndex) {
        // Use a background thread for networking
        new Thread(new Runnable() {
            @Override
            public void run() {
                DataStoreManager dataStoreManager = new DataStoreManager(context);
                String url = dataStoreManager.getServerUrl() + "/sms";

                String accountName = (simSlotIndex == 0) ?
                        dataStoreManager.getFirstAccount() :
                        dataStoreManager.getSecondAccount();

                final JSONObject jsonBody = new JSONObject();
                try {
                    jsonBody.put("accountName", accountName);
                    jsonBody.put("sender", sender);
                    jsonBody.put("message", message);
                } catch (JSONException e) {
                    Log.e(TAG, "JSON Exception: " + e.getMessage());
                    return;
                }

                final String requestBody = jsonBody.toString();

                RequestQueue requestQueue = Volley.newRequestQueue(context.getApplicationContext());

                StringRequest stringRequest = new StringRequest(Request.Method.POST, url,
                        new Response.Listener<String>() {
                            @Override
                            public void onResponse(String response) {
                                Log.d(TAG, "Server response: " + response);
                            }
                        },
                        new Response.ErrorListener() {
                            @Override
                            public void onErrorResponse(VolleyError error) {
                                Log.e(TAG, "Failed to send to server: " + error.toString());
                            }
                        }) {
                    @Override
                    public byte[] getBody() {
                        return requestBody.getBytes();
                    }

                    @Override
                    public String getBodyContentType() {
                        return "application/json; charset=utf-8";
                    }
                };

                requestQueue.add(stringRequest);
            }
        }).start();
    }
}