import os
import json
import logging
from datetime import datetime
from flask import Flask, jsonify
from paho.mqtt.client import Client
from PIL import Image
import pytesseract

# ──────────────── Configuration ────────────────
MQTT_BROKER = "localhost"
MQTT_PORT = 8883
MQTT_TOPIC = "test/topic"

# TLS certs
CA_CERT_PATH = "certs/ca.crt"
CLIENT_CERT_PATH = "certs/client.crt"
CLIENT_KEY_PATH = "certs/client.key"

UPLOAD_FOLDER = "received_images"
os.makedirs(UPLOAD_FOLDER, exist_ok=True)

# ──────────────── Logging ────────────────
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("mqtt_flask_backend")

# ──────────────── Flask Setup ────────────────
app = Flask(__name__)

@app.route("/")
def index():
    return "Flask MQTT backend with OCR and mTLS."

@app.route("/health")
def health():
    return jsonify({"status": "ok"})

# ──────────────── MQTT Callbacks ────────────────
def on_connect(client, userdata, flags, rc):
    if rc == 0:
        logger.info(f"[MQTT] Connected to {MQTT_BROKER}:{MQTT_PORT}")
        client.subscribe(MQTT_TOPIC)
        logger.info(f"[MQTT] Subscribed to topic: {MQTT_TOPIC}")
    else:
        logger.error(f"[MQTT] Failed to connect, return code {rc}")

def on_message(client, userdata, msg):
    logger.info(f"[MQTT] Message received on topic: {msg.topic}")
    try:
        image_bytes = msg.payload
        timestamp = datetime.utcnow().strftime("%Y%m%d_%H%M%S")
        filename = os.path.join(UPLOAD_FOLDER, f"image_{timestamp}.jpg")

        # Save image to disk
        with open(filename, "wb") as f:
            f.write(image_bytes)
        logger.info(f"[MQTT] Image saved as {filename}")

        # OCR: extract text
        try:
            image = Image.open(filename)
            extracted_text = pytesseract.image_to_string(image)
            logger.info(f"[OCR] Extracted text:\n{extracted_text.strip()}")
        except Exception as e:
            logger.warning(f"[OCR] Failed to extract text: {e}")

    except Exception as e:
        logger.exception(f"[MQTT] Error processing message: {e}")

# ──────────────── MQTT Setup with mTLS ────────────────
mqtt_client = Client()
mqtt_client.tls_set(
    ca_certs=CA_CERT_PATH,
    certfile=CLIENT_CERT_PATH,
    keyfile=CLIENT_KEY_PATH
)
mqtt_client.on_connect = on_connect
mqtt_client.on_message = on_message

mqtt_client.connect(MQTT_BROKER, MQTT_PORT, keepalive=60)
mqtt_client.loop_start()

# ──────────────── Run Flask ────────────────
if __name__ == "__main__":
app.run(host="127.0.0.1", port=5000)

