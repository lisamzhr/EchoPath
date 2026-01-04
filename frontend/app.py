import streamlit as st
import requests
import pandas as pd
import plotly.express as px
import plotly.graph_objects as go
from datetime import datetime

# ========================================
# CONFIGURATION
# ========================================
API_BASE_URL = "https://echopath-production.up.railway.app"

st.set_page_config(
    page_title="EcoPath Dashboard",
    page_icon="🏥",
    layout="wide",
    initial_sidebar_state="expanded"
)

# ========================================
# THEME STATE
# ========================================
if 'theme' not in st.session_state:
    st.session_state.theme = 'light'

def toggle_theme():
    st.session_state.theme = 'light' if st.session_state.theme == 'dark' else 'dark'

# ========================================
# THEME STYLES
# ========================================
if st.session_state.theme == 'dark':
    st.markdown("""
    <style>
    .stApp {
        background: linear-gradient(135deg, #0b0207 0%, #2a0d18 100%);
        color: #fdf2f8;
    }
    [data-testid="stSidebar"] {
        background: linear-gradient(180deg, #1a050d 0%, #3b0f1e 100%);
    }
    h1, h2, h3, h4, h5, h6, p, span, label {
        color: #fdf2f8 !important;
    }
    .success-box {
        background-color: #0f2f24;
        border-left: 4px solid #34d399;
        padding: 1rem;
        border-radius: 0.5rem;
        margin: 1rem 0;
        color: #d1fae5;
    }
    .error-box {
        background-color: #3a0a12;
        border-left: 4px solid #fb7185;
        padding: 1rem;
        border-radius: 0.5rem;
        margin: 1rem 0;
        color: #ffe4e6;
    }
    .info-box {
        background-color: #1f0a14;
        border-left: 4px solid #f472b6;
        padding: 1rem;
        border-radius: 0.5rem;
        margin: 1rem 0;
        color: #fce7f3;
    }
    .warning-box {
        background-color: #2d1f0a;
        border-left: 4px solid #fb923c;
        padding: 1rem;
        border-radius: 0.5rem;
        margin: 1rem 0;
        color: #fed7aa;
    }
    </style>
    """, unsafe_allow_html=True)
else:
    st.markdown("""
    <style>
    .stApp {
        background: linear-gradient(135deg, #fff1f2 0%, #ffe4e6 100%);
        color: #111827;
    }
    [data-testid="stSidebar"] {
        background: linear-gradient(180deg, #ffffff 0%, #ffe4e6 100%);
    }
    h1, h2, h3, h4, h5, h6, p, span, label {
        color: #111827 !important;
    }
    .stButton > button {
        background: linear-gradient(135deg, #f472b6 0%, #fb7185 100%);
        color: white;
        border-radius: 10px;
    }
    .stButton > button:hover {
        background: linear-gradient(135deg, #ec4899 0%, #f43f5e 100%);
    }
    </style>
    """, unsafe_allow_html=True)

# ========================================
# HELPER FUNCTIONS
# ========================================

def api_get(endpoint):
    """Generic GET request"""
    try:
        response = requests.get(f"{API_BASE_URL}{endpoint}", timeout=10)
        if response.status_code == 200:
            return response.json()
        return {"status": "FAILED", "error": f"HTTP {response.status_code}"}
    except Exception as e:
        return {"status": "FAILED", "error": str(e)}

def api_post(endpoint, payload):
    """Generic POST request"""
    try:
        response = requests.post(
            f"{API_BASE_URL}{endpoint}",
            json=payload,
            timeout=30
        )
        if response.status_code == 200:
            return response.json()
        return {"status": "FAILED", "error": f"HTTP {response.status_code}"}
    except Exception as e:
        return {"status": "FAILED", "error": str(e)}

# ========================================
# SIDEBAR
# ========================================
st.sidebar.title("EcoPath Dashboard")

col1, col2 = st.sidebar.columns([3, 1])
with col1:
    st.sidebar.markdown(f"**Theme:** {'Dark' if st.session_state.theme == 'dark' else 'Light'}")
