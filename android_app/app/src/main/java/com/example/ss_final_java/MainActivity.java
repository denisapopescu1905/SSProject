package com.example.ss_final_java;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.Manifest;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
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
import java.util.Timer;
import java.util.TimerTask;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.*;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class MainActivity extends AppCompatActivity {

    public static final String APP_TAG = "CameraApp";
    public static final int REQUEST_CAMERA_PERMISSION_CODE = 1;
    Uri photoUri;
    private ImageView imageView;

    private Timer periodicTimer;
    private Timer liveTimer;


    protected enum Mode { NONE, ON_DEMAND, PERIODIC, LIVE }

    public static Mode currentMode = Mode.NONE;
    private Handler liveHandler = new Handler(Looper.getMainLooper());
    private boolean isLiveRunning = false;

    static final String BROKER = "ssl://192.168.1.110:8883";
    static final String CLIENT_ID = "Android_client";
    static final String TOPIC = "test/topic";
    static final String RECV_TOPIC = "camera/commands";

    MqttHandler mqttHandler;

    private void startPeriodicMode() {
        if (periodicTimer != null) {
            periodicTimer.cancel();
        }
        periodicTimer = new Timer();
        periodicTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                new Thread(() -> {
                    Log.d(APP_TAG, "Periodic 30s");
                    mqttHandler.resendStoredImages(TOPIC);
                });
            }
        }, 0, 30000);  // la fiecare 30 secunde
    }

    private void startLiveMode() {
        if (liveTimer != null) {
            liveTimer.cancel();
        }
        liveTimer = new Timer();
        liveTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                new Thread(() -> {
                    Log.d(APP_TAG, "Live 1s");
                    mqttHandler.resendStoredImages(TOPIC);
                });
            }
        }, 0, 1000);  // la fiecare 30 secunde
    }

    private void stopPeriodicMode() {
        if (periodicTimer != null) {
            periodicTimer.cancel();
            periodicTimer = null;
        }
    }

    private void stopLiveMode() {
        if (liveTimer != null) {
            liveTimer.cancel();
            liveTimer = null;
        }
    }


    ///< Camera launcher using Activity Result API
    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    imageView.setImageURI(photoUri); ///< Display the captured image
                    saveImageToGallery(photoUri); ///< Save the image to the gallery
                    sendImageOverMqtt(photoUri);
                }
            }
    );

    void sendImageOverMqtt(Uri imageUri) {
        if(imageUri != null) {
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

                    ///< Optionally, compress or encode to Base64 if needed
                    ///< String base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT);
                    mqttHandler.publishBinary(TOPIC, imageBytes);
                }
            } catch (IOException e) {
                Log.e(APP_TAG, "Error sending image via MQTT", e);
                Toast.makeText(this, "Failed to send image", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        System.setProperty("javax.net.debug", "ssl,handshake,verbose");

        setContentView(R.layout.activity_main);
        Log.d(APP_TAG, "START");

        imageView = findViewById(R.id.image_view);
        Button captureButton = findViewById(R.id.button_capture);
        captureButton.setOnClickListener(this::captureImage);

        Button deleteButton = findViewById(R.id.button_delete);
        deleteButton.setOnClickListener(v -> mqttHandler.deleteStoredImages());

        Spinner modeSpinner = findViewById(R.id.mode_spinner);
        modeSpinner.setSelection(0); // Default to 'None'
        modeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0:
                        currentMode = Mode.NONE;
                        stopPeriodicMode();
                        stopLiveMode();
                        //stopLiveMode();
                       //stopPeriodicMode();
                        break;
                    case 1:
                        currentMode = Mode.ON_DEMAND;
                        stopPeriodicMode();
                        stopLiveMode();
                        //stopLiveMode();
                        //stopPeriodicMode();
                        break;
                    case 2:
                        currentMode = Mode.PERIODIC;
                        stopLiveMode();
                        startPeriodicMode();
                        //startPeriodicMode();
                        //stopLiveMode();
                        break;
                    case 3:
                        currentMode = Mode.LIVE;
                        stopPeriodicMode();
                        startLiveMode();
                        //startLiveMode();
                        //stopPeriodicMode();
                        break;
                }
                Toast.makeText(MainActivity.this, "Mode: " + currentMode.name(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        mqttHandler = new MqttHandler(this);
        NetworkMonitor networkMonitor = new NetworkMonitor(this, mqttHandler);
    }

    void captureImage(View view) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_CAMERA_PERMISSION_CODE);
            return;
        }

        ///< Create a file for the full-resolution image
        File photoFile = createImageFile();
        if (photoFile != null) {
            ///< Get the Uri of the file to pass it in the intent
            photoUri = FileProvider.getUriForFile(this,
                    "com.example.ss_final_java.fileprovider", photoFile);

            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri); // Set the URI where the image will be saved
            cameraLauncher.launch(intent);

        }
    }
    File createImageFile() {
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
            Log.e(APP_TAG, "Error creating image file", e);
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
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            if (inputStream != null) {
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, "CapturedImage_" + System.currentTimeMillis());
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/YourAppName");  // Set storage location

                ContentResolver contentResolver = getContentResolver();
                Uri savedImageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

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
            Log.e(APP_TAG, "Error saving image to gallery", e);
            Toast.makeText(this, "Error saving image to gallery", Toast.LENGTH_SHORT).show();
        }
    }
    @Override
    protected void onDestroy() {
        mqttHandler.disconnect();
        super.onDestroy();
    }


}
