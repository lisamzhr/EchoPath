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
            System.out.println("📦 Updating stock: " + type + " " + quantity + " units for " + facilityId + " - " + itemId);

            // STEP 1: Check if inventory record exists
            String checkSql = "SELECT inventory_id, current_stock " +
                    "FROM ECOPATH_DB.PUBLIC.fact_inventory " +
                    "WHERE facility_id = ? AND item_id = ?";

            List<Map<String, Object>> existing = jdbcTemplate.queryForList(checkSql, facilityId, itemId);

            if (existing.isEmpty()) {
                System.err.println("Inventory record not found for: " + facilityId + " - " + itemId);
                return "Error: Inventory record not found for facility " + facilityId + " and item " + itemId;
            }

            int currentStock = ((Number) existing.get(0).get("CURRENT_STOCK")).intValue();
            System.out.println("Current stock: " + currentStock);

            // STEP 2: Calculate new stock
            int delta = type.equalsIgnoreCase("IN") ? quantity : -quantity;
            int newStock = currentStock + delta;

            if (newStock < 0) {
                return "Error: Cannot reduce stock below 0. Current: " + currentStock + ", Requested: " + quantity;
            }

            System.out.println("New stock will be: " + newStock + " (delta: " + delta + ")");

            // STEP 3: Update inventory
            String updateSql = "UPDATE ECOPATH_DB.PUBLIC.fact_inventory " +
                    "SET current_stock = ?, " +
                    "    last_updated = CURRENT_TIMESTAMP() " +
                    "WHERE facility_id = ? AND item_id = ?";

            int updated = jdbcTemplate.update(updateSql, newStock, facilityId, itemId);

            if (updated == 0) {
                return "Error: Failed to update inventory";
            }

            System.out.println("Stock updated: " + currentStock + " → " + newStock);

            // STEP 4: Record transaction
            String transactionId = "TRX-" + UUID.randomUUID().toString().substring(0, 8);
            String insertSql = "INSERT INTO ECOPATH_DB.PUBLIC.fact_stock_transactions " +
                    "(transaction_id, facility_id, item_id, transaction_type, " +
                    "quantity, transaction_date, notes) " +
                    "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP(), ?)";

            jdbcTemplate.update(insertSql, transactionId, facilityId, itemId,
                    type, quantity, "Stock " + type + " via Dashboard");

            System.out.println("Transaction recorded: " + transactionId);

            return String.format("Stock updated successfully! %s → %s (Transaction: %s)",
                    currentStock, newStock, transactionId);

        } catch (Exception e) {
            System.err.println("Error updating stock: " + e.getMessage());
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Detect stock anomalies (understocked, overstocked, near expiry)
     */
    public Map<String, Object> detectAnomalies() {
        try {
            // Understocked items - TAMBAHKAN DISTINCT
            String underSql = "SELECT DISTINCT f.facility_name, m.item_name, " +
                    "i.current_stock, i.min_stock_threshold " +
                    "FROM ECOPATH_DB.PUBLIC.fact_inventory i " +
                    "JOIN ECOPATH_DB.PUBLIC.dim_health_facilities f " +
                    "  ON i.facility_id = f.facility_id " +
                    "JOIN ECOPATH_DB.PUBLIC.dim_medical_items m " +
                    "  ON i.item_id = m.item_id " +
                    "WHERE i.current_stock < i.min_stock_threshold";

            List<Map<String, Object>> understocked = jdbcTemplate.queryForList(underSql);

            // Overstocked items - TAMBAHKAN DISTINCT
            String overSql = "SELECT DISTINCT f.facility_name, m.item_name, " +
                    "i.current_stock, i.max_stock_capacity " +
                    "FROM ECOPATH_DB.PUBLIC.fact_inventory i " +
                    "JOIN ECOPATH_DB.PUBLIC.dim_health_facilities f " +
                    "  ON i.facility_id = f.facility_id " +
                    "JOIN ECOPATH_DB.PUBLIC.dim_medical_items m " +
                    "  ON i.item_id = m.item_id " +
                    "WHERE i.current_stock > i.max_stock_capacity * 0.9";

            List<Map<String, Object>> overstocked = jdbcTemplate.queryForList(overSql);

            // Near expiry items - TAMBAHKAN DISTINCT
            String expirySql = "SELECT DISTINCT f.facility_name, m.item_name, " +
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