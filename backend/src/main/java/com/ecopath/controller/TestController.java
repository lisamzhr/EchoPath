package com.ecopath.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/test")
public class TestController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/health")
    public Map<String, Object> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "EcoPath Backend");
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }

    @GetMapping("/snowflake")
    public Map<String, Object> testSnowflake() {
        Map<String, Object> response = new HashMap<>();

        try {
            String sql = "SELECT CURRENT_VERSION() as version";
            Map<String, Object> result = jdbcTemplate.queryForMap(sql);

            response.put("status", "SUCCESS");
            response.put("message", "Snowflake connected!");
            response.put("version", result.get("VERSION"));

        } catch (Exception e) {
            response.put("status", "FAILED");
            response.put("error", e.getMessage());
        }

        return response;
    }

    @GetMapping("/facilities")
    public Map<String, Object> getFacilities() {
        Map<String, Object> response = new HashMap<>();

        try {
            String sql = "SELECT facility_id, facility_name, district, province " +
                    "FROM dim_health_facilities LIMIT 5";

            List<Map<String, Object>> facilities = jdbcTemplate.queryForList(sql);

            response.put("status", "SUCCESS");
            response.put("count", facilities.size());
            response.put("data", facilities);

            System.out.println("Fetched " + facilities.size() + " facilities from Snowflake");

        } catch (Exception e) {
            response.put("status", "FAILED");
            response.put("error", e.getMessage());
        }

        return response;
    }

    @GetMapping("/inventory")
    public Map<String, Object> getInventory() {
        Map<String, Object> response = new HashMap<>();

        try {
            String sql = "SELECT i.inventory_id, f.facility_name, m.item_name, " +
                    "i.current_stock, i.min_stock_threshold " +
                    "FROM ECOPATH_DB.PUBLIC.fact_inventory i " +
                    "JOIN ECOPATH_DB.PUBLIC.dim_health_facilities f " +
                    "  ON i.facility_id = f.facility_id " +
                    "JOIN ECOPATH_DB.PUBLIC.dim_medical_items m " +
                    "  ON i.item_id = m.item_id " +
                    "LIMIT 10";

            List<Map<String, Object>> inventory = jdbcTemplate.queryForList(sql);

            response.put("status", "SUCCESS");
            response.put("count", inventory.size());
            response.put("data", inventory);

            System.out.println("Fetched " + inventory.size() + " inventory records");

        } catch (Exception e) {
            response.put("status", "FAILED");
            response.put("error", e.getMessage());
            e.printStackTrace();
        }

        return response;
    }

    @GetMapping("/weather")
    public Map<String, Object> getWeather() {
        Map<String, Object> response = new HashMap<>();

        try {
            String sql = "SELECT w.weather_id, f.facility_name, w.date, " +
                    "w.temperature_avg, w.humidity_avg, w.rainfall_mm, w.weather_condition " +
                    "FROM ECOPATH_DB.PUBLIC.fact_weather_data w " +
                    "JOIN ECOPATH_DB.PUBLIC.dim_health_facilities f " +
                    "  ON w.facility_id = f.facility_id " +
                    "ORDER BY w.date DESC LIMIT 10";

            List<Map<String, Object>> weather = jdbcTemplate.queryForList(sql);

            response.put("status", "SUCCESS");
            response.put("count", weather.size());
            response.put("data", weather);

        } catch (Exception e) {
            response.put("status", "FAILED");
            response.put("error", e.getMessage());
        }

        return response;
    }

    @GetMapping("/reports")
    public Map<String, Object> getNurseReports() {
        Map<String, Object> response = new HashMap<>();

        try {
            String sql = "SELECT r.report_id, f.facility_name, r.report_date, " +
                    "r.raw_text, r.disease_detected, r.severity_level, r.patient_count " +
                    "FROM ECOPATH_DB.PUBLIC.fact_nurse_reports r " +
                    "JOIN ECOPATH_DB.PUBLIC.dim_health_facilities f " +
                    "  ON r.facility_id = f.facility_id " +
                    "ORDER BY r.report_date DESC";

            List<Map<String, Object>> reports = jdbcTemplate.queryForList(sql);

            response.put("status", "SUCCESS");
            response.put("count", reports.size());
            response.put("data", reports);

        } catch (Exception e) {
            response.put("status", "FAILED");
            response.put("error", e.getMessage());
        }

        return response;
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        Map<String, Object> response = new HashMap<>();

        try {
            String sql = "SELECT " +
                    "(SELECT COUNT(*) FROM ECOPATH_DB.PUBLIC.dim_health_facilities) as total_facilities, " +
                    "(SELECT COUNT(*) FROM ECOPATH_DB.PUBLIC.dim_medical_items) as total_items, " +
                    "(SELECT COUNT(*) FROM ECOPATH_DB.PUBLIC.fact_inventory) as total_inventory, " +
                    "(SELECT COUNT(*) FROM ECOPATH_DB.PUBLIC.fact_stock_transactions) as total_transactions, " +
                    "(SELECT COUNT(*) FROM ECOPATH_DB.PUBLIC.fact_weather_data) as total_weather, " +
                    "(SELECT COUNT(*) FROM ECOPATH_DB.PUBLIC.fact_nurse_reports) as total_reports";

            Map<String, Object> stats = jdbcTemplate.queryForMap(sql);

            response.put("status", "SUCCESS");
            response.put("statistics", stats);

        } catch (Exception e) {
            response.put("status", "FAILED");
            response.put("error", e.getMessage());
        }

        return response;
    }

    @GetMapping("/tables")
    public Map<String, Object> listTables() {
        Map<String, Object> response = new HashMap<>();

        try {
            String sql = "SHOW TABLES IN ECOPATH_DB.PUBLIC";
            List<Map<String, Object>> tables = jdbcTemplate.queryForList(sql);

            response.put("status", "SUCCESS");
            response.put("count", tables.size());
            response.put("tables", tables);

        } catch (Exception e) {
            response.put("status", "FAILED");
            response.put("error", e.getMessage());
        }

        return response;
    }
}