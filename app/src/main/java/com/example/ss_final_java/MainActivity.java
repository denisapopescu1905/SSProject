package com.example.ss_final_java;

import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MQTT"; // Tag for Logcat

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "START");
        setContentView(R.layout.activity_main);

        //String broker = "tcp://192.168.42.19:1883";
        String broker = "tcp://10.41.202.136:1883";


        String clientId = "demo_client";
        String topic = "topic/test";
        int subQos = 1;
        int pubQos = 1;
        String msg = "Hello MQTT";

        try {
            // Initialize MQTT client with in-memory persistence
            MqttClient client = new MqttClient(broker, clientId, new MqttDefaultFilePersistence(getFilesDir().getAbsolutePath()));
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);  // Optionally use clean session
            Log.d(TAG, "Connecting to broker: " + broker);
            client.connect(options);
            Log.d(TAG, "Connected to broker");

            if (client.isConnected()) {
                // Set callback to handle incoming messages
                client.setCallback(new MqttCallback() {

                    // When a message arrives
                    public void messageArrived(String topic, MqttMessage message) throws Exception {
                        Log.d(TAG, "Message arrived on topic: " + topic);
                        Log.d(TAG, "QoS: " + message.getQos());
                        Log.d(TAG, "Message content: " + new String(message.getPayload()));
                    }

                    // When the connection is lost
                    public void connectionLost(Throwable cause) {
                        Log.e(TAG, "Connection lost: " + cause.getMessage());
                    }

                    // When message delivery is complete
                    public void deliveryComplete(IMqttDeliveryToken token) {
                        Log.d(TAG, "Delivery complete: " + token.isComplete());
                    }
                });

                // Subscribe to the topic
                Log.d(TAG, "Subscribing to topic: " + topic);
                client.subscribe(topic, subQos);

                // Create and publish a message
                MqttMessage message = new MqttMessage(msg.getBytes());
                message.setQos(pubQos);
                Log.d(TAG, "Publishing message: " + msg);
                client.publish(topic, message);
            }

            // Disconnect from the broker
            client.disconnect();
            Log.d(TAG, "Disconnected from broker");

            client.close();
            Log.d(TAG, "Client closed");

        } catch (MqttException e) {
            Log.e(TAG, "Error: " + e.getMessage(), e);
        }
    }
}