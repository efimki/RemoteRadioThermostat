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

import android.support.annotation.IntDef;
import android.support.annotation.VisibleForTesting;

import com.google.android.things.pio.I2cDevice;
import com.google.android.things.pio.PeripheralManagerService;
import com.google.android.things.pio.UartDevice;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Driver for the BMP/BME 280 temperature sensor.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class Ds18b20 extends OneWire {

    private static final String TAG = Ds18b20.class.getSimpleName();

    static final int DS18X20_CONVERT_T = 0x44;
    static final int DS18X20_READ = 0xBE;




    /**
     * Chip ID for the BMP280
     */
    public static final int CHIP_ID_BMP280 = 0x58;
    /**
     * Chip ID for the BME280
     */
    public static final int CHIP_ID_BME280 = 0x60;
    /**
     * Default I2C address for the sensor.
     */
    public static final int DEFAULT_I2C_ADDRESS = 0x77;
    @Deprecated
    public static final int I2C_ADDRESS = DEFAULT_I2C_ADDRESS;

    // Sensor constants from the datasheet.
    // https://cdn-shop.adafruit.com/datasheets/BST-BMP280-DS001-11.pdf
    /**
     * Mininum temperature in Celsius the sensor can measure.
     */
    public static final float MIN_TEMP_C = -40f;
    /**
     * Maximum temperature in Celsius the sensor can measure.
     */
    public static final float MAX_TEMP_C = 85f;
    /**
     * Minimum pressure in hPa the sensor can measure.
     */
    public static final float MIN_PRESSURE_HPA = 300f;
    /**
     * Maximum pressure in hPa the sensor can measure.
     */
    public static final float MAX_PRESSURE_HPA = 1100f;
    /**
     * Mininum humidity in RH the sensor can measure.
     */
    public static final float MIN_HUM_RH = 0f;
    /**
     * Maximum temperature in RH the sensor can measure.
     */
    public static final float MAX_HUM_RH = 100f;
    /**
     * Maximum power consumption in micro-amperes when measuring temperature.
     */
    public static final float MAX_POWER_CONSUMPTION_TEMP_UA = 325f;
    /**
     * Maximum power consumption in micro-amperes when measuring pressure.
     */
    public static final float MAX_POWER_CONSUMPTION_PRESSURE_UA = 720f;
    /**
     * Maximum power consumption in micro-amperes when measuring humidity.
     */
    public static final float MAX_POWER_CONSUMPTION_HUMIDITY_UA = 340f;
    /**
     * Maximum frequency of the measurements.
     */
    public static final float MAX_FREQ_HZ = 181f;
    /**
     * Minimum frequency of the measurements.
     */
    public static final float MIN_FREQ_HZ = 23.1f;

    /**
     * Power mode.
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({MODE_SLEEP, MODE_FORCED, MODE_NORMAL})
    public @interface Mode {}
    public static final int MODE_SLEEP = 0;
    public static final int MODE_FORCED = 1;
    public static final int MODE_NORMAL = 2;

    /**
     * Oversampling multiplier.
     * TODO: add other oversampling modes
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({OVERSAMPLING_SKIPPED, OVERSAMPLING_1X})
    public @interface Oversampling {}
    public static final int OVERSAMPLING_SKIPPED = 0;
    public static final int OVERSAMPLING_1X = 1;

    // Registers
    private static final int BMX280_REG_TEMP_CALIB_1 = 0x88;
    private static final int BMX280_REG_TEMP_CALIB_2 = 0x8A;
    private static final int BMX280_REG_TEMP_CALIB_3 = 0x8C;

    private static final int BMX280_REG_PRESS_CALIB_1 = 0x8E;
    private static final int BMX280_REG_PRESS_CALIB_2 = 0x90;
    private static final int BMX280_REG_PRESS_CALIB_3 = 0x92;
    private static final int BMX280_REG_PRESS_CALIB_4 = 0x94;
    private static final int BMX280_REG_PRESS_CALIB_5 = 0x96;
    private static final int BMX280_REG_PRESS_CALIB_6 = 0x98;
    private static final int BMX280_REG_PRESS_CALIB_7 = 0x9A;
    private static final int BMX280_REG_PRESS_CALIB_8 = 0x9C;
    private static final int BMX280_REG_PRESS_CALIB_9 = 0x9E;

    private static final int BME280_REG_HUM_CALIB_1 = 0xA1;
    private static final int BME280_REG_HUM_CALIB_2 = 0xE1;
    private static final int BME280_REG_HUM_CALIB_3 = 0xE3;
    private static final int BME280_REG_HUM_CALIB_4 = 0xE4;
    private static final int BME280_REG_HUM_CALIB_5 = 0xE5;
    private static final int BME280_REG_HUM_CALIB_6 = 0xE6;
    private static final int BME280_REG_HUM_CALIB_7 = 0xE7;

    private static final int BMX280_REG_ID = 0xD0;

    @VisibleForTesting
    static final int BMX280_REG_CTRL = 0xF4;
    @VisibleForTesting
    static final int BME280_REG_CTRL_HUM = 0xF2;

    private static final int BMX280_REG_PRESS = 0xF7;
    private static final int BMX280_REG_TEMP = 0xFA;
    private static final int BME280_REG_HUM = 0xFD;

    private static final int BMX280_POWER_MODE_MASK = 0b00000011;
    private static final int BMX280_POWER_MODE_SLEEP = 0b00000000;
    private static final int BMX280_POWER_MODE_NORMAL = 0b00000011;
    private static final int BMX280_OVERSAMPLING_PRESSURE_MASK = 0b00011100;
    private static final int BMX280_OVERSAMPLING_PRESSURE_BITSHIFT = 2;
    private static final int BMX280_OVERSAMPLING_TEMPERATURE_MASK = 0b11100000;
    private static final int BMX280_OVERSAMPLING_TEMPERATURE_BITSHIFT = 5;

    private I2cDevice mDevice;
    private final int[] mTempCalibrationData = new int[3];
    private final int[] mPressureCalibrationData = new int[9];
    private final int[] mHumCalibrationData = new int[6];
    private final byte[] mBuffer = new byte[3]; // for reading sensor values
    private boolean mEnabled;
    private boolean mHasHumiditySensor;
    private int mChipId;
    private int mMode;
    private int mPressureOversampling;
    private int mTemperatureOversampling;
    private int mHumidityOversampling;

    /**
     * Create a new Ds18b20 sensor driver connected on the given UART.
     * @param uart UART port the sensor is connected to.
     * @throws IOException
     */
    public Ds18b20(String uart) throws IOException {
        super(uart);
    }

    /**
     * Create a new Ds18b20 sensor driver connected on the given UART with particular ID.
     * @param uart UART port the sensor is connected to.
     * @param id OneWire ID of the sensorr.
     * @throws IOException
     */
    public Ds18b20(String uart, String id) throws IOException {
        super(uart, id);
    }

    /**
     * Read the current temperature.
     *
     * @return the current temperature in degrees Celsius
     */
    float readTemperature() throws IOException {
        oneWireCommand(DS18X20_CONVERT_T, getOneWireId());
        // Wait for conversion
        try {
            while (oneWireBit(true)) {
                Thread.sleep(100);
            }
        } catch (InterruptedException e)
        {
            throw new IOException("Interrupted waiting for conversion.");
        }
        // Read result
        oneWireCommand(DS18X20_READ, getOneWireId());
        byte[] raw_measure = oneWireReadBytes(9);
        int msb = raw_measure[1] & 0xff;
        int lsb = raw_measure[0] & 0xff;
        float temp_read = (msb << 8 | lsb);
        float temp = temp_read / 16;
        return temp;
    }

    /**
     * Create a new OneWire sensor driver connected on the given UART.
     * @param device UART device of the sensor.
     * @throws IOException
     */
    /*package*/ Ds18b20(UartDevice device) throws IOException {
        super(device);
    }

}
