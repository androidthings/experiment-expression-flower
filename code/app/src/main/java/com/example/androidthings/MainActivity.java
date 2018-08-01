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

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import com.google.android.things.contrib.driver.pwmservo.Servo;
import com.google.firebase.FirebaseApp;
import java.io.IOException;

/** Expression flower activity that starts the VideoProcessor, motor, and LEDs. */
public class MainActivity extends Activity {

  private static final String TAG = MainActivity.class.getSimpleName();

  private static final int OVERLAY_RADIUS = 80;
  private static final String SERVO_PIN = "PWM0";
  private VideoProcessor videoProcessor;
  private FlowerLEDController ledController;
  private Servo servo;
  private Flower flower;
  private ImageView overlay;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    getActionBar().hide();
    FirebaseApp.initializeApp(this);

    overlay = findViewById(R.id.overlay);
    createOverlayDisplay();

    try {
      ledController = new FlowerLEDController();
    } catch (IOException e) {
      throw new RuntimeException("Couldn't set up led controller.", e);
    }
    try {
      servo = new Servo(SERVO_PIN);
      servo.setPulseDurationRange(0.5, 2.5); // According to the DS3218 servo spec.
      servo.setAngleRange(0, 180); // According to the DS3218 servo spec.
      servo.setEnabled(true);
      Log.i(TAG, "Servo set up complete.");
    } catch (IOException e) {
      throw new RuntimeException("Couldn't set up servo.", e);
    }

    try {
      flower = new Flower(servo, ledController);
    } catch (IOException e) {
      throw new RuntimeException("Couldn't set up flower.", e);
    }

    videoProcessor =
        new VideoProcessor(flower, this, findViewById(R.id.imageView), getMainLooper());
  }

  /** Draws the overlay for the configuration mode. */
  private void createOverlayDisplay() {
    Bitmap bitmap =
        Bitmap.createBitmap(
            videoProcessor.IMAGE_WIDTH, videoProcessor.IMAGE_HEIGHT, Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    Paint overlayPaint = new Paint();
    overlayPaint.setAntiAlias(true);
    overlayPaint.setColor(Color.BLACK);
    overlayPaint.setAlpha(178);
    Path rectPath = new Path();
    rectPath.addRect(0, 0, videoProcessor.IMAGE_WIDTH, videoProcessor.IMAGE_HEIGHT, Direction.CCW);
    Path circlePath = new Path();
    circlePath.addCircle(
        videoProcessor.CENTER_IMAGE_X, videoProcessor.CENTER_IMAGE_Y, OVERLAY_RADIUS, Direction.CW);
    rectPath.op(circlePath, Path.Op.DIFFERENCE);
    canvas.drawPath(rectPath, overlayPaint);

    overlay.setImageBitmap(bitmap);
  }

  /**
   * Listens for a keyboard event to toggle configuration mode. When isInConfigMode = true, the
   * servo will rotate to the max opening position, and the overlay display is shown.
   */
  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    switch (keyCode) {
      case KeyEvent.KEYCODE_SPACE:
        boolean flag = flower.getIsInConfigMode();
        if (!flag) {
          overlay.setVisibility(View.VISIBLE);
        } else {
          overlay.setVisibility(View.INVISIBLE);
        }
        flower.setIsInConfigMode(!flag);
    }
    return super.onKeyUp(keyCode, event);
  }

  @Override
  protected void onDestroy() {
    videoProcessor.stop();
    try {
      flower.destroy();
    } catch (IOException e) {
      Log.e(TAG, "Flower was unable to destroy.", e);
    }
    super.onDestroy();
  }
}