package com.ashencostha.mqtt;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.ProgressBar;
import androidx.appcompat.app.AppCompatActivity;

public class LoadingActivity extends AppCompatActivity {

    private String TAG = "thisisloading";
    private String username;
    private String password;
    private String imeiNo1;
    private String imeiNo2;
    private String mqttUsername;
    private String mqttPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loading);
        getSupportActionBar().hide();

        // Retrieve the data passed from LoginActivity
        Intent intent = getIntent();
        if (intent != null) {
            username = intent.getStringExtra("username");
            password = intent.getStringExtra("password");
            imeiNo1 = intent.getStringExtra("imeiNo1");
            imeiNo2 = intent.getStringExtra("imeiNo2");
            mqttUsername = intent.getStringExtra("mqtt_username");
            mqttPassword = intent.getStringExtra("mqtt_password");
        }

        // Get reference to ProgressBar
        ProgressBar progressBar = findViewById(R.id.progressBar);

        // Start the ProgressBar animation
        progressBar.setIndeterminate(true);

        // Delay for 1 second and then navigate to the MainActivity
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                navigateToMainActivity(username, password, imeiNo1, imeiNo2, mqttUsername, mqttPassword);
            }
        }, 1000);
    }

    /*
     * Send data to MainActivity
     * Such as Username, Password etc
     * */
    private void navigateToMainActivity(String username, String password, String imeiNo1, String imeiNo2, String mqttUsername, String mqttPassword) {
        Intent intent = new Intent(LoadingActivity.this, MainActivity.class);
        intent.putExtra("username", username);
        intent.putExtra("password", password);
        intent.putExtra("imeiNo1", imeiNo1);
        intent.putExtra("imeiNo2", imeiNo2);
        intent.putExtra("mqtt_username", mqttUsername);
        intent.putExtra("mqtt_password", mqttPassword);
        startActivity(intent);
        finish(); // This will close the LoadingActivity so the user won't be able to navigate back to it without logging out
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }
}
