package com.example.ss_final_java;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.Manifest;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.*;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = "CameraApp";
    private static final int REQUEST_CAMERA_PERMISSION_CODE = 1;
    private Uri photoUri;
    private ImageView imageView;

    private static final String BROKER = "ssl://192.168.1.110:8883";
    private static final String CLIENT_ID = "Android_client";
    private static final String TOPIC = "test/topic";
    private static final int SUB_QOS = 1;
    private static final int PUB_QOS = 1;
    private static final String MESSAGE = "Hello MQTT";

    private MqttHandler mqttHandler;



    // Camera launcher using Activity Result API
    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    imageView.setImageURI(photoUri); // Display the captured image
                    saveImageToGallery(photoUri); // Save the image to the gallery
                    sendImageOverMqtt(photoUri);
                }
            }
    );

    private void sendImageOverMqtt(Uri imageUri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            if (inputStream != null) {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    byteArrayOutputStream.write(buffer, 0, bytesRead);
                }

                byte[] imageBytes = byteArrayOutputStream.toByteArray();
                inputStream.close();

                // Optionally, compress or encode to Base64 if needed
                // String base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT);

                mqttHandler.publishBinary(TOPIC, imageBytes);
                Log.d(TAG, "Image sent via MQTT");
                Toast.makeText(this, "Image sent via MQTT", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error sending image via MQTT", e);
            Toast.makeText(this, "Failed to send image", Toast.LENGTH_SHORT).show();
        }


    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        System.setProperty("javax.net.debug", "ssl,handshake,verbose");

        setContentView(R.layout.activity_main);
        Log.d(TAG, "START");
        //System.setProperty("javax.net.debug", "ssl,handshake,ext");


        imageView = findViewById(R.id.image_view);
        Button captureButton = findViewById(R.id.button_capture);
        captureButton.setOnClickListener(this::captureImage);

        mqttHandler = new MqttHandler(this);
        mqttHandler.connect(BROKER, CLIENT_ID);

    }

    private void captureImage(View view) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_CAMERA_PERMISSION_CODE);
            return;
        }

        // Create a file for the full-resolution image
        File photoFile = createImageFile();
        if (photoFile != null) {
            // Get the Uri of the file to pass it in the intent
            photoUri = FileProvider.getUriForFile(this,
                    "com.example.ss_final_java.fileprovider", photoFile);

            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri); // Set the URI where the image will be saved
            cameraLauncher.launch(intent);
        }
    }

   /* private void connectToBroker() {
        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setAutomaticReconnect(true);
        mqttConnectOptions.setCleanSession(false);
        mqttConnectOptions.setUserName("");
        mqttConnectOptions.setPassword("".toCharArray());
        // Log the connection options for debugging purposes
        Log.d(TAG, "Connecting with options: Clean Session = " + mqttConnectOptions.isCleanSession()
                + ", Timeout = " + mqttConnectOptions.getConnectionTimeout()
                + ", Keep-Alive Interval = " + mqttConnectOptions.getKeepAliveInterval() + "aaa" + mqttClient.getClientId());
        try {
            Log.d(TAG, "Connecting to MQTT broker...");
            mqttClient.connect(mqttConnectOptions, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d(TAG, "Successfully connected to MQTT broker");
                    // Once connected, you can subscribe to topics or publish messages
                    subscribeToTopic();  // Subscribe to a topic after successful connection
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    if (exception != null) {
                        Log.e(TAG, "Failed to connect to MQTT broker: " + exception.getMessage(), exception);
                    } else {
                        Log.e(TAG, "Connection failed with return code: " + asyncActionToken.getClient());
                    }
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
            Log.e(TAG, "Error connecting to MQTT broker", e);
        }
    }

    private void subscribeToTopic() {
        try {
            mqttClient.subscribe(TOPIC, SUB_QOS);
            Log.d(TAG, "Subscribed to topic: " + TOPIC);
            publishMessage();
        } catch (MqttException e) {
            Log.e(TAG, "Error subscribing to topic", e);
        }
    }

    private void publishMessage() {
        try {
            MqttMessage message = new MqttMessage(MESSAGE.getBytes());
            message.setQos(PUB_QOS);
            mqttClient.publish(TOPIC, message);
            Log.d(TAG, "Message published: " + MESSAGE);
        } catch (MqttException e) {
            Log.e(TAG, "Error publishing message", e);
        }
    }*/

    private File createImageFile() {
        // Create an image file in the external storage or app's specific directory
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(null); // Use the app's specific external directory
        File imageFile = null;
        try {
            imageFile = File.createTempFile(
                    imageFileName,  /* prefix */
                    ".jpg",         /* suffix */
                    storageDir      /* directory */
            );
        } catch (IOException e) {
            Log.e(TAG, "Error creating image file", e);
        }
        return imageFile;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                captureImage(null);
            } else {
                Toast.makeText(this, "Camera permission denied!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void saveImageToGallery(Uri imageUri) {
        try {
            // Open the image from the URI using ContentResolver
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            if (inputStream != null) {
                // Decode the InputStream into a Bitmap
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

                // Use ContentValues to specify the image details to be stored
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, "CapturedImage_" + System.currentTimeMillis());
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/YourAppName");  // Set storage location

                // Insert image into MediaStore
                ContentResolver contentResolver = getContentResolver();
                Uri savedImageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

                // Save the image data to the gallery via OutputStream
                if (savedImageUri != null) {
                    OutputStream outputStream = contentResolver.openOutputStream(savedImageUri);
                    if (outputStream != null) {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);  // Compress and write to output stream
                        outputStream.close();
                        Toast.makeText(this, "Image saved to gallery!", Toast.LENGTH_SHORT).show();
                    }
                }
                inputStream.close();
            }

        } catch (IOException e) {
            Log.e(TAG, "Error saving image to gallery", e);
            Toast.makeText(this, "Error saving image to gallery", Toast.LENGTH_SHORT).show();
        }
    }

    private void publishMessage(String topic, String message)
    {
        mqttHandler.publish(topic, message);
    }

    private void subscribeToTopic(String topic)
    {
        mqttHandler.subscribe(topic);
    }

    @Override
    protected void onDestroy() {
        mqttHandler.disconnect();
        super.onDestroy();
        /*if (mqttClient != null) {
            try {
                mqttClient.disconnect();
                Log.d(TAG, "Disconnected from MQTT broker");
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }*/
    }
}
