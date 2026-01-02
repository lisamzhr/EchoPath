package com.ecopath.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InventoryService {

    private final JdbcTemplate jdbcTemplate;

    public InventoryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Update stock (IN atau OUT)
     */
    public String updateStock(String facilityId, String itemId, int quantity, String type) {
        try {
            System.out.println("Updating stock: " + type + " " + quantity + " units");

            // 1. Update inventory
            String updateSql = "UPDATE ECOPATH_DB.PUBLIC.fact_inventory " +
                    "SET current_stock = current_stock + ?, " +
                    "    last_updated = CURRENT_TIMESTAMP() " +
                    "WHERE facility_id = ? AND item_id = ?";

            int delta = type.equalsIgnoreCase("IN") ? quantity : -quantity;
            int updated = jdbcTemplate.update(updateSql, delta, facilityId, itemId);

            if (updated == 0) {
                return "Error: Inventory record not found";
            }

            // 2. Record transaction
            String transactionId = "TRX-" + UUID.randomUUID().toString().substring(0, 8);
            String insertSql = "INSERT INTO ECOPATH_DB.PUBLIC.fact_stock_transactions " +
                    "(transaction_id, facility_id, item_id, transaction_type, " +
                    "quantity, transaction_date, notes) " +
                    "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP(), ?)";

            jdbcTemplate.update(insertSql, transactionId, facilityId, itemId,
                    type, quantity, "Stock " + type + " via API");

            System.out.println("Stock updated: " + facilityId + " - " + itemId);

            return "Stock updated successfully: " + transactionId;

        } catch (Exception e) {
            System.err.println("Error updating stock: " + e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Detect stock anomalies (understocked, overstocked, near expiry)
     */
    public Map<String, Object> detectAnomalies() {
        try {
            // Understocked items
            String underSql = "SELECT f.facility_name, m.item_name, " +
                    "i.current_stock, i.min_stock_threshold " +
                    "FROM ECOPATH_DB.PUBLIC.fact_inventory i " +
                    "JOIN ECOPATH_DB.PUBLIC.dim_health_facilities f " +
                    "  ON i.facility_id = f.facility_id " +
                    "JOIN ECOPATH_DB.PUBLIC.dim_medical_items m " +
                    "  ON i.item_id = m.item_id " +
                    "WHERE i.current_stock < i.min_stock_threshold";

            List<Map<String, Object>> understocked = jdbcTemplate.queryForList(underSql);

            // Overstocked items
            String overSql = "SELECT f.facility_name, m.item_name, " +
                    "i.current_stock, i.max_stock_capacity " +
                    "FROM ECOPATH_DB.PUBLIC.fact_inventory i " +
                    "JOIN ECOPATH_DB.PUBLIC.dim_health_facilities f " +
                    "  ON i.facility_id = f.facility_id " +
                    "JOIN ECOPATH_DB.PUBLIC.dim_medical_items m " +
                    "  ON i.item_id = m.item_id " +
                    "WHERE i.current_stock > i.max_stock_capacity * 0.9";

            List<Map<String, Object>> overstocked = jdbcTemplate.queryForList(overSql);

            // Near expiry items
            String expirySql = "SELECT f.facility_name, m.item_name, " +
                    "i.current_stock, i.expiry_date, " +
                    "DATEDIFF(day, CURRENT_DATE(), i.expiry_date) as days_until_expiry " +
                    "FROM ECOPATH_DB.PUBLIC.fact_inventory i " +
                    "JOIN ECOPATH_DB.PUBLIC.dim_health_facilities f " +
                    "  ON i.facility_id = f.facility_id " +
                    "JOIN ECOPATH_DB.PUBLIC.dim_medical_items m " +
                    "  ON i.item_id = m.item_id " +
                    "WHERE DATEDIFF(day, CURRENT_DATE(), i.expiry_date) < 30";

            List<Map<String, Object>> nearExpiry = jdbcTemplate.queryForList(expirySql);

            return Map.of(
                    "understocked", understocked,
                    "overstocked", overstocked,
                    "near_expiry", nearExpiry,
                    "total_issues", understocked.size() + overstocked.size() + nearExpiry.size()
            );

        } catch (Exception e) {
            System.err.println("Error detecting anomalies: " + e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }
}