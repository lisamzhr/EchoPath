-- =====================================================
-- ECOPATH DATABASE SETUP
-- =====================================================
-- 1. Create Database
CREATE DATABASE IF NOT EXISTS ECOPATH_DB;

-- 2. Use Database
USE DATABASE ECOPATH_DB;

-- 3. Create Schema (PUBLIC sudah ada by default)
CREATE SCHEMA IF NOT EXISTS PUBLIC;

-- 4. Create Warehouse (untuk compute power)
CREATE WAREHOUSE IF NOT EXISTS COMPUTE_WH 
WITH 
  WAREHOUSE_SIZE = 'XSMALL'
  AUTO_SUSPEND = 300
  AUTO_RESUME = TRUE
  INITIALLY_SUSPENDED = FALSE;

-- 5. Use Warehouse
USE WAREHOUSE COMPUTE_WH;

-- 6. Verify setup
SELECT 
  CURRENT_DATABASE() as database_name,
  CURRENT_SCHEMA() as schema_name,
  CURRENT_WAREHOUSE() as warehouse_name,
  CURRENT_VERSION() as snowflake_version;