package com.example.ss_final_java;

import static com.example.ss_final_java.MainActivity.RECV_TOPIC;
import static com.example.ss_final_java.MainActivity.TOPIC;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import java.security.cert.X509Certificate;
import java.util.Enumeration;

public class MqttHandler {
    MqttClient client;
    private Context context;
    private static final int MAX_STORED_IMAGES = 50;  ///< Storage Limit

    public static final String MQTT_TAG = "MQTT";

    IMqttMessageListener messageListener;

    public MqttHandler(Context context) {
        this.context = context;
    }

    public void connect(String brokerUrl, String clientId) {
        try {
            Log.d(MQTT_TAG, "Initializing MQTT client...");

            ///< Set up MQTT client
            MemoryPersistence persistence = new MemoryPersistence();
            client = new MqttClient(brokerUrl, clientId, persistence);
            Log.d(MQTT_TAG, "Client initialized. Connecting to broker: " + brokerUrl);

            ///< Set up connection options
            MqttConnectOptions connectOptions = new MqttConnectOptions();

            ///< Allow any hostname verification (for testing)
            ///< connectOptions.setHttpsHostnameVerificationEnabled(false);
            connectOptions.setSSLHostnameVerifier((hostname, session) -> true);
            connectOptions.setAutomaticReconnect(true);
            connectOptions.setCleanSession(true);

            try {
                SSLSocketFactory socketFactory = getSocketFactory(context,"123456");
                connectOptions.setSocketFactory(socketFactory);
            } catch (Exception e) {
                e.printStackTrace();
            }

            Log.d(MQTT_TAG, "Connection options set.");
            client.connect(connectOptions);
            Log.d(MQTT_TAG, "Connected to broker: " + brokerUrl);
            //resendStoredImages();
            subscribe(RECV_TOPIC);

        } catch (MqttException e) {
            Log.e(MQTT_TAG, "Error connecting to broker: " + e.getMessage());
            Log.e(MQTT_TAG, "Reason: " + e.getReasonCode(), e);
            Log.e(MQTT_TAG, "Cause: " + e.getCause(), e);
        }

    }

    ///< Create SSL Socket Factory
    static SSLSocketFactory getSocketFactory(Context context, String password) throws Exception {
        Log.d(MQTT_TAG, "Initializing custom SSLSocketFactory...");

        Security.addProvider(new BouncyCastleProvider());

        ///< Load CA certificate
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate caCert;
        try (InputStream caInput = context.getResources().openRawResource(R.raw.ca)) {
            caCert = (X509Certificate) cf.generateCertificate(caInput);
        }

        Log.d(MQTT_TAG, caCert.toString());

        ///< Load client certificate
        X509Certificate clientCert;
        try (InputStream certInput = context.getResources().openRawResource(R.raw.client)) {
            clientCert = (X509Certificate) cf.generateCertificate(certInput);
        }

        ///< Load client private key
        InputStream keyInput = context.getResources().openRawResource(R.raw.client_key);
        PEMParser pemParser = new PEMParser(new InputStreamReader(keyInput));
        Object object = pemParser.readObject();
        pemParser.close();

        JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
        PrivateKey privateKey;
        if (object instanceof PEMEncryptedKeyPair) {
            PEMDecryptorProvider decProv = new JcePEMDecryptorProviderBuilder().build(password.toCharArray());
            KeyPair keyPair = converter.getKeyPair(((PEMEncryptedKeyPair) object).decryptKeyPair(decProv));
            privateKey = keyPair.getPrivate();
        } else if (object instanceof PEMKeyPair) {
            privateKey = converter.getKeyPair((PEMKeyPair) object).getPrivate();
        } else if (object instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo) {
            privateKey = converter.getPrivateKey((org.bouncycastle.asn1.pkcs.PrivateKeyInfo) object);
        } else {
            throw new IllegalArgumentException("Unsupported key format: " + object.getClass());
        }

        ///< Create and initialize KeyStore with client cert and private key
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("client", privateKey, "".toCharArray(), new Certificate[]{clientCert});
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, "".toCharArray());

