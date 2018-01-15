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
    public static final float MIN_FREQ_HZ = 1f;

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
     * @param id OneWire ID of the sensor.
     * @throws IOException
     */
    public Ds18b20(String uart, String id) throws IOException {
        super(uart, id);
    }

    /**
     * Create a new OneWire sensor driver connected on the given UART.
     * @param device UART device of the sensor.
     * @throws IOException
     */
    /*package*/ Ds18b20(UartDevice device) throws IOException {
        super(device);
    }

    /**
     * Create a new OneWire sensor driver connected on the given UART with particular ID..
     * @param device UART device of the sensor.
     * @param id OneWire ID of the sensor.
     * @throws IOException
     */
    /*package*/ Ds18b20(UartDevice device, String id) throws IOException {
        super(device, id);
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
        // TODO(mef): Verify CRC8 of the result.
        int msb = raw_measure[1] & 0xff;
        int lsb = raw_measure[0] & 0xff;
        float temp_read = (msb << 8 | lsb);
        float temp = temp_read / 16;
        return temp;
    }

}
