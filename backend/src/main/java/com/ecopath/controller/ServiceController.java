package com.ecopath.controller;

import com.ecopath.service.GeminiService;
import com.ecopath.service.InventoryService;
import com.ecopath.service.RedistributionService;
import com.ecopath.service.WeatherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/services")
@CrossOrigin(origins = "*") // Allow Streamlit to access
public class ServiceController {

    @Autowired
    private WeatherService weatherService;

    @Autowired
    private GeminiService geminiService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RedistributionService redistributionService;

    /**
     * Fetch weather untuk 1 facility
     */
    @PostMapping("/weather/fetch")
    public Map<String, Object> fetchWeather(@RequestBody Map<String, Object> request) {
        try {
            String facilityId = (String) request.get("facilityId");
            double lat = ((Number) request.get("lat")).doubleValue();
            double lon = ((Number) request.get("lon")).doubleValue();

            String result = weatherService.fetchAndStoreWeather(facilityId, lat, lon);

            return Map.of(
                    "status", result.contains("Error") ? "FAILED" : "SUCCESS",
                    "message", result
            );
        } catch (Exception e) {
            return Map.of(
                    "status", "FAILED",
                    "error", e.getMessage()
            );
        }
    }

    /**
     * Fetch weather untuk semua facilities
     */
    @PostMapping("/weather/fetch-all")
    public Map<String, Object> fetchWeatherAll() {
        try {
            String result = weatherService.fetchWeatherForAllFacilities();
            return Map.of(
                    "status", "SUCCESS",
                    "message", result
            );
        } catch (Exception e) {
            return Map.of(
                    "status", "FAILED",
                    "error", e.getMessage()
            );
        }
    }

    @GetMapping("/weather/recent")
    public Map<String, Object> getRecentWeather() {
        try {
            String sql = "SELECT w.weather_id, f.facility_name, w.date, " +
                    "w.temperature_avg, w.humidity_avg, w.rainfall_mm, w.weather_condition " +
                    "FROM ECOPATH_DB.PUBLIC.fact_weather_data w " +
                    "JOIN ECOPATH_DB.PUBLIC.dim_health_facilities f " +
                    "  ON w.facility_id = f.facility_id " +
                    "ORDER BY w.created_at DESC LIMIT 10";

            List<Map<String, Object>> weatherData = jdbcTemplate.queryForList(sql);

            return Map.of(
                    "status", "SUCCESS",
                    "count", weatherData.size(),
                    "data", weatherData
            );

        } catch (Exception e) {
            return Map.of(
                    "status", "FAILED",
                    "error", e.getMessage()
            );
        }
    }

    /**
     * Process laporan perawat dengan AI
     */
    @PostMapping("/reports/process")
    public Map<String, Object> processReport(@RequestBody Map<String, String> request) {
        try {
            String facilityId = request.get("facilityId");
            String rawText = request.get("text");

            if (facilityId == null || rawText == null || rawText.trim().isEmpty()) {
                return Map.of(
                        "status", "FAILED",
                        "message", "facilityId and text are required"
                );
            }

            String result = geminiService.processNurseReportSmart(facilityId, rawText);

            return Map.of(
                    "status", result.contains("Error") ? "FAILED" : "SUCCESS",
                    "message", result
            );
        } catch (Exception e) {
            return Map.of(
                    "status", "FAILED",
                    "error", e.getMessage()
            );
        }
    }

    /**
     * Update stock
     */
    @PostMapping("/inventory/update")
    public Map<String, Object> updateStock(@RequestBody Map<String, Object> request) {
        try {
            // Validate input
            if (!request.containsKey("facilityId") || !request.containsKey("itemId") ||
                !request.containsKey("quantity") || !request.containsKey("type")) {
                return Map.of(
                        "status", "FAILED",
                        "message", "Missing required fields: facilityId, itemId, quantity, type"
                );
            }

            String facilityId = (String) request.get("facilityId");
            String itemId = (String) request.get("itemId");
            int quantity = ((Number) request.get("quantity")).intValue();
            String type = (String) request.get("type");

            // Validate type
            if (!type.equals("IN") && !type.equals("OUT")) {
                return Map.of(
                        "status", "FAILED",
                        "message", "Type must be 'IN' or 'OUT'"
                );
            }

            // Validate quantity
            if (quantity <= 0) {
                return Map.of(
                        "status", "FAILED",
                        "message", "Quantity must be greater than 0"
                );
            }

            String result = inventoryService.updateStock(facilityId, itemId, quantity, type);

            return Map.of(
                    "status", result.contains("Error") || result.contains("Failed") ? "FAILED" : "SUCCESS",
                    "message", result
            );

        } catch (Exception e) {
            return Map.of(
                    "status", "FAILED",
                    "error", e.getMessage()
            );
        }
    }

