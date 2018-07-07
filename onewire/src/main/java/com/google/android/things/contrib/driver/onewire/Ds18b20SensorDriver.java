/*
 * Copyright 2018 Google Inc.
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

package com.google.android.things.contrib.driver.onewire;

import android.hardware.Sensor;

import com.google.android.things.userdriver.UserDriverManager;
import com.google.android.things.userdriver.sensor.UserSensor;
import com.google.android.things.userdriver.sensor.UserSensorDriver;
import com.google.android.things.userdriver.sensor.UserSensorReading;

import java.io.IOException;
import java.util.UUID;

public class Ds18b20SensorDriver implements AutoCloseable {
    private static final String TAG = "Ds18b20SensorDriver";

    // DRIVER parameters
    // documented at https://source.android.com/devices/sensors/hal-interface.html#sensor_t
    private static final String DRIVER_VENDOR = "Dallas Semiconductors";
    private static final String DRIVER_NAME = "DS18B20";
    private static final int DRIVER_MIN_DELAY_US = 10 * 1000 * 1000;
    private static final int DRIVER_MAX_DELAY_US = 20 * 1000 * 1000;

    private String mUart;
    private long mId;


    private TemperatureUserDriver mTemperatureUserDriver;

    /**
     * Create a new Ds18b20 sensor driver connected on the given UART.
     * The driver emits {@link android.hardware.Sensor} with temperature data when
     * registered.
     * @param uart UART port the sensor is connected to.
     * @throws IOException
     * @see #registerTemperatureSensor()
     */
    public Ds18b20SensorDriver(String uart) throws IOException {
        this(uart, 0);
    }

    /**
     * Create a new Ds18b20 sensor driver connected on the given UART.
     * The driver emits {@link android.hardware.Sensor} with temperature data when
     * registered.
     * @param uart UART port the sensor is connected to.
     * @param id   OneWire ID of the sensor.
     * @throws IOException
     * @see #registerTemperatureSensor()
     */
    public Ds18b20SensorDriver(String uart, long id) throws IOException {
        mUart = uart;
    }


    /**
     * Close the driver and the underlying device.
     * @throws IOException
     */
    @Override
    public void close() throws IOException {
        unregisterTemperatureSensor();
    }

    /**
     * Register a {@link UserSensor} that pipes temperature readings into the Android SensorManager.
     * @see #unregisterTemperatureSensor()
     */
    public void registerTemperatureSensor() {
        if (mTemperatureUserDriver == null) {
            mTemperatureUserDriver = new TemperatureUserDriver();
            UserDriverManager.getInstance().registerSensor(mTemperatureUserDriver.getUserSensor());
        }
    }

    /**
     * Unregister the temperature {@link UserSensor}.
     */
    public void unregisterTemperatureSensor() {
        if (mTemperatureUserDriver != null) {
            UserDriverManager.getInstance().unregisterSensor(mTemperatureUserDriver.getUserSensor());
            mTemperatureUserDriver = null;
        }
    }

    private class TemperatureUserDriver implements UserSensorDriver {
        // DRIVER parameters
        // documented at https://source.android.com/devices/sensors/hal-interface.html#sensor_t
        private static final float DRIVER_MAX_RANGE = Ds18b20.MAX_TEMP_C;
        private static final float DRIVER_RESOLUTION = 0.5f;
        private static final float DRIVER_POWER = Ds18b20.MAX_POWER_CONSUMPTION_TEMP_UA / 1000.f;
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
            Ds18b20 device = new Ds18b20(mUart, mId);
            try {
                return new UserSensorReading(new float[]{device.readTemperature()});
            } finally {
                device.close();
            }
        }

        @Override
        public void setEnabled(boolean enabled) throws IOException {
            mEnabled = enabled;
        }

        private boolean isEnabled() {
            return mEnabled;
        }
    }

}