with col2:
    if st.sidebar.button("🌓", key="theme_toggle"):
        toggle_theme()
        st.rerun()

st.sidebar.markdown("---")

page = st.sidebar.radio(
    "Navigation",
    ["Dashboard", "Nurse Reports", "Inventory", "Redistribution", "Weather", "System Health"],
    label_visibility="collapsed"
)

st.sidebar.markdown("---")
st.sidebar.info("""
**EcoPath System**  
Healthcare Resource Management  
Version 1.0.0
""")

# ========================================
# PAGE: DASHBOARD
# ========================================
if page == "Dashboard":
    st.title("Dashboard Overview")
    
    stats_data = api_get("/test/stats")
    reports_data = api_get("/services/reports/summary")
    
    col1, col2, col3, col4 = st.columns(4)
    
    if stats_data.get("status") == "SUCCESS":
        stats = stats_data.get("statistics", {})
        
        with col1:
            st.metric("Total Facilities", stats.get("TOTAL_FACILITIES", 0))
        with col2:
            st.metric("Medical Items", stats.get("TOTAL_ITEMS", 0))
        with col3:
            st.metric("Inventory Records", stats.get("TOTAL_INVENTORY", 0))
        with col4:
            st.metric("Reports", stats.get("TOTAL_REPORTS", 0))
    
    st.markdown("---")
    
    if reports_data.get("status") == "SUCCESS" and reports_data.get("count", 0) > 0:
        df_reports = pd.DataFrame(reports_data.get("data", []))
        
        col1, col2 = st.columns(2)
        
        with col1:
            st.subheader("Disease Distribution")
            disease_counts = df_reports.groupby('DISEASE_DETECTED')['TOTAL_PATIENTS'].sum().reset_index()
            fig = px.pie(
                disease_counts,
                values='TOTAL_PATIENTS',
                names='DISEASE_DETECTED',
                title='Patients by Disease',
                color_discrete_sequence=px.colors.sequential.Purples
            )
            fig.update_layout(
                template='plotly_dark' if st.session_state.theme == 'dark' else 'plotly_white',
                paper_bgcolor='rgba(0,0,0,0)',
                plot_bgcolor='rgba(0,0,0,0)'
            )
            st.plotly_chart(fig, use_container_width=True)
        
        with col2:
            st.subheader("Severity Levels")
            severity_counts = df_reports.groupby('SEVERITY_LEVEL')['REPORT_COUNT'].sum().reset_index()
            fig = px.bar(
                severity_counts,
                x='SEVERITY_LEVEL',
                y='REPORT_COUNT',
                title='Reports by Severity',
                color='SEVERITY_LEVEL',
                color_discrete_map={
                    'Low': '#10b981',
                    'Medium': '#f59e0b',
                    'High': '#f97316',
                    'Critical': '#ef4444'
                }
            )
            fig.update_layout(
                template='plotly_dark' if st.session_state.theme == 'dark' else 'plotly_white',
                paper_bgcolor='rgba(0,0,0,0)',
                plot_bgcolor='rgba(0,0,0,0)'
            )
            st.plotly_chart(fig, use_container_width=True)
        
        st.subheader("Recent Reports")
        display_cols = ['FACILITY_NAME', 'DISEASE_DETECTED', 'SEVERITY_LEVEL', 
                       'TOTAL_PATIENTS', 'LAST_REPORT_DATE']
        st.dataframe(df_reports[display_cols].head(10), use_container_width=True)
    else:
        st.markdown('<div class="info-box"> No report data available</div>', unsafe_allow_html=True)

