package com.example.ss_final_java;
import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.*;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

import javax.net.ssl.*;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MQTTClient";
    private MqttAndroidClient mqttClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String brokerUrl = "ssl://mqtt-broker-address:8883"; // Configurable
        String clientId = "androidClient";

        mqttClient = new MqttAndroidClient(getApplicationContext(), brokerUrl, clientId);

        try {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName("admin");
            options.setPassword("123456".toCharArray());
            options.setSocketFactory(createSSLSocketFactory());


            mqttClient.connect(options, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d(TAG, "Conectare reușită la broker!");
                    subscribeToTopic();
                    publishMessage("Hello from Android!");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(TAG, "Conectare eșuată: " + exception.getMessage());
                }
            });

        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private SSLSocketFactory createSSLSocketFactory() {
        try {
            BufferedInputStream caInput = new BufferedInputStream(Files.newInputStream(Paths.get("path")));
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(Files.newInputStream(Paths.get("path")), "password".toCharArray()); // Client certificate

            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Certificate ca = cf.generateCertificate(caInput);
            keyStore.setCertificateEntry("ca", ca);

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, "clientpassword".toCharArray());
            KeyManager[] keyManagers = keyManagerFactory.getKeyManagers();

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init((KeyStore) null);
            TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, trustManagers, new java.security.SecureRandom());
            return sslContext.getSocketFactory();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void subscribeToTopic() {
        try {
            mqttClient.subscribe("topic/example", 0);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void publishMessage(String message) {
        try {
            MqttMessage mqttMessage = new MqttMessage(message.getBytes());
            mqttMessage.setQos(0);
            mqttClient.publish("topic/example", mqttMessage);
            Log.d(TAG, "Mesaj trimis: " + message);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect();
                Log.d(TAG, "Deconectare de la broker");
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }
    }
}
