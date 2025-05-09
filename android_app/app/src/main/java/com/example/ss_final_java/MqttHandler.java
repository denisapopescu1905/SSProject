package com.example.ss_final_java;

import static com.example.ss_final_java.MainActivity.TAG;

import android.content.Context;
import android.util.Log;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileReader;
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
            //connectOptions.setHttpsHostnameVerificationEnabled(false);
            // Allow any hostname verification (for testing)
            connectOptions.setSSLHostnameVerifier((hostname, session) -> true);  // Allow any hostname

            // Optionally, configure additional connection options
            connectOptions.setAutomaticReconnect(true);
            connectOptions.setCleanSession(true);


            try {
                SSLSocketFactory socketFactory = getSocketFactory(context,"123456");


                // HostnameVerifier for matching hostname or IP address with SAN (Subject Alternative Name)

                //SSLSocketFactory socketFactory = getSocketFactory();
                connectOptions.setSocketFactory(socketFactory);

                //connectOptions.setSocketFactory(socketFactory);
                /*HostnameVerifier allHostsValid = new HostnameVerifier() {
                    @Override
                    public boolean verify(String hostname, SSLSession session) {
                        return true;
                    }
                };
                connectOptions.setSSLHostnameVerifier(allHostsValid);
                */



            } catch (Exception e) {
                e.printStackTrace();
            }

            Log.d(TAG, "Connection options set.");
            client.connect(connectOptions);
            Log.d(TAG, "Connected to broker: " + brokerUrl);

        } catch (MqttException e) {
            Log.e(TAG, "Error connecting to broker: " + e.getMessage());
            Log.e(TAG, "Reason: " + e.getReasonCode(), e);
            Log.e(TAG, "Cause: " + e.getCause(), e);
        }

    }

    // Create SSL Socket Factory

    /*public static SSLSocketFactory getSocketFactory() {
        try {
            // Create a custom TrustManager that doesn't perform any certificate validation
            TrustManager[] trustAllCertificates = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }

                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }
                    }
            };

            // Install the all-trusting trust manager
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCertificates, new java.security.SecureRandom());

            // Create an SSLSocketFactory from the SSLContext
            return sc.getSocketFactory();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }*/

    private static SSLSocketFactory getSocketFactory(Context context, String password) throws Exception {
        Log.d(TAG, "Initializing custom SSLSocketFactory...");

        Security.addProvider(new BouncyCastleProvider());

        // Load CA certificate
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate caCert;
        try (InputStream caInput = context.getResources().openRawResource(R.raw.ca)) {
            caCert = (X509Certificate) cf.generateCertificate(caInput);
        }

        Log.d(TAG, caCert.toString());

        // Load client certificate
        X509Certificate clientCert;
        try (InputStream certInput = context.getResources().openRawResource(R.raw.client)) {
            clientCert = (X509Certificate) cf.generateCertificate(certInput);
        }

        // Load client private key
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

        // Create and initialize KeyStore with client cert and private key
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("client", privateKey, "".toCharArray(), new Certificate[]{clientCert});
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, "".toCharArray());

        // Create and initialize TrustStore with CA cert
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("ca", caCert);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        // Initialize SSLContext with both managers
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        return sslContext.getSocketFactory();
    }




    public void publishBinary(String topic, byte[] payload) {
        try {
            MqttMessage message = new MqttMessage(payload);
            message.setQos(1);
            client.publish(topic, message);
            Log.d("MQTT", "Binary message published to topic: " + topic);
        } catch (MqttException e) {
            Log.e("MQTT", "Failed to publish binary message", e);
        }
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
