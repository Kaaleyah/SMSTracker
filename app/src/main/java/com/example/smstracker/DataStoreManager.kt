package com.example.smstracker

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "walkers")

object Global {
    val baseURL = "http://192.168.1.132:3000"
}

class DataStoreManager {
    val firstLaunch_KEY = booleanPreferencesKey("first_launch")

    val FIRST_ACCOUNT_KEY = stringPreferencesKey("first_account")
    val SECOND_ACCOUNT_KEY = stringPreferencesKey("second_account")

    val SOCKET_URL_KEY = stringPreferencesKey("socket_url")

    suspend fun isFirstLaunch(context: Context): Boolean =
        context.dataStore.data
            .map { preferences ->
                preferences[firstLaunch_KEY] ?: true
            }.first()

    suspend fun setFirstLaunch(context: Context) {
        context.dataStore.edit { preferences ->
            preferences[firstLaunch_KEY] = false
        }
    }

    suspend fun getFirstAccount(context: Context): String =
        context.dataStore.data
            .map { preferences ->
                preferences[FIRST_ACCOUNT_KEY] ?: ""
            }.first()

    suspend fun saveFirstAccount(context: Context, account: String) {
        context.dataStore.edit { preferences ->
            preferences[FIRST_ACCOUNT_KEY] = account
        }
    }

    suspend fun getSecondAccount(context: Context): String =
        context.dataStore.data
            .map { preferences ->
                preferences[SECOND_ACCOUNT_KEY] ?: ""
            }.first()

    suspend fun saveSecondAccount(context: Context, account: String) {
        context.dataStore.edit { preferences ->
            preferences[SECOND_ACCOUNT_KEY] = account
        }
    }

    suspend fun saveSocketUrl(context: Context, url: String) {
        context.dataStore.edit { preferences ->
            preferences[SOCKET_URL_KEY] = url
        }
    }

    suspend fun getSocketUrl(context: Context): String =
        context.dataStore.data
            .map { preferences ->
                preferences[SOCKET_URL_KEY] ?: ""
            }.first()
}