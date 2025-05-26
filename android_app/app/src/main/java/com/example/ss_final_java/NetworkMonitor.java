package com.example.ss_final_java;

import static com.example.ss_final_java.MainActivity.BROKER;
import static com.example.ss_final_java.MainActivity.CLIENT_ID;
import static com.example.ss_final_java.MainActivity.RECV_TOPIC;
import static com.example.ss_final_java.MainActivity.TOPIC;
import static com.example.ss_final_java.MainActivity.currentMode;
import static com.example.ss_final_java.MqttHandler.MQTT_TAG;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

public class NetworkMonitor {
    private final ConnectivityManager connectivityManager;
    private final MqttHandler mqttHandler;

    public static final String NETWORK_TAG = "NetworkTag";

    public NetworkMonitor(@NonNull Context context, MqttHandler mqttHandler) {
        this.mqttHandler = mqttHandler;
        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        registerNetworkCallback();
    }

    // New constructor for testing with injected NetworkRequest
    public NetworkMonitor(@NonNull Context context, MqttHandler mqttHandler, NetworkRequest networkRequest) {
        this.mqttHandler = mqttHandler;
        this.connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        //registerNetworkCallback(networkRequest);
    }

    private void registerNetworkCallback() {
        NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build();

        connectivityManager.registerNetworkCallback(networkRequest, new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                Logger.d(NETWORK_TAG, "Network Connected.");
                if (mqttHandler != null) {
                    mqttHandler.connect(BROKER, CLIENT_ID);
                    mqttHandler.setMessageListener((topic, message) -> {
                        String payload = new String(message.getPayload());
                        Logger.d(MQTT_TAG, "MQTT command received: " + payload);

                        if (payload.equalsIgnoreCase("capture") && currentMode == MainActivity.Mode.ON_DEMAND) {
                            Logger.d(MQTT_TAG, "Resend on Demand");
                            mqttHandler.resendStoredImages(RECV_TOPIC);
                        }
                    });
                    mqttHandler.resendStoredImages(TOPIC);
                }
            }
            @Override
            public void onLost(@NonNull Network network) {
                Logger.d(NETWORK_TAG, "Network Disconnected.");
                mqttHandler.disconnect();
            }
        });
    }
}
