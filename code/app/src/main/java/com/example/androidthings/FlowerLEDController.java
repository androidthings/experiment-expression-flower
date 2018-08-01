package com.example.androidthings;

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

import android.graphics.Color;
import com.google.android.things.contrib.driver.apa102.Apa102;
import com.google.android.things.contrib.driver.apa102.Apa102.Mode;
import java.io.IOException;
import java.util.Arrays;

/** This class controls the flower's LEDs. */
public class FlowerLEDController {
  public static final int LED_COUNT = 26;
  private static final int LED_BRIGHTNESS = 31;
  private static final Apa102.Mode LED_MODE = Mode.BGR;
  private Apa102 apa102;
  private static final int[] ledArray = new int [LED_COUNT];
  FlowerLEDController() throws IOException {
      apa102 = new Apa102("SPI0.0", LED_MODE);
      apa102.setBrightness(LED_BRIGHTNESS);
  }

  void setFlowerHue(float hue, float saturation, float brightness) throws IOException {
    setFlowerLEDs(
        generateSolidColorArrayForColor(hsvToColor(hue, saturation, brightness)));
  }

  synchronized void setFlowerLEDs(int[] colors) throws IOException {
    apa102.write(colors);
  }

  private static int[] generateSolidColorArrayForColor(int color) {
    Arrays.fill(ledArray, color);
    return ledArray;
  }

  private static int hsvToColor(float hue, float saturation, float brightness) {
    return Color.HSVToColor(0, new float[] {hue, saturation, brightness});
  }
}
