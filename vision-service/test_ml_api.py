
import requests
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent

original_paths = [
    BASE_DIR / "test_images" / "original" / "Screenshot 2026-09-18 154320.png",
    BASE_DIR / "test_images" / "original" / "Screenshot 2026-09-18 154719.png",
    BASE_DIR / "test_images" / "original" / "Screenshot 2026-09-22 125052.png",
    BASE_DIR / "test_images" / "original" / "image.png",
]

returned_paths = [
    BASE_DIR / "test_images" / "returned" / "image.png",
    BASE_DIR / "test_images" / "returned" / "Screenshot 2026-09-18 154320.png",
    BASE_DIR / "test_images" / "returned" / "Screenshot 2026-09-18 154753.png",
    BASE_DIR / "test_images" / "returned" / "Screenshot 2026-09-22 125052.png",
]

# Confirm that all eight test images exist.
for path in original_paths + returned_paths:
    if not path.is_file():
        raise FileNotFoundError(f"Image not found: {path}")

files = []

try:
    for path in original_paths:
        files.append(
            ("original_images", (path.name, open(path, "rb"), "image/png"))
        )

    for path in returned_paths:
        files.append(
            ("returned_images", (path.name, open(path, "rb"), "image/png"))
        )

    print("Sending 4 original and 4 returned photos to the ML API...")

    response = requests.post(
        "http://127.0.0.1:5000/api/verify",
        files=files,
        timeout=300,
    )

    print("HTTP status:", response.status_code)
    print("API response:")
    print(response.text)

    response.raise_for_status()

except requests.RequestException as error:
    print("API request failed:", error)

finally:
    for _, (_, file_object, _) in files:
        file_object.close()