    /**
     * Detect stock anomalies
     */
    @GetMapping("/inventory/anomalies")
    public Map<String, Object> detectAnomalies() {
        try {
            Map<String, Object> anomalies = inventoryService.detectAnomalies();

            return Map.of(
                    "status", anomalies.containsKey("error") ? "FAILED" : "SUCCESS",
                    "data", anomalies
            );
        } catch (Exception e) {
            return Map.of(
                    "status", "FAILED",
                    "error", e.getMessage()
            );
        }
    }

    @GetMapping("/reports/summary")
    public Map<String, Object> getReportsSummary() {
        Map<String, Object> response = new HashMap<>();

        try {
            String sql = "SELECT facility_id, facility_name, disease_detected, " +
                    "severity_level, report_count, total_patients, " +
                    "last_report_date, last_updated " +
                    "FROM analytics_nurse_reports_summary " +
                    "ORDER BY last_updated DESC";

            List<Map<String, Object>> summary = jdbcTemplate.queryForList(sql);

            response.put("status", "SUCCESS");
            response.put("count", summary.size());
            response.put("data", summary);

        } catch (Exception e) {
            response.put("status", "FAILED");
            response.put("error", e.getMessage());
        }
        return response;
    }

    @GetMapping("/weather/test-url")
    public Map<String, Object> testWeatherUrl() {
        String testUrl = "https://api.openweathermap.org/data/2.5/weather?lat=-6.2088&lon=106.8456&appid=fad1d8600daf04af0c83ebb24bf20d18&units=metric";

        try {
            RestTemplate restTemplate = new RestTemplate();
            String response = restTemplate.getForObject(testUrl, String.class);

            return Map.of(
                    "status", "SUCCESS",
                    "message", "OpenWeather API is working!",
                    "url_tested", testUrl,
                    "response", response
            );

        } catch (Exception e) {
            return Map.of(
                    "status", "FAILED",
                    "error", e.getMessage(),
                    "url_tested", testUrl
            );
        }
    }

    /**
     * Generate redistribution recommendations - FIXED VERSION
     */
    @PostMapping("/redistribution/generate")
    public Map<String, Object> generateRedistributions() {
        try {
            Map<String, Object> result = redistributionService.generateRecommendations();
            
            // Ensure proper response format
            if (result.containsKey("error")) {
                return Map.of(
                        "status", "FAILED",
                        "error", result.get("error")
                );
            }
            
            return Map.of(
                    "status", "SUCCESS",
                    "data", result
            );
            
        } catch (Exception e) {
            return Map.of(
                    "status", "FAILED",
                    "error", e.getMessage()
            );
        }
    }

    /**
     * Get pending redistributions - FIXED VERSION
     */
    @GetMapping("/redistribution/pending")
    public Map<String, Object> getPendingRedistributions() {
        try {
            List<Map<String, Object>> recommendations =
                    redistributionService.getPendingRecommendations();

            return Map.of(
                    "status", "SUCCESS",
                    "count", recommendations.size(),
                    "recommendations", recommendations
            );
        } catch (Exception e) {
            return Map.of(
                    "status", "FAILED",
                    "error", e.getMessage()
            );
        }
    }

    /**
     * Approve redistribution
     */
    @PostMapping("/redistribution/approve")
    public Map<String, Object> approveRedistribution(@RequestBody Map<String, String> request) {
        try {
            String recId = request.get("recommendationId");
            String approvedBy = request.get("approvedBy");

            if (recId == null || approvedBy == null) {
                return Map.of(
                        "status", "FAILED",
                        "message", "recommendationId and approvedBy are required"
                );
            }

            String result = redistributionService.approveRecommendation(recId, approvedBy);

            return Map.of(
                    "status", result.contains("Error") || result.contains("not found") ? "FAILED" : "SUCCESS",
                    "message", result
            );
        } catch (Exception e) {
            return Map.of(
                    "status", "FAILED",
                    "error", e.getMessage()
            );
        }
    }

