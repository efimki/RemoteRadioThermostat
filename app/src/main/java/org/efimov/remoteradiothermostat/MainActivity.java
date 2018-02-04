package org.efimov.remoteradiothermostat;

import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventCallback;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;

import com.google.android.things.contrib.driver.onewire.Ds18b20SensorDriver;
import com.google.android.things.contrib.driver.radiothermostat.RadioThermostatSensorDriver;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;


public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private SensorManager mSensorManager;

    private RadioThermostatSensorDriver mRadioThermostatSensorDriver;
    private Ds18b20SensorDriver mDs18b20SensorDriver;

    private FirebaseDatabase mDatabase;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Connect to Firebase database.
        mDatabase = FirebaseDatabase.getInstance();

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSensorManager.registerDynamicSensorCallback(new SensorCallback());

        try {
            // Register RadioThermostat
            mRadioThermostatSensorDriver = new RadioThermostatSensorDriver("192.168.1.60");
            mRadioThermostatSensorDriver.registerTemperatureSensor();
            // Register Ds18b20
            mDs18b20SensorDriver = new Ds18b20SensorDriver("UART6");
            mDs18b20SensorDriver.registerTemperatureSensor();
        } catch (IOException e) {
            Log.e(TAG, "Cannot register sensor", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private class SensorListener extends SensorEventCallback {
        @Override
        public void onSensorChanged (SensorEvent event) {
            Log.i("SensorEventCallback", "onSensorChanged: " + event.sensor.toString());
            String sensorName = event.sensor.getName();
            float temp = event.values[0];
            Log.i( TAG, "Reporting temperature: " + sensorName + " = " + Float.toString(temp));
            final DatabaseReference log = mDatabase.getReference("Temperatures").child(sensorName);
            // upload temperature to firebase.
            log.child("sensor").setValue(sensorName);
            log.child("temp").setValue((int)event.values[0]);
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

}
