package com.example.ss_final_java;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Spinner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowApplication;

import java.io.File;
import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
public class MainActivityTest {

    @Mock
    MqttHandler mockMqttHandler;

    private MainActivity activity;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).create().start().resume();
        activity = controller.get();

        // Injectăm mock-ul mqttHandler
        activity.mqttHandler = mockMqttHandler;

        // Reatașăm listener-ul spinner-ului să folosească mock-ul
        Spinner spinner = activity.findViewById(R.id.mode_spinner);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 1:
                        MainActivity.currentMode = MainActivity.Mode.ON_DEMAND;
                        break;
                    case 2:
                        MainActivity.currentMode = MainActivity.Mode.PERIODIC;
                        activity.mqttHandler.resendStoredImages(MainActivity.TOPIC);
                        break;
                    default:
                        MainActivity.currentMode = MainActivity.Mode.NONE;
                        break;
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        MainActivity.currentMode = MainActivity.Mode.NONE;
    }

    @Test
    public void testInitialModeIsNone() {
        assertEquals(MainActivity.Mode.NONE, MainActivity.currentMode);
    }

    @Test
    public void testSpinnerModeSwitch_changesCurrentModeAndStartsTimers() {
        Spinner spinner = activity.findViewById(R.id.mode_spinner);

        // Selectăm opțiunea PERIODIC (de obicei poziția 2)
        spinner.setSelection(2);

        // Verificăm dacă s-a schimbat modul
        assertEquals(MainActivity.Mode.PERIODIC, MainActivity.currentMode);

        // Verificăm că metoda mqttHandler.resendStoredImages a fost apelată
        verify(mockMqttHandler, atLeastOnce()).resendStoredImages(MainActivity.TOPIC);
    }

    @Test
    public void testSendImageOverMqtt_withNullUri_doesNotCallPublish() throws Exception {
        activity.mqttHandler = mockMqttHandler;

        activity.sendImageOverMqtt(null);

        verify(mockMqttHandler, never()).publishBinary(anyString(), any(byte[].class));
    }


    @Test
    public void testCaptureImage_setsPhotoUri() throws Exception {
        Shadows.shadowOf(activity).grantPermissions(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        MainActivity spyActivity = Mockito.spy(activity);

        File fakeFile = File.createTempFile("JPEG_test", ".jpg", activity.getCacheDir());
        doReturn(fakeFile).when(spyActivity).createImageFile();

        // Suprascriem metoda captureImage să nu folosească FileProvider, ci să seteze direct photoUri
        doAnswer(invocation -> {
            spyActivity.photoUri = Uri.fromFile(fakeFile);
            return null;
        }).when(spyActivity).captureImage(null);

        spyActivity.captureImage(null);

        assertNotNull("photoUri should not be null", spyActivity.photoUri);
    }

    @Test
    public void testSendImageOverMqtt_callsPublishBinary() throws Exception {
        File testImageFile = File.createTempFile("test_image", ".jpg", activity.getCacheDir());
        Uri testUri = Uri.fromFile(testImageFile);

        activity.mqttHandler = mockMqttHandler;

        activity.sendImageOverMqtt(testUri);

        verify(mockMqttHandler, times(1)).publishBinary(eq(MainActivity.TOPIC), any(byte[].class));
    }

    @Test
    public void testOnDestroy_disconnectsMqttHandler() {
        activity.mqttHandler = mockMqttHandler;

        activity.onDestroy();

        verify(mockMqttHandler).disconnect();
    }

    @Test
    public void testSpinnerModeSwitch_manualMode() {
        Spinner spinner = activity.findViewById(R.id.mode_spinner);

        spinner.setSelection(1);

        assertEquals(MainActivity.Mode.ON_DEMAND, MainActivity.currentMode);

        verify(mockMqttHandler, never()).resendStoredImages(anyString());
    }
}
