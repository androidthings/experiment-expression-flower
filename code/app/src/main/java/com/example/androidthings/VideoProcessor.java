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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import com.example.androidthings.Flower.State;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Helper class that handles the collection of images and the detection of faces on those images.
 */
public class VideoProcessor implements ImageReader.OnImageAvailableListener {

  private static final String TAG = VideoProcessor.class.getSimpleName();

  // This value is 2 ^ 18 - 1, and is used to clamp the RGB values before their ranges
  // are normalized to eight bits.
  static final int MAX_CHANNEL_VALUE = 262143;

  // Max numbers of captured images to be queued before displaying on view.
  private static final int MAX_IMAGES = 20;


  public static final int IMAGE_WIDTH = 320;
  public static final int IMAGE_HEIGHT = 240;

  public static final float CENTER_IMAGE_Y = IMAGE_HEIGHT / 2;
  public static final float CENTER_IMAGE_X = IMAGE_WIDTH / 2;

  // Allows detected faces within the third portion of the camera's view to be considered to be a
  // priority face.
  private static final int PRIORITY_BOUND_LEFT = (IMAGE_WIDTH / 3) - 20;
  private static final int PRIORITY_BOUND_RIGHT = ((IMAGE_WIDTH / 3) * 2) + 20;

  // Smallest area allowed for a face's bouding box to be considered to be a priority face.
  private static final int SMALL_BOX_LIMIT = 3000;
  private static final double WEIGHT = .6;

  private final ExecutorService executorService = Executors.newFixedThreadPool(1);

  private CameraDevice cameraDevice;
  private Handler backgroundHandler;
  private HandlerThread backgroundThread;
  private Handler uiHandler;
  private ImageReader mImageReader;
  private ImageView mImage;

  private OnAnnotatedFrameListener frameCallback;

  private Canvas canvas;
  private Bitmap displayBitmap;
  private Bitmap buffer;
  private Canvas bufferCanvas;
  private Paint detectionPaint;
  private Paint textPaint;
  private byte[][] cachedYuvBytes;
  private int[] output;

  private FirebaseVisionFaceDetectorOptions options;
  private FirebaseVisionImage firebaseImage;
  private FirebaseVisionFaceDetector detector;
  private Task<List<FirebaseVisionFace>> result;

  private ConcurrentHashMap<Integer, FirebaseVisionFace> idToFace;
  private ConcurrentHashMap<Integer, FirebaseVisionFace> currentIdToFace;

  private Set<Integer> removeIdSet;

  private final Flower flower;

  private boolean detectNewFace;
  private Integer priorityId;
  private double closestDistance;

  /**
   * Listener that controls image processing.
   */
  public interface OnAnnotatedFrameListener {

    void onAnnotatedFrame(Bitmap bitmap);
  }

  VideoProcessor(Flower flower, Context context, ImageView imageView, Looper mainLooper) {
    this.flower = flower;
    FirebaseApp.initializeApp(context);
    flower.setState(State.IDLE);
    setUpImageCapture(context, imageView, mainLooper);
  }

  void stop() {
    executorService.shutdown();
  }

  /**
   * Gets updated images from camera using a looper.
   *
   * @param mainLooper Name of the main thread loop.
   */
  private void setUpImageCapture(Context context, ImageView imageView, Looper mainLooper) {
    cachedYuvBytes = new byte[3][];
    output = new int[IMAGE_WIDTH * IMAGE_HEIGHT];

    displayBitmap = Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Config.ARGB_8888);
    canvas = new Canvas(displayBitmap);
    firebaseImage = FirebaseVisionImage.fromBitmap(displayBitmap);

