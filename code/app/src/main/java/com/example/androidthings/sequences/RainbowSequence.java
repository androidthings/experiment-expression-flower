package com.example.androidthings.sequences;

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
import android.util.Log;
import com.example.androidthings.Flower;
import com.example.androidthings.FlowerLEDController;
import java.io.IOException;

/** Rotates a rainbow sequence around a flower's LEDs */
public class RainbowSequence extends Sequence {

  private static final String TAG = RainbowSequence.class.getSimpleName();

  private final int[] colors = new int[FlowerLEDController.LED_COUNT];
  private final float opening;

  public RainbowSequence(Flower flower, float opening, Runnable sequenceCompletedCallback)
      throws IOException {
    super(flower, sequenceCompletedCallback);
    this.opening = opening;
  }

  @Override
  public boolean isInterruptible() {
    return true;
  }

  @Override
  boolean animateNextFrame(int frame) throws IOException {
    generateRainbow(colors, frame);
    if (flower.getIsInConfigMode()) {
      flower.setOpening(1f);
      Log.i(TAG, "Configuration Mode Active.");
    } else {
      flower.setOpening(opening);
    }
    flower.setLEDs(colors);
    return false;
  }

  // Assigns gradient colors.
  private static void generateRainbow(int[] colors, int frame) {
    float[] hsv = {1f, 1f, 1f};
    for (int i = 0; i < colors.length; i++) {
      int n = (i + frame) % colors.length;
      hsv[0] = n * 360f / colors.length;
      colors[i] = Color.HSVToColor(0, hsv);
    }
  }
}

