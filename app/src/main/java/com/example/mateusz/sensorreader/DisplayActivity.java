package com.example.mateusz.sensorreader;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

public class DisplayActivity extends AppCompatActivity implements SensorEventListener {

    private EditText sensorValueDisplay;
    private SensorManager sensorManager;
    private Sensor lightSensor;
    float lightValue = (float)-1.0;
    private boolean isServiceRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display);
        sensorValueDisplay = findViewById(R.id.sensorValue);
        sensorValueDisplay.setKeyListener(null);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        if (lightSensor == null) {
            showAlertDialog(getString(R.string.lightSensorNotFoundError));
            finish();
        }
        startBackGroundService();
    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public final void onSensorChanged(SensorEvent event) {
        lightValue = event.values[0];
        displayData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
        if (isServiceRunning) {
            Toast.makeText(this, R.string.dataInBackgroundNotification,
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopBackgroundService();
    }

    @Override
    public void onBackPressed() {
        Intent homeScreen = new Intent(Intent.ACTION_MAIN);
        homeScreen.addCategory(Intent.CATEGORY_HOME);
        homeScreen.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(homeScreen);
    }

    private void displayData() {
        String toDisplay = Float.toString(lightValue) + " lx";
        sensorValueDisplay.setText(toDisplay);
    }

    private void startBackGroundService() {
        Bundle bundle = getIntent().getExtras();
        String destinationIP = null;
        int sendingPeriod = -1;
        if (bundle != null) {
            destinationIP = (String)bundle.get("ip");
            sendingPeriod = (int)bundle.get("period");
        }
        if (destinationIP == null || sendingPeriod == -1) {
            showAlertDialog(getString(R.string.networkDataNotSpecified));
            finish();
        }
        Intent sendingServiceIntent = new Intent(this, SendingService.class);
        sendingServiceIntent.putExtra("ip", destinationIP);
        sendingServiceIntent.putExtra("period", sendingPeriod);
        startService(sendingServiceIntent);
        isServiceRunning = true;
    }

    public void stopSending(View view) {
        stopBackgroundService();
        finish();
    }

    private void stopBackgroundService() {
        Intent serviceIntent = new Intent(this, SendingService.class);
        stopService(serviceIntent);
        isServiceRunning = false;
    }

    private void showAlertDialog(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Error");
        builder.setMessage(message);
        builder.setPositiveButton("OK", null);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

}
