package com.example.smstracker.ui.theme;

import android.content.Context;
import android.content.SharedPreferences;

public class DataStoreManager {

    private static final String PREFERENCES_NAME = "sms_tracker_prefs";
    private static final String KEY_FIRST_ACCOUNT = "first_account";
    private static final String KEY_SECOND_ACCOUNT = "second_account";
    private static final String KEY_SERVER_URL = "server_url";

    private SharedPreferences sharedPreferences;

    public DataStoreManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public void saveFirstAccount(String account) {
        sharedPreferences.edit().putString(KEY_FIRST_ACCOUNT, account).apply();
    }

    public String getFirstAccount() {
        return sharedPreferences.getString(KEY_FIRST_ACCOUNT, "");
    }

    public void saveSecondAccount(String account) {
        sharedPreferences.edit().putString(KEY_SECOND_ACCOUNT, account).apply();
    }

    public String getSecondAccount() {
        return sharedPreferences.getString(KEY_SECOND_ACCOUNT, "");
    }

    public void saveServerUrl(String url) {
        sharedPreferences.edit().putString(KEY_SERVER_URL, url).apply();
    }

    public String getServerUrl() {
        return sharedPreferences.getString(KEY_SERVER_URL, "http://192.168.1.132:3000");
    }
}
