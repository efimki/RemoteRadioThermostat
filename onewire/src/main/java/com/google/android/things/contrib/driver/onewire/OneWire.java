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

import com.google.android.things.pio.PeripheralManagerService;
import com.google.android.things.pio.UartDevice;

import java.io.IOException;

public class OneWire implements AutoCloseable {
    private static final String TAG = OneWire.class.getSimpleName();

    static final byte OW_MATCH_ROM = 0x55;
    static final byte OW_SKIP_ROM = (byte) 0xcc;
    static final byte OW_SEARCH_ROM = (byte) 0xf0;

    UartDevice mUartDevice;
    byte[] mOneWireId;

    /**
     * Create a new OneWire sensor driver connected on the given UART.
     *
     * @param uart UART port the sensor is connected to.
     * @throws IOException
     */
    public OneWire(String uart) throws IOException {
        this(uart, null);
    }

    /**
     * Create a new OneWire sensor driver connected on the given UART with particular ID.
     *
     * @param uart UART port the sensor is connected to.
     * @param id   OneWire ID of the sensorr.
     * @throws IOException
     */
    public OneWire(String uart, String id) throws IOException {
        this(new PeripheralManagerService().openUartDevice(uart), id);
    }

    /**
     * Create a new OneWire sensor driver connected on the given UART.
     *
     * @param device UART device of the sensor.
     * @throws IOException
     */
    @VisibleForTesting
    /*package*/ OneWire(UartDevice device) throws IOException {
        this(device, null);
    }

    /**
     * Create a new OneWire sensor driver connected on the given UART with particular ID..
     *
     * @param device UART device of the sensor.
     * @param id     OneWire ID of the sensor.
     * @throws IOException
     */
    @VisibleForTesting
    /*package*/ OneWire(UartDevice device, String id) throws IOException {
        mUartDevice = device;

        try {
            mUartDevice.setDataSize(8);
            mUartDevice.setParity(UartDevice.PARITY_NONE);
            mUartDevice.setStopBits(1);
            mUartDevice.setHardwareFlowControl(UartDevice.HW_FLOW_CONTROL_NONE);
            // This hangs 'close' test if there is no data to read.
            // reset();
        } catch (IOException | RuntimeException e) {
            try {
                close();
            } catch (IOException | RuntimeException ignored) {
            }
            throw e;
        }
    }

    /**
     * Returns the One Wire Device ID.
     */
    public byte[] getOneWireId() {
        return mOneWireId;
    }

    protected boolean oneWireBit(boolean b) throws IOException {
        if (b) {
            /* Write 1 */
            uartWriteByte(0xff);
        } else {
            /* Write 0 */
            uartWriteByte(0x00);
        }

        /* Read */
        int c = uartReadByte();
        b = ((c & 0xff) == 0xff);
        return b;
    }

    protected byte oneWireWriteByte(byte b) throws IOException {
        int i = 8;
        do {
            boolean j = oneWireBit((b & 1) != 0);
            b >>= 1;
            b &= 0x7f;
            if (j) {
                b |= 0x80;
            }
        } while (--i != 0);
        return b;
    }

    // Read bytes by writing 0xFF.
    protected byte[] oneWireReadBytes(int readCount) throws IOException {
        byte[] bytes = new byte[readCount];
        for (int i = 0; i < readCount; ++i) {
            bytes[i] = oneWireWriteByte((byte) 0xff);
        }
        return bytes;
    }

    void oneWireCommand(int command, byte[] id) throws IOException {
        reset();
        if (id != null) {
            oneWireWriteByte(OW_MATCH_ROM);
            for (byte b : id) {
                oneWireWriteByte(b);
            }
        } else {
            oneWireWriteByte(OW_SKIP_ROM);
        }
        oneWireWriteByte((byte) command);
    }

    private void uartWriteByte(int b) throws IOException {
        if (mUartDevice == null) {
            throw new IllegalStateException("Uart device is not open");
        }
        byte[] buffer = new byte[]{(byte) b};
        mUartDevice.write(buffer, 1);
    }

    private int uartReadByte() throws IOException {
        if (mUartDevice == null) {
            throw new IllegalStateException("Uart device is not open");
        }
        // Maximum amount of data to read at one time
        final int maxCount = 1;
        byte[] buffer = new byte[maxCount];
        while (mUartDevice.read(buffer, buffer.length) == 0) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                break;
            }
        }
        int b = (buffer[0] & 0xff);
        return b;
    }

    protected boolean reset() throws IOException {
        if (mUartDevice == null) {
            throw new IllegalStateException("Uart device is not open");
        }
        mUartDevice.setBaudrate(9600);
        uartWriteByte(0xf0);
        int probe = uartReadByte();
        mUartDevice.setBaudrate(115200);
        if (probe == 0 || probe == 0xf0) {
            throw new IOException("OneWire devices not found");
        }
        return true;
    }

    @Override
    public void close() throws IOException {
        if (mUartDevice != null) {
            try {
                mUartDevice.close();
                mUartDevice = null;
            } catch (IOException e) {
                Log.w(TAG, "Unable to close UART device", e);
            }
        }
    }
}
