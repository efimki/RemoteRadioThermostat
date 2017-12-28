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

import android.hardware.Sensor;

import com.google.android.things.userdriver.UserDriverManager;
import com.google.android.things.userdriver.UserSensor;
import com.google.android.things.userdriver.UserSensorDriver;
import com.google.android.things.userdriver.UserSensorReading;

import java.io.IOException;
import java.util.UUID;

public class RadioThermostatSensorDriver implements AutoCloseable {
    private static final String TAG = "RadioThermostatSensorDriver";

    // DRIVER parameters
    // documented at https://source.android.com/devices/sensors/hal-interface.html#sensor_t
    private static final String DRIVER_VENDOR = "RadioThermostat";
    private static final String DRIVER_NAME = "Wi-Fi USNAP ";
    private static final int DRIVER_MIN_DELAY_US = Math.round(1000000.f / RadioThermostat.MAX_FREQ_HZ);
    private static final int DRIVER_MAX_DELAY_US = Math.round(1000000.f / RadioThermostat.MIN_FREQ_HZ);

    private RadioThermostat mDevice;

    private TemperatureUserDriver mTemperatureUserDriver;

    /**
     * Create a new Radio Thermostat sensor driver connected on the given host.
     * The driver emits {@link android.hardware.Sensor} with temperature data when
     * registered.
     * @param host Name or IP Address of radio thermostat host.     *
     * @throws IOException
     * @see #registerTemperatureSensor()
     */
    public RadioThermostatSensorDriver(String host) throws IOException {
        mDevice = new RadioThermostat(host);
    }

    /**
     * Close the driver and the underlying device.
     * @throws IOException
     */
    @Override
    public void close() throws IOException {
        unregisterTemperatureSensor();
        if (mDevice != null) {
            try {
                mDevice.close();
            } finally {
                mDevice = null;
            }
        }
    }

    /**
     * Register a {@link UserSensor} that pipes temperature readings into the Android SensorManager.
     * @see #unregisterTemperatureSensor()
     */
    public void registerTemperatureSensor() {
        if (mDevice == null) {
            throw new IllegalStateException("cannot register closed driver");
        }

        if (mTemperatureUserDriver == null) {
            mTemperatureUserDriver = new TemperatureUserDriver();
            UserDriverManager.getManager().registerSensor(mTemperatureUserDriver.getUserSensor());
        }
    }

    /**
     * Unregister the temperature {@link UserSensor}.
     */
    public void unregisterTemperatureSensor() {
        if (mTemperatureUserDriver != null) {
            UserDriverManager.getManager().unregisterSensor(mTemperatureUserDriver.getUserSensor());
            mTemperatureUserDriver = null;
        }
    }


    private void maybeSleep() throws IOException {
        if ((mTemperatureUserDriver == null || !mTemperatureUserDriver.isEnabled())) {
            mDevice.setMode(RadioThermostat.MODE_SLEEP);
        } else {
            mDevice.setMode(RadioThermostat.MODE_NORMAL);
        }
    }

    private class TemperatureUserDriver extends UserSensorDriver {
        // DRIVER parameters
        // documented at https://source.android.com/devices/sensors/hal-interface.html#sensor_t
        private static final float DRIVER_MAX_RANGE = RadioThermostat.MAX_TEMP_C;
        private static final float DRIVER_RESOLUTION = 0.5f;
        private static final float DRIVER_POWER = RadioThermostat.MAX_POWER_CONSUMPTION_TEMP_UA / 1000.f;
        private static final int DRIVER_VERSION = 1;
        private static final String DRIVER_REQUIRED_PERMISSION = "";

        private boolean mEnabled;
        private UserSensor mUserSensor;

        private UserSensor getUserSensor() {
            if (mUserSensor == null) {
                mUserSensor = new UserSensor.Builder()
                        .setType(Sensor.TYPE_AMBIENT_TEMPERATURE)
                        .setName(DRIVER_NAME)
                        .setVendor(DRIVER_VENDOR)
                        .setVersion(DRIVER_VERSION)
                        .setMaxRange(DRIVER_MAX_RANGE)
                        .setResolution(DRIVER_RESOLUTION)
                        .setPower(DRIVER_POWER)
                        .setMinDelay(DRIVER_MIN_DELAY_US)
                        .setRequiredPermission(DRIVER_REQUIRED_PERMISSION)
                        .setMaxDelay(DRIVER_MAX_DELAY_US)
                        .setUuid(UUID.randomUUID())
                        .setDriver(this)
                        .build();
            }
            return mUserSensor;
        }

        @Override
        public UserSensorReading read() throws IOException {
            return new UserSensorReading(new float[]{mDevice.readTemperature()});
        }

        @Override
        public void setEnabled(boolean enabled) throws IOException {
            mEnabled = enabled;
            maybeSleep();
        }

        private boolean isEnabled() {
            return mEnabled;
        }
    }

}
