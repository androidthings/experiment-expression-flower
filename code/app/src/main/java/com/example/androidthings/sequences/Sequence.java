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

import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import com.example.androidthings.Flower;
import java.io.IOException;

/** Interface representing an animation sequence. */
public abstract class Sequence {

  private static final String TAG = Sequence.class.getSimpleName();
  static final int FRAME_DELAY_MS = 80;
  private static final long AFTER_SEQUENCE_COMPLETION_DELAY_MS = 1000;

  protected final Flower flower;

  private HandlerThread ledThread;
  private Handler ledHandler;

  private boolean animationRunning = false;
  private boolean isComplete = false;
  private int currentFrame = 0;
  private Runnable sequenceCompletedCallback;

  Sequence(Flower flower, Runnable sequenceCompletedCallback) {
    this.sequenceCompletedCallback = sequenceCompletedCallback;
    this.flower = flower;
    ledThread = new HandlerThread(TAG + "Thread");
    ledThread.start();
    ledHandler = new Handler(ledThread.getLooper());
  }

  /** Starts animation sequence. */
  public void start() {
    Log.e(TAG, "sequence started");
    animationRunning = true;
    ledHandler.post(this::prepareNextFrame);
  }

  /** Pauses current animation sequence. */
  public void pause() {
    animationRunning = false;
  }

  /** Stops current animation by removing ledhandler callback. */
  public synchronized void stop() {
    pause();
    currentFrame = 0;

    if (ledThread != null) {
      ledThread.quit();
    }

    ledHandler = null;
    ledThread = null;
  }

  abstract boolean animateNextFrame(int frame) throws IOException;

  /** Determines if a sequence can be interrupted. */
  public abstract boolean isInterruptible();

  /** Determines if a sequence is completed. */
  public boolean isComplete() {
    return isComplete;
  }

  /** Prepares animation for the next frame. */
  private void prepareNextFrame() {
    long currFrameStartMs = SystemClock.elapsedRealtime();
    boolean newIsComplete = false;

    try {
      newIsComplete = animateNextFrame(currentFrame++);
    } catch (Exception e) {
      Log.e(TAG, "Couldn't animate next frame.", e);
    }

    if (animationRunning) {
      if (newIsComplete && !isComplete) {
        ledHandler.postDelayed(sequenceCompletedCallback, AFTER_SEQUENCE_COMPLETION_DELAY_MS);
      } else {
        long now = SystemClock.elapsedRealtime();
        long delay = Math.max(0, FRAME_DELAY_MS - (now - currFrameStartMs));
        ledHandler.postDelayed(this::prepareNextFrame, delay);
      }
    }
    isComplete = newIsComplete;
  }
}