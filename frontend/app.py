import streamlit as st
import requests
import pandas as pd
import plotly.express as px
import plotly.graph_objects as go
from datetime import datetime, date

# ========================================
# CONFIGURATION
# ========================================
API_BASE_URL = "http://localhost:8080/services"

st.set_page_config(
    page_title="EcoPath Dashboard",
    page_icon="🏥",
    layout="wide",
    initial_sidebar_state="expanded"
)

# ========================================
# CUSTOM CSS
# ========================================
st.markdown("""
<style>
    .main-header {
        font-size: 2.5rem;
        font-weight: bold;
        color: #1f77b4;
        text-align: center;
        margin-bottom: 2rem;
    }
    .metric-card {
        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        padding: 20px;
        border-radius: 10px;
        color: white;
        text-align: center;
    }
    .success-box {
        padding: 1rem;
        border-radius: 0.5rem;
        background-color: #d4edda;
        border: 1px solid #c3e6cb;
        color: #155724;
    }
    .error-box {
        padding: 1rem;
        border-radius: 0.5rem;
        background-color: #f8d7da;
        border: 1px solid #f5c6cb;
        color: #721c24;
    }
</style>
""", unsafe_allow_html=True)

# ========================================
# HELPER FUNCTIONS
# ========================================

def fetch_reports_summary():
    """Fetch nurse reports summary from API"""
    try:
        response = requests.get(f"{API_BASE_URL}/reports/summary", timeout=10)
        if response.status_code == 200:
            data = response.json()
            if data.get("status") == "SUCCESS":
                return pd.DataFrame(data.get("data", []))
        return pd.DataFrame()
    except Exception as e:
        st.error(f"Error fetching reports: {str(e)}")
        return pd.DataFrame()

def fetch_anomalies():
    """Fetch inventory anomalies"""
    try:
        response = requests.get(f"{API_BASE_URL}/inventory/anomalies", timeout=10)
        if response.status_code == 200:
            data = response.json()
            if data.get("status") == "SUCCESS":
                return data.get("data", {})
        return {}
    except Exception as e:
        st.error(f"Error fetching anomalies: {str(e)}")
        return {}

def process_nurse_report(facility_id, text):
    """Process nurse report via AI"""
    try:
        payload = {
            "facilityId": facility_id,
            "text": text
        }
        response = requests.post(
            f"{API_BASE_URL}/reports/process",
            json=payload,
            timeout=30
        )
        return response.json()
    except Exception as e:
        return {"status": "FAILED", "message": str(e)}

def update_stock(facility_id, item_id, quantity, tx_type):
    """Update inventory stock"""
    try:
        payload = {
            "facilityId": facility_id,
            "itemId": item_id,
            "quantity": quantity,
            "type": tx_type
        }
        response = requests.post(
            f"{API_BASE_URL}/inventory/update",
            json=payload,
            timeout=10
        )
        return response.json()
    except Exception as e:
        return {"status": "FAILED", "message": str(e)}

def fetch_weather(facility_id, lat, lon):
    """Fetch weather for a facility"""
    try:
        payload = {
            "facilityId": facility_id,
            "lat": lat,
            "lon": lon
        }
        response = requests.post(
            f"{API_BASE_URL}/weather/fetch",
            json=payload,
            timeout=15
        )
        return response.json()
    except Exception as e:
        return {"status": "FAILED", "message": str(e)}

def fetch_weather_all():
    """Fetch weather for all facilities"""
    try:
        response = requests.post(
            f"{API_BASE_URL}/weather/fetch-all",
            timeout=30
        )
        return response.json()
    except Exception as e:
        return {"status": "FAILED", "message": str(e)}

# ========================================
# SIDEBAR NAVIGATION
# ========================================
st.sidebar.title("🏥 EcoPath Navigation")
page = st.sidebar.radio(
    "Select Module",
    ["📊 Dashboard", "🩺 Nurse Reports", "📦 Inventory", "🌤️ Weather", "⚙️ Settings"]
)

st.sidebar.markdown("---")
st.sidebar.info("""
**EcoPath System**  
Real-time Healthcare  
Resource Management  
v1.0.0
""")

