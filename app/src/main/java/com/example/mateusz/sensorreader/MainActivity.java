package com.example.mateusz.sensorreader;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;
import java.util.zip.DataFormatException;

public class MainActivity extends AppCompatActivity {

    private EditText destinationIpDisplay;
    private EditText destinationPortDisplay;
    private EditText periodDisplay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        destinationIpDisplay = findViewById(R.id.destinationIP);
        destinationPortDisplay = findViewById(R.id.destinationPort);
        periodDisplay = findViewById(R.id.sendingPeriod);
    }

    public void sendData(View view) {
        Intent displayActivityIntent = new Intent(this, DisplayActivity.class);
        String destinationIP;
        int destinationPort;
        int sendingPeriod;
        try {
            destinationIP = getIP();
        } catch (DataFormatException e) {
            destinationIpDisplay.setError(getString(R.string.invalidIpError));
            return;
        }
        try {
            destinationPort = getPort();
        } catch (DataFormatException e) {
            destinationPortDisplay.setError(getString(R.string.invalidPortError));
            return;
        }
        try {
            sendingPeriod = getPeriod();
        } catch (DataFormatException e){
            periodDisplay.setError(getString(R.string.invalidPeriodError));
            return;
        }
        destinationIP = destinationIP.concat(":").concat(Integer.toString(destinationPort));
        displayActivityIntent.putExtra("ip", destinationIP);
        displayActivityIntent.putExtra("period", sendingPeriod);
        startActivity(displayActivityIntent);
    }

    private String getIP() throws DataFormatException {
        String ip = destinationIpDisplay.getText().toString();

        if (Patterns.IP_ADDRESS.matcher(ip).matches()) {
            return "https://".concat(ip);
        } else {
            throw new DataFormatException();
        }
    }

    private int getPort() throws DataFormatException {
        int port = Integer.parseInt(destinationPortDisplay.getText().toString());
        if (port > 0) {
            return port;
        } else {
            throw new DataFormatException();
        }
    }

    private int getPeriod() throws DataFormatException {
        int period = Integer.parseInt(periodDisplay.getText().toString());
        if (period >= 100) {
            return period;
        } else {
            throw new DataFormatException();
        }
    }

}
