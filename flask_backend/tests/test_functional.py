import os
import pytest
from flask_backend.app import app

@pytest.fixture
def client():
    app.config['TESTING'] = True
    return app.test_client()

def test_valid_image(client):
    test_image_path = os.path.join(os.path.dirname(__file__), "test_files", "sample_image.png")
    with open(test_image_path, "rb") as img:
        response = client.post("/process", data={"image": img})
        assert response.status_code == 200
        assert "text" in response.get_json()

def test_missing_image(client):
    response = client.post("/process", data={})
    assert response.status_code == 400
