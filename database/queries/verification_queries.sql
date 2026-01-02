-- =====================================================
-- VERIFICATION QUERIES
-- =====================================================
USE DATABASE ECOPATH_DB;
USE WAREHOUSE COMPUTE_WH;
USE SCHEMA PUBLIC;

-- Check row counts
SELECT 'dim_health_facilities' as table_name, COUNT(*) as row_count FROM dim_health_facilities
UNION ALL
SELECT 'dim_medical_items', COUNT(*) FROM dim_medical_items
UNION ALL
SELECT 'fact_inventory', COUNT(*) FROM fact_inventory
UNION ALL
SELECT 'fact_stock_transactions', COUNT(*) FROM fact_stock_transactions
UNION ALL
SELECT 'fact_weather_data', COUNT(*) FROM fact_weather_data
UNION ALL
SELECT 'fact_nurse_reports', COUNT(*) FROM fact_nurse_reports
UNION ALL
SELECT 'analytics_redistribution_recommendations', COUNT(*) FROM analytics_redistribution_recommendations;

-- Table structure summary
SELECT 
    TABLE_SCHEMA,
    TABLE_NAME,
    TABLE_TYPE,
    ROW_COUNT,
    BYTES,
    CREATED
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_SCHEMA = 'PUBLIC'
  AND TABLE_NAME IN (
    'DIM_HEALTH_FACILITIES',
    'DIM_MEDICAL_ITEMS',
    'FACT_INVENTORY',
    'FACT_STOCK_TRANSACTIONS',
    'FACT_WEATHER_DATA',
    'FACT_NURSE_REPORTS',
    'ANALYTICS_REDISTRIBUTION_RECOMMENDATIONS'
  )
ORDER BY TABLE_NAME;

-- Latest nurse reports
SELECT report_id, facility_id, report_date, disease_detected, 
       severity_level, patient_count, raw_text, created_at
FROM fact_nurse_reports 
ORDER BY created_at DESC 
LIMIT 5;