package com.ecopath.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RedistributionService {

    private final JdbcTemplate jdbcTemplate;

    public RedistributionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Generate redistribution recommendations
     */
    public Map<String, Object> generateRecommendations() {
        try {
            System.out.println("Generating redistribution recommendations...");

            // 1. Find overstocked facilities
            String overSql = "SELECT i.facility_id, f.facility_name, " +
                    "i.item_id, m.item_name, i.current_stock, " +
                    "i.max_stock_capacity, f.latitude, f.longitude " +
                    "FROM ECOPATH_DB.PUBLIC.fact_inventory i " +
                    "JOIN ECOPATH_DB.PUBLIC.dim_health_facilities f " +
                    "  ON i.facility_id = f.facility_id " +
                    "JOIN ECOPATH_DB.PUBLIC.dim_medical_items m " +
                    "  ON i.item_id = m.item_id " +
                    "WHERE i.current_stock > i.max_stock_capacity * 0.8";

            List<Map<String, Object>> overstocked = jdbcTemplate.queryForList(overSql);

            // 2. Find understocked facilities
            String underSql = "SELECT i.facility_id, f.facility_name, " +
                    "i.item_id, m.item_name, i.current_stock, " +
                    "i.min_stock_threshold, f.latitude, f.longitude " +
                    "FROM ECOPATH_DB.PUBLIC.fact_inventory i " +
                    "JOIN ECOPATH_DB.PUBLIC.dim_health_facilities f " +
                    "  ON i.facility_id = f.facility_id " +
                    "JOIN ECOPATH_DB.PUBLIC.dim_medical_items m " +
                    "  ON i.item_id = m.item_id " +
                    "WHERE i.current_stock < i.min_stock_threshold * 1.5";

            List<Map<String, Object>> understocked = jdbcTemplate.queryForList(underSql);

            System.out.println("Found " + overstocked.size() + " overstocked, " +
                    understocked.size() + " understocked items");

            List<Map<String, Object>> recommendations = new ArrayList<>();

            // 3. Match overstocked with understocked
            for (var over : overstocked) {
                for (var under : understocked) {
                    // Check if same item
                    if (over.get("ITEM_ID").equals(under.get("ITEM_ID"))) {

                        // Calculate transfer quantity
                        int overStock = ((Number) over.get("CURRENT_STOCK")).intValue();
                        int overCapacity = ((Number) over.get("MAX_STOCK_CAPACITY")).intValue();
                        int underStock = ((Number) under.get("CURRENT_STOCK")).intValue();
                        int underThreshold = ((Number) under.get("MIN_STOCK_THRESHOLD")).intValue();

                        int surplus = overStock - (int)(overCapacity * 0.7);
                        int deficit = underThreshold - underStock;
                        int transferQty = Math.min(surplus, deficit);

                        if (transferQty > 10) {

                            // Calculate distance
                            double lat1 = ((Number) over.get("LATITUDE")).doubleValue();
                            double lon1 = ((Number) over.get("LONGITUDE")).doubleValue();
                            double lat2 = ((Number) under.get("LATITUDE")).doubleValue();
                            double lon2 = ((Number) under.get("LONGITUDE")).doubleValue();
                            double distance = calculateDistance(lat1, lon1, lat2, lon2);

                            // Calculate priority score
                            int priorityScore = calculatePriority(transferQty, distance, deficit);

                            // Generate recommendation ID
                            String recId = "REC-" + UUID.randomUUID().toString().substring(0, 8);

                            // Save to database
                            String insertSql = "INSERT INTO ECOPATH_DB.PUBLIC.analytics_redistribution_recommendations " +
                                    "(recommendation_id, from_facility_id, to_facility_id, item_id, " +
                                    "recommended_quantity, priority_score, reason, status, created_at) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', CURRENT_TIMESTAMP())";

                            String reason = String.format(
                                    "Transfer %d units from %s (surplus) to %s (deficit). Distance: %.1f km",
                                    transferQty, over.get("FACILITY_NAME"),
                                    under.get("FACILITY_NAME"), distance
                            );

                            jdbcTemplate.update(insertSql, recId,
                                    over.get("FACILITY_ID"), under.get("FACILITY_ID"),
                                    over.get("ITEM_ID"), transferQty, priorityScore, reason);

                            recommendations.add(Map.of(
                                    "recommendation_id", recId,
                                    "from_facility", over.get("FACILITY_NAME"),
                                    "to_facility", under.get("FACILITY_NAME"),
                                    "item", over.get("ITEM_NAME"),
                                    "quantity", transferQty,
                                    "priority", priorityScore,
                                    "distance_km", String.format("%.1f", distance)
                            ));

                            System.out.println("✅ Recommendation: " +
                                    over.get("FACILITY_NAME") + " → " + under.get("FACILITY_NAME") +
                                    " (" + transferQty + " " + over.get("ITEM_NAME") + ")");
                        }
                    }
                }
            }

            System.out.println("Generated " + recommendations.size() + " recommendations");

            return Map.of(
                    "status", "SUCCESS",
                    "recommendations_generated", recommendations.size(),
                    "recommendations", recommendations
            );

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            return Map.of("status", "FAILED", "error", e.getMessage());
        }
    }

    /**
     * Calculate distance (Haversine formula)
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Radius bumi dalam km

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    /**
     * Calculate priority score (0-100)
     */
    private int calculatePriority(int quantity, double distance, int deficit) {
        int qtyScore = Math.min(quantity / 10, 40);
        int distScore = (int) Math.max(30 - distance, 0);
        int deficitScore = Math.min(deficit / 5, 30);

        return Math.min(qtyScore + distScore + deficitScore, 100);
    }

    /**
     * Get pending recommendations
     */
    public List<Map<String, Object>> getPendingRecommendations() {
        try {
            String sql = "SELECT " +
                    "r.recommendation_id, " +
                    "r.source_facility_id, " +
                    "r.destination_facility_id, " +
                    "r.item_id, " +
                    "r.quantity_to_move, " +
                    "r.priority_score, " +
                    "r.status, " +
                    "r.created_at, " +
                    "fs.facility_name as from_facility_name, " +
                    "fd.facility_name as to_facility_name, " +
                    "m.item_name, " +
                    "r.source_current_stock as from_current_stock, " +
                    "r.destination_current_stock as to_current_stock, " +
                    "(r.source_current_stock - r.quantity_to_move) as from_after_stock, " +
                    "(r.destination_current_stock + r.quantity_to_move) as to_after_stock, " +
                    "r.priority_score as priority " +
                    "FROM ECOPATH_DB.PUBLIC.ANALYTICS_REDISTRIBUTION_RECOMMENDATIONS r " +
                    "JOIN ECOPATH_DB.PUBLIC.dim_health_facilities fs " +
                    "  ON r.source_facility_id = fs.facility_id " +
                    "JOIN ECOPATH_DB.PUBLIC.dim_health_facilities fd " +
                    "  ON r.destination_facility_id = fd.facility_id " +
                    "JOIN ECOPATH_DB.PUBLIC.dim_medical_items m " +
                    "  ON r.item_id = m.item_id " +
                    "WHERE r.status = 'PENDING' " +
                    "ORDER BY r.priority_score DESC, r.created_at DESC";

            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
            System.out.println("Pending recommendations found: " + results.size());

            return results;

        } catch (Exception e) {
            System.err.println("Error getting pending recommendations: " + e.getMessage());
            e.printStackTrace();
            return List.of();
        }
    }

    /**
     * Approve recommendation
     */
    public String approveRecommendation(String recommendationId, String approvedBy) {
        try {
            // Get recommendation details
            String selectSql = "SELECT * FROM ECOPATH_DB.PUBLIC.ANALYTICS_REDISTRIBUTION_RECOMMENDATIONS " +
                    "WHERE recommendation_id = ?";

            List<Map<String, Object>> results = jdbcTemplate.queryForList(selectSql, recommendationId);

            if (results.isEmpty()) {
                return "Error: Recommendation not found";
            }

            Map<String, Object> rec = results.get(0);
            String sourceFacilityId = (String) rec.get("SOURCE_FACILITY_ID");
            String destFacilityId = (String) rec.get("DESTINATION_FACILITY_ID");
            String itemId = (String) rec.get("ITEM_ID");
            int quantity = ((Number) rec.get("QUANTITY_TO_MOVE")).intValue();

            // Update status to APPROVED
            String updateSql = "UPDATE ECOPATH_DB.PUBLIC.ANALYTICS_REDISTRIBUTION_RECOMMENDATIONS " +
                    "SET status = 'APPROVED', " +
                    "    approved_by = ?, " +
                    "    approved_at = CURRENT_TIMESTAMP() " +
                    "WHERE recommendation_id = ?";

            jdbcTemplate.update(updateSql, approvedBy, recommendationId);

            // Update inventory stocks
            // Reduce from source
            String reduceStockSql = "UPDATE ECOPATH_DB.PUBLIC.fact_inventory " +
                    "SET current_stock = current_stock - ?, " +
                    "    last_updated = CURRENT_TIMESTAMP() " +
                    "WHERE facility_id = ? AND item_id = ?";

            jdbcTemplate.update(reduceStockSql, quantity, sourceFacilityId, itemId);

            // Add to destination
            String addStockSql = "UPDATE ECOPATH_DB.PUBLIC.fact_inventory " +
                    "SET current_stock = current_stock + ?, " +
                    "    last_updated = CURRENT_TIMESTAMP() " +
                    "WHERE facility_id = ? AND item_id = ?";

            jdbcTemplate.update(addStockSql, quantity, destFacilityId, itemId);

            System.out.println("Approved redistribution: " + recommendationId);

            return "Redistribution approved successfully! Stock updated.";

        } catch (Exception e) {
            System.err.println("Error approving recommendation: " + e.getMessage());
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }
}