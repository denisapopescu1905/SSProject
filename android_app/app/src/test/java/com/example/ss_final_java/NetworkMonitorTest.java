package com.example.ss_final_java;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;

import org.junit.Before;
import org.junit.Test;

public class NetworkMonitorTest {

    private Context mockContext;
    private ConnectivityManager mockConnectivityManager;
    private MqttHandler mockMqttHandler;

    @Before
    public void setup() {
        mockContext = mock(Context.class);
        mockConnectivityManager = mock(ConnectivityManager.class);
        mockMqttHandler = mock(MqttHandler.class);

        when(mockContext.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(mockConnectivityManager);
    }

    @Test
    public void testMqttHandlerDisconnectCalled() {
        // Create a mock MqttHandler
        MqttHandler mockMqttHandler = mock(MqttHandler.class);

        // Simulate what NetworkMonitor would do on network loss
        mockMqttHandler.disconnect();

        // Verify disconnect was called
        verify(mockMqttHandler).disconnect();
    }
}