# ========================================
# PAGE: DASHBOARD
# ========================================
if page == "📊 Dashboard":
    st.markdown('<div class="main-header">🏥 EcoPath Dashboard</div>', unsafe_allow_html=True)
    
    # Fetch data
    df_reports = fetch_reports_summary()
    anomalies = fetch_anomalies()
    
    # Metrics Row
    col1, col2, col3, col4 = st.columns(4)
    
    with col1:
        total_reports = len(df_reports) if not df_reports.empty else 0
        st.metric("📝 Total Reports", total_reports)
    
    with col2:
        total_patients = df_reports['TOTAL_PATIENTS'].sum() if not df_reports.empty else 0
        st.metric("👥 Total Patients", int(total_patients))
    
    with col3:
        critical_cases = len(df_reports[df_reports['SEVERITY_LEVEL'] == 'Critical']) if not df_reports.empty else 0
        st.metric("🚨 Critical Cases", critical_cases)
    
    with col4:
        low_stock_count = len(anomalies.get('low_stock', [])) if anomalies else 0
        st.metric("📦 Low Stock Items", low_stock_count)
    
    st.markdown("---")
    
    # Charts Row
    col1, col2 = st.columns(2)
    
    with col1:
        st.subheader("📈 Disease Distribution")
        if not df_reports.empty:
            disease_counts = df_reports.groupby('DISEASE_DETECTED')['TOTAL_PATIENTS'].sum().reset_index()
            fig = px.pie(
                disease_counts,
                values='TOTAL_PATIENTS',
                names='DISEASE_DETECTED',
                title='Patients by Disease',
                color_discrete_sequence=px.colors.qualitative.Set3
            )
            st.plotly_chart(fig, use_container_width=True)
        else:
            st.info("No disease data available")
    
    with col2:
        st.subheader("🏥 Severity Levels")
        if not df_reports.empty:
            severity_counts = df_reports.groupby('SEVERITY_LEVEL')['REPORT_COUNT'].sum().reset_index()
            fig = px.bar(
                severity_counts,
                x='SEVERITY_LEVEL',
                y='REPORT_COUNT',
                title='Reports by Severity',
                color='SEVERITY_LEVEL',
                color_discrete_map={
                    'Low': '#28a745',
                    'Medium': '#ffc107',
                    'High': '#fd7e14',
                    'Critical': '#dc3545'
                }
            )
            st.plotly_chart(fig, use_container_width=True)
        else:
            st.info("No severity data available")
    
    # Recent Reports Table
    st.subheader("📋 Recent Reports")
    if not df_reports.empty:
        display_cols = ['FACILITY_NAME', 'DISEASE_DETECTED', 'SEVERITY_LEVEL', 
                       'TOTAL_PATIENTS', 'LAST_REPORT_DATE']
        st.dataframe(df_reports[display_cols].head(10), use_container_width=True)
    else:
        st.info("No reports available")

# ========================================
# PAGE: NURSE REPORTS
# ========================================
elif page == "🩺 Nurse Reports":
    st.markdown('<div class="main-header">🩺 Nurse Report Processing</div>', unsafe_allow_html=True)
    
    tab1, tab2 = st.tabs(["📝 Submit Report", "📊 View Reports"])
    
    with tab1:
        st.subheader("Submit New Report")
        
        col1, col2 = st.columns([1, 2])
        
        with col1:
            facility_id = st.selectbox(
                "Select Facility",
                ["PKM001", "PKM002", "PKM003", "PKM004", "PKM005",
                 "PKM006", "PKM007", "PKM008", "PKM009", "PKM010"]
            )
        
        with col2:
            report_text = st.text_area(
                "Report Details",
                placeholder="e.g., Ada 15 pasien dengan gejala DBD hari ini",
                height=150
            )
        
        if st.button("🤖 Process with AI", type="primary"):
            if report_text.strip():
                with st.spinner("Processing report with Gemini AI..."):
                    result = process_nurse_report(facility_id, report_text)
                    
                    if result.get("status") == "SUCCESS":
                        st.markdown(f'<div class="success-box">✅ {result.get("message")}</div>', 
                                  unsafe_allow_html=True)
                    else:
                        st.markdown(f'<div class="error-box">❌ {result.get("message")}</div>', 
                                  unsafe_allow_html=True)
            else:
                st.warning("Please enter report text")
    
    with tab2:
        st.subheader("All Reports Summary")
        df_reports = fetch_reports_summary()
        
        if not df_reports.empty:
            # Filters
            col1, col2 = st.columns(2)
            with col1:
                selected_disease = st.multiselect(
                    "Filter by Disease",
                    options=df_reports['DISEASE_DETECTED'].unique(),
                    default=df_reports['DISEASE_DETECTED'].unique()
                )
            
            with col2:
                selected_severity = st.multiselect(
                    "Filter by Severity",
                    options=df_reports['SEVERITY_LEVEL'].unique(),
                    default=df_reports['SEVERITY_LEVEL'].unique()
                )
            
            # Apply filters
            filtered_df = df_reports[
                (df_reports['DISEASE_DETECTED'].isin(selected_disease)) &
                (df_reports['SEVERITY_LEVEL'].isin(selected_severity))
            ]
            
            st.dataframe(filtered_df, use_container_width=True)
            
            # Download button
            csv = filtered_df.to_csv(index=False)
            st.download_button(
                label="📥 Download CSV",
                data=csv,
                file_name=f"reports_{datetime.now().strftime('%Y%m%d')}.csv",
                mime="text/csv"
            )
        else:
            st.info("No reports available")

