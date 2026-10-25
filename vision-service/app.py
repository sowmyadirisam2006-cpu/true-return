
import time
import os
import csv
from datetime import datetime
from image_matching import compare_four_images


# Folder paths are relative to this app.py file.
BASE_DIR = os.path.dirname(os.path.abspath(__file__))

ORIGINAL_FOLDER = os.path.join(BASE_DIR, "test_images", "original")
RETURNED_FOLDER = os.path.join(BASE_DIR, "test_images", "returned")
HISTORY_FILE = os.path.join(BASE_DIR, "verification_history.csv")


def get_images(folder):
    valid_extensions = (".jpg", ".jpeg", ".png", ".webp")

    if not os.path.isdir(folder):
        raise FileNotFoundError(
            f"Image folder not found: {folder}"
        )

    images = []

    for filename in os.listdir(folder):
        file_path = os.path.join(folder, filename)

        if (
            os.path.isfile(file_path)
            and filename.lower().endswith(valid_extensions)
        ):
            images.append(file_path)

    images.sort()
    return images


def main():
    print()
    print("=" * 65)
    print("              TRUE-RETURN")
    print("        AI VISUAL VERIFICATION")
    print("=" * 65)

    try:
        original_images = get_images(ORIGINAL_FOLDER)
        returned_images = get_images(RETURNED_FOLDER)
    except FileNotFoundError as error:
        print(f"\nERROR: {error}")
        return

    print("\nOriginal Product Photos:")
    for i, image in enumerate(original_images, 1):
        print(f"  Original {i}: {os.path.basename(image)}")

    print("\nReturned Product Photos:")
    for i, image in enumerate(returned_images, 1):
        print(f"  Returned {i}: {os.path.basename(image)}")

    if len(original_images) != 4:
        print(
            f"\nERROR: Exactly 4 original photos are required. "
            f"Found {len(original_images)}."
        )
        return

    if len(returned_images) != 4:
        print(
            f"\nERROR: Exactly 4 returned photos are required. "
            f"Found {len(returned_images)}."
        )
        return

    print("\n" + "-" * 65)
    print("Comparing 4 returned photos against 4 original photos...")
    print("-" * 65)

    comparison_start = time.perf_counter()

    try:
        verification = compare_four_images(
            original_images,
            returned_images
        )

        # Append verification results to the history CSV.
        file_exists = (
            os.path.exists(HISTORY_FILE)
            and os.path.getsize(HISTORY_FILE) > 0
        )

        with open(
            HISTORY_FILE,
            "a",
            newline="",
            encoding="utf-8"
        ) as file:
            writer = csv.writer(file)

            if not file_exists:
                writer.writerow([
                    "timestamp",
                    "returned_photo",
                    "best_original",
                    "similarity_percent",
                    "result",
                    "reason"
                ])

            for item in verification["results"]:
                original_index = item["best_original_index"]

                returned_index = item["returned_index"]
                returned_filename = os.path.basename(
                    returned_images[returned_index - 1]
                )

                writer.writerow([
                    datetime.now().isoformat(timespec="seconds"),
                    returned_filename,
                    original_index,
                    item["similarity_percent"],
                    item["result"],
                    item["reason"]
                ])

        print(f"\nVerification history saved to: {HISTORY_FILE}")

        # Display the final result if provided by image_matching.py.
        if "final_result" in verification:
            print(f"\nFINAL RESULT: {verification['final_result']}")
        elif "final_status" in verification:
            print(f"\nFINAL RESULT: {verification['final_status']}")

    except Exception as error:
        print(f"\nERROR during image verification: {error}")
        raise

    finally:
        comparison_seconds = time.perf_counter() - comparison_start
        print(f"IMAGE COMPARISON TIME: {comparison_seconds:.2f} seconds")


if __name__ == "__main__":
    main()