# ========================================
# PAGE: NURSE REPORTS
# ========================================
elif page == "Nurse Reports":
    st.title("Nurse Report Processing")
    
    tab1, tab2 = st.tabs(["Submit Report", "View Reports"])
    
    with tab1:
        st.subheader("Submit New Report")
        
        facilities_data = api_get("/test/facilities")
        facility_options = []
        
        if facilities_data.get("status") == "SUCCESS":
            facilities = facilities_data.get("data", [])
            facility_options = [f"{f['FACILITY_ID']} - {f['FACILITY_NAME']}" for f in facilities]
        
        col1, col2 = st.columns([1, 2])
        
        with col1:
            if facility_options:
                selected_facility = st.selectbox("Select Facility", facility_options)
                facility_id = selected_facility.split(" - ")[0]
            else:
                facility_id = st.text_input("Facility ID", "PKM001")
        
        with col2:
            report_text = st.text_area(
                "Report Details",
                placeholder="e.g., 15 patients with severe dengue fever symptoms today",
                height=150
            )
        
        if st.button("Processing", type="primary"):
            if report_text.strip():
                with st.spinner("Processing report with Gemini AI..."):
                    result = api_post("/services/reports/process", {
                        "facilityId": facility_id,
                        "text": report_text
                    })
                    
                    if result.get("status") == "SUCCESS":
                        st.markdown(f'<div class="success-box">✓ {result.get("message")}</div>', 
                                  unsafe_allow_html=True)
                    else:
                        st.markdown(f'<div class="error-box">✗ Error: {result.get("message", result.get("error"))}</div>', 
                                  unsafe_allow_html=True)
            else:
                st.warning("Please enter report text")
    
    with tab2:
        st.subheader("All Reports")
        
        reports_data = api_get("/test/reports")
        
        if reports_data.get("status") == "SUCCESS":
            df_reports = pd.DataFrame(reports_data.get("data", []))
            
            if not df_reports.empty:
                col1, col2 = st.columns(2)
                with col1:
                    diseases = df_reports['DISEASE_DETECTED'].dropna().unique().tolist()
                    selected_disease = st.multiselect(
                        "Filter by Disease",
                        options=diseases,
                        default=diseases
                    )
                
                with col2:
                    severities = df_reports['SEVERITY_LEVEL'].dropna().unique().tolist()
                    selected_severity = st.multiselect(
                        "Filter by Severity",
                        options=severities,
                        default=severities
                    )
                
                filtered_df = df_reports[
                    (df_reports['DISEASE_DETECTED'].isin(selected_disease)) &
                    (df_reports['SEVERITY_LEVEL'].isin(selected_severity))
                ]
                
                st.dataframe(filtered_df, use_container_width=True)
                
                csv = filtered_df.to_csv(index=False)
                st.download_button(
                    label="Download CSV",
                    data=csv,
                    file_name=f"reports_{datetime.now().strftime('%Y%m%d')}.csv",
                    mime="text/csv"
                )
            else:
                st.markdown('<div class="info-box">No reports available</div>', unsafe_allow_html=True)
        else:
            st.markdown(f'<div class="error-box">Failed to fetch reports: {reports_data.get("error")}</div>', 
                      unsafe_allow_html=True)

