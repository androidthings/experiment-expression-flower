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
import android.support.annotation.IntDef;
import android.util.Log;
import com.example.androidthings.sequences.ExpressionSequence;
import com.example.androidthings.sequences.IdleSequence;
import com.example.androidthings.sequences.RainbowSequence;
import com.example.androidthings.sequences.Sequence;
import com.google.android.things.contrib.driver.pwmservo.Servo;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Class representing a physical expression flower. */
public class Flower {

  private static final String TAG = Flower.class.getSimpleName();

  // HSV Hue values (0-360) for pink and yellow.
  private static final float YELLOW_HUE = 40f;
  private static final float PINK_HUE = 314f;
  private static final int DEFAULT_MAX_ANGLE = 50;

  private final Servo motorController;
  private final FlowerLEDController ledController;

  private int maxAngle;

  private float hue;
  private float saturation;
  private float brightness;
  private float opening = 0f;

  private boolean running = true;

  private boolean inConfigMode = false;

  /** Differentiates between the different expression states. */
  @IntDef({
      State.UNDEFINED,
      State.IDLE,
      State.DETECTING,
      State.INDIFFERENCE,
      State.SMILE,
      State.WINK,
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface State {
    int UNDEFINED = -1;
    int IDLE = 0;
    int DETECTING = 1;
    int INDIFFERENCE = 2;
    int SMILE = 3;
    int WINK = 4;
  }

  private Sequence sequence;
  private @State int currentState = State.UNDEFINED;
  private @State int underlyingState;

  Flower(Servo motorController, FlowerLEDController ledController) throws IOException {
    this.motorController = motorController;
    this.ledController = ledController;
    maxAngle = DEFAULT_MAX_ANGLE;
    setOpening(1f);
  }

  /** Activates and deactivates configuration mode. */
  public void setIsInConfigMode(boolean inConfigMode) {
    this.inConfigMode = inConfigMode;
  }

  /** Returns configuration mode flag. */
  public boolean getIsInConfigMode() {
    return inConfigMode;
  }

  /** Sets the hsv values of the flower. */
  public void setHSV(float hue, float saturation, float brightness) throws IOException {
    this.hue = ensureInRange(hue, 0, 359);
    this.saturation = ensureInRange(saturation, (float) .5, 1);
    this.brightness = ensureInRange(brightness, 0, 1);
    ledController.setFlowerHue(this.hue, this.saturation, this.brightness);
  }

  /** Sets the LEDs of the flower. */
  public void setLEDs(int[] colors) throws IOException {
    float[] hsv = new float[3];
    Color.colorToHSV(colors[0], hsv);
    this.hue = hsv[0];
    this.saturation = hsv[1];
    this.brightness = hsv[2];
    ledController.setFlowerLEDs(colors);
  }

  /** Sets how open the flower's petals are. */
  public void setOpening(float opening) throws IOException {
    setOpening(opening, false);
  }

  /** Sets at what angle the flower shall be opened if force is true. */
  private void setOpening(float opening, boolean force) throws IOException {
    if (!force && Float.compare(opening, this.opening) == 0) {
      return;
    }
    this.opening = ensureInRange(opening, 0, 1);
    motorController.setAngle(maxAngle * this.opening);
  }

  /** Returns the angle the flower is currently opened to. */
  public float getOpening() {
    return this.opening;
  }

  /** Determines that the new angle is in the range that the flower can handle. */
  private static float ensureInRange(float value, float min, float max) {
    return Math.max(min, Math.min(max, value));
  }

  /** Sets the current state of the flower. */
  synchronized void setState(@State int newState) {
    setState(newState, false);
  }

  /** Sets the current state of the flower and starts a new sequence depending on the state. */
  private synchronized void setState(@State int newState, boolean force) {
    if (!running) {
      return;
    }

    if (newState == State.IDLE || newState == State.DETECTING) {
      Log.i(TAG, "Changing underlyingState from " + underlyingState + " to " + newState);
      underlyingState = newState;
    }

    if (!force && newState == currentState && sequence != null) {
      Log.i(TAG, "NOT changing state from " + currentState + " to " + newState);
      return;
    }

    if (sequence != null) {
      if (sequence.isInterruptible() || sequence.isComplete()) {
        sequence.pause();
      } else {
        Log.i(
            TAG,
            "Can't change state from " + currentState + " to " + newState + " during sequence.");
        return;
      }
    }
    Log.i(TAG, "Changing state from " + currentState + " to " + newState);

    try {
      clearCurrentSequence();
      switch (newState) {
        case State.DETECTING:
          sequence = new RainbowSequence(this, .75f, this::onSequenceCompleted);
          break;
        case State.INDIFFERENCE:
          sequence = new RainbowSequence(this, .75f, this::onSequenceCompleted);
          break;
        case State.SMILE:
          sequence = new ExpressionSequence(this, YELLOW_HUE, 1f, this::onSequenceCompleted);
          break;
        case State.WINK:
          sequence = new ExpressionSequence(this, PINK_HUE, .25f, this::onSequenceCompleted);
          break;
        case State.IDLE:
          sequence = new IdleSequence(this, opening, this::onSequenceCompleted);
          break;
        case State.UNDEFINED:
          sequence = new IdleSequence(this, opening, this::onSequenceCompleted);
          break;
        default:
          Log.e(TAG, "Unrecognized state." + newState);
      }
      sequence.start();
    } catch (Exception e) {
      Log.e(TAG, "Couldn't Start sequence.", e);
    }
    currentState = newState;
  }

  /** Determines whether a sequence is completed and switches back to an idle or detecting state. */
  void onSequenceCompleted() {
    clearCurrentSequence();
    Log.i(TAG, "Sequence complete, reverting to underlying state " + underlyingState);
    setState(underlyingState, true);
  }

  /** Ends current sequence. */
  private void clearCurrentSequence() {
    if (sequence != null) {
      sequence.stop();
      sequence = null;
    }
  }

  synchronized void destroy() throws IOException {
    running = false;
    clearCurrentSequence();
  }
}