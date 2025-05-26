package com.example.ss_final_java;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class RMqttHandlerTest {

    private Context context;
    private MqttHandler mqttHandler;
    private File offlineDir;
    @Mock
    MqttClient mockClient;

    @Mock
    IMqttMessageListener mockListener;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        context = ApplicationProvider.getApplicationContext();
        mqttHandler = new MqttHandler(context);
        mqttHandler.client = mockClient;

        offlineDir = new File(context.getFilesDir(), "offline_images");
        if (!offlineDir.exists()) offlineDir.mkdirs();
    }

    @After
    public void tearDown() {
        // Clean up files after each test
        if (offlineDir.exists()) {
            for (File file : offlineDir.listFiles()) {
                file.delete();
            }
            offlineDir.delete();
        }
    }

    @Test
    public void testSaveImageLocally_enforcesMaxLimit() throws Exception {
        // Save 55 images, max allowed is 50
        for (int i = 0; i < 55; i++) {
            byte[] dummyData = ("data" + i).getBytes();
            mqttHandler.saveImageLocally(dummyData);
            // Add tiny delay to ensure unique timestamps if needed
            Thread.sleep(5);
        }

        File[] files = offlineDir.listFiles();
        assertNotNull(files);
        assertTrue("No more than 50 files should be saved", files.length <= 50);
    }

    @Test
    public void testDeleteStoredImages_deletesAllFiles() throws Exception {
        // Create some dummy files
        for (int i = 0; i < 3; i++) {
            File file = new File(offlineDir, "img_" + i + ".jpg");
            file.createNewFile();
            assertTrue(file.exists());
        }

        mqttHandler.deleteStoredImages();

        File[] files = offlineDir.listFiles();
        assertTrue("All stored images should be deleted", files == null || files.length == 0);
    }

    @Test
    public void testPublishBinary_connected_publishesMessage() throws Exception {
        when(mockClient.isConnected()).thenReturn(true);
        doNothing().when(mockClient).publish(anyString(), any(MqttMessage.class));

        byte[] payload = "test payload".getBytes();
        mqttHandler.publishBinary("test/topic", payload);

        verify(mockClient, times(1)).publish(eq("test/topic"), any(MqttMessage.class));
    }

    @Test
    public void testPublishBinary_notConnected_savesImageLocally() throws Exception {
        when(mockClient.isConnected()).thenReturn(false);

        byte[] payload = "offline data".getBytes();
        mqttHandler.publishBinary("test/topic", payload);

        // Verificăm că imaginea este salvată local
        File[] files = offlineDir.listFiles();
        assertNotNull(files);
        assertTrue(files.length > 0);
    }

    @Test
    public void testPublish_callsMqttClientPublish() throws Exception {
        doNothing().when(mockClient).publish(anyString(), any(MqttMessage.class));

        mqttHandler.publish("topic/test", "hello world");

        verify(mockClient, times(1)).publish(eq("topic/test"), any(MqttMessage.class));
    }

    @Test
    public void testSubscribe_callsMqttClientSubscribe() throws Exception {
        doNothing().when(mockClient).subscribe(anyString(), anyInt(), any());

        mqttHandler.subscribe("topic/subscribe");

        verify(mockClient, times(1)).subscribe(eq("topic/subscribe"), eq(0), any());
    }

    @Test
    public void testDisconnect_whenConnected_callsDisconnect() throws Exception {
        when(mockClient.isConnected()).thenReturn(true);
        doNothing().when(mockClient).disconnect();

        mqttHandler.disconnect();

        verify(mockClient, times(1)).disconnect();
    }

    @Test
    public void testDisconnect_whenNotConnected_doesNotCallDisconnect() throws Exception {
        when(mockClient.isConnected()).thenReturn(false);

        mqttHandler.disconnect();

        verify(mockClient, never()).disconnect();
    }

    @Test
    public void testSetMessageListener_assignsListener() {
        mqttHandler.setMessageListener(mockListener);
        assertEquals(mockListener, mqttHandler.messageListener);
    }

    @Test
    public void testSaveImageLocally_createsFile() throws Exception {
        byte[] dummyData = "dummy image data".getBytes();

        mqttHandler.saveImageLocally(dummyData);

        File[] files = offlineDir.listFiles();
        assertNotNull(files);
        assertTrue("Should save at least one image file", files.length > 0);

        boolean foundFileWithData = false;
        for (File file : files) {
            if (file.length() == dummyData.length) {
                foundFileWithData = true;
                break;
            }
        }
        assertTrue("Saved file should have correct size", foundFileWithData);
    }

}
