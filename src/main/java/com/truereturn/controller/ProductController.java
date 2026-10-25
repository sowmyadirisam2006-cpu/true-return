package com.truereturn.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.truereturn.dto.VerificationResponse;
import com.truereturn.model.Product;
import com.truereturn.model.ReturnRecord;
import com.truereturn.repository.ProductRepository;
import com.truereturn.repository.ReturnRecordRepository;

import jakarta.servlet.http.HttpSession;

import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final long MAX_IMAGE_SIZE = 15L * 1024 * 1024;

    // A difference of 10 grams or less is accepted.
    private static final double WEIGHT_TOLERANCE_GRAMS = 10.0;

    private final ProductRepository productRepository;
    private final ReturnRecordRepository returnRecordRepository;
    private final RestClient mlClient;

    public ProductController(
            ProductRepository productRepository,
            ReturnRecordRepository returnRecordRepository) {

        this.productRepository = productRepository;
        this.returnRecordRepository = returnRecordRepository;

        this.mlClient = RestClient.builder()
                .baseUrl("http://127.0.0.1:5000")
                .build();
    }

    // ============================================
    // TEST ENDPOINTS
    // ============================================

    @GetMapping("/test")
    public String test() {
        return "TrueReturn Product Verification System is working!";
    }

    @GetMapping
    public String productsTest() {
        return "Products endpoint is working!";
    }

    // ============================================
    // REGISTER PRODUCT - ADMIN ONLY
    // ============================================

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> addProduct(
            @RequestParam String name,
            @RequestParam String description,
            @RequestParam double price,
            @RequestParam double originalWeight,
            @RequestParam String productCode,
            @RequestParam String category,
            @RequestParam("originalPhoto1") MultipartFile originalPhoto1,
            @RequestParam("originalPhoto2") MultipartFile originalPhoto2,
            @RequestParam("originalPhoto3") MultipartFile originalPhoto3,
            @RequestParam("originalPhoto4") MultipartFile originalPhoto4,
            HttpSession session) {

        if (!isAdmin(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Access denied. Admin only.");
        }

        if (name == null || name.isBlank()
                || description == null || description.isBlank()
                || productCode == null || productCode.isBlank()
                || category == null || category.isBlank()
                || !Double.isFinite(price) || price <= 0
                || !Double.isFinite(originalWeight)
                || originalWeight <= 0) {

            return ResponseEntity.badRequest()
                    .body("Please provide valid product details and original weight.");
        }

        String normalizedCode = productCode.trim();

        if (normalizedCode.length() > 100) {
            return ResponseEntity.badRequest()
                    .body("Product code must not exceed 100 characters.");
        }

        if (productRepository.findByProductCode(normalizedCode).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Product code already exists.");
        }

        List<MultipartFile> photos = List.of(
                originalPhoto1,
                originalPhoto2,
                originalPhoto3,
                originalPhoto4
        );

        for (MultipartFile photo : photos) {
            String error = validateImage(photo);

            if (error != null) {
                return ResponseEntity.badRequest().body(error);
            }
        }

        try {
            Product product = new Product();

            product.setName(name.trim());
            product.setDescription(description.trim());
            product.setPrice(price);
            product.setOriginalWeight(originalWeight);
            product.setProductCode(normalizedCode);
            product.setCategory(category.trim());
            product.setStatus("ACTIVE");

            product.setOriginalPhoto1(saveOriginalImage(originalPhoto1));
            product.setOriginalPhoto2(saveOriginalImage(originalPhoto2));
            product.setOriginalPhoto3(saveOriginalImage(originalPhoto3));
            product.setOriginalPhoto4(saveOriginalImage(originalPhoto4));

            Product savedProduct = productRepository.save(product);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(savedProduct);

        } catch (DataIntegrityViolationException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Product code already exists.");

        } catch (IOException exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unable to save original product photos.");
        }
    }

    // ============================================
    // VERIFY PRODUCT
    // ============================================

    @GetMapping("/verify/{productCode}")
    public VerificationResponse verifyProduct(
            @PathVariable String productCode) {

        Optional<Product> optional =
                productRepository.findByProductCode(productCode.trim());

        if (optional.isEmpty()) {
            return new VerificationResponse(
                    false,
                    "Product not found",
                    productCode,
                    null,
                    0,
                    null
            );
        }

        Product product = optional.get();

        boolean active =
                "ACTIVE".equalsIgnoreCase(product.getStatus());

        VerificationResponse response = new VerificationResponse(
                active,
                active
                        ? "Product is genuine"
                        : "Product status: " + product.getStatus(),
                product.getProductCode(),
                product.getName(),
                product.getPrice(),
                product.getStatus()
        );

        response.setCategory(product.getCategory());

        return response;
    }

    // ============================================
    // UPDATE PRODUCT STATUS - DELIVERY ONLY
    // ============================================

    @PutMapping("/status/{productCode}")
    public ResponseEntity<String> updateStatus(
            @PathVariable String productCode,
            @RequestParam("status") String status,
            HttpSession session) {

        if (!isDelivery(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Access denied. Delivery only.");
        }

        if (status == null || status.isBlank()) {
            return ResponseEntity.badRequest()
                    .body("Product status is required.");
        }

        String normalized = status.trim().toUpperCase(Locale.ROOT);

        if (!"ACTIVE".equals(normalized)
                && !"RETURNED".equals(normalized)) {

            return ResponseEntity.badRequest()
                    .body("Invalid product status.");
        }

        Optional<Product> optional =
                productRepository.findByProductCode(productCode.trim());

        if (optional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Product not found.");
        }

        Product product = optional.get();
        product.setStatus(normalized);
        productRepository.save(product);

        return ResponseEntity.ok(
                "Product status updated to " + normalized
        );
    }

    // ============================================
    // RETURN PRODUCT
    // AI + RFID + AUTOMATIC WEIGHT COMPARISON
    // ============================================

    @PostMapping(
            value = "/return/{productCode}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @Transactional
    public ResponseEntity<String> returnProduct(
            @PathVariable String productCode,
            @RequestParam String reason,
            @RequestParam String category,
            @RequestParam("photo1") MultipartFile photo1,
            @RequestParam("photo2") MultipartFile photo2,
            @RequestParam("photo3") MultipartFile photo3,
            @RequestParam("photo4") MultipartFile photo4,
            @RequestParam(required = false) String mlResult,
            @RequestParam(required = false) String rfidResult,
            @RequestParam(required = false) String weightResult,
            @RequestParam(required = false) String finalResult,
            @RequestParam(required = false) String remark,
            @RequestParam(required = false) String approvalStatus,
            @RequestParam(required = false) Double capturedWeight,
            @RequestParam(required = false) String capturedWeightUnit,
            HttpSession session) {

        if (!isAdmin(session) && !isDelivery(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Access denied. Please log in as Admin or Delivery.");
        }

        if (productCode == null || productCode.isBlank()
                || reason == null || reason.isBlank()
                || category == null || category.isBlank()) {

            return ResponseEntity.badRequest()
                    .body("Product code, return reason and category are required.");
        }

        // Use only the fixed reading captured by the delivery user.
        // Never use the changing live hardware reading for comparison.
        if (capturedWeight == null
                || !Double.isFinite(capturedWeight)
                || capturedWeight < 0) {

            return ResponseEntity.badRequest()
                    .body("Capture a valid hardware weight before submitting.");
        }

        String unit = capturedWeightUnit == null
                ? "g"
                : capturedWeightUnit.trim();

        if (!"g".equalsIgnoreCase(unit)) {
            return ResponseEntity.badRequest()
                    .body("Captured weight must be submitted in grams (g).");
        }

        Optional<Product> optional =
                productRepository.findByProductCode(productCode.trim());

        if (optional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Product not found.");
        }

        Product product = optional.get();

        if (product.getOriginalWeight() == null
                || !Double.isFinite(product.getOriginalWeight())
                || product.getOriginalWeight() <= 0) {

            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("The product has no valid original registered weight.");
        }

        if (!"ACTIVE".equalsIgnoreCase(product.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("This product is not active and cannot be returned again.");
        }

        List<MultipartFile> returnedPhotos = List.of(
                photo1,
                photo2,
                photo3,
                photo4
        );

        for (MultipartFile photo : returnedPhotos) {
            String error = validateImage(photo);

            if (error != null) {
                return ResponseEntity.badRequest().body(error);
            }
        }

        List<String> originalPaths = List.of(
                safePath(product.getOriginalPhoto1()),
                safePath(product.getOriginalPhoto2()),
                safePath(product.getOriginalPhoto3()),
                safePath(product.getOriginalPhoto4())
        );

        for (String path : originalPaths) {
            if (path.isBlank()
                    || !Files.isRegularFile(Paths.get(path))) {

                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("Original product photos are missing. "
                                + "The product must have four saved original photos.");
            }
        }

        // ============================================
        // AUTOMATIC WEIGHT VERIFICATION
        // ============================================

        double originalWeight = product.getOriginalWeight();

        double weightDifference =
                Math.abs(originalWeight - capturedWeight);

        boolean weightMatched =
                weightDifference <= WEIGHT_TOLERANCE_GRAMS;

        // Ignore any manually submitted weightResult.
        // The backend is the source of truth.
        String normalizedWeight =
                weightMatched ? "MATCHED" : "MISMATCHED";

        String normalizedRfid = normalizeResult(rfidResult);

        String returnPath1 = null;
        String returnPath2 = null;
        String returnPath3 = null;
        String returnPath4 = null;

        try {
            returnPath1 = saveReturnImage(photo1);
            returnPath2 = saveReturnImage(photo2);
            returnPath3 = saveReturnImage(photo3);
            returnPath4 = saveReturnImage(photo4);

            // ============================================
            // AI IMAGE VERIFICATION
            // ============================================

            Map<?, ?> mlResponse = callMlApi(
                    originalPaths,
                    List.of(
                            returnPath1,
                            returnPath2,
                            returnPath3,
                            returnPath4
                    )
            );

            if (!Boolean.TRUE.equals(mlResponse.get("success"))) {
                deleteFilesQuietly(
                        returnPath1,
                        returnPath2,
                        returnPath3,
                        returnPath4
                );

                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body("ML verification failed. No return decision was saved.");
            }

            Object rawResult = mlResponse.get("finalResult");

            if (!(rawResult instanceof String)) {
                deleteFilesQuietly(
                        returnPath1,
                        returnPath2,
                        returnPath3,
                        returnPath4
                );

                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body("ML API returned an invalid verification result.");
            }

            String modelResult =
                    ((String) rawResult).trim().toUpperCase(Locale.ROOT);

            boolean mlMatched =
                    "MATCH".equals(modelResult)
                    || "MATCHED".equals(modelResult)
                    || "VERIFIED".equals(modelResult);

            boolean mlSuspicious =
                    "SUSPICIOUS".equals(modelResult)
                    || "NO_MATCH".equals(modelResult)
                    || "MISMATCH".equals(modelResult)
                    || "MISMATCHED".equals(modelResult);

            // ============================================
            // RFID VERIFICATION
            // ============================================

            boolean rfidMismatch =
                    "MISMATCHED".equals(normalizedRfid)
                    || "MISMATCH".equals(normalizedRfid)
                    || "FAILED".equals(normalizedRfid);

            boolean rfidMatched =
                    "MATCHED".equals(normalizedRfid)
                    || "MATCH".equals(normalizedRfid);

            // ============================================
            // FINAL RESULT
            // ============================================

            String computedFinalResult;
            String decisionMessage;

            if (mlSuspicious || rfidMismatch || !weightMatched) {

                computedFinalResult = "NOT VERIFIED";

                decisionMessage = String.format(
                        Locale.ROOT,
                        "Verification failed. Original weight: %.2f g; "
                                + "captured return weight: %.2f g; "
                                + "difference: %.2f g; allowed difference: %.2f g.",
                        originalWeight,
                        capturedWeight,
                        weightDifference,
                        WEIGHT_TOLERANCE_GRAMS
                );

            } else if (mlMatched && rfidMatched && weightMatched) {

                computedFinalResult = "VERIFIED";

                decisionMessage = String.format(
                        Locale.ROOT,
                        "AI, RFID and weight checks passed. "
                                + "Original weight: %.2f g; "
                                + "captured weight: %.2f g; "
                                + "difference: %.2f g.",
                        originalWeight,
                        capturedWeight,
                        weightDifference
                );

            } else {

                // Unknown or unavailable AI/RFID results cannot
                // be treated as successful verification.
                computedFinalResult = "INCOMPLETE";

                decisionMessage =
                        "Verification incomplete. Check the AI and RFID results.";
            }

            String computedApprovalStatus =
                    "VERIFIED".equals(computedFinalResult)
                            ? "APPROVED"
                            : "PENDING";

            Object reasonsObject = mlResponse.get("reasons");

            String modelReasons = reasonsObject == null
                    ? ""
                    : reasonsObject.toString();

            String finalRemark = remark == null
                    ? ""
                    : remark.trim();

            String weightNote = String.format(
                    Locale.ROOT,
                    "Original weight: %.2f g; captured weight: %.2f g; "
                            + "difference: %.2f g; weight result: %s.",
                    originalWeight,
                    capturedWeight,
                    weightDifference,
                    normalizedWeight
            );

            finalRemark = finalRemark.isBlank()
                    ? weightNote
                    : finalRemark + " " + weightNote;

            finalRemark += " " + decisionMessage;

            if (mlSuspicious && !modelReasons.isBlank()) {
                finalRemark += " ML reasons: " + modelReasons;
            }

            if ("INCOMPLETE".equals(computedFinalResult)) {
                finalRemark += " RFID result: "
                        + (normalizedRfid.isBlank()
                                ? "NOT AVAILABLE"
                                : normalizedRfid)
                        + "; ML result: " + modelResult + ".";
            }

            // ============================================
            // SAVE RETURN RECORD
            // ============================================

            ReturnRecord record = new ReturnRecord(
                    product.getProductCode(),
                    reason.trim()
            );

            record.setCategory(category.trim());
            record.setPhoto1(returnPath1);
            record.setPhoto2(returnPath2);
            record.setPhoto3(returnPath3);
            record.setPhoto4(returnPath4);
            record.setMlResult(modelResult);

            record.setRfidResult(
                    normalizedRfid.isBlank()
                            ? null
                            : normalizedRfid
            );

            // Store the backend-calculated weight result.
            record.setWeightResult(normalizedWeight);
            record.setFinalResult(computedFinalResult);
            record.setRemark(finalRemark);
            record.setApprovalStatus(computedApprovalStatus);

            returnRecordRepository.save(record);

            // Mark the product as RETURNED only when every required
            // verification check passes. Failed or incomplete returns
            // must not automatically be marked as successful returns.
            if ("VERIFIED".equals(computedFinalResult)) {
                product.setStatus("RETURNED");
                productRepository.save(product);
            }

            return ResponseEntity.ok(
                    "Return verification completed. "
                            + "Result: " + computedFinalResult
                            + ". Approval: " + computedApprovalStatus
                            + ". " + weightNote
                            + " " + decisionMessage
            );

        } catch (IOException exception) {
            deleteFilesQuietly(
                    returnPath1,
                    returnPath2,
                    returnPath3,
                    returnPath4
            );

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unable to save return photos.");

        } catch (RestClientException exception) {
            deleteFilesQuietly(
                    returnPath1,
                    returnPath2,
                    returnPath3,
                    returnPath4
            );

            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("Unable to contact the Python ML service. "
                            + "The return was not approved.");
        }
    }

    // ============================================
    // NORMALIZE RESULT LABELS
    // ============================================

    private String normalizeResult(String value) {
        return value == null
                ? ""
                : value.trim().toUpperCase(Locale.ROOT);
    }

    // ============================================
    // CALL PYTHON ML API
    // ============================================

    private Map<?, ?> callMlApi(
            List<String> originalPaths,
            List<String> returnedPaths) {

        if (originalPaths == null || originalPaths.size() != 4
                || returnedPaths == null || returnedPaths.size() != 4) {

            throw new RestClientException(
                    "Exactly four original and four returned photos are required."
            );
        }

        MultiValueMap<String, Object> body =
                new LinkedMultiValueMap<>();

        for (String path : originalPaths) {
            FileSystemResource image = new FileSystemResource(path);

            if (!image.exists() || !image.isReadable()) {
                throw new RestClientException(
                        "An original product photo is missing or unreadable."
                );
            }

            body.add("original_images", image);
        }

        for (String path : returnedPaths) {
            FileSystemResource image = new FileSystemResource(path);

            if (!image.exists() || !image.isReadable()) {
                throw new RestClientException(
                        "A returned product photo is missing or unreadable."
                );
            }

            body.add("returned_images", image);
        }

        Map<?, ?> response = mlClient.post()
                .uri("/api/verify")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(Map.class);

        if (response == null) {
            throw new RestClientException("Empty response from ML API.");
        }

        return response;
    }

    // ============================================
    // SAVE ORIGINAL PRODUCT IMAGE
    // ============================================

    private String saveOriginalImage(MultipartFile file)
            throws IOException {

        validateImageForSaving(file);

        Path directory = Paths.get("uploads", "originals")
                .toAbsolutePath().normalize();

        Files.createDirectories(directory);

        Path destination = directory
                .resolve(UUID.randomUUID() + getImageExtension(file))
                .normalize();

        if (!destination.startsWith(directory)) {
            throw new IOException("Invalid upload path.");
        }

        file.transferTo(destination);

        return destination.toString();
    }

    // ============================================
    // SAVE RETURN IMAGE
    // ============================================

    private String saveReturnImage(MultipartFile file)
            throws IOException {

        validateImageForSaving(file);

        Path directory = Paths.get("uploads", "returns")
                .toAbsolutePath().normalize();

        Files.createDirectories(directory);

        Path destination = directory
                .resolve(UUID.randomUUID() + getImageExtension(file))
                .normalize();

        if (!destination.startsWith(directory)) {
            throw new IOException("Invalid upload path.");
        }

        file.transferTo(destination);

        return destination.toString();
    }

    // ============================================
    // IMAGE VALIDATION
    // ============================================

    private String validateImage(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            return "All four photos are required.";
        }

        if (!isAllowedImage(file)) {
            return "Only JPG, PNG, or WEBP images are allowed.";
        }

        if (file.getSize() > MAX_IMAGE_SIZE) {
            return "Each image must be 15 MB or smaller.";
        }

        return null;
    }

    private void validateImageForSaving(MultipartFile file)
            throws IOException {

        String error = validateImage(file);

        if (error != null) {
            throw new IOException(error);
        }
    }

    private boolean isAllowedImage(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            return false;
        }

        String type = file.getContentType();

        return type != null
                && (MediaType.IMAGE_JPEG_VALUE.equalsIgnoreCase(type)
                || MediaType.IMAGE_PNG_VALUE.equalsIgnoreCase(type)
                || "image/webp".equalsIgnoreCase(type));
    }

    private String getImageExtension(MultipartFile file)
            throws IOException {

        String type = file.getContentType();

        if (type == null) {
            throw new IOException("Missing image content type.");
        }

        switch (type.toLowerCase(Locale.ROOT)) {
            case "image/jpeg":
                return ".jpg";
            case "image/png":
                return ".png";
            case "image/webp":
                return ".webp";
            default:
                throw new IOException("Unsupported image type.");
        }
    }

    private String safePath(String path) {
        return path == null ? "" : path;
    }

    private void deleteFilesQuietly(String... filePaths) {

        for (String filePath : filePaths) {
            if (filePath == null || filePath.isBlank()) {
                continue;
            }

            try {
                Files.deleteIfExists(Paths.get(filePath));
            } catch (IOException ignored) {
                // Do not hide the original error.
            }
        }
    }

    // ============================================
    // RETURN HISTORY - ADMIN ONLY
    // ============================================

    @GetMapping("/returns")
    public ResponseEntity<?> getReturnHistory(HttpSession session) {

        if (!isAdmin(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Access denied. Admin only.");
        }

        return ResponseEntity.ok(returnRecordRepository.findAll());
    }

    // ============================================
    // ALL PRODUCTS - ADMIN ONLY
    // ============================================

    @GetMapping("/all")
    public ResponseEntity<?> getAllProducts(HttpSession session) {

        if (!isAdmin(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Access denied. Admin only.");
        }

        return ResponseEntity.ok(productRepository.findAll());
    }

    // ============================================
    // ROLE CHECKS
    // ============================================

    private boolean isAdmin(HttpSession session) {

        if (session == null) {
            return false;
        }

        Object role = session.getAttribute("role");

        return role != null
                && "ADMIN".equalsIgnoreCase(role.toString());
    }

    private boolean isDelivery(HttpSession session) {

        if (session == null) {
            return false;
        }

        Object role = session.getAttribute("role");

        return role != null
                && "DELIVERY".equalsIgnoreCase(role.toString());
    }
}