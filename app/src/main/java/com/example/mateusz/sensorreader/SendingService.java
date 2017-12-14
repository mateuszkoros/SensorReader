package com.example.mateusz.sensorreader;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Vibrator;
import android.util.Log;
import android.widget.Toast;

import com.mklimek.sslutilsandroid.SslUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Scanner;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManagerFactory;

public class SendingService extends Service implements SensorEventListener {

    private String destinationIP;
    private int sendingPeriod = 100;
    private SensorManager sensorManager;
    private Sensor lightSensor;
    float lightValue = (float)-1.0;
    private Handler connectionHandler;
    private Runnable connectionRunnable;
    private Semaphore lightValueMutex = new Semaphore(1, true);

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Toast.makeText(this, R.string.serviceStartedNotification, Toast.LENGTH_SHORT).show();
        Bundle bundle = intent.getExtras();
        if (bundle != null) {
            destinationIP = (String)bundle.get("ip");
            sendingPeriod = (int)bundle.get("period");
        }
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        if (lightSensor == null) {
            showAlert(getString(R.string.lightSensorNotFoundError));
            stopSelf();
        }
        sensorManager.registerListener(this, lightSensor, sendingPeriod*1000);
        setupConnection();
        return START_STICKY;
    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public final void onSensorChanged(SensorEvent event) {
        try {
            lightValueMutex.acquire();
            lightValue = event.values[0];
            lightValueMutex.release();
        } catch (InterruptedException e) {
            Log.e("Mutex error", "Exception stacktrace:", e);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        connectionHandler.removeCallbacks(connectionRunnable);
        sensorManager.unregisterListener(this);
        Toast.makeText(this, R.string.serviceStoppedNotification, Toast.LENGTH_SHORT).show();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void setupConnection() {
        Context context = this;
        URL tmpUrl = null; // temporary variable to avoid assigning final value in try block
        HttpsURLConnection.setDefaultHostnameVerifier((hostname, sslSession) -> {
            Log.i("Certificate", "Approving certificate for: " + hostname);
            return true;
        });
        try {
            tmpUrl = new URL(destinationIP);
        } catch(MalformedURLException e) {
            Log.e("Error", "Exception stacktrace:", e);
            showAlert("Wrong ip specified: ".concat(destinationIP));
            stopSelf();
        }
        final URL url = tmpUrl;
        // read certificate from assets directory
        final SSLContext sslContext = SslUtils
                .getSslContextForCertificateFile(context, "cert.pem");
        connectionHandler = new Handler();
        connectionRunnable = new Runnable() {
            @Override
            public void run() {
                AsyncTask.execute(() -> {
                    try {
                        HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
                        urlConnection.setSSLSocketFactory(sslContext.getSocketFactory());
                        urlConnection.setDoOutput(true);
                        urlConnection.setDoInput(true);
                        urlConnection.setRequestProperty("Content-type", "text/plain");
                        writeToStream(urlConnection.getOutputStream());
                        urlConnection.connect();
                        if (urlConnection.getResponseCode() == HttpsURLConnection.HTTP_OK) {
                            readResponse(urlConnection.getInputStream());
                        } else {
                            Log.e("Error", "Failed to connect to server");
                        }
                        urlConnection.disconnect();
                    } catch (UnsupportedEncodingException e) {
                        Log.e("Error", "Exception stacktrace:", e);
                        showAlert("Failed to encode message");
                        stopSelf();
                    } catch(MalformedURLException e) {
                        Log.e("Error", "Exception stacktrace:", e);
                        showAlert("Wrong ip specified: ".concat(destinationIP));
                        stopSelf();
                    } catch (IOException e) {
                        Log.e("Error", "Exception stacktrace:", e);
                        showAlert("Failed to open connection");
                        stopSelf();
                    }
                    connectionHandler.postDelayed(this, sendingPeriod);
                });
            }
        };
        connectionHandler.post(connectionRunnable);
    }

    private void showAlert(String message) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification notification = new Notification.Builder(this)
                .setContentTitle("Error")
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build();
        manager.notify(42, notification);
    }

    private void writeToStream(OutputStream out) {
        try {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, "UTF-8"));
            lightValueMutex.acquire();
            writer.write(Float.toString(lightValue));
            lightValueMutex.release();
            writer.flush();
            writer.close();
            out.close();
        } catch (IOException e) {
            Log.e("Error", "Exception stacktrace:", e);
            showAlert("Error sending message");
        } catch (InterruptedException e) {
            Log.e("Mutex error", "Exception stacktrace:", e);
        }
    }

    private void readResponse(InputStream in) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        String inputLine;
        StringBuilder response = new StringBuilder();
        try {
            while ((inputLine = reader.readLine()) != null) {
                response.append(inputLine);
            }
            Log.i("Response:", response.toString());
            reader.close();
        } catch (IOException e) {
            Log.e("Error", "Exception stacktrace:", e);
        }
    }

}
