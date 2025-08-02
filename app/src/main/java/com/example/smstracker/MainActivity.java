package com.example.smstracker;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.example.smstracker.ui.theme.DataStoreManager;

public class MainActivity extends AppCompatActivity {

    private static final int SMS_PERMISSION_CODE = 101;

    private EditText firstAccountEditText;
    private EditText secondAccountEditText;
    private EditText serverUrlEditText;
    private Button saveButton;
    private DataStoreManager dataStoreManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkAndRequestPermissions();

        dataStoreManager = new DataStoreManager(this);

        firstAccountEditText = findViewById(R.id.firstAccountEditText);
        secondAccountEditText = findViewById(R.id.secondAccountEditText);
        serverUrlEditText = findViewById(R.id.serverUrlEditText);
        saveButton = findViewById(R.id.saveButton);

        loadSettings();

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
            }
        });
    }

    private void checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.RECEIVE_SMS,
                            Manifest.permission.READ_SMS,
                            Manifest.permission.READ_PHONE_STATE
                    },
                    SMS_PERMISSION_CODE);
        }
    }

    private void loadSettings() {
        firstAccountEditText.setText(dataStoreManager.getFirstAccount());
        secondAccountEditText.setText(dataStoreManager.getSecondAccount());
        serverUrlEditText.setText(dataStoreManager.getServerUrl());
    }

    private void saveSettings() {
        String firstAccount = firstAccountEditText.getText().toString();
        String secondAccount = secondAccountEditText.getText().toString();
        String serverUrl = serverUrlEditText.getText().toString();

        dataStoreManager.saveFirstAccount(firstAccount);
        dataStoreManager.saveSecondAccount(secondAccount);
        dataStoreManager.saveServerUrl(serverUrl);

        Toast.makeText(this, "Settings Saved", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SMS_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permissions are required for the app to function", Toast.LENGTH_LONG).show();
            }
        }
    }
}