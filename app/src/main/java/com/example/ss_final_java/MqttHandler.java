package com.example.ss_final_java;

import static com.example.ss_final_java.MainActivity.TAG;

import android.content.Context;
import android.util.Log;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.security.cert.X509Certificate;

public class MqttHandler {
    private MqttClient client;
    private Context context;


    // Constructor that takes Context
    public MqttHandler(Context context) {
        this.context = context; // Save the context
    }

    public void connect(String brokerUrl, String clientId) {
        try {
            Log.d(TAG, "Initializing MQTT client...");

            // Set up MQTT client
            MemoryPersistence persistence = new MemoryPersistence();
            client = new MqttClient(brokerUrl, clientId, persistence);
            Log.d(TAG, "Client initialized. Connecting to broker: " + brokerUrl);

            // Set up connection options
            MqttConnectOptions connectOptions = new MqttConnectOptions();
            connectOptions.setCleanSession(true);

            try {
                InputStream caCrtFile = context.getResources().openRawResource(R.raw.ca);
                InputStream crtFile = context.getResources().openRawResource(R.raw.client);
                InputStream keyFile = context.getResources().openRawResource(R.raw.key);

                connectOptions.setSocketFactory(getSocketFactory(caCrtFile, crtFile, keyFile, "1234"));
            } catch (Exception e) {
                e.printStackTrace();
            }

            Log.d(TAG, "Connection options set.");
            client.connect(connectOptions);
            Log.d(TAG, "Connected to broker: " + brokerUrl);

        } catch (MqttException e) {
            Log.e(TAG, "Error connecting to broker: " + e.getMessage());
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    // Create SSL Socket Factory
    public static SSLSocketFactory getSocketFactory(InputStream caCrtFile, InputStream crtFile, InputStream keyFile,
                                                    String password) throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        // load CA certificate
        X509Certificate caCert = null;

        BufferedInputStream bis = new BufferedInputStream(caCrtFile);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");

        while (bis.available() > 0) {
            caCert = (X509Certificate) cf.generateCertificate(bis);
        }

        // load client certificate
        bis = new BufferedInputStream(crtFile);
        X509Certificate cert = null;
        while (bis.available() > 0) {
            cert = (X509Certificate) cf.generateCertificate(bis);
        }

        // load client private cert
        PEMParser pemParser = new PEMParser(new InputStreamReader(keyFile));
        Object object = pemParser.readObject();
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
        KeyPair key = converter.getKeyPair((PEMKeyPair) object);

        KeyStore caKs = KeyStore.getInstance(KeyStore.getDefaultType());
        caKs.load(null, null);
        caKs.setCertificateEntry("cert-certificate", caCert);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(caKs);

        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);
        ks.setCertificateEntry("certificate", cert);
        ks.setKeyEntry("private-cert", key.getPrivate(), password.toCharArray(),
                new java.security.cert.Certificate[]{cert});
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, password.toCharArray());

        SSLContext context = SSLContext.getInstance("TLSv1.2");
        context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        return context.getSocketFactory();
    }





    public void disconnect() {
        try {
            if (client != null && client.isConnected()) {
                Log.d(TAG, "Disconnecting from broker...");
                client.disconnect();
                Log.d(TAG, "Disconnected from broker.");
            } else {
                Log.d(TAG, "Client is not connected, no need to disconnect.");
            }
        } catch (MqttException e) {
            Log.e(TAG, "Error disconnecting from broker: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void publish(String topic, String message) {
        try {
            Log.d(TAG, "Publishing message to topic: " + topic);
            MqttMessage mqttMessage = new MqttMessage(message.getBytes());
            client.publish(topic, mqttMessage);
            Log.d(TAG, "Message published to topic: " + topic);
        } catch (MqttException e) {
            Log.e(TAG, "Error publishing message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void subscribe(String topic) {
        try {
            Log.d(TAG, "Subscribing to topic: " + topic);
            client.subscribe(topic);
            Log.d(TAG, "Subscribed to topic: " + topic);
        } catch (MqttException e) {
            Log.e(TAG, "Error subscribing to topic: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
