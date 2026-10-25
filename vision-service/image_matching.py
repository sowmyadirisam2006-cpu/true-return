
import os
import time
from functools import lru_cache
from itertools import permutations

import numpy as np
import torch
from PIL import Image
from transformers import AutoImageProcessor, AutoModel


# ============================================================
# TRUE-RETURN: DINOv2 PRODUCT FEATURE MATCHING
# ============================================================

MODEL_NAME = "facebook/dinov2-base"
MATCH_THRESHOLD = 0.70

device = torch.device("cpu")

print()
print("Loading DINOv2 model...")
print("First run may download the pretrained model.")

model_load_start = time.perf_counter()

processor = AutoImageProcessor.from_pretrained(MODEL_NAME)
model = AutoModel.from_pretrained(MODEL_NAME)

model.to(device)
model.eval()

model_load_seconds = time.perf_counter() - model_load_start

print("DINOv2 model loaded successfully.")
print(f"MODEL LOAD TIME: {model_load_seconds:.2f} seconds")
print()


# ============================================================
# IMAGE LOADING
# ============================================================

def load_image(image_path):
    """Open an image safely and convert it to RGB."""
    with Image.open(image_path) as image:
        return image.convert("RGB")


# ============================================================
# PRODUCT-FOCUSED CROPS
# ============================================================

def create_product_crops(image):
    """Create full-image and tighter center crops."""
    width, height = image.size

    crops = [image]

    # Central 80% crop
    margin_x = int(width * 0.10)
    margin_y = int(height * 0.10)

    crops.append(
        image.crop((
            margin_x,
            margin_y,
            width - margin_x,
            height - margin_y
        ))
    )

    # Central 65% crop
    margin_x = int(width * 0.175)
    margin_y = int(height * 0.175)

    crops.append(
        image.crop((
            margin_x,
            margin_y,
            width - margin_x,
            height - margin_y
        ))
    )

    # Central 50% crop
    margin_x = int(width * 0.25)
    margin_y = int(height * 0.25)

    crops.append(
        image.crop((
            margin_x,
            margin_y,
            width - margin_x,
            height - margin_y
        ))
    )

    return crops


# ============================================================
# DINOv2 FEATURE EXTRACTION
# ============================================================

@torch.no_grad()
def get_embedding(image):
    """Convert one image into a normalized DINOv2 feature vector."""

    inputs = processor(
        images=image,
        return_tensors="pt"
    )

    inputs = {
        key: value.to(device)
        for key, value in inputs.items()
    }

    outputs = model(**inputs)

    embedding = outputs.last_hidden_state[:, 0, :]

    embedding = torch.nn.functional.normalize(
        embedding,
        p=2,
        dim=1
    )

    return embedding.cpu().numpy()[0]


# ============================================================
# BATCHED AND CACHED PRODUCT EMBEDDINGS
# ============================================================

@lru_cache(maxsize=128)
def _cached_product_embedding(image_path, modified_time):
    """
    Extract embeddings for the full image and three center crops.
    Cache the combined vector for repeated comparisons.
    """
    image = load_image(image_path)
    crops = create_product_crops(image)

    inputs = processor(
        images=crops,
        return_tensors="pt"
    )

    inputs = {
        key: value.to(device)
        for key, value in inputs.items()
    }

    with torch.no_grad():
        outputs = model(**inputs)

    embeddings = outputs.last_hidden_state[:, 0, :]

    embeddings = torch.nn.functional.normalize(
        embeddings,
        p=2,
        dim=1
    )

    embeddings = embeddings.cpu().numpy()

    # Weighted average of the full image and center crops.
    weights = np.array([0.20, 0.30, 0.30, 0.20])

    combined = np.average(
        embeddings,
        axis=0,
        weights=weights
    )

    norm = np.linalg.norm(combined)

    if norm > 0:
        combined = combined / norm

    return combined


def get_product_embedding(image_path):
    """Return a cached embedding for an image file."""
    image_path = os.path.abspath(image_path)
    modified_time = os.path.getmtime(image_path)

    return _cached_product_embedding(
        image_path,
        modified_time
    )


# ============================================================
# COSINE SIMILARITY
# ============================================================

def cosine_similarity(vector1, vector2):
    """Calculate cosine similarity between two feature vectors."""

    denominator = (
        np.linalg.norm(vector1) *
        np.linalg.norm(vector2)
    )

    if denominator == 0:
        return 0.0

    similarity = np.dot(vector1, vector2) / denominator

    return float(similarity)


# ============================================================
# COMPARE TWO IMAGES
# ============================================================

