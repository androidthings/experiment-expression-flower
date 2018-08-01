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
import java.util.Random;

/** Sequences LEDs through various colors. */
public class IdleSequence extends Sequence {

  private static final String TAG = IdleSequence.class.getSimpleName();
  private static final Random random = new Random();
  private final int[] colors = new int[FlowerLEDController.LED_COUNT];
  private double openingIncrementPerStep = .0075f;
  public IdleSequence(Flower flower, float opening, Runnable sequenceCompletedCallback)
      throws IOException {
    super(flower, sequenceCompletedCallback);
  }

  @Override
  public boolean isInterruptible() {
    return true;
  }

  @Override
  boolean animateNextFrame(int frame) throws IOException {
    int directionChange = random.nextInt(100 - 1 + 1) + 1;
    if (flower.getIsInConfigMode()) {
      flower.setOpening(1f);
      Log.i(TAG, "Configuration Mode Active.");
    } else {
      if ((flower.getOpening() <= .5f && openingIncrementPerStep < 0)
          || (flower.getOpening() >= .75f && openingIncrementPerStep > 0)
          || (directionChange > 85)) {
        openingIncrementPerStep *= -1;
      }
      flower.setOpening(flower.getOpening() + (float) openingIncrementPerStep);
    }
    generateRainbowVertical(colors, 6, frame);
    flower.setLEDs(colors);
    return false;
  }

  private static void generateRainbowVertical(int[] colors, int top, int frame) {
    int numColors = 150;
    float[] hsv0 = {360f * (frame % numColors) / numColors, 1f, 1f};
    float[] hsv1 = {360f * ((frame + 10) % numColors) / numColors, 1f, 1f};

    for (int i = 0; i < top; i++) {
      colors[i] = Color.HSVToColor(0, hsv1);
    }

    for (int i = top; i < colors.length; i++) {
      colors[i] = Color.HSVToColor(0, hsv0);
    }
  }
}