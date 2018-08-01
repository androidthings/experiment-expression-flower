# Expression Flower Code

The Expression Flower is an Android Things powered smart flower that is able to sense a person’s facial expressions, such as smiling or winking, and reacts by changing its colors and opening or closing its petals. This project showcases some key capabilities of Android Things. First, the Android Things powered device is able to capture sensor inputs, in this case the camera is the sensor. Second, on-device processing detects faces and infers the facial expressions using the machine learning modules from Firebase MLKit. Third, the processed result is used to inform the output actuations, such as rotating a servo arm to open/close the petals or changing the colors on the addressable LEDs inside the flower.

### Pre-requisites

- Raspberry Pi 3B running Android Things release 1.0
- Raspberry Pi camera or USB camera
- Android Studio 2.2+
- Firebase project with MLKit

### Setup and Build

To set up, follow these steps below.

 Add a valid `google-services.json` from Firebase to `app/`
  - Create a Firebase project on [Firebase Console](https://console.firebase.google.com)
  - Add an Android app with your specific package name in the project
  - Download the auto-generated `google-services.json` and save to the `app/` folder

You can also follow the instructions listed [here](https://firebase.google.com/docs/ml-kit/android/detect-faces) to get your app running with Firebase's MLKit

### Running

To run the 'app' module on an Android Things board:

1. Make sure the camera is properly set up and attached to your Android Things board.
2. Connect a Raspberry Pi display or HDMI display to the board.
3. Deploy and run the 'app'.
4. Verify that video is streaming to your device's display and that nearby faces are detected and surrounded by bounding boxes.

### Enable Auto-Launch Behavior

This sample app is currently configured to launch only when deployed from your
development machine. To enable the main activity to launch automatically on boot,
add the following `intent-filter` to the app's manifest file:

```xml
<activity ...>

    <intent-filter>
        <action android:name="android.intent.action.MAIN"/>
        <category android:name="android.intent.category.HOME"/>
        <category android:name="android.intent.category.DEFAULT"/>
    </intent-filter>

</activity>
```
### Making Your Own Changes

##### Setting new color and opening for expressions
Currently the app is designed to turn yellow and open (1.0f) when a user smiles and turn pink and close (.25f) when a user winks. If you wish to change these settings you can find them in the `Flower.java` file.

```java
37 private static final float YELLOW_HUE = 40f;
38 private static final float PINK_HUE = 314f;
```
```java
179 case State.SMILE:
180  sequence = new ExpressionSequence(this, YELLOW_HUE, 1f, this::onSequenceCompleted);
181  break;
182 case State.WINK:
183  sequence = new ExpressionSequence(this, PINK_HUE, .25f, this::onSequenceCompleted);
```
The two hues are defined at the top of the file and are used when initializing the `ExpressionSequence`. Simply find the HSV hue value of your desired color and switch it with the hue you wish to replace.

You can also change how opened or closed the flower is when an expression is made. `0f` is fully closed and `1f` is fully opened. This value can be changed when the `ExpressionSequence` is initialized.

##### Disable idle sequence movement
While the flower is not detecting a face the `IdleSequence` changes the flower's colors and gives the flower a breathing affect. To disable this breathing movement, in the 'IdleSequence.java' class replace
```java
55 flower.setOpening(flower.getOpening() + (float) openingIncrementPerStep);
```
with
```java
55 flower.setOpening(1f);
```
### License

Copyright 2018 The Android Open Source Project, Inc.

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
