package com.example.ss_final_java;

import static com.example.ss_final_java.MainActivity.RECV_TOPIC;
import static com.example.ss_final_java.MainActivity.TOPIC;
import static com.example.ss_final_java.MainActivity.currentMode;
import static com.example.ss_final_java.MainActivity.Mode.ON_DEMAND;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkRequest;

import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class RNetworkMonitorTest {

    @Mock Context mockContext;
    @Mock ConnectivityManager mockConnectivityManager;

    MqttHandler stubMqttHandler;

    boolean connectCalled = false;
    boolean disconnectCalled = false;
    boolean resendStoredImagesCalledForRecv = false;
    boolean resendStoredImagesCalledForTopic = false;
    IMqttMessageListener savedListener;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockContext.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(mockConnectivityManager);

        // Create a stub MqttHandler with overridden methods to track calls and save listener
        stubMqttHandler = spy(new MqttHandler(mockContext) {
            @Override
            public void connect(String broker, String clientId) {
                connectCalled = true;
            }

            @Override
            public void disconnect() {
                disconnectCalled = true;
            }

            @Override
            public void resendStoredImages(String topic) {
                if (RECV_TOPIC.equals(topic)) resendStoredImagesCalledForRecv = true;
                if (TOPIC.equals(topic)) resendStoredImagesCalledForTopic = true;
            }

            @Override
            public void setMessageListener(IMqttMessageListener listener) {
                savedListener = listener;
            }
        });

        currentMode = ON_DEMAND;

        // Construct NetworkMonitor with mocks and stub
        new NetworkMonitor(mockContext, stubMqttHandler);
    }

    @Test
    public void testOnAvailable_ConnectsAndHandlesMessage() throws Exception {
        // Capture the registered NetworkCallback
        ArgumentCaptor<ConnectivityManager.NetworkCallback> callbackCaptor =
                ArgumentCaptor.forClass(ConnectivityManager.NetworkCallback.class);

        verify(mockConnectivityManager).registerNetworkCallback(any(NetworkRequest.class), callbackCaptor.capture());
        ConnectivityManager.NetworkCallback callback = callbackCaptor.getValue();

        Network dummyNetwork = mock(Network.class);

        // Simulate network becoming available
        callback.onAvailable(dummyNetwork);

        assertTrue("connect should be called", connectCalled);

        // Simulate MQTT message with payload "capture"
        MqttMessage message = new MqttMessage("capture".getBytes());

        // Use saved listener to simulate message arrival
        savedListener.messageArrived(RECV_TOPIC, message);

        // Verify resendStoredImages called for RECV_TOPIC and TOPIC
        assertTrue("resendStoredImages should be called for RECV_TOPIC", resendStoredImagesCalledForRecv);
        assertTrue("resendStoredImages should be called for TOPIC", resendStoredImagesCalledForTopic);
    }

    @Test
    public void testOnLost_Disconnects() {
        // Capture the registered NetworkCallback
        ArgumentCaptor<ConnectivityManager.NetworkCallback> callbackCaptor =
                ArgumentCaptor.forClass(ConnectivityManager.NetworkCallback.class);

        verify(mockConnectivityManager).registerNetworkCallback(any(NetworkRequest.class), callbackCaptor.capture());
        ConnectivityManager.NetworkCallback callback = callbackCaptor.getValue();

        Network dummyNetwork = mock(Network.class);

        // Simulate network lost
        callback.onLost(dummyNetwork);

        assertTrue("disconnect should be called", disconnectCalled);
    }
}
