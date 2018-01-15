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

package com.google.android.things.contrib.driver.onewire;

import com.google.android.things.pio.UartDevice;

import junit.framework.Assert;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.verification.VerificationMode;

import java.io.IOException;

import static junit.framework.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.byteThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;

public class Ds18b20Test {

    @Mock
    private UartDevice mUart;

    @Rule
    public MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Rule
    public ExpectedException mExpectedException = ExpectedException.none();

    @Test
    public void close() throws IOException {
        Ds18b20 ds18b20 = new Ds18b20(mUart);
        ds18b20.close();
        Mockito.verify(mUart).close();
    }

    @Test
    public void close_safeToCallTwice() throws IOException {
        Ds18b20 ds18b20 = new Ds18b20(mUart);
        ds18b20.close();
        ds18b20.close();  // should not throw
        Mockito.verify(mUart).close();
    }

    @Test
    public void readTemperature_throwsIfClosed() throws IOException {
        Ds18b20 ds18b20 = new Ds18b20(mUart);
        ds18b20.close();
        mExpectedException.expect(IllegalStateException.class);
        mExpectedException.expectMessage("not open");
        ds18b20.readTemperature();
    }

    @Test
    public void readTemperature_throwsIfNoDevices() throws IOException {
        Ds18b20 ds18b20 = new Ds18b20(mUart);
        // Initial Setup
        Mockito.verify(mUart).setDataSize(8);
        Mockito.verify(mUart).setParity(0);
        Mockito.verify(mUart).setStopBits(1);
        Mockito.verify(mUart).setHardwareFlowControl(0);

        Mockito.when(mUart.read(any(byte[].class), eq(1))).thenReturn(1);
        mExpectedException.expect(IOException.class);
        mExpectedException.expectMessage("not found");
        ds18b20.readTemperature();
        Mockito.verify(mUart).setBaudrate(9600);
        Mockito.verify(mUart).write(new byte[]{(byte) (0xf0)}, 1);
        Mockito.verify(mUart).setBaudrate(115200);
    }

    @Test
    public void oneWire_ResetFails() throws IOException {
        OneWire oneWire = new OneWire(mUart);
        // Initial Setup
        Mockito.verify(mUart).setDataSize(8);
        Mockito.verify(mUart).setParity(0);
        Mockito.verify(mUart).setStopBits(1);
        Mockito.verify(mUart).setHardwareFlowControl(0);
        // Reset fails because read is returning '0'.
        Mockito.when(mUart.read(any(byte[].class), eq(1))).thenReturn(1);
        mExpectedException.expect(IOException.class);
        mExpectedException.expectMessage("not found");
        boolean success = oneWire.reset();
        Assert.assertFalse(success);
        Mockito.verify(mUart).setBaudrate(9600);
        Mockito.verify(mUart).write(new byte[]{(byte) (0xf0)}, 1);
        Mockito.verify(mUart).setBaudrate(115200);
    }

    @Test
    public void convertTemperatureValid() throws IOException {
        Ds18b20 ds18b20 = new Ds18b20(mUart);
        // Valid raw measurement
        byte[] validRaw = {0x3e, 0x01, 0x4b, 0x46, 0x7f, (byte) 0xff, 0x0c, 0x10, (byte) 0xb0};
        float temp = ds18b20.convertTemperature(validRaw);
        assertEquals(19.875f, temp);
        assertEquals(85f,
                ds18b20.convertTemperature(new byte[]{(byte) 0x50, (byte) 0x05, 0, 0, 0, 0, 0, 0, 0}));
        assertEquals(125f,
                ds18b20.convertTemperature(new byte[]{(byte) 0xd0, (byte) 0x07, 0, 0, 0, 0, 0, 0, 0}));
    }

    @Test
    public void convertTemperatureZero() throws IOException {
        Ds18b20 ds18b20 = new Ds18b20(mUart);
        // Raw measurement equal to 0 celsius (but with invalid CRC8)
        byte[] validRaw = {0x0, 0x0, 0, 0, 0, 0, 0, 0, 0};
        float temp = ds18b20.convertTemperature(validRaw);
        assertEquals(0f, temp);
    }

    @Test
    public void convertTemperatureNegative() throws IOException {
        Ds18b20 ds18b20 = new Ds18b20(mUart);
        // Raw measurement equal to -1/16 celsius (but with invalid CRC8)
        byte[] validRaw = {(byte) 0x5e, (byte) 0xff, 0, 0, 0, 0, 0, 0, 0};
        float temp = ds18b20.convertTemperature(validRaw);
        assertEquals(-10.125f, temp);
        assertEquals(-25.0625f,
                ds18b20.convertTemperature(new byte[]{(byte) 0x6f, (byte) 0xfe, 0, 0, 0, 0, 0, 0, 0}));
        assertEquals(-0.5f,
                ds18b20.convertTemperature(new byte[]{(byte) 0xf8, (byte) 0xff, 0, 0, 0, 0, 0, 0, 0}));

    }
}
