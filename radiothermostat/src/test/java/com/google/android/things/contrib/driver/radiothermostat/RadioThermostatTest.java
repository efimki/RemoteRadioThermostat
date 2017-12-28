/*
 * Copyright 2017 Google Inc.
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

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;

import static junit.framework.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;

public class RadioThermostatTest {


    private static final float EXPECTED_TEMPERATURE = 25.08f;
    private static final float EXPECTED_FINE_TEMPERATURE = 128422.0f;
    private static final float EXPECTED_PRESSURE = 1006.5327f;
    private static final float EXPECTED_HUMIDITY = 45.708218f;

    // Note: the datasheet points out that the calculated values can differ slightly because of
    // rounding. We'll check that the results are within a tolerance of 0.1%
    private static final float TOLERANCE = .001f;

    @Mock
    private HttpURLConnection mConnection;

    @Mock
    private JSONObject mResponseJson;

    @Rule
    public MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Rule
    public ExpectedException mExpectedException = ExpectedException.none();

    @Test
    public void close() throws IOException {
        RadioThermostat radioThermostat = new RadioThermostat(mConnection);
        radioThermostat.close();
        Mockito.verify(mConnection).disconnect();
    }

    @Test
    public void close_safeToCallTwice() throws IOException {
        RadioThermostat radioThermostat = new RadioThermostat(mConnection);
        radioThermostat.close();
        radioThermostat.close(); // should not throw
        Mockito.verify(mConnection, times(1)).disconnect();
    }

    @Test
    public void setMode() throws IOException {
        RadioThermostat radioThermostat = new RadioThermostat(mConnection);
        radioThermostat.setMode(RadioThermostat.MODE_NORMAL);
        Mockito.reset(mConnection);
        radioThermostat.setMode(RadioThermostat.MODE_SLEEP);
    }

    @Test
    public void setMode_throwsIfClosed() throws IOException {
        RadioThermostat radioThermostat = new RadioThermostat(mConnection);
        radioThermostat.close();
        mExpectedException.expect(IllegalStateException.class);
        radioThermostat.setMode(RadioThermostat.MODE_NORMAL);
    }

    @Test
    public void readTemperature() throws IOException, JSONException {
        String response = "{\"temp\":66.00,\"tmode\":1,\"fmode\":2,\"override\":1,\"hold\":0,\"t_heat\":45.00,\"tstate\":0,\"fstate\":1,\"time\":{\"day\":2,\"hour\":19,\"minute\":57},\"t_type_post\":0}";
        ByteArrayInputStream data = new ByteArrayInputStream(response.getBytes());
        Mockito.when(mConnection.getInputStream()).thenReturn(data);
        Mockito.when(mResponseJson.getDouble("temp")).thenReturn((double)66f);
        RadioThermostat radioThermostat = new RadioThermostat(mConnection, mResponseJson);

        float temperature = radioThermostat.readTemperature();
        assertEquals(18.88889f, temperature);
    }

    @Test
    public void readTemperature_throwsIfClosed() throws IOException {
        RadioThermostat radioThermostat = new RadioThermostat(mConnection);
        radioThermostat.close();
        mExpectedException.expect(IllegalStateException.class);
        mExpectedException.expectMessage("not open");
        radioThermostat.readTemperature();
    }

}