# ========================================
# PAGE: INVENTORY
# ========================================
elif page == "Inventory":
    st.title("Inventory Management")
    
    tab1, tab2, tab3 = st.tabs(["Current Stock", "Update Stock", "Anomalies"])
    
    with tab1:
        st.subheader("Current Inventory")
        
        if st.button("Refresh Data"):
            st.rerun()
        
        inventory_data = api_get("/test/inventory")
        
        if inventory_data.get("status") == "SUCCESS":
            df_inventory = pd.DataFrame(inventory_data.get("data", []))
            
            if not df_inventory.empty:
                # Normalize column names (handle both upper and lower case)
                df_inventory.columns = df_inventory.columns.str.upper()
                
                # Check if required columns exist
                required_cols = ['FACILITY_ID', 'ITEM_NAME', 'CURRENT_STOCK']
                missing_cols = [col for col in required_cols if col not in df_inventory.columns]
                
                if missing_cols:
                    st.error(f"Missing columns: {', '.join(missing_cols)}")
                    st.info(f"Available columns: {', '.join(df_inventory.columns.tolist())}")
                else:
                    # Filter and Sort Section
                    st.markdown("### 🔍 Filter & Sort Options")
                    
                    col1, col2, col3 = st.columns(3)
                    
                    with col1:
                        # Filter by Facility
                        facilities = ["All"] + sorted(df_inventory['FACILITY_ID'].unique().tolist())
                        selected_facility = st.selectbox(
                            "Filter by Facility",
                            facilities,
                            key="filter_facility"
                        )
                
                with col2:
                    # Sort by Stock
                    sort_stock = st.selectbox(
                        "Sort by Stock Level",
                        ["Default", "Highest Stock First", "Lowest Stock First"],
                        key="sort_stock"
                    )
                
                with col3:
                    # Filter by Item Name (search)
                    search_item = st.text_input(
                        "Search Item Name",
                        placeholder="Type to search...",
                        key="search_item"
                    )
                
                # Apply filters
                df_filtered = df_inventory.copy()
                
                if selected_facility != "All":
                    df_filtered = df_filtered[df_filtered['FACILITY_ID'] == selected_facility]
                
                if search_item:
                    df_filtered = df_filtered[
                        df_filtered['ITEM_NAME'].str.contains(search_item, case=False, na=False)
                    ]
                
                # Apply sorting
                if sort_stock == "Highest Stock First":
                    df_filtered = df_filtered.sort_values('CURRENT_STOCK', ascending=False)
                elif sort_stock == "Lowest Stock First":
                    df_filtered = df_filtered.sort_values('CURRENT_STOCK', ascending=True)
                
                # Display filtered count
                st.markdown(f"**Showing {len(df_filtered)} of {len(df_inventory)} items**")
                
                # Display dataframe with all data
                st.dataframe(
                    df_filtered,
                    use_container_width=True,
                    height=400
                )
                
                # Visualization section
                st.markdown("### 📊 Stock Visualization")
                
                viz_col1, viz_col2 = st.columns(2)
                
                with viz_col1:
                    # Show top 10 or filtered data
                    chart_data = df_filtered.head(10) if len(df_filtered) > 10 else df_filtered
                    
                    fig = px.bar(
                        chart_data,
                        x='ITEM_NAME',
                        y='CURRENT_STOCK',
                        title=f'Stock Levels (Top {len(chart_data)} Items)',
                        labels={'CURRENT_STOCK': 'Stock Quantity'},
                        color='CURRENT_STOCK',
                        color_continuous_scale='Purples',
                        text='CURRENT_STOCK'
                    )
                    fig.update_traces(textposition='outside')
                    fig.update_layout(
                        template='plotly_dark' if st.session_state.theme == 'dark' else 'plotly_white',
                        paper_bgcolor='rgba(0,0,0,0)',
                        plot_bgcolor='rgba(0,0,0,0)',
                        xaxis_tickangle=-45
                    )
                    st.plotly_chart(fig, use_container_width=True)
                
                with viz_col2:
                    # Stock distribution by facility (if showing all facilities)
                    if selected_facility == "All":
                        facility_stock = df_filtered.groupby('FACILITY_ID')['CURRENT_STOCK'].sum().reset_index()
                        
                        fig2 = px.pie(
                            facility_stock,
                            values='CURRENT_STOCK',
                            names='FACILITY_ID',
                            title='Stock Distribution by Facility'
                        )
                        fig2.update_layout(
                            template='plotly_dark' if st.session_state.theme == 'dark' else 'plotly_white',
                            paper_bgcolor='rgba(0,0,0,0)'
                        )
                        st.plotly_chart(fig2, use_container_width=True)
                    else:
                        # Show item distribution for selected facility
                        fig2 = px.pie(
                            df_filtered.head(10),
                            values='CURRENT_STOCK',
                            names='ITEM_NAME',
                            title=f'Item Distribution - {selected_facility}'
                        )
                        fig2.update_layout(
                            template='plotly_dark' if st.session_state.theme == 'dark' else 'plotly_white',
                            paper_bgcolor='rgba(0,0,0,0)'
                        )
                        st.plotly_chart(fig2, use_container_width=True)
                
                # Summary statistics
                st.markdown("### 📈 Summary Statistics")
                stat_col1, stat_col2, stat_col3, stat_col4 = st.columns(4)
                
                with stat_col1:
                    st.metric("Total Items", len(df_filtered))
                
                with stat_col2:
                    st.metric("Total Stock", f"{df_filtered['CURRENT_STOCK'].sum():,}")
                
                with stat_col3:
                    st.metric("Avg Stock", f"{df_filtered['CURRENT_STOCK'].mean():.0f}")
                
                with stat_col4:
                    facilities_count = df_filtered['FACILITY_ID'].nunique()
                    st.metric("Facilities", facilities_count)
                
            else:
                st.markdown('<div class="info-box">No inventory data available</div>', unsafe_allow_html=True)
        else:
            st.markdown(f'<div class="error-box">Failed to fetch inventory: {inventory_data.get("error")}</div>', 
                      unsafe_allow_html=True)
    
    with tab2:
        st.subheader("Update Stock Transaction")
        
        # Get facilities from API
        facilities_data = api_get("/test/facilities")
        facility_options = []
        
        if facilities_data.get("status") == "SUCCESS":
            facilities = facilities_data.get("data", [])
            facility_options = [f"{f['FACILITY_ID']} - {f['FACILITY_NAME']}" for f in facilities]
        
        col1, col2 = st.columns(2)
        
        with col1:
            if facility_options:
                selected_facility = st.selectbox(
                    "Select Facility",
                    facility_options,
                    key="inv_facility"
                )
                facility_id = selected_facility.split(" - ")[0]
            else:
                # Fallback: manual input dengan semua PKM
                st.warning("⚠️ Could not fetch facilities from API. Using manual input.")
                facility_id = st.selectbox(
                    "Facility ID",
                    ["PKM001", "PKM002", "PKM003", "PKM004", "PKM005", 
                     "PKM006", "PKM007", "PKM008", "PKM009", "PKM010"],
                    key="inv_facility_fallback"
                )
            
            item_id = st.selectbox(
                "Item ID",
                ["MED001", "MED002", "MED003", "MED004", "MED005",
                 "ALT001", "ALT002", "VAK001"]
            )
        
        with col2:
            quantity = st.number_input("Quantity", min_value=1, value=10)
            tx_type = st.radio("Transaction Type", ["IN", "OUT"], horizontal=True)
        
        if st.button("Update Stock", type="primary", use_container_width=True):
            with st.spinner("Updating stock..."):
                result = api_post("/services/inventory/update", {
                    "facilityId": facility_id,
                    "itemId": item_id,
                    "quantity": quantity,
                    "type": tx_type
                })
                
                if result.get("status") == "SUCCESS":
                    st.markdown(f'<div class="success-box">✓ {result.get("message")}</div>', 
                              unsafe_allow_html=True)
                    st.balloons()
                else:
                    st.markdown(f'<div class="error-box">✗ {result.get("message", result.get("error"))}</div>', 
                              unsafe_allow_html=True)
    
    with tab3:
        st.subheader("Stock Anomalies Detection")

        if st.button("Detect Anomalies", type="primary"):
            with st.spinner("Analyzing inventory..."):
                anomalies_data = api_get("/services/inventory/anomalies")

                if anomalies_data.get("status") == "SUCCESS":
                    anomalies = anomalies_data.get("data", {})

                    # FIX: Ganti key names sesuai backend response
                    understocked = anomalies.get('understocked', [])
                    near_expiry = anomalies.get('near_expiry', [])
                    overstocked = anomalies.get('overstocked', [])

                    col1, col2, col3 = st.columns(3)

                    with col1:
                        st.metric("Low Stock", len(understocked))
                    with col2:
                        st.metric("Expiring Soon", len(near_expiry))
                    with col3:
                        st.metric("Overstock", len(overstocked))

                    # Show understocked items
                    if understocked:
                        st.warning("⚠️ Low Stock Items")
                        df_under = pd.DataFrame(understocked)
                        st.dataframe(df_under, use_container_width=True)

                    # Show near expiry items
                    if near_expiry:
                        st.warning("⏰ Items Expiring Soon")
                        df_expiry = pd.DataFrame(near_expiry)
                        st.dataframe(df_expiry, use_container_width=True)

                    # Show overstocked items
                    if overstocked:
                        st.info("📦 Overstock Items")
                        df_over = pd.DataFrame(overstocked)
                        st.dataframe(df_over, use_container_width=True)

                    # No anomalies
                    if not any([understocked, near_expiry, overstocked]):
                        st.markdown('<div class="success-box">✓ No anomalies detected! All stock levels are healthy.</div>', 
                                  unsafe_allow_html=True)
                else:
                    st.markdown(f'<div class="error-box">❌ Failed: {anomalies_data.get("error")}</div>', 
                              unsafe_allow_html=True)

