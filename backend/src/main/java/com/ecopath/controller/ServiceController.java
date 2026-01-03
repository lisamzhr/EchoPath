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
        String facilityId = (String) request.get("facilityId");
        double lat = ((Number) request.get("lat")).doubleValue();
        double lon = ((Number) request.get("lon")).doubleValue();

        String result = weatherService.fetchAndStoreWeather(facilityId, lat, lon);

        return Map.of(
                "status", result.contains("Error") ? "FAILED" : "SUCCESS",
                "message", result
        );
    }

    /**
     * Fetch weather untuk semua facilities
     */
    @PostMapping("/weather/fetch-all")
    public Map<String, Object> fetchWeatherAll() {
        String result = weatherService.fetchWeatherForAllFacilities();

        return Map.of(
                "status", "SUCCESS",
                "message", result
        );
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

            return Map.of(
                    "status", "SUCCESS",
                    "data", "weather data"
            );

        } catch (Exception e) {
            return Map.of("status", "FAILED", "error", e.getMessage());
        }
    }

    /**
     * Process laporan perawat dengan AI
     */
    @PostMapping("/reports/process")
    public Map<String, Object> processReport(@RequestBody Map<String, String> request) {
        String facilityId = request.get("facilityId");
        String rawText = request.get("text");

        // Gunakan smart version yang bisa update existing report
        String result = geminiService.processNurseReportSmart(facilityId, rawText);

        return Map.of(
                "status", result.contains("Error") ? "FAILED" : "SUCCESS",
                "message", result
        );
    }

    /**
     * Update stock
     */
    @PostMapping("/inventory/update")
    public Map<String, Object> updateStock(@RequestBody Map<String, Object> request) {
        String facilityId = (String) request.get("facilityId");
        String itemId = (String) request.get("itemId");
        int quantity = ((Number) request.get("quantity")).intValue();
        String type = (String) request.get("type"); // "IN" or "OUT"

        String result = inventoryService.updateStock(facilityId, itemId, quantity, type);

        return Map.of(
                "status", result.contains("Error") ? "FAILED" : "SUCCESS",
                "message", result
        );
    }

    /**
     * Detect stock anomalies
     */
    @GetMapping("/inventory/anomalies")
    public Map<String, Object> detectAnomalies() {
        Map<String, Object> anomalies = inventoryService.detectAnomalies();

        return Map.of(
                "status", anomalies.containsKey("error") ? "FAILED" : "SUCCESS",
                "data", anomalies
        );
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
        // Hardcode test
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
    @PostMapping("/redistribution/generate")
    public Map<String, Object> generateRedistributions() {
        return redistributionService.generateRecommendations();
    }

    @GetMapping("/redistribution/pending")
    public Map<String, Object> getPendingRedistributions() {
        List<Map<String, Object>> recommendations =
                redistributionService.getPendingRecommendations();

        return Map.of(
                "status", "SUCCESS",
                "count", recommendations.size(),
                "recommendations", recommendations
        );
    }

    @PostMapping("/redistribution/approve")
    public Map<String, Object> approveRedistribution(@RequestBody Map<String, String> request) {
        String recId = request.get("recommendationId");
        String approvedBy = request.get("approvedBy");

        String result = redistributionService.approveRecommendation(recId, approvedBy);

        return Map.of(
                "status", result.contains("Error") || result.contains("not found") ? "FAILED" : "SUCCESS",
                "message", result
        );
    }

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