        ///< Create and initialize TrustStore with CA cert
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("ca", caCert);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        ///< Initialize SSLContext with both managers
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        return sslContext.getSocketFactory();
    }

    public void publishBinary(String topic, byte[] payload) {
        try {
            if (client != null && client.isConnected()) {
                MqttMessage message = new MqttMessage(payload);
                message.setQos(1);
                client.publish(topic, message);
                Log.d(MQTT_TAG, "Binary message published to topic: " + topic);
                Toast.makeText(context, "Image successfully sent via MQTT", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "Connection unavailable. Image stored locally.", Toast.LENGTH_SHORT).show();
            }
        } catch (MqttException e) {
            Log.e(MQTT_TAG, "Failed to publish binary message", e);
            Toast.makeText(context, "Send error. Image saved locally", Toast.LENGTH_SHORT).show();
        }
        saveImageLocally(payload);
    }

    void saveImageLocally(byte[] data) {
        try {
            File dir = new File(context.getFilesDir(), "offline_images");
            if (!dir.exists()) dir.mkdirs();

            File[] files = dir.listFiles();
            if (files != null && files.length >= MAX_STORED_IMAGES) {
                File oldest = files[0];
                for (File f : files) {
                    if (f.lastModified() < oldest.lastModified()) oldest = f;
                }
                oldest.delete();
            }

            String fileName = "img_" + System.currentTimeMillis() + ".jpg";
            File file = new File(dir, fileName);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(data);
            fos.close();

            Log.d(MQTT_TAG, "Imagine saved locally: " + fileName);
        } catch (IOException e) {
            Log.e(MQTT_TAG, "Error saving image locally.", e);
        }
    }

    void resendStoredImages(String topic) {
        File dir = new File(context.getFilesDir(), "offline_images");
        Log.e(MQTT_TAG, dir.getPath());
        if (!dir.exists()) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            try {
                byte[] data = Files.readAllBytes(file.toPath());
                publishBinary(topic, data);
                Toast.makeText(context, "Resent local image: " + file.getName(), Toast.LENGTH_SHORT).show();
                //file.delete();
            } catch (IOException e) {
                Log.e(MQTT_TAG, "Error reading file for resend", e);
            }
        }
    }

    public void deleteStoredImages() {
        File dir = new File(context.getFilesDir(), "offline_images");
        if (!dir.exists()) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            boolean deleted = file.delete();
            if (deleted) {
                Log.d(MQTT_TAG, "Deleted stored image: " + file.getName());
            } else {
                Log.e(MQTT_TAG, "Failed to delete stored image: " + file.getName());
            }
        }
    }



    public void disconnect() {
        try {
            if (client != null && client.isConnected()) {
                Log.d(MQTT_TAG, "Disconnecting from broker...");
                client.disconnect();
                Log.d(MQTT_TAG, "Disconnected from broker.");
            } else {
                Log.d(MQTT_TAG, "Client is not connected, no need to disconnect.");
            }
        } catch (MqttException e) {
            Log.e(MQTT_TAG, "Error disconnecting from broker: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void publish(String topic, String message) {
        try {
            Log.d(MQTT_TAG, "Publishing message to topic: " + topic);
            MqttMessage mqttMessage = new MqttMessage(message.getBytes());
            client.publish(topic, mqttMessage);
            Log.d(MQTT_TAG, "Message published to topic: " + topic);
        } catch (MqttException e) {
            Log.e(MQTT_TAG, "Error publishing message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void subscribe(String topic) {
        try {
            Log.d(MQTT_TAG, "Subscribing to topic: " + topic);
            client.subscribe(topic, 0, (receivedTopic, mqttMessage) -> {
                Log.d(MQTT_TAG, "Message received on topic: " + receivedTopic);
                if (messageListener != null) {
                    messageListener.messageArrived(receivedTopic, mqttMessage);
                }
            });
            Log.d(MQTT_TAG, "Subscribed to topic: " + topic);
            Toast.makeText(context, "Subscribed to topic: " + topic, Toast.LENGTH_SHORT).show();
        } catch (MqttException e) {
            Log.e(MQTT_TAG, "Error subscribing to topic: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void setMessageListener(IMqttMessageListener listener) {
        this.messageListener = listener;
    }
}