# ========================================
# PAGE: REDISTRIBUTION 
# ========================================
elif page == "Redistribution":
    st.title("Stock Redistribution Management")
    
    tab1, tab2, tab3 = st.tabs(["Generate", "Pending", "Approved"])
    
    with tab1:
        st.subheader("Generate Redistribution Recommendations")
        
        st.markdown("""
        <div class="info-box">
        This feature analyzes inventory levels across all facilities and generates 
        smart redistribution recommendations to balance stock levels.
        </div>
        """, unsafe_allow_html=True)
        
        if st.button("Generate Recommendations", type="primary", use_container_width=True):
            with st.spinner("Analyzing inventory and generating recommendations..."):
                result = api_post("/services/redistribution/generate", {})
                
                if result.get("status") == "SUCCESS":
                    data = result.get("data", {})
                    st.markdown(f'<div class="success-box">✓ Generated {data.get("recommendations_created", 0)} recommendations!</div>', 
                              unsafe_allow_html=True)
                    
                    if data.get("details"):
                        st.json(data.get("details"))
                    
                    st.success("Recommendations are now in 'Pending' tab for review.")
                else:
                    st.markdown(f'<div class="error-box">❌ Error: {result.get("error", "Unknown error")}</div>', 
                              unsafe_allow_html=True)
    
    with tab2:
        st.subheader("Pending Redistribution Recommendations")
        
        col1, col2 = st.columns([3, 1])
        with col2:
            if st.button("Refresh", use_container_width=True):
                st.rerun()
        
        pending_data = api_get("/services/redistribution/pending")
        
        if pending_data.get("status") == "SUCCESS":
            recommendations = pending_data.get("recommendations", [])
            
            if recommendations:
                st.metric("Pending Recommendations", len(recommendations))
                
                for rec in recommendations:
                    with st.expander(f"{rec.get('ITEM_NAME', 'Unknown')} - {rec.get('FROM_FACILITY_NAME', 'N/A')} → {rec.get('TO_FACILITY_NAME', 'N/A')}"):
                        col1, col2, col3 = st.columns(3)
                        
                        with col1:
                            st.write("**From:**")
                            st.write(f"{rec.get('FROM_FACILITY_NAME', 'N/A')}")
                            st.write(f"Current: {rec.get('FROM_CURRENT_STOCK', 0)}")
                            st.write(f"After: {rec.get('FROM_AFTER_STOCK', 0)}")
                        
                        with col2:
                            st.write("**To:**")
                            st.write(f"{rec.get('TO_FACILITY_NAME', 'N/A')}")
                            st.write(f"Current: {rec.get('TO_CURRENT_STOCK', 0)}")
                            st.write(f"After: {rec.get('TO_AFTER_STOCK', 0)}")
                        
                        with col3:
                            st.write("**Details:**")
                            st.write(f"Quantity: {rec.get('QUANTITY', 0)}")
                            st.write(f"Priority: {rec.get('PRIORITY', 'N/A')}")
                            st.write(f"Created: {rec.get('CREATED_AT', 'N/A')}")
                        
                        st.divider()
                        
                        col_approve, col_reject = st.columns([1, 1])
                        
                        with col_approve:
                            approver_name = st.text_input(
                                "Approved by:",
                                key=f"approver_{rec.get('RECOMMENDATION_ID')}",
                                placeholder="Your name"
                            )
                            
                            if st.button(
                                "Approve",
                                key=f"approve_{rec.get('RECOMMENDATION_ID')}",
                                type="primary",
                                use_container_width=True
                            ):
                                if approver_name.strip():
                                    with st.spinner("Processing approval..."):
                                        approve_result = api_post("/services/redistribution/approve", {
                                            "recommendationId": rec.get('RECOMMENDATION_ID'),
                                            "approvedBy": approver_name
                                        })
                                        
                                        if approve_result.get("status") == "SUCCESS":
                                            st.success(f"{approve_result.get('message')}")
                                            st.rerun()
                                        else:
                                            st.error(f" {approve_result.get('message', approve_result.get('error'))}")
                                else:
                                    st.warning("Please enter approver name")
            else:
                st.markdown('<div class="info-box">No pending recommendations. Generate new ones in the "Generate" tab.</div>', 
                          unsafe_allow_html=True)
        else:
            st.markdown(f'<div class="error-box"> Failed to fetch: {pending_data.get("error")}</div>', 
                      unsafe_allow_html=True)
    
    with tab3:
        st.subheader("Approved Redistributions History")

        col1, col2 = st.columns([3, 1])
        with col2:
            if st.button("Refresh History", use_container_width=True):
                st.rerun()

        # FIX: Call actual endpoint
        approved_data = api_get("/services/redistribution/approved")

        if approved_data.get("status") == "SUCCESS":
            approved_list = approved_data.get("data", [])

            if approved_list:
                st.metric("Total Approved", len(approved_list))

                # Convert to DataFrame for better display
                df_approved = pd.DataFrame(approved_list)

                # Rename columns for display
                column_mapping = {
                    'RECOMMENDATION_ID': 'ID',
                    'SOURCE_FACILITY': 'From Facility',
                    'DESTINATION_FACILITY': 'To Facility',
                    'ITEM_NAME': 'Item',
                    'QUANTITY_TO_MOVE': 'Quantity',
                    'PRIORITY_SCORE': 'Priority',
                    'APPROVED_BY': 'Approved By',
                    'APPROVED_AT': 'Approved Date',
                    'STATUS': 'Status'
                }

                # Select and rename columns
                display_cols = [col for col in column_mapping.keys() if col in df_approved.columns]
                df_display = df_approved[display_cols].rename(columns=column_mapping)

                st.dataframe(df_display, use_container_width=True)

            else:
                st.markdown('<div class="info-box">No approved redistributions yet.</div>', 
                          unsafe_allow_html=True)
        else:
            st.markdown(f'<div class="error-box">❌ Failed to fetch: {approved_data.get("error")}</div>', 
                      unsafe_allow_html=True)

