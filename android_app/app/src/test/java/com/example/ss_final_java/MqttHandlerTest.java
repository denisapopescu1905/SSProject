package com.example.ss_final_java;

import android.content.Context;

import org.eclipse.paho.client.mqttv3.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.robolectric.annotation.Config;

import java.io.File;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MqttHandlerTest {

    private MqttHandler mqttHandler;
    private Context mockContext;
    private MqttClient mockClient;
    private ToastWrapper mockToastWrapper;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Before
    public void setUp() throws Exception {
        mockContext = mock(Context.class);
        mqttHandler = new MqttHandler(mockContext);
        System.setProperty("IS_TEST", "true");
        mockToastWrapper = mock(ToastWrapper.class);

        // Inject mock MQTT client
        mockClient = mock(MqttClient.class);
        when(mockContext.getFilesDir()).thenReturn(new File(System.getProperty("java.io.tmpdir")));
        mqttHandler = new MqttHandler(mockContext, mockToastWrapper);
        mqttHandler.client = mockClient;
    }

    @Test
    public void testLoggerDoesNotCrash() {
        Logger.d("TEST", "This is a debug log");
        Logger.e("TEST", "This is an error log");

        assertTrue(true);
    }

    @Test
    public void testPublishStringMessageSuccess() throws Exception {
        when(mockClient.isConnected()).thenReturn(true);

        mqttHandler.publish("test/topic", "Hello World");

        ArgumentCaptor<MqttMessage> captor = ArgumentCaptor.forClass(MqttMessage.class);
        verify(mqttHandler.client).publish(eq("test/topic"), captor.capture());
        assertEquals("Hello World", new String(captor.getValue().getPayload()));
    }

    @Test
    @Config
    public void testPublishBinaryMessageWhenConnected() throws Exception {
        when(mockClient.isConnected()).thenReturn(true);
        byte[] payload = "ImageData".getBytes();

        mqttHandler.publishBinary("img/topic", payload);

        verify(mqttHandler.client).publish(eq("img/topic"), any(MqttMessage.class));
    }

    @Test
    public void testPublishBinaryMessageWhenDisconnected() throws Exception {
        when(mockClient.isConnected()).thenReturn(false);
        byte[] payload = "OfflineData".getBytes();

        mqttHandler.publishBinary("img/topic", payload);

        File dir = new File(mockContext.getFilesDir(), "offline_images");
        File[] files = dir.listFiles();
        assertTrue(files.length > 0);
    }

    @Test
    public void testSubscribe() throws Exception {
        mqttHandler.subscribe("my/topic");
        verify(mqttHandler.client).subscribe(eq("my/topic"), eq(0), any(IMqttMessageListener.class));
    }

    @Test
    public void testDisconnectWhenConnected() throws Exception {
        when(mockClient.isConnected()).thenReturn(true);
        mqttHandler.disconnect();
        verify(mqttHandler.client).disconnect();
    }

    @Test
    public void testDisconnectWhenNotConnected() throws Exception {
        when(mockClient.isConnected()).thenReturn(false);
        mqttHandler.disconnect();
        verify(mqttHandler.client, never()).disconnect();
    }

    @Test
    public void testDeleteStoredImages() throws Exception {
        File dir = new File(mockContext.getFilesDir(), "offline_images");
        if (!dir.exists()) dir.mkdirs();
        File testFile = new File(dir, "test.jpg");
        testFile.createNewFile();

        mqttHandler.deleteStoredImages();
        assertFalse(testFile.exists());
    }

    @Test
    public void testPublishBinaryHandlesMqttException() throws Exception {
        when(mockClient.isConnected()).thenReturn(true);
        doThrow(new MqttException(MqttException.REASON_CODE_CLIENT_EXCEPTION))
                .when(mockClient).publish(anyString(), any(MqttMessage.class));

        byte[] payload = "fail".getBytes();
        mqttHandler.publishBinary("topic/fail", payload);

        // Verify that toastWrapper.show() is called with error message
        verify(mockToastWrapper).show(contains("Send error"));
    }

    @Test
    public void testSaveImageLocallyDeletesOldestFileWhenMaxExceeded() throws Exception {
        File dir = tempFolder.newFolder("offline_images");

        // Create MAX_STORED_IMAGES files with different lastModified times
        for (int i = 0; i < MqttHandler.MAX_STORED_IMAGES; i++) {
            File file = new File(dir, "file_" + i + ".jpg");
            file.createNewFile();
            file.setLastModified(System.currentTimeMillis() - i * 1000L);
        }
        // Add one more file to trigger deletion of oldest
        File extraFile = new File(dir, "extra.jpg");
        extraFile.createNewFile();
        extraFile.setLastModified(System.currentTimeMillis() - 100000L);

        when(mockContext.getFilesDir()).thenReturn(tempFolder.getRoot());

        // Call saveImageLocally via publishBinary with disconnected client to skip publish
        mqttHandler.client = null;
        mqttHandler.publishBinary("some/topic", "data".getBytes());

        // After saveImageLocally runs, the oldest file should be deleted
        File[] files = new File(dir, "").listFiles();
        assertEquals(MqttHandler.MAX_STORED_IMAGES + 1, files.length);
        assertFalse(extraFile.exists());
    }

    @Test
    public void testResendStoredImagesCallsPublishBinary() throws Exception {
        File dir = tempFolder.newFolder("offline_images");

        File file = new File(dir, "resend.jpg");
        file.createNewFile();
        when(mockContext.getFilesDir()).thenReturn(tempFolder.getRoot());

        // Write some bytes to file
        java.nio.file.Files.write(file.toPath(), "data".getBytes());

        // Spy on mqttHandler to verify publishBinary called
        MqttHandler spyHandler = spy(new MqttHandler(mockContext, mockToastWrapper));
        spyHandler.client = mockClient;
        when(mockClient.isConnected()).thenReturn(true);

        spyHandler.resendStoredImages("resend/topic");

        verify(spyHandler).publishBinary(eq("resend/topic"), any(byte[].class));
        verify(mockToastWrapper).show(contains("Resent local image"));
    }

    @Test
    public void testSubscribeHandlesMqttException() throws Exception {
        doThrow(new MqttException(MqttException.REASON_CODE_CLIENT_EXCEPTION))
                .when(mockClient).subscribe(anyString(), anyInt(), any(IMqttMessageListener.class));

        mqttHandler.subscribe("fail/topic");

        verify(mockToastWrapper, never()).show(anyString()); // Should not show toast on failure (or you can check logs)
    }

    @Test
    public void testConnectExceptionHandledGracefully() {
        try (MockedStatic<MqttHandler> mockedStatic = mockStatic(MqttHandler.class)) {
            mockedStatic.when(() -> MqttHandler.getSocketFactory(mockContext, "123456"))
                    .thenThrow(new Exception("SSL error"));

            doThrow(new MqttException(MqttException.REASON_CODE_CLIENT_EXCEPTION))
                    .when(mockClient).connect(any(MqttConnectOptions.class));

            MqttHandler handler = new MqttHandler(mockContext, mockToastWrapper);
            handler.client = mockClient;

            // If this throws, test should fail
            handler.connect("ssl://192.168.1.110:8883", "clientId");
        } catch (Exception e) {
            fail("Exception should be handled inside connect method: " + e.getMessage());
        }
    }

    @Test
    public void testSetMessageListenerAndReceiveMessage() throws Exception {
        IMqttMessageListener listener = mock(IMqttMessageListener.class);
        mqttHandler.setMessageListener(listener);

        // Simulate subscription callback
        ArgumentCaptor<IMqttMessageListener> captor = ArgumentCaptor.forClass(IMqttMessageListener.class);

        mqttHandler.client = mockClient;
        mqttHandler.subscribe("topic");

        verify(mockClient).subscribe(eq("topic"), eq(0), captor.capture());

        MqttMessage msg = new MqttMessage("payload".getBytes());
        captor.getValue().messageArrived("topic", msg);

        verify(listener).messageArrived("topic", msg);
    }
}
