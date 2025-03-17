package com.example.ss_final_java;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.core.CameraSelector;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.google.common.util.concurrent.ListenableFuture;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MQTT";
    private static final String BROKER = "tcp://192.168.1.110:8883";
    private static final String CLIENT_ID = "demo_client";
    private static final String TOPIC = "test/topic";
    private static final int SUB_QOS = 1;
    private static final int PUB_QOS = 1;
    private static final String MESSAGE = "Hello MQTT";

    private MqttAndroidClient mqttClient;
    private ImageCapture imageCapture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "START");

        Button takePictureButton = findViewById(R.id.openCameraButton);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
        }

        takePictureButton.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                startCamera();  // Start the camera when the button is clicked
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
            }
        });

        // mqttClient = new MqttAndroidClient(getApplicationContext(), BROKER, CLIENT_ID);
        // MqttConnectOptions options = new MqttConnectOptions();
        // options.setCleanSession(true);  // Set clean session (no retained data between reconnects)
        // connectToBroker(options);
    }


    private void connectToBroker(MqttConnectOptions options) {
        try {
            mqttClient.connect(options, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d(TAG, "Successfully connected to broker");
                    subscribeToTopic();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(TAG, "Connection failed: " + exception.getMessage());
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void subscribeToTopic() {
        try {
            mqttClient.subscribe(TOPIC, SUB_QOS);
            Log.d(TAG, "Subscribed to topic: " + TOPIC);
            publishMessage();
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void publishMessage() {
        try {
            MqttMessage message = new MqttMessage(MESSAGE.getBytes());
            message.setQos(PUB_QOS);
            mqttClient.publish(TOPIC, message);
            Log.d(TAG, "Message published: " + MESSAGE);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                PreviewView previewView = findViewById(R.id.previewView);

                Preview preview = new Preview.Builder().build();

                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder().build();

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                cameraProvider.unbindAll();

                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);

            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void captureImage() {
        if (imageCapture != null) {
            imageCapture.takePicture(ContextCompat.getMainExecutor(this), new ImageCapture.OnImageCapturedCallback() {
                @Override
                public void onCaptureSuccess(androidx.camera.core.ImageProxy image) {
                    super.onCaptureSuccess(image);
                    Log.d(TAG, "Image captured successfully");
                    processAndSaveImage(image);
                }

                @Override
                public void onError(ImageCaptureException exc) {
                    super.onError(exc);
                    Log.e(TAG, "Image capture failed: " + exc.getMessage());
                }
            });
        }
    }

    private void processAndSaveImage(androidx.camera.core.ImageProxy image) {
        @OptIn(markerClass = ExperimentalGetImage.class) android.media.Image mediaImage = image.getImage();
        ByteBuffer buffer = mediaImage.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 800, 600, false);  // Resize to 800x600
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream);  // Compress the image to 80% quality

        saveImage(byteArrayOutputStream.toByteArray());
    }

    private void saveImage(byte[] imageData) {
        try {
            File file = new File(getExternalFilesDir(null), "captured_image.jpg");

            FileOutputStream fos = new FileOutputStream(file);
            fos.write(imageData);
            fos.close();

            Toast.makeText(this, "Image saved successfully!", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error saving image!", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mqttClient != null) {
            try {
                mqttClient.disconnect();
                Log.d(TAG, "Disconnected from MQTT broker");
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
