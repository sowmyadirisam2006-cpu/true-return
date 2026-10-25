
import os
import tempfile
from pathlib import Path

from flask import Flask, jsonify, request
from PIL import Image, UnidentifiedImageError
from werkzeug.utils import secure_filename

from image_matching import compare_four_images


# ============================================================
# TRUE-RETURN ML API CONFIGURATION
# ============================================================

app = Flask(__name__)

ALLOWED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"}
MAX_FILE_SIZE = 15 * 1024 * 1024  # 15 MB per image

# Eight images maximum, with a small allowance for multipart overhead.
app.config["MAX_CONTENT_LENGTH"] = 8 * MAX_FILE_SIZE + 1024 * 1024


# ============================================================
# IMAGE UPLOAD VALIDATION
# ============================================================

def get_uploaded_images(field_name):
    """Validate and retrieve exactly four uploaded images."""

    files = request.files.getlist(field_name)

    if len(files) != 4:
        raise ValueError(
            f"Exactly 4 images are required for '{field_name}'. "
            f"Received {len(files)}."
        )

    for uploaded_file in files:
        if not uploaded_file.filename:
            raise ValueError("An uploaded image has no filename.")

        safe_filename = secure_filename(uploaded_file.filename)
        extension = Path(safe_filename).suffix.lower()

        if extension not in ALLOWED_EXTENSIONS:
            raise ValueError(
                f"Unsupported image extension: {extension}"
            )

        # Check file size.
        uploaded_file.stream.seek(0, os.SEEK_END)
        file_size = uploaded_file.stream.tell()
        uploaded_file.stream.seek(0)

        if file_size == 0:
            raise ValueError("An uploaded image is empty.")

        if file_size > MAX_FILE_SIZE:
            raise ValueError(
                "Each image must be 15 MB or smaller."
            )

    return files


# ============================================================
# SAVE AND VALIDATE IMAGE CONTENT
# ============================================================

def save_and_validate_image(uploaded_file, destination):
    """Save an upload and verify that it contains valid image data."""

    uploaded_file.save(destination)

    try:
        # Verify that the uploaded bytes contain a supported image.
        with Image.open(destination) as image:
            actual_format = image.format
            image.verify()

        # Ensure the actual image format agrees with its extension.
        expected_formats = {
            ".jpg": "JPEG",
            ".jpeg": "JPEG",
            ".png": "PNG",
            ".webp": "WEBP"
        }

        extension = Path(destination).suffix.lower()
        expected_format = expected_formats.get(extension)

        if actual_format != expected_format:
            raise ValueError(
                "Image content does not match its file extension."
            )

    except (UnidentifiedImageError, OSError) as error:
        Path(destination).unlink(missing_ok=True)
        raise ValueError(
            f"Invalid or corrupted image: {uploaded_file.filename}"
        ) from error

    except ValueError:
        Path(destination).unlink(missing_ok=True)
        raise


# ============================================================
# HEALTH CHECK
# ============================================================

@app.get("/health")
def health():
    return jsonify({
        "status": "UP",
        "service": "TRUE-RETURN ML API"
    }), 200


# ============================================================
# PRODUCT RETURN VERIFICATION
# ============================================================

@app.post("/api/verify")
def verify_product():
    try:
        # Exactly four original and four returned images.
        original_files = get_uploaded_images("original_images")
        returned_files = get_uploaded_images("returned_images")

        with tempfile.TemporaryDirectory(
            prefix="true_return_"
        ) as temp_dir:

            original_dir = Path(temp_dir) / "original"
            returned_dir = Path(temp_dir) / "returned"

            original_dir.mkdir()
            returned_dir.mkdir()

            original_paths = []
            returned_paths = []

            # Save original photos using their correct extensions.
            for index, uploaded_file in enumerate(
                original_files, start=1
            ):
                extension = Path(
                    secure_filename(uploaded_file.filename)
                ).suffix.lower()

                destination = (
                    original_dir / f"original_{index}{extension}"
                )

                save_and_validate_image(
                    uploaded_file, destination
                )

                original_paths.append(str(destination))

            # Save returned photos using their correct extensions.
            for index, uploaded_file in enumerate(
                returned_files, start=1
            ):
                extension = Path(
                    secure_filename(uploaded_file.filename)
                ).suffix.lower()

                destination = (
                    returned_dir / f"returned_{index}{extension}"
                )

                save_and_validate_image(
                    uploaded_file, destination
                )

                returned_paths.append(str(destination))

            # Run the existing DINOv2 verification pipeline.
            result = compare_four_images(
                original_paths,
                returned_paths
            )

            # Validate the result before reporting success.
            final_result = result.get("final_result")

            if final_result not in {"MATCH", "SUSPICIOUS"}:
                raise RuntimeError(
                    "The image matching service returned "
                    "an unrecognized verification result."
                )

            response = {
                "success": True,
                "finalResult": final_result,
                "matchedCount": result["matched_count"],
                "suspiciousCount": result["unmatched_count"],
                "reasons": result["reasons"],
                "results": result["results"]
            }

        return jsonify(response), 200

    except ValueError as error:
        return jsonify({
            "success": False,
            "error": str(error)
        }), 400

    except Exception:
        app.logger.exception("ML verification failed")

        return jsonify({
            "success": False,
            "error": (
                "ML verification failed. "
                "Check the Python service logs."
            )
        }), 500


# ============================================================
# START FLASK SERVER
# ============================================================

if __name__ == "__main__":
    print("Starting TRUE-RETURN ML API...")
    print("Health endpoint: http://127.0.0.1:5000/health")
    print("Verification endpoint: http://127.0.0.1:5000/api/verify")

    app.run(
        host="127.0.0.1",
        port=5000,
        debug=False
    )