# ========================================
# PAGE: WEATHER
# ========================================
elif page == "Weather":
    st.title("Weather Data")
    
    tab1, tab2, tab3 = st.tabs(["Current Data", "Fetch Single", "Fetch All"])
    
    with tab1:
        st.subheader("Current Weather Records")
        
        weather_data = api_get("/test/weather")
        
        if weather_data.get("status") == "SUCCESS":
            df_weather = pd.DataFrame(weather_data.get("data", []))
            
            if not df_weather.empty:
                st.dataframe(df_weather, use_container_width=True)
                
                # --- FIX DATE ---
                df_weather['DATE'] = pd.to_datetime(df_weather['DATE'], errors='coerce')

                # Sort biar urut
                df_weather = df_weather.sort_values(['FACILITY_NAME', 'DATE'])

                # Bikin index waktu buatan per facility
                df_weather['TIME_INDEX'] = df_weather.groupby('FACILITY_NAME').cumcount()

                fig = px.bar(
                    df_weather,
                    x='FACILITY_NAME',
                    y='TEMPERATURE_AVG',
                    title='Current Average Temperature per Facility',
                    labels={'TEMPERATURE_AVG': 'Temperature (°C)'},
                    color='TEMPERATURE_AVG',
                    color_continuous_scale='RdYlBu_r'
                )

                fig.update_layout(
                    xaxis_tickangle=-30,
                    template='plotly_dark' if st.session_state.theme == 'dark' else 'plotly_white',
                    paper_bgcolor='rgba(0,0,0,0)',
                    plot_bgcolor='rgba(0,0,0,0)'
                )

                st.plotly_chart(
                    fig,
                    use_container_width=True,
                    key="weather_temperature_chart"
                )
            else:
                st.markdown('<div class="info-box">No weather data available</div>', unsafe_allow_html=True)
        else:
            st.markdown(f'<div class="error-box">Failed to fetch weather: {weather_data.get("error")}</div>', 
                      unsafe_allow_html=True)
    
    with tab2:
        st.subheader("Fetch Weather for Single Facility")
        
        col1, col2 = st.columns(2)
        
        with col1:
            facility_id = st.selectbox(
                "Facility ID",
                ["PKM001", "PKM002", "PKM003", "PKM004", "PKM005", "PKM006", "PKM007", "PKM008", "PKM009", "PKM0010"],
                key="weather_facility"
            )
            lat = st.number_input("Latitude", value=-7.1234, format="%.6f")
        
        with col2:
            lon = st.number_input("Longitude", value=107.5678, format="%.6f")
        
        if st.button("Fetch Weather", type="primary"):
            with st.spinner("Fetching weather data..."):
                result = api_post("/services/weather/fetch", {
                    "facilityId": facility_id,
                    "lat": lat,
                    "lon": lon
                })
                
                if result.get("status") == "SUCCESS":
                    st.markdown(f'<div class="success-box">✓ {result.get("message")}</div>', 
                              unsafe_allow_html=True)
                else:
                    st.markdown(f'<div class="error-box">✗ {result.get("message", result.get("error"))}</div>', 
                              unsafe_allow_html=True)
    
    with tab3:
        st.subheader("Fetch Weather for All Facilities")
        
        if st.button("Fetch All Weather Data", type="primary"):
            with st.spinner("Fetching weather data for all facilities..."):
                result = api_post("/services/weather/fetch-all", {})
                
                if result.get("status") == "SUCCESS":
                    st.markdown(f'<div class="success-box">✓ {result.get("message")}</div>', 
                              unsafe_allow_html=True)
                else:
                    st.markdown(f'<div class="error-box">✗ {result.get("message", result.get("error"))}</div>', 
                              unsafe_allow_html=True)

# ========================================
# PAGE: SYSTEM HEALTH
# ========================================
elif page == "System Health":
    st.title("System Health Check")
    
    col1, col2 = st.columns(2)
    
    with col1:
        st.subheader("Backend Health")
        health_data = api_get("/test/health")
        
        if health_data.get("status") == "UP":
            st.markdown('<div class="success-box">✓ Backend is running</div>', 
                      unsafe_allow_html=True)
            st.json(health_data)
        else:
            st.markdown('<div class="error-box">✗ Backend is down</div>', 
                      unsafe_allow_html=True)
    
    with col2:
        st.subheader("Snowflake Connection")
        snowflake_data = api_get("/test/snowflake")
        
        if snowflake_data.get("status") == "SUCCESS":
            st.markdown('<div class="success-box">✓ Snowflake connected</div>', 
                      unsafe_allow_html=True)
            st.json(snowflake_data)
        else:
            st.markdown('<div class="error-box">✗ Snowflake connection failed</div>', 
                      unsafe_allow_html=True)

# ========================================
# RUN THE APPLICATION
# ========================================
if __name__ == "__main__":
    # You can run this script directly with: streamlit run app.py
    pass