def compare_two_images(original_path, returned_path):
    """Compare two images using cached DINOv2 product embeddings."""
    try:
        original_embedding = get_product_embedding(original_path)
        returned_embedding = get_product_embedding(returned_path)

        similarity = cosine_similarity(
            original_embedding,
            returned_embedding
        )

        score = max(0.0, min(similarity, 1.0))

        return {
            "similarity": round(score, 4),
            "similarity_percent": round(score * 100, 1)
        }

    except Exception as error:
        print("Image comparison error:")
        print("Original:", original_path)
        print("Returned:", returned_path)
        print("Reason:", error)
        return None


# ============================================================
# BUILD THE COMPLETE 4 x 4 SIMILARITY MATRIX
# ============================================================

def build_similarity_matrix(original_images, returned_images):
    """
    Compare every returned image with every original image.

    Matrix rows are returned photos.
    Matrix columns are original photos.
    """
    matrix = []

    for returned_image in returned_images:
        row = []

        for original_image in original_images:
            result = compare_two_images(
                original_image,
                returned_image
            )

            if result is None:
                score = 0.0
            else:
                score = result["similarity"]

            row.append(score)

        matrix.append(row)

    return matrix


# ============================================================
# ORDER-INDEPENDENT ONE-TO-ONE ASSIGNMENT
# ============================================================

def find_best_assignment(similarity_matrix):
    """
    Find the assignment with the highest total similarity.

    Each returned photo is assigned to a different original
    photo. With four photos, only 4! = 24 assignments are checked.
    """
    best_assignment = None
    best_total = -1.0

    for assignment in permutations(range(4)):
        total = sum(
            similarity_matrix[returned_index][
                assignment[returned_index]
            ]
            for returned_index in range(4)
        )

        if total > best_total:
            best_total = total
            best_assignment = assignment

    return best_assignment


# ============================================================
# COMPARE FOUR ORIGINAL AND FOUR RETURNED PHOTOS
# ============================================================

def compare_four_images(original_images, returned_images):
    """
    Compare four original photos against four returned photos.

    Each returned photo is assigned to a different original
    photo using the highest-total-similarity assignment.
    """
    if len(original_images) != 4 or len(returned_images) != 4:
        raise ValueError(
            "Exactly 4 original and 4 returned images are required."
        )

    print("\n" + "=" * 65)
    print("TRUE-RETURN: DINOv2 PRODUCT VERIFICATION")
    print("=" * 65)

    # Step 1: Compare every returned photo against every original.
    similarity_matrix = build_similarity_matrix(
        original_images,
        returned_images
    )

    # Step 2: Find the best one-to-one assignment.
    assignment = find_best_assignment(similarity_matrix)

    if assignment is None:
        raise RuntimeError("Unable to assign the product photos.")

    matched_count = 0
    suspicious_count = 0
    reasons = []
    all_results = []

    # Step 3: Evaluate each assigned pair.
    for returned_index in range(4):
        original_index = assignment[returned_index]
        score = float(
            similarity_matrix[returned_index][original_index]
        )

        score = max(0.0, min(score, 1.0))

        if score >= MATCH_THRESHOLD:
            photo_result = "MATCH"
            reason = (
                "Assigned original photo passed the similarity threshold."
            )
            matched_count += 1

        else:
            photo_result = "SUSPICIOUS"
            reason = (
                "Assigned original photo did not pass the similarity threshold."
            )
            suspicious_count += 1

            reasons.append(
                f"Returned Photo {returned_index + 1}: {reason}"
            )

        print(f"\nReturned Photo {returned_index + 1}")
        print(f"Assigned Original: {original_index + 1}")
        print(f"Similarity: {score * 100:.1f}%")
        print(f"Result: {photo_result}")

        all_results.append({
            "returned_index": returned_index + 1,
            "best_original_index": original_index + 1,
            "similarity": round(score, 4),
            "similarity_percent": round(score * 100, 1),
            "result": photo_result,
            "reason": reason
        })

    # Step 4: Determine the overall verification result.
    final_result = (
        "MATCH" if suspicious_count == 0 else "SUSPICIOUS"
    )

    print("\n" + "=" * 65)
    print(f"Matched Photos: {matched_count}/4")
    print(f"Suspicious Photos: {suspicious_count}/4")
    print(f"FINAL RESULT: {final_result}")

    if reasons:
        print("\nPhotos requiring inspection:")

        for reason in reasons:
            print("-", reason)

    print("=" * 65)

    return {
        "matched_count": matched_count,
        "unmatched_count": suspicious_count,
        "final_result": final_result,
        "reasons": reasons,
        "results": all_results
    }
