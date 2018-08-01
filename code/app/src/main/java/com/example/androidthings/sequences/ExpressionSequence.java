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

import android.util.Log;
import com.example.androidthings.Flower;
import java.io.IOException;

/**
 * Base class for an animation sequence that turns the flower a different color based on the
 * expression passed in
 */
public class ExpressionSequence extends Sequence {

  private static final String TAG = ExpressionSequence.class.getSimpleName();
  private static final int TOTAL_STEPS = 35;

  private final float hue;
  private final float opening;

  public ExpressionSequence(
      Flower flower, float hue, float opening, Runnable sequenceCompletedCallback)
      throws IOException {
    super(flower, sequenceCompletedCallback);
    this.hue = hue;
    this.opening = opening;
  }

  @Override
  public boolean isInterruptible() {
    return false;
  }

  /** Gradually fades LEDs to white and then fades to the indicated hue. */
  @Override
  boolean animateNextFrame(int frame) throws IOException {
    if (flower.getIsInConfigMode()) {
      flower.setOpening(1f);
      Log.i(TAG, "Configuration Mode Active");
    } else {
      flower.setOpening(opening);
    }
    flower.setHSV(hue, 1f, 1f);
    return frame >= TOTAL_STEPS;
  }
}
