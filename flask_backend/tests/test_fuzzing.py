import io
import random
import string
import pytest
from flask_backend.app import app

@pytest.fixture
def client():
    app.config['TESTING'] = True
    return app.test_client()

def generate_random_bytes(length=1024):
    return bytes([random.randint(0, 255) for _ in range(length)])

def test_random_binary_payload(client):
    response = client.post("/process", data={"image": (io.BytesIO(generate_random_bytes()), "fuzz.bin")})
    assert response.status_code in [400, 422, 500]

def test_random_text_as_image(client):
    fuzz_string = ''.join(random.choices(string.ascii_letters, k=1000))
    response = client.post("/process", data={"image": (io.BytesIO(fuzz_string.encode()), "fake.jpg")})
    assert response.status_code in [400, 422, 500]
