/*
 * Copyright 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.things.contrib.driver.radiothermostat;

import android.support.annotation.IntDef;
import android.support.annotation.VisibleForTesting;

import com.google.android.things.pio.I2cDevice;
import com.google.android.things.pio.PeripheralManagerService;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Driver for the RadioThermostat temperature sensor.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class RadioThermostat implements AutoCloseable {

    private static final String TAG = RadioThermostat.class.getSimpleName();

    // Sensor constants from the datasheet.
    // https://radiothermostat.desk.com/customer/portal/articles/1268461-where-do-i-find-information-about-the-wifi-api-
    /**
     * Mininum temperature in Celsius the sensor can measure.
     */
    public static final float MIN_TEMP_C = -40f;
    /**
     * Maximum temperature in Celsius the sensor can measure.
     */
    public static final float MAX_TEMP_C = 85f;
    /**
     * Temperature reported if sensor is invalid.
     */
    public static final float INVALID_TEMP_C = -99f;
    /**
     * Maximum power consumption in micro-amperes when measuring temperature.
     */
    public static final float MAX_POWER_CONSUMPTION_TEMP_UA = 325f;
    /**
     * Maximum frequency of the measurements.
     */
    public static final float MAX_FREQ_HZ = 1f;
    /**
     * Minimum frequency of the measurements.
     */
    public static final float MIN_FREQ_HZ = 0.01f;

    /**
     * Power mode.
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({MODE_SLEEP, MODE_FORCED, MODE_NORMAL})
    public @interface Mode {
    }

    public static final int MODE_SLEEP = 0;
    public static final int MODE_FORCED = 1;
    public static final int MODE_NORMAL = 2;

    private HttpURLConnection mConnnection;
    private JSONObject mLastResponse;
    private int mMode = MODE_NORMAL;
    private boolean mEnabled;
    private float mLastTemperature = INVALID_TEMP_C;

    /**
     * Create a new Radio Thermostat sensor driver connected on the given host.
     *
     * @param host Name or IP Address of radio thermostat host.
     */
    public RadioThermostat(String host) throws IOException {
        URL urlObj = new URL("http://" + host + "/tstat");
        mConnnection = (HttpURLConnection) urlObj.openConnection();
    }

    @VisibleForTesting
    /*package*/ RadioThermostat(HttpURLConnection connection) {
        mConnnection = connection;
    }

    @VisibleForTesting
    /*package*/ RadioThermostat(HttpURLConnection connection, JSONObject responseJson) {
        mConnnection = connection;
        mLastResponse = responseJson;
    }

    private void connect() throws IOException {
        mConnnection.setDoOutput(false);
        mConnnection.setRequestMethod("GET");
        mConnnection.setConnectTimeout(15000);
        mConnnection.connect();
    }

    private void getTemperatureFromThermostat() throws IOException, JSONException {
        connect();
        //Receive the response from the server
        InputStream in = new BufferedInputStream(mConnnection.getInputStream());
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StringBuilder result = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            result.append(line);
        }
        in.close();
        reader.close();
        if (mLastResponse == null) {
            mLastResponse = new JSONObject(result.toString());
        }
    }

    /**
     * Read the current temperature.
     *
     * @return the current temperature in degrees Celsius
     */
    private float readTemperatureJson() throws IOException, IllegalStateException {
        try {
            // try parse the string to a JSON object
            getTemperatureFromThermostat();
            double tempF = mLastResponse.getDouble("temp");
            return (float) (tempF - 32.0f) / 1.8f;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return INVALID_TEMP_C;
    }

    /**
     * Close the driver and the underlying connection.
     */
    @Override
    public void close() throws IOException {
        if (mConnnection != null) {
            mConnnection.disconnect();
            mConnnection = null;
        }
    }

    public void setMode(int mode) throws IllegalStateException {
        if (mConnnection == null) {
            throw new IllegalStateException("Thermostat connection is not open.");
        }
        mMode = mode;
    }

    /**
     * Read the current temperature.
     *
     * @return the current temperature in degrees Celsius
     */
    public float readTemperature() throws IOException, IllegalStateException {
        if (mConnnection == null) {
            throw new IllegalStateException("Thermostat connection is not open.");
        }
        switch (mMode) {
            case MODE_FORCED:
                return readTemperatureJson();
            case MODE_NORMAL:
                return readTemperatureJson();
            case MODE_SLEEP:
                return mLastTemperature;
        }
        return mLastTemperature;
    }
}