    buffer = Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Config.ARGB_8888);
    bufferCanvas = new Canvas(buffer);

    mImage = imageView;
    mImage.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

    detectionPaint = new Paint();
    detectionPaint.setColor(Color.RED);
    detectionPaint.setStyle(Style.STROKE);
    detectionPaint.setStrokeWidth(4.0f);
    detectionPaint.setAlpha(200);

    textPaint = new Paint();
    textPaint.setColor(Color.BLACK);
    textPaint.setStyle(Style.FILL);
    textPaint.setStrokeWidth(3.0f);
    textPaint.setAlpha(200);
    textPaint.setTextSize(15);
    detectNewFace = true;
    priorityId = Integer.MAX_VALUE;

    backgroundThread = new HandlerThread("capture");
    backgroundThread.start();
    backgroundHandler = new Handler(backgroundThread.getLooper());

    options =
        new FirebaseVisionFaceDetectorOptions.Builder()
            .setClassificationType(FirebaseVisionFaceDetectorOptions.ALL_CLASSIFICATIONS)
            .setTrackingEnabled(true)
            .build();

    idToFace = new ConcurrentHashMap<Integer, FirebaseVisionFace>();
    currentIdToFace = new ConcurrentHashMap<Integer, FirebaseVisionFace>();

    removeIdSet = new HashSet<>();

    closestDistance = Integer.MAX_VALUE;

    detector = FirebaseVision.getInstance().getVisionFaceDetector(options);

    uiHandler = new Handler(mainLooper);
    frameCallback =
        (bitmap) ->
            uiHandler.post(
                () -> {
                  synchronized (bitmap) {
                    Log.e(TAG, "Inside update bitmap");
                    mImage.setImageBitmap(bitmap);
                  }
                });

    try {
      openCamera((CameraManager) context.getSystemService(Context.CAMERA_SERVICE));
    } catch (CameraAccessException e) {
      Log.e(TAG, "camera exception", e);
    }
  }

  /**
   * This method opens the camera and calls startCamera.
   */
  @SuppressLint("MissingPermission")
  private void openCamera(CameraManager manager) throws CameraAccessException {
    String[] camIds = manager.getCameraIdList();
    if (camIds.length < 1) {
      Log.w(TAG, "No cameras found");
      return;
    }
    mImageReader =
        ImageReader.newInstance(IMAGE_WIDTH, IMAGE_HEIGHT, ImageFormat.YUV_420_888, MAX_IMAGES);
    mImageReader.setOnImageAvailableListener(this, backgroundHandler);
    manager.openCamera(
        camIds[0],
        new CameraDevice.StateCallback() {
          @Override
          public void onOpened(CameraDevice camera) {
            Log.d(TAG, "Camera opened");
            startCamera(camera);
          }

          @Override
          public void onDisconnected(CameraDevice camera) {
          }

          @Override
          public void onError(CameraDevice camera, int error) {
          }
        },
        backgroundHandler);
  }

  /**
   * This method starts the camera to capture a session of images.
   */
  private void startCamera(CameraDevice camera) throws RuntimeException {
    this.cameraDevice = camera;
    try {
      final CaptureRequest.Builder captureRequestBuilder =
          cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
      captureRequestBuilder.addTarget(mImageReader.getSurface());
      cameraDevice.createCaptureSession(
          Collections.singletonList(mImageReader.getSurface()),
          new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(CameraCaptureSession session) {
              Log.d(TAG, "Capture session configured");
              // The camera is already closed.
              if (cameraDevice == null) {
                return;
              }
              try {
                CaptureRequest captureRequest = captureRequestBuilder.build();
                Log.d(TAG, "let's initiate capture...");
                session.setRepeatingRequest(captureRequest, null, backgroundHandler);
              } catch (CameraAccessException e) {
                throw new RuntimeException("Camera acces exception", e);
              }
            }

            @Override
            public void onConfigureFailed(CameraCaptureSession session) {
              Log.e(TAG, "Capture session configure FAILED");
            }
          },
          backgroundHandler);
    } catch (CameraAccessException e) {
      Log.e(TAG, "Camera access exception", e);
    }
  }

  /**
   * This method acquires images and detects faces and facial expressions in the image.
   */
  @Override
  public synchronized void onImageAvailable(ImageReader reader) {
    try {
      Image image = reader.acquireLatestImage();
      if (image == null) {
        return;
      }
      imageToBitmap(image, displayBitmap);
      image.close();
    } catch (IllegalStateException e) {
      Log.e(TAG, "Too many images queued to be saved. Dropping this one.", e);
      return;
    }

    result =
        detector
            .detectInImage(firebaseImage)
            .addOnSuccessListener(
                new OnSuccessListener<List<FirebaseVisionFace>>() {
                  @Override
                  public void onSuccess(List<FirebaseVisionFace> faces) {
                    currentIdToFace.clear();
                    for (FirebaseVisionFace face : faces) {
                      idToFace.put(face.getTrackingId(), face);
                      currentIdToFace.put(face.getTrackingId(), face);
                    }
                  }
                });
    canvas.drawBitmap(displayBitmap, 0, 0, null);

    synchronized (buffer) {
      bufferCanvas.drawBitmap(displayBitmap, 0, 0, null);
      try {
        if (currentIdToFace.isEmpty()) {
          flower.setState(State.IDLE);
          detectNewFace = true;
        } else if (currentIdToFace.size() == 1) {
          flower.setState(State.DETECTING);
          detectNewFace = false;
        } else {
          flower.setState(State.DETECTING);
          detectNewFace = checkPriorityId();
          if (detectNewFace) {
            priorityId = getNewPriorityId();
          }
        }

        for (Entry<Integer, FirebaseVisionFace> face : currentIdToFace.entrySet()) {
          if (idToFace.containsKey(face.getKey())) {
            drawBoundingBoxes(bufferCanvas, face.getKey());
          } else {
            removeIdSet.add(face.getKey());
          }
        }

      } catch (IOException e) {
        Log.e(TAG, "Unable to draw bounding boxes", e);
      }
    }

    for (int id : removeIdSet) {
      idToFace.remove(id);
    }
    frameCallback.onAnnotatedFrame(buffer);
  }

  /**
   * Sends the priorityId's expression state to the flower.
   */
  public void setExpressionState(FirebaseVisionFace face) throws IOException {
    if (face.getSmilingProbability() > .5) {
      flower.setState(State.SMILE);
    }
    if ((face.getRightEyeOpenProbability() < .2 && face.getRightEyeOpenProbability() != -1
        && face.getLeftEyeOpenProbability() > .2)
        || (face.getLeftEyeOpenProbability() < .2 && face.getLeftEyeOpenProbability() != -1
        && face.getRightEyeOpenProbability() > .2)) {
      flower.setState(State.WINK);
    }
  }

  /**
   * Determines a new priorityId to track.
   */
  private synchronized int getNewPriorityId() {
    Set<Integer> validIds = new HashSet<>();
    for (Entry<Integer, FirebaseVisionFace> face : idToFace.entrySet()) {
      if (currentIdToFace.containsKey(face.getKey())) {

        if (idToFace.get(face.getKey()).getBoundingBox().width()
            * idToFace.get(face.getKey()).getBoundingBox().height()
            > SMALL_BOX_LIMIT) {

          if ((idToFace.get(face.getKey()).getBoundingBox().centerX() > PRIORITY_BOUND_LEFT)
              && (idToFace.get(face.getKey()).getBoundingBox().centerX() < PRIORITY_BOUND_RIGHT)) {
            validIds.add(face.getKey());
          }
        }
      }
    }

    closestDistance = Integer.MAX_VALUE;
    for (int id : validIds) {
      double distanceFromCenter =
          Math.sqrt(
              (WEIGHT * Math.pow(idToFace.get(id).getBoundingBox().centerX() - CENTER_IMAGE_X, 2))
                  + ((1 - WEIGHT)
                  * Math.pow(idToFace.get(id).getBoundingBox().centerY() - CENTER_IMAGE_Y, 2)));
      if (distanceFromCenter < closestDistance) {
        closestDistance = distanceFromCenter;
        priorityId = id;
      }
    }

    if (validIds.isEmpty()) {
      priorityId = Integer.MAX_VALUE;
    }

    return priorityId;
  }

  /**
   * Checks that the current priorityId is still a valid.
   */
  private synchronized boolean checkPriorityId() {
    if (priorityId == Integer.MAX_VALUE) {
      return true;
    }

    if (!(currentIdToFace.containsKey(priorityId))) {
      priorityId = Integer.MAX_VALUE;
      return true;
    }

    if (!(idToFace.containsKey(priorityId))) {
      priorityId = Integer.MAX_VALUE;
      return true;
    }
    if (!((idToFace.get(priorityId).getBoundingBox().centerX() > PRIORITY_BOUND_LEFT)
        && (idToFace.get(priorityId).getBoundingBox().centerX() < PRIORITY_BOUND_RIGHT))) {
      return true;
    }
    if (idToFace.get(priorityId).getBoundingBox().height()
        * idToFace.get(priorityId).getBoundingBox().width()
        < SMALL_BOX_LIMIT) {
      return true;
    }

    return false;
  }

  /**
   * Draws the bounding boxes of the currentIds to the screen.
   */
  private synchronized void drawBoundingBoxes(Canvas canvas, int id) throws IOException {
    detectionPaint.setColor(Color.RED);
    if (currentIdToFace.size() == 1) {
      priorityId = id;
    }
    if ((priorityId != Integer.MAX_VALUE) && (id == priorityId)) {
      detectionPaint.setColor(Color.BLUE);
      try {
        setExpressionState(idToFace.get(priorityId));
      } catch (IOException e) {
        Log.e(TAG, "Unable to set expression state", e);
      }
    }
    canvas.drawRect(idToFace.get(id).getBoundingBox(), detectionPaint);
    canvas.drawText(
        String.format("%.2f", idToFace.get(id).getSmilingProbability()),
        idToFace.get(id).getBoundingBox().right,
        idToFace.get(id).getBoundingBox().bottom,
        textPaint);
    canvas.drawText(
        String.format("%.2f", idToFace.get(id).getLeftEyeOpenProbability()),
        idToFace.get(id).getBoundingBox().right,
        idToFace.get(id).getBoundingBox().top,
        textPaint);
    canvas.drawText(
        String.format("%.2f", idToFace.get(id).getRightEyeOpenProbability()),
        idToFace.get(id).getBoundingBox().left,
        idToFace.get(id).getBoundingBox().top,
        textPaint);
  }

  private void imageToBitmap(Image image, Bitmap bitmap) {
    if (image == null) {
      return;
    }
    convertYUVImageToBitmap(image, bitmap, output, cachedYuvBytes);
  }

  private static void convertYUVImageToBitmap(
      Image image, Bitmap bitmap, int[] output, byte[][] cachedYuvBytes) {
    Image.Plane[] planes = image.getPlanes();
    fillBytes(planes, cachedYuvBytes);

    final int yRowStride = planes[0].getRowStride();
    final int uvRowStride = planes[1].getRowStride();
    final int uvPixelStride = planes[1].getPixelStride();

    convertYUV420ToARGB8888(
        cachedYuvBytes[0],
        cachedYuvBytes[1],
        cachedYuvBytes[2],
        image.getWidth(),
        image.getHeight(),
        yRowStride,
        uvRowStride,
        uvPixelStride,
        output);

    bitmap.setPixels(output, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());
  }

  private static void fillBytes(final Image.Plane[] planes, final byte[][] yuvBytes) {
    // Because of the variable row stride it's not possible to know in
    // advance the actual necessary dimensions of the yuv planes.
    for (int i = 0; i < planes.length; ++i) {
      final ByteBuffer buffer = planes[i].getBuffer();
      if (yuvBytes[i] == null || yuvBytes[i].length != buffer.capacity()) {
        yuvBytes[i] = new byte[buffer.capacity()];
      }
      buffer.get(yuvBytes[i]);
    }
  }

  private static void convertYUV420ToARGB8888(
      byte[] yData,
      byte[] uData,
      byte[] vData,
      int width,
      int height,
      int yRowStride,
      int uvRowStride,
      int uvPixelStride,
      int[] out) {
    int i = 0;
    for (int y = 0; y < height; y++) {
      int pY = yRowStride * y;
      int uvRowStart = uvRowStride * (y >> 1);
      int pU = uvRowStart;
      int pV = uvRowStart;

      for (int x = 0; x < width; x++) {
        int uvOffset = (x >> 1) * uvPixelStride;
        out[i++] =
            YUV2RGB(
                convertByteToInt(yData, pY + x),
                convertByteToInt(uData, pU + uvOffset),
                convertByteToInt(vData, pV + uvOffset));
      }
    }
  }

  private static int YUV2RGB(int nY, int nU, int nV) {
    nY -= 16;
    nU -= 128;
    nV -= 128;
    if (nY < 0) {
      nY = 0;
    }

    int nR = (int) (1192 * nY + 1634 * nV);
    int nG = (int) (1192 * nY - 833 * nV - 400 * nU);
    int nB = (int) (1192 * nY + 2066 * nU);

    nR = Math.min(MAX_CHANNEL_VALUE, Math.max(0, nR));
    nG = Math.min(MAX_CHANNEL_VALUE, Math.max(0, nG));
    nB = Math.min(MAX_CHANNEL_VALUE, Math.max(0, nB));

    nR = (nR >> 10) & 0xff;
    nG = (nG >> 10) & 0xff;
    nB = (nB >> 10) & 0xff;

    return 0xff000000 | (nR << 16) | (nG << 8) | nB;
  }

  private static int convertByteToInt(byte[] arr, int pos) {
    return arr[pos] & 0xFF;
  }
}
