from __future__ import annotations

import streamlit as st

APP_BUILD_ID = "2026-11-09.3"

st.set_page_config(
    page_title="ResearchMate AI | Scientific Research Workspace",
    page_icon="🔬",
    layout="wide",
)

st.markdown(
    """
    <style>
    html, body, [data-testid="stAppViewContainer"] { direction: rtl; }
    [data-testid="stSidebar"] * { direction: rtl; text-align: right; }
    [data-testid="stMarkdownContainer"], label, p, h1, h2, h3, h4 { text-align: right; }
    .block-container { padding-top: 1.5rem; }
    </style>
    """,
    unsafe_allow_html=True,
)

st.title("🔬 ResearchMate AI")
st.caption(f"نسخه ۲٫۰ | پژوهش دو‌مسیره، بدون نیاز به API مدل زبانی | Build {APP_BUILD_ID}")
st.markdown(
    """
    ResearchMate کارها را به دو دسته شفاف تقسیم می‌کند:

    - **ابزارهای مستقل از هوش مصنوعی:** جست‌وجوی منابع دسترسی‌آزاد، بازیابی قانونی متن، استخراج داده و جدول، مدیریت فراداده و ساخت فایل‌های مستند.
    - **سازنده پرامپت تخصصی:** تبدیل انتخاب‌های پژوهشگر به دستورالعمل انگلیسی کامل برای مدلی که خودش انتخاب می‌کند؛ زبان گزارش نهایی می‌تواند فارسی یا انگلیسی باشد.
    """
)

left, right = st.columns(2, gap="large")
with left:
    st.subheader("🔬 مسیر اول — ابزارهای بدون AI")
    st.write(
        "جست‌وجوی اکتشافی مقالات Open Access، دریافت نسخه‌های قانونی، "
        "استخراج متن و جدول، شناسایی سرنخ‌های واژه‌محور و خروجی داده."
    )
    st.page_link(
        "pages/1_ابزارهای_بدون_AI.py",
        label="ورود به ابزارهای بدون AI",
        use_container_width=True,
    )
with right:
    st.subheader("✍️ مسیر دوم — سازنده پرامپت")
    st.write(
        "انتخاب وظیفه، نوع گزارش، زبان خروجی، امکانات تخصصی، "
        "ماتریس پوشش، چرخه نقد و اصلاح و داوری نهایی."
    )
    st.page_link(
        "pages/2_سازنده_پرامپت.py",
        label="ورود به سازنده پرامپت",
        use_container_width=True,
    )

st.divider()
st.subheader("مرز قابلیت‌ها و صداقت علمی")
st.markdown(
    """
    - این سایت **مدل زبانی را اجرا نمی‌کند** و برای ساخت پرامپت به کلید API نیاز ندارد.
    - استخراج واژه‌محور و الگوهای عددی، تحلیل معنایی یا تأیید علمی محسوب نمی‌شوند.
    - جست‌وجوی OpenAlex اکتشافی است و جایگزین جست‌وجوی نظام‌مند در همه پایگاه‌ها نیست.
    - فقط نسخه‌هایی دریافت می‌شوند که دسترسی آزاد آن‌ها از مسیرهای مجاز تأیید شده باشد؛ محدودیت دسترسی دور زده نمی‌شود.
    - تحلیل تخصصی، تفسیر تصاویر، استنتاج شکاف پژوهشی و داوری علمی در مدل خارجی انتخاب‌شده توسط کاربر انجام می‌شوند.
    - پرامپت به‌تنهایی دسترسی فنی به لینک یا PDF ایجاد نمی‌کند؛ در صورت نیاز باید فایل مجاز مقاله نیز به مدل مقصد داده شود.
    """
)
st.caption(
    "ResearchMate AI 2.0 — Deterministic research utilities + external-AI prompt studio. "
    "No language-model API is called by this application."
)