    /**
     * Get approved redistributions
     */
    @GetMapping("/redistribution/approved")
    public Map<String, Object> getApprovedRedistributions() {
        try {
            String sql = "SELECT r.recommendation_id, " +
                    "       fs.facility_name as source_facility, " +
                    "       fd.facility_name as destination_facility, " +
                    "       m.item_name, " +
                    "       r.quantity_to_move, " +
                    "       r.priority_score, " +
                    "       r.status, " +
                    "       r.approved_by, " +
                    "       r.approved_at, " +
                    "       r.created_at " +
                    "FROM ECOPATH_DB.PUBLIC.fact_redistribution_recommendations r " +
                    "JOIN ECOPATH_DB.PUBLIC.dim_health_facilities fs " +
                    "  ON r.source_facility_id = fs.facility_id " +
                    "JOIN ECOPATH_DB.PUBLIC.dim_health_facilities fd " +
                    "  ON r.destination_facility_id = fd.facility_id " +
                    "JOIN ECOPATH_DB.PUBLIC.dim_medical_items m " +  // FIX: Ganti dari dim_inventory_items
                    "  ON r.item_id = m.item_id " +
                    "WHERE r.status = 'APPROVED' " +
                    "ORDER BY r.approved_at DESC " +
                    "LIMIT 50";

            List<Map<String, Object>> redistributions = jdbcTemplate.queryForList(sql);

            return Map.of(
                    "status", "SUCCESS",
                    "count", redistributions.size(),
                    "data", redistributions
            );
        } catch (Exception e) {
            return Map.of(
                    "status", "FAILED",
                    "error", e.getMessage()
            );
        }
    }

    /**
     * Get rejected redistributions
     */
    @GetMapping("/redistribution/rejected")
    public Map<String, Object> getRejectedRedistributions() {
        try {
            String sql = "SELECT r.recommendation_id, " +
                    "       fs.facility_name as source_facility, " +
                    "       fd.facility_name as destination_facility, " +
                    "       i.item_name, " +
                    "       r.quantity_to_move, " +
                    "       r.priority_score, " +
                    "       r.status, " +
                    "       r.approved_by, " +
                    "       r.approved_at, " +
                    "       r.created_at " +
                    "FROM ECOPATH_DB.PUBLIC.fact_redistribution_recommendations r " +
                    "JOIN ECOPATH_DB.PUBLIC.dim_health_facilities fs " +
                    "  ON r.source_facility_id = fs.facility_id " +
                    "JOIN ECOPATH_DB.PUBLIC.dim_health_facilities fd " +
                    "  ON r.destination_facility_id = fd.facility_id " +
                    "JOIN ECOPATH_DB.PUBLIC.dim_inventory_items i " +
                    "  ON r.item_id = i.item_id " +
                    "WHERE r.status = 'REJECTED' " +
                    "ORDER BY r.created_at DESC";

            List<Map<String, Object>> redistributions = jdbcTemplate.queryForList(sql);

            return Map.of(
                    "status", "SUCCESS",
                    "count", redistributions.size(),
                    "data", redistributions
            );
        } catch (Exception e) {
            return Map.of(
                    "status", "FAILED",
                    "error", e.getMessage()
            );
        }
    }

    /**
     * Dashboard summary
     */
    @GetMapping("/dashboard/summary")
    public Map<String, Object> getDashboardSummary() {
        Map<String, Object> response = new HashMap<>();

        try {
            // Get anomalies
            Map<String, Object> anomalies = inventoryService.detectAnomalies();
            int totalIssues = (int) anomalies.getOrDefault("total_issues", 0);

            // Get pending redistributions
            List<Map<String, Object>> pending = redistributionService.getPendingRecommendations();

            response.put("status", "SUCCESS");
            response.put("summary", Map.of(
                    "stock_issues", totalIssues,
                    "pending_redistributions", pending.size()
            ));
            response.put("anomalies", anomalies);
            response.put("pending_redistributions", pending);

        } catch (Exception e) {
            response.put("status", "FAILED");
            response.put("error", e.getMessage());
        }

        return response;
    }
}