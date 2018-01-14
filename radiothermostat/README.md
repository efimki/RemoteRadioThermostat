Radio Thermostat driver for Android Things
==========================================

This driver supports [Radio Thermostat Wi-Fi] models 
 CT50 and CT80 as Temperature Sensor for Android Things.
 
Theses termostats provide simple [Rest API] that is accessible over WiFi on local network.

NOTE: this driver is not production-ready. There is no guarantee
of correctness, completeness or robustness.

How to use the driver
---------------------

### Gradle dependency

To use the `RadioThermostat` driver, simply add the line below to your project's `build.gradle`,
where `<version>` matches the last version of the driver available on [jcenter][jcenter].

```
dependencies {
    compile 'com.google.android.things.contrib:driver-radiothermostat:<version>'
}
```

### Sample usage

```java
import com.google.android.things.contrib.driver.radiothermostat.RadioThermostat;

// Access the thermostat temperature snsor:

RadioThermostat mRadioThermostat;

try {
    mRadioThermostat = new RadioThermostat(radioTheromostatHostAddress);
} catch (IOException e) {
    // couldn't configure the device...
}

// Read the current temperature:

try {
    float temperature = mRadioThermostat.readTemperature();
} catch (IOException e) {
    // error reading temperature
}

// Close the thermostat sensor when finished:

try {
    mRadioThermostat.close();
} catch (IOException e) {
    // error closing sensor
}
```

If you need to read sensor values continuously, you can register the RadioThermostat with the system and
listen for sensor values using the [Sensor APIs][sensors]:
```java
SensorManager mSensorManager = getSystemService(Context.SENSOR_SERVICE);
SensorEventListener mListener = ...;
Bmx280SensorDriver mSensorDriver;

mSensorManager.registerDynamicSensorCallback(new SensorManager.DynamicSensorCallback() {
    @Override
    public void onDynamicSensorConnected(Sensor sensor) {
        if (sensor.getType() == Sensor.TYPE_AMBIENT_TEMPERATURE) {
            mSensorManager.registerListener(mListener, sensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
        }
    }
});

try {
    mRadioThermostat = new RadioThermostat(radioTheromostatHost);
    
    mSensorDriver = new RadioThermostatSensorDriver(radioTheromostatHostAddress);
    mSensorDriver.registerTemperatureSensor();
} catch (IOException e) {
    // Error configuring sensor
}

// Unregister and close the driver when finished:

mSensorManager.unregisterListener(mListener);
mSensorDriver.unregisterTemperatureSensor();
try {
    mSensorDriver.close();
} catch (IOException e) {
    // error closing sensor
}
```

License
-------

Copyright 2018 Google Inc.

Licensed to the Apache Software Foundation (ASF) under one or more contributor
license agreements.  See the NOTICE file distributed with this work for
additional information regarding copyright ownership.  The ASF licenses this
file to you under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License.  You may obtain a copy of
the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
License for the specific language governing permissions and limitations under
the License.

[Rest API]: https://radiothermostat.desk.com/customer/portal/articles/1268461-where-do-i-find-information-about-the-wifi-api-
[Radio Thermostat Wi-Fi]: http://www.radiothermostat.com/wifi
[jcenter]: https://bintray.com/google/androidthings/contrib-driver-bmx280/_latestVersion
[sensors]: https://developer.android.com/guide/topics/sensors/sensors_overview.html
