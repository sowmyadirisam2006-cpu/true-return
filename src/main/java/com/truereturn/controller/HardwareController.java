
package com.truereturn.controller;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hardware")
public class HardwareController {

    private final Map<String, Map<String, Object>> latestReadings =
            new ConcurrentHashMap<>();

    // Receive RFID and weight data from ESP32
    @PostMapping("/data")
    public ResponseEntity<?> receiveHardwareData(
            @RequestBody Map<String, Object> request) {

        Object codeValue = request.get("productCode");
        Object uidValue = request.get("uid");
        Object weightValue = request.get("weight");

        if (codeValue == null || uidValue == null
                || weightValue == null) {
            return ResponseEntity.badRequest().body(
                    Map.of("error",
                            "productCode, uid and weight are required"));
        }

        String productCode = codeValue.toString().trim();
        String uid = uidValue.toString().trim().toUpperCase();

        if (productCode.isEmpty() || uid.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Product code and UID cannot be empty"));
        }

        double weight;

        try {
            weight = Double.parseDouble(weightValue.toString());

            if (!Double.isFinite(weight)) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Weight must be a valid number"));
        }

        Map<String, Object> reading = Map.of(
                "productCode", productCode,
                "uid", uid,
                "weight", weight,
                "receivedAt", Instant.now().toString()
        );

        latestReadings.put(productCode, reading);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Hardware reading received",
                "reading", reading
        ));
    }

    // Get the latest hardware reading for a product
    @GetMapping("/latest")
    public ResponseEntity<?> getLatestReading(
            @RequestParam String productCode) {

        Map<String, Object> reading =
                latestReadings.get(productCode.trim());

        if (reading == null) {
            return ResponseEntity.status(404).body(
                    Map.of("error",
                            "No hardware reading received for this product"));
        }

        return ResponseEntity.ok(reading);
    }
}
