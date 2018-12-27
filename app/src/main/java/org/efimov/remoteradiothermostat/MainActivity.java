package org.efimov.remoteradiothermostat;

import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventCallback;
import android.hardware.SensorManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;

import com.google.android.things.contrib.driver.onewire.Ds18b20SensorDriver;
import com.google.android.things.contrib.driver.radiothermostat.RadioThermostatSensorDriver;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.IgnoreExtraProperties;
import com.google.firebase.database.ValueEventListener;

import org.chromium.net.CronetEngine;

import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;


public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private SensorManager mSensorManager;

    private RadioThermostatSensorDriver mRadioThermostatSensorDriver;
    private Ds18b20SensorDriver mDs18b20SensorDriver;

    private FirebaseDatabase mDatabase;
    private CronetEngine mCronetEngine;

    MainActivity() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Use Cronet for networking.
        mCronetEngine = new CronetEngine.Builder(this).build();
        URL.setURLStreamHandlerFactory(mCronetEngine.createURLStreamHandlerFactory());

        // Connect to Firebase database.
        mDatabase = FirebaseDatabase.getInstance();
        mDatabase.setPersistenceEnabled(true);

        // Register sensor callback.
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSensorManager.registerDynamicSensorCallback(new SensorCallback());

        readConfig();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private class SensorListener extends SensorEventCallback {
        @Override
        public void onSensorChanged(SensorEvent event) {
            Log.i("SensorEventCallback", "onSensorChanged: " + event.sensor.toString());
            String sensorName = event.sensor.getName();
            float temp = event.values[0];
            Log.i(TAG, "Reporting temperature: " + sensorName + " = " + Float.toString(temp));
            final DatabaseReference log = mDatabase.getReference("Temperatures").child(sensorName);
            // upload temperature to firebase.
            log.child("sensor").setValue(sensorName);
            log.child("temp").setValue((int) event.values[0]);
            log.child("time").setValue(new SimpleDateFormat("yyyy.MM.dd HH:mm:ss").format(new Date()));
        }
    }

    private class SensorCallback extends SensorManager.DynamicSensorCallback {
        @Override
        public void onDynamicSensorConnected(Sensor sensor) {
            //Sensor connected
            Log.e("DynamicSensorCallback", "Sensor Connected. " + sensor.toString());
            if (sensor.getType() == Sensor.TYPE_AMBIENT_TEMPERATURE) {
                mSensorManager.registerListener(new SensorListener(), sensor, SensorManager.SENSOR_DELAY_NORMAL);
            }
        }

        @Override
        public void onDynamicSensorDisconnected(Sensor sensor) {
            //Sensor disconnected
            Log.e("DynamicSensorCallback", "Sensor Disconnected. " + sensor.toString());
        }
    }

    private boolean setupWifi(String ssid, String passkey) {
        WifiConfiguration wifiConfiguration = new WifiConfiguration();
        wifiConfiguration.SSID = "\"" + ssid + "\"";
        wifiConfiguration.preSharedKey = "\"" + passkey + "\"";

        WifiManager manager = (WifiManager) getSystemService(WIFI_SERVICE);
        boolean enabled = manager.enableNetwork(manager.addNetwork(wifiConfiguration), true);
        return enabled;
    }

    private void readConfig() {
        mDatabase.getReference("Config").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String ssid = dataSnapshot.child("ssid").getValue(String.class);
                String pwd = dataSnapshot.child("pwd").getValue(String.class);
                if (ssid != "") {
                    Log.i(TAG, "Setup WiFI: " + ssid);
                    setupWifi(ssid, pwd);
                }
                try {
                    // Register Ds18b20
                    String host = dataSnapshot.child("host").getValue(String.class);
                    if (host != "") {
                        Log.i(TAG, "Register RadioThermostatSensorDriver: " + host);
                        mRadioThermostatSensorDriver = new RadioThermostatSensorDriver(host);
                        mRadioThermostatSensorDriver.registerTemperatureSensor();
                    }
                    // Register Ds18b20
                    String uart = dataSnapshot.child("uart").getValue(String.class);
                    if (uart != "") {
                        Log.i(TAG, "Register mDs18b20SensorDriver: " + uart);
                        mDs18b20SensorDriver = new Ds18b20SensorDriver(uart);
                        mDs18b20SensorDriver.registerTemperatureSensor();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Cannot register sensor", e);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

}
