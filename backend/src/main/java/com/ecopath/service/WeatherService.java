package com.ecopath.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.UUID;

@Service
public class WeatherService {

    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${weather.api.key}")
    private String apiKey;

    @Value("${weather.api.url}")
    private String apiUrl;

    public WeatherService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Fetch weather data dari OpenWeather API dan simpan ke Snowflake
     */
    public String fetchAndStoreWeather(String facilityId, double lat, double lon) {
        try {
            // 1. Fetch dari OpenWeather API
            String url = String.format("%s?lat=%f&lon=%f&appid=%s&units=metric",
                    apiUrl, lat, lon, apiKey);

            System.out.println("📡 Fetching weather from: " + url);

            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);

            // 2. Parse response
            String weatherId = "WTH-" + UUID.randomUUID().toString().substring(0, 8);
            double temp = root.path("main").path("temp").asDouble();
            double humidity = root.path("main").path("humidity").asDouble();
            double rainfall = root.path("rain").path("1h").asDouble(0.0);
            String condition = root.path("weather").get(0).path("main").asText();

            System.out.println("Temperature: " + temp + "°C");
            System.out.println("Humidity: " + humidity + "%");
            System.out.println("Rainfall: " + rainfall + " mm");
            System.out.println("Condition: " + condition);

            // 3. Insert ke Snowflake
            String sql = "INSERT INTO ECOPATH_DB.PUBLIC.fact_weather_data " +
                    "(weather_id, facility_id, date, temperature_avg, humidity_avg, " +
                    "rainfall_mm, weather_condition, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP())";

            jdbcTemplate.update(sql, weatherId, facilityId, LocalDate.now().toString(),
                    temp, humidity, rainfall, condition);

            System.out.println("Weather data stored for facility: " + facilityId);

            return "Weather data stored successfully: " + weatherId;

        } catch (Exception e) {
            System.err.println("Error fetching weather: " + e.getMessage());
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Fetch weather untuk semua fasilitas
     */
    public String fetchWeatherForAllFacilities() {
        try {
            String sql = "SELECT facility_id, facility_name, latitude, longitude " +
                    "FROM ECOPATH_DB.PUBLIC.dim_health_facilities";

            var facilities = jdbcTemplate.queryForList(sql);

            int successCount = 0;
            int failCount = 0;

            for (var facility : facilities) {
                String facilityId = (String) facility.get("FACILITY_ID");
                double lat = ((Number) facility.get("LATITUDE")).doubleValue();
                double lon = ((Number) facility.get("LONGITUDE")).doubleValue();

                try {
                    fetchAndStoreWeather(facilityId, lat, lon);
                    successCount++;

                    // Delay 1 detik untuk avoid rate limit
                    Thread.sleep(1000);

                } catch (Exception e) {
                    failCount++;
                    System.err.println("Failed for facility: " + facilityId);
                }
            }

            return String.format("Weather fetch completed: %d success, %d failed",
                    successCount, failCount);

        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}