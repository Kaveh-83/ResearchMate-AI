from __future__ import annotations
import os, tempfile, json
import streamlit as st
import pandas as pd
from research_engine import run_research, fallback_synthesis, synthesize_with_llm
from outputs import package_outputs

APP_BUILD_ID = "2026-11-09.2"
st.set_page_config(page_title="ResearchMate AI | Scientific Research Workspace", page_icon="🔬", layout="wide")
st.markdown("""
<style>
html, body, [data-testid="stAppViewContainer"] { direction: rtl; }
[data-testid="stSidebar"] * { direction: rtl; text-align: right; }
[data-testid="stMarkdownContainer"], label, p, h1, h2, h3, h4 { text-align: right; }
[data-testid="stDataFrame"] { direction: ltr; }
.small-muted { color: #738194; font-size: .88rem; }
.block-container { padding-top: 1.5rem; }
</style>
""", unsafe_allow_html=True)

st.title("🔬 ResearchMate AI")
st.caption(f"نسخه ۲٫۰ | دو مسیر پژوهشی مستقل | Build {APP_BUILD_ID}")
st.markdown("### انتخاب مسیر پژوهش")
nav_left, nav_right = st.columns(2)
with nav_left:
    st.page_link("pages/1_ابزارهای_بدون_AI.py", label="🔬 ابزارهای بدون هوش مصنوعی", use_container_width=True)
    st.caption("جست‌وجوی Open Access، بازیابی قانونی، استخراج متن و جدول، فراداده و فایل‌های داده — بدون API مدل زبانی.")
with nav_right:
    st.page_link("pages/2_سازنده_پرامپت.py", label="✍️ سازنده پرامپت تخصصی", use_container_width=True)
    st.caption("انتخاب امکانات و نوع گزارش؛ تولید پرامپت انگلیسی با زبان خروجی فارسی یا انگلیسی و چرخه نقد، اصلاح و داوری.")
st.info("برای تحلیل معنایی، تفسیر علمی، شناسایی شکاف پژوهشی و داوری تخصصی از سازنده پرامپت استفاده کن و پرامپت را به مدل دلخواهت بده. مسیر قدیمی Gemini در پایین‌ترِ منوی کناری فقط برای سازگاری نگه داشته شده است.")
st.divider()

def _read_secret(name: str) -> str:
    try:
        return str(st.secrets.get(name, "") or "")
    except Exception:
        return ""
