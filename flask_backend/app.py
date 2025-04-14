import os
import base64
import json
from datetime import datetime
from flask import Flask
from paho.mqtt.client import Client, CallbackAPIVersion

app = Flask(__name__)

MQTT_BROKER = "localhost"
MQTT_PORT = 1883
MQTT_TOPIC = "test/topic"
CA_CERT_PATH = "certs/ca.crt"
CLIENT_CERT_PATH = "certs/client.crt"
CLIENT_KEY_PATH = "certs/client.key"

UPLOAD_FOLDER = "received_images"
os.makedirs(UPLOAD_FOLDER, exist_ok=True)

# MQTT Callbacks
def on_connect(client, userdata, flags, rc):
    print(f"[MQTT] Connected with result code {rc}")
    client.subscribe(MQTT_TOPIC)

def on_message(client, userdata, msg):
    print(f"[MQTT] Message received on topic: {msg.topic}")
    try:
        payload = msg.payload.decode()

        try:
            # the default is JSON
            data = json.loads(payload)
            b64_image = data.get("image")
        except json.JSONDecodeError:
            # if not, it accepts base64 images
            b64_image = payload

        if b64_image:
            image_bytes = base64.b64decode(b64_image)
            timestamp = datetime.utcnow().strftime("%Y%m%d_%H%M%S")
            filename = os.path.join(UPLOAD_FOLDER, f"image_{timestamp}.jpg")
            with open(filename, "wb") as f:
                f.write(image_bytes)
            print(f"[MQTT] Image saved as {filename}")
        else:
            print("[MQTT] No image data found.")

    except Exception as e:
        print(f"[MQTT] Error processing message: {e}")


mqtt_client = Client(callback_api_version=CallbackAPIVersion.VERSION1)
mqtt_client.tls_set(ca_certs=CA_CERT_PATH,
                    certfile=CLIENT_CERT_PATH,
                    keyfile=CLIENT_KEY_PATH)
mqtt_client.on_connect = on_connect
mqtt_client.on_message = on_message
mqtt_client.connect(MQTT_BROKER, MQTT_PORT, 60)
mqtt_client.loop_start()

@app.route("/")
def index():
    return "Flask MQTT backend is running!"

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)

