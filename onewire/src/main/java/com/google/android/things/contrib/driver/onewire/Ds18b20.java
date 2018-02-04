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

import android.support.annotation.VisibleForTesting;
import android.util.Log;

import com.google.android.things.pio.UartDevice;
import com.dalsemi.onewire.utils.CRC8;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Driver for the DS18B20 temperature sensor.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class Ds18b20 extends OneWire {

    private static final String TAG = Ds18b20.class.getSimpleName();

    static final int DS18X20_CONVERT_T = 0x44;
    static final int DS18X20_READ = 0xBE;

    // Sensor constants from the datasheet.
    // https://datasheets.maximintegrated.com/en/ds/DS18B20.pdf

    /**
     * Mininum temperature in Celsius the sensor can measure.
     */
    public static final float MIN_TEMP_C = -55f;
    /**
     * Maximum temperature in Celsius the sensor can measure.
     */
    public static final float MAX_TEMP_C = 125f;
    /**
     * Maximum power consumption in micro-amperes when measuring temperature.
     */
    public static final float MAX_POWER_CONSUMPTION_TEMP_UA = 1500f;

    /**
     * Maximum frequency of the measurements.
     */
    public static final float MAX_FREQ_HZ = 1f;
    /**
     * Minimum frequency of the measurements.
     */
    public static final float MIN_FREQ_HZ = 1/600f;

    /**
     * Create a new Ds18b20 sensor driver connected on the given UART.
     *
     * @param uart UART port the sensor is connected to.
     * @throws IOException
     */
    public Ds18b20(String uart) throws IOException {
        super(uart);
    }

    /**
     * Create a new Ds18b20 sensor driver connected on the given UART with particular ID.
     *
     * @param uart UART port the sensor is connected to.
     * @param id   OneWire ID of the sensor.
     * @throws IOException
     */
    public Ds18b20(String uart, long id) throws IOException {
        super(uart, id);
    }

    /**
     * Create a new OneWire sensor driver connected on the given UART.
     *
     * @param device UART device of the sensor.
     * @throws IOException
     */
    @VisibleForTesting
    /*package*/ Ds18b20(UartDevice device) throws IOException {
        super(device);
    }

    /**
     * Create a new OneWire sensor driver connected on the given UART with particular ID..
     *
     * @param device UART device of the sensor.
     * @param id     OneWire ID of the sensor.
     * @throws IOException
     */
    @VisibleForTesting
    /*package*/ Ds18b20(UartDevice device, long id) throws IOException {
        super(device, id);
    }

    /**
     * Read the current temperature.
     *
     * @return the current temperature in degrees Celsius
     */
    float readTemperature() throws IOException {
        Log.i(TAG, "Reading temperature.");
        oneWireCommand(DS18X20_CONVERT_T, getOneWireId());
        // Wait for conversion.
        try {
            for (int i = 1; i < 10; ++i) {
                if(oneWireBit(true))
                    break;
                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            throw new IOException("Interrupted waiting for conversion.");
        }
        // Read result.
        oneWireCommand(DS18X20_READ, getOneWireId());
        float temp = convertTemperature(oneWireReadBytes(9));
        Log.i(TAG, "Read Temperature: " + Float.toString(temp));
        return temp;
    }

    float convertTemperature(byte[] rawMeasure) throws IOException {
        // Verify CRC8 of the result.
        int crc = CRC8.compute(rawMeasure, 0, 9);
        if (crc != 0) {
            // Calculate expected CRC.
            int crcminus = CRC8.compute(rawMeasure, 0, 8);
            throw new IOException("Invalid CRC8. Expected: " + Integer.toHexString(crcminus));
        }

        ByteBuffer raw = ByteBuffer.wrap(rawMeasure).order(ByteOrder.LITTLE_ENDIAN);
        return raw.getShort() / 16f;
    }
}