# ========================================
# PAGE: INVENTORY
# ========================================
elif page == "📦 Inventory":
    st.markdown('<div class="main-header">📦 Inventory Management</div>', unsafe_allow_html=True)
    
    tab1, tab2 = st.tabs(["📝 Update Stock", "⚠️ Anomalies"])
    
    with tab1:
        st.subheader("Update Stock Transaction")
        
        col1, col2 = st.columns(2)
        
        with col1:
            facility_id = st.selectbox(
                "Facility ID",
                ["PKM001", "PKM002", "PKM003", "PKM004", "PKM005",
                 "PKM006", "PKM007", "PKM008", "PKM009", "PKM010"],
                key="inv_facility"
            )
            
            item_id = st.selectbox(
                "Item ID",
                ["MED001", "MED002", "MED003", "MED004", "MED005",
                 "ALT001", "ALT002", "VAK001"]
            )
        
        with col2:
            quantity = st.number_input("Quantity", min_value=1, value=10)
            
            tx_type = st.radio("Transaction Type", ["IN", "OUT"])
        
        if st.button("💾 Update Stock", type="primary"):
            with st.spinner("Updating stock..."):
                result = update_stock(facility_id, item_id, quantity, tx_type)
                
                if result.get("status") == "SUCCESS":
                    st.markdown(f'<div class="success-box">✅ {result.get("message")}</div>', 
                              unsafe_allow_html=True)
                else:
                    st.markdown(f'<div class="error-box">❌ {result.get("message")}</div>', 
                              unsafe_allow_html=True)
    
    with tab2:
        st.subheader("Stock Anomalies Detection")
        
        if st.button("🔍 Detect Anomalies", type="primary"):
            with st.spinner("Analyzing inventory..."):
                anomalies = fetch_anomalies()
                
                if anomalies:
                    col1, col2, col3 = st.columns(3)
                    
                    with col1:
                        st.metric("🔴 Low Stock", len(anomalies.get('low_stock', [])))
                    
                    with col2:
                        st.metric("🟡 Expiring Soon", len(anomalies.get('expiring_soon', [])))
                    
                    with col3:
                        st.metric("🟢 Overstock", len(anomalies.get('overstock', [])))
                    
                    # Display details
                    if anomalies.get('low_stock'):
                        st.warning("⚠️ Low Stock Items")
                        st.json(anomalies['low_stock'])
                    
                    if anomalies.get('expiring_soon'):
                        st.warning("⏰ Items Expiring Soon")
                        st.json(anomalies['expiring_soon'])
                    
                    if anomalies.get('overstock'):
                        st.info("📦 Overstock Items")
                        st.json(anomalies['overstock'])
                else:
                    st.success("✅ No anomalies detected!")

# ========================================
# PAGE: WEATHER
# ========================================
elif page == "🌤️ Weather":
    st.markdown('<div class="main-header">🌤️ Weather Data</div>', unsafe_allow_html=True)
    
    tab1, tab2 = st.tabs(["🏥 Single Facility", "🌍 All Facilities"])
    
    with tab1:
        st.subheader("Fetch Weather for Single Facility")
        
        col1, col2 = st.columns(2)
        
        with col1:
            facility_id = st.selectbox(
                "Facility ID",
                ["PKM001", "PKM002", "PKM003", "PKM004", "PKM005",
                 "PKM006", "PKM007", "PKM008", "PKM009", "PKM010"],
                key="weather_facility"
            )
            
            lat = st.number_input("Latitude", value=-7.1234, format="%.6f")
        
        with col2:
            lon = st.number_input("Longitude", value=107.5678, format="%.6f")
        
        if st.button("🌡️ Fetch Weather", type="primary"):
            with st.spinner("Fetching weather data..."):
                result = fetch_weather(facility_id, lat, lon)
                
                if result.get("status") == "SUCCESS":
                    st.markdown(f'<div class="success-box">✅ {result.get("message")}</div>', 
                              unsafe_allow_html=True)
                else:
                    st.markdown(f'<div class="error-box">❌ {result.get("message")}</div>', 
                              unsafe_allow_html=True)
    
    with tab2:
        st.subheader("Fetch Weather for All Facilities")
        
        if st.button("🌍 Fetch All Weather Data", type="primary"):
            with st.spinner("Fetching weather data for all facilities..."):
                result = fetch_weather_all()
                
                if result.get("status") == "SUCCESS":
                    st.markdown(f'<div class="success-box">✅ {result.get("message")}</div>', 
                              unsafe_allow_html=True)
                else:
                    st.markdown(f'<div class="error-box">❌ {result.get("message")}</div>', 
                              unsafe_allow_html=True)

# ========================================
# PAGE: SETTINGS
# ========================================
elif page == "⚙️ Settings":
    st.markdown('<div class="main-header">⚙️ Settings</div>', unsafe_allow_html=True)
    
    st.subheader("API Configuration")
    
    new_api_url = st.text_input("API Base URL", value=API_BASE_URL)
    
    if st.button("💾 Save Settings"):
        st.success("Settings saved!")
    
    st.markdown("---")
    
    st.subheader("About EcoPath")
    st.info("""
    **EcoPath Healthcare Management System**
    
    - Real-time disease outbreak monitoring
    - AI-powered nurse report processing
    - Inventory anomaly detection
    - Weather data integration
    
    Built with: Spring Boot + Snowflake + Gemini AI + Streamlit
    """)