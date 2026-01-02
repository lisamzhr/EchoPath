-- =====================================================
-- ECOPATH - CREATE VIEWS
-- =====================================================
USE DATABASE ECOPATH_DB;
USE WAREHOUSE COMPUTE_WH;
USE SCHEMA PUBLIC;

-- View: Nurse Reports Summary
CREATE OR REPLACE VIEW analytics_nurse_reports_summary AS
SELECT 
    r.facility_id,
    f.facility_name,
    r.disease_detected,
    r.severity_level,
    COUNT(*) as report_count,
    SUM(r.patient_count) as total_patients,
    MAX(r.report_date) as last_report_date,
    MAX(r.created_at) as last_updated
FROM fact_nurse_reports r
JOIN dim_health_facilities f 
  ON r.facility_id = f.facility_id
GROUP BY r.facility_id, f.facility_name, r.disease_detected, r.severity_level
ORDER BY last_updated DESC;