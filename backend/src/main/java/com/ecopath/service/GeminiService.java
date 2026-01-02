package com.ecopath.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class GeminiService {

    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    public GeminiService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Normalisasi nama penyakit agar konsisten
     */
    private String normalizeDisease(String disease) {
        if (disease == null) return "Unknown";

        String normalized = disease.toUpperCase().trim();

        // Mapping berbagai variasi nama ke format standar
        if (normalized.contains("DBD") || normalized.contains("DEMAM BERDARAH") ||
                normalized.contains("DENGUE")) {
            return "DBD";
        } else if (normalized.contains("ISPA") || normalized.contains("INFEKSI SALURAN") ||
                normalized.contains("BATUK") || normalized.contains("PILEK")) {
            return "ISPA";
        } else if (normalized.contains("DIARE") || normalized.contains("MENCRET")) {
            return "Diare";
        } else if (normalized.contains("COVID") || normalized.contains("CORONA")) {
            return "COVID-19";
        } else if (normalized.contains("TIFOID") || normalized.contains("TIPES") ||
                normalized.contains("TYPHOID")) {
            return "Demam Tifoid";
        } else if (normalized.contains("MALARIA")) {
            return "Malaria";
        } else if (normalized.contains("PNEUMONIA")) {
            return "Pneumonia";
        } else if (normalized.contains("TUBERKULOSIS") || normalized.contains("TBC") ||
                normalized.contains("TB")) {
            return "Tuberkulosis";
        }

        return disease; // Return original jika tidak match
    }

    /**
     * SMART: Update existing report atau create new jika belum ada
     */
    public String processNurseReportSmart(String facilityId, String rawText) {
        try {
            System.out.println("Processing nurse report with Gemini AI...");
            System.out.println("Raw text: " + rawText);

            // 1. Prepare prompt untuk Gemini
            String prompt = "Analisis laporan kesehatan berikut dan ekstrak informasi dalam format JSON. " +
                    "Berikan HANYA JSON tanpa teks lain, dengan struktur: " +
                    "{\"disease\": \"nama penyakit\", \"severity\": \"Low/Medium/High/Critical\", \"patient_count\": angka}. " +
                    "Gunakan nama penyakit standar seperti: DBD, ISPA, Diare, COVID-19, Demam Tifoid, Malaria. " +
                    "Laporan: " + rawText;

            // 2. Build request body untuk Gemini API
            Map<String, Object> requestBody = new HashMap<>();
            Map<String, Object> contents = new HashMap<>();
            Map<String, String> parts = new HashMap<>();
            parts.put("text", prompt);
            contents.put("parts", new Object[]{parts});
            requestBody.put("contents", new Object[]{contents});

            // 3. Call Gemini API
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String urlWithKey = apiUrl + "?key=" + apiKey;

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    urlWithKey, HttpMethod.POST, entity, String.class);

            System.out.println("Gemini API Response: " + response.getStatusCode());

            // 4. Parse Gemini response
            JsonNode root = objectMapper.readTree(response.getBody());
            String aiResponse = root.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText();

            System.out.println("AI Response: " + aiResponse);

            // 5. Parse JSON dari AI response
            String cleanJson = aiResponse.replaceAll("```json|```", "").trim();
            JsonNode parsed = objectMapper.readTree(cleanJson);

            String diseaseRaw = parsed.path("disease").asText("Unknown");
            String disease = normalizeDisease(diseaseRaw); // NORMALIZE HERE
            String severity = parsed.path("severity").asText("Medium");
            int patientCount = parsed.path("patient_count").asInt(0);

            System.out.println("Extracted - Disease: " + diseaseRaw + " → " + disease +
                    ", Severity: " + severity +
                    ", Patients: " + patientCount);

            String reportDate = LocalDate.now().toString();

            // 6. CHECK: Apakah sudah ada report untuk facility + disease hari ini?
            String checkSql = "SELECT report_id, patient_count " +
                    "FROM ECOPATH_DB.PUBLIC.fact_nurse_reports " +
                    "WHERE facility_id = ? " +
                    "  AND disease_detected = ? " +
                    "  AND report_date = ?";

            List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                    checkSql, facilityId, disease, reportDate);

            if (!existing.isEmpty()) {
                // UPDATE existing record (tambahkan patient count)
                String existingReportId = (String) existing.get(0).get("REPORT_ID");
                int existingCount = ((Number) existing.get(0).get("PATIENT_COUNT")).intValue();
                int newTotal = existingCount + patientCount;

                String updateSql = "UPDATE ECOPATH_DB.PUBLIC.fact_nurse_reports " +
                        "SET patient_count = ?, " +
                        "    raw_text = raw_text || ' | ' || ?, " +
                        "    severity_level = ?, " +
                        "    created_at = CURRENT_TIMESTAMP() " +
                        "WHERE report_id = ?";

                jdbcTemplate.update(updateSql, newTotal, rawText, severity, existingReportId);

                System.out.println("Report UPDATED: " + existingReportId +
                        " (patient count: " + existingCount + " → " + newTotal + ")");

                return String.format("Report updated: %s (Total patients: %d → %d) - ID: %s",
                        disease, existingCount, newTotal, existingReportId);

            } else {
                // INSERT new record
                String reportId = "RPT-" + UUID.randomUUID().toString().substring(0, 8);

                String insertSql = "INSERT INTO ECOPATH_DB.PUBLIC.fact_nurse_reports " +
                        "(report_id, facility_id, report_date, raw_text, " +
                        "disease_detected, severity_level, patient_count, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP())";

                jdbcTemplate.update(insertSql, reportId, facilityId, reportDate,
                        rawText, disease, severity, patientCount);

                System.out.println("New report created: " + reportId);

                return String.format("New report created: %s (%s severity, %d patients) - ID: %s",
                        disease, severity, patientCount, reportId);
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    public String processNurseReport(String facilityId, String rawText) {
        return processNurseReportSmart(facilityId, rawText);
    }
}