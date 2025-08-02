package com.example.smstracker

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.smstracker.ui.theme.SMSTrackerTheme
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private lateinit var dataStoreManager: DataStoreManager
    private lateinit var simStatusMonitor: SimStatusMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dataStoreManager = DataStoreManager()

        lifecycleScope.launch {
            if (dataStoreManager.isFirstLaunch(this@MainActivity)) {
                openAutoStartSetting(this@MainActivity)
                dataStoreManager.setFirstLaunch(this@MainActivity)
            }
        }

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.RECEIVE_SMS), 1)
        }

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.READ_SMS), 1)
        }

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.READ_PHONE_STATE), 1)
        }

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_PRECISE_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.READ_PRECISE_PHONE_STATE), 1)
        }

        simStatusMonitor = SimStatusMonitor(this)
        simStatusMonitor.startMonitoring()

        setContent {
            MainScreen(dataStoreManager)
        }
    }
}

// Mi UI Shenanigan
fun openAutoStartSetting(context: Context) {
    val packageName = context.packageName
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    if (!pm.isIgnoringBatteryOptimizations(packageName)) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        context.startActivity(intent)
    }

    try {
        val intent = Intent()
        intent.component = ComponentName(
            "com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity"
        )
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e("MI UI", "Error opening auto start settings: ${e.message}")
    }
}

@Composable
fun MainScreen(dataStoreManager: DataStoreManager) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var firstAccount by remember { mutableStateOf("") }
    var secondAccount by remember { mutableStateOf("") }
    var socketUrl by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        firstAccount = dataStoreManager.getFirstAccount(context)
        secondAccount = dataStoreManager.getSecondAccount(context)
        socketUrl = dataStoreManager.getSocketUrl(context)
    }

    SMSTrackerTheme {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Welcome to SMS Tracker",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            TextField(
                value = firstAccount,
                onValueChange = { newValue ->
                    firstAccount = newValue
                },
                label = { Text("First SIM Account Name") },
                modifier = Modifier.padding(bottom = 24.dp)
            )

            TextField(
                value = secondAccount,
                onValueChange = { newValue ->
                    secondAccount = newValue
                },
                label = { Text("Second SIM Account Name") },
                modifier = Modifier.padding(bottom = 24.dp)
            )

            TextField(
                value = socketUrl,
                onValueChange = { newValue ->
                    socketUrl = newValue
                },
                label = { Text("Socket URL") },
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Uri,
                )
            )

            Button(
                onClick = {
                    coroutineScope.launch {
                        dataStoreManager.saveFirstAccount(context, firstAccount)
                        dataStoreManager.saveSecondAccount(context, secondAccount)
                        dataStoreManager.saveSocketUrl(context, socketUrl)

                        Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Save")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    val context = LocalContext.current
    val dataStoreManager = DataStoreManager()

    MainScreen(dataStoreManager)
}