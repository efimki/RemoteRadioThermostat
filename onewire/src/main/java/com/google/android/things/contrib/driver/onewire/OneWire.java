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

import com.google.android.things.pio.PeripheralManager;
import com.google.android.things.pio.UartDevice;

import java.io.IOException;
import java.nio.ByteBuffer;

public class OneWire implements AutoCloseable {
    private static final String TAG = OneWire.class.getSimpleName();

    static final byte OW_MATCH_ROM = 0x55;
    static final byte OW_SKIP_ROM = (byte) 0xcc;
    static final byte OW_SEARCH_ROM = (byte) 0xf0;
    static final int OW_SEARCH_FIRST = -1;
    static final int OW_ID_SIZE = 8;


    UartDevice mUartDevice;

    /**
     * Create a new OneWire sensor driver connected on the given UART.
     *
     * @param uart UART port the sensor is connected to.
     * @throws IOException
     */
    public OneWire(String uart) throws IOException {
        this(PeripheralManager.getInstance().openUartDevice(uart));
    }

    /**
     * Create a new OneWire sensor driver connected on the given UART.
     *
     * @param device UART device of the sensor.
     * @throws IOException
     */
    @VisibleForTesting
    /*package*/ OneWire(UartDevice device) throws IOException {
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
        Log.i(TAG, "oneWireWriteByte start: " + Integer.toHexString(b));

        int i = 8;
        do {
            boolean j = oneWireBit((b & 1) != 0);
            b >>= 1;
            b &= 0x7f;
            if (j) {
                b |= 0x80;
            }
        } while (--i != 0);
        Log.i(TAG, "oneWireWriteByte done: " + Integer.toHexString(b));
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

    void oneWireCommand(int command, long id) throws IOException {
        reset();
        if (id != 0) {
            oneWireWriteByte(OW_MATCH_ROM);
            ByteBuffer buffer = ByteBuffer.allocate(8).putLong(id);
            buffer.rewind();
            while (buffer.hasRemaining()) {
                oneWireWriteByte(buffer.get());
            }
        } else {
            oneWireWriteByte(OW_SKIP_ROM);
        }
        oneWireWriteByte((byte) command);
    }

    long oneWireFindRom() throws IOException {
        byte[] romId = new byte[8];
        int diff = OW_SEARCH_FIRST;
        diff = findNextRom(diff, romId);
        long id = ByteBuffer.wrap(romId).getLong();
        return id;
    }

    private int findNextRom(int diff, byte[] id) throws IOException {
        int nextDiff = 0;
        int bitPos = OW_ID_SIZE * 8;
        int bytePos = 0;
        reset();
        oneWireWriteByte(OW_SEARCH_ROM); /* ROM search command */
        do {
            for (int j = 0; j < 8; ++j) {
                // Read one bit.
                boolean bit = oneWireBit(true);
                // Read complement bit.
                if (oneWireBit(true)) {
                    if (bit) {
                        // Read complement bit, if 1-1: data error.
                        throw new IOException("Data Error");
                    }
                } else if (!bit) {
                    if (diff > bitPos || (((id[bytePos] & 1) != 0) && diff != bitPos)) {
                        // if 0-0: 2 devices
                        // now true
                        bit = true;
                        // Next pass 0.
                        nextDiff = bitPos;
                    }
                }
                // Write one bit back.
                oneWireBit(bit);
                // Shift current id byte right.
                id[bytePos] = (byte) ((id[bytePos] >> 1) & 0x7f);
                if (bit) {
                    // Store bit.
                    id[bytePos] = (byte) (id[bytePos] | 0x80);
                }

                --bitPos;
            }
            // Next byte.
            bytePos++;
        } while (bitPos != 0);
        // Next bit to continue search.
        return nextDiff;
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
        int sleepMillis = 10;
        while (mUartDevice.read(buffer, buffer.length) == 0) {
            try {
                Thread.sleep(sleepMillis);
                sleepMillis = sleepMillis * 2;
                if (sleepMillis > 100) {
                    Log.e(TAG, "ReadByte timeout, throwing exception");
                    throw new IOException("UART ReadByte timeout");
                }
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
