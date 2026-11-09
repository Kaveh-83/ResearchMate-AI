from __future__ import annotations

import json
from datetime import datetime
import pandas as pd
import streamlit as st

from research_engine import run_research
from non_ai_exports import build_non_ai_exports

st.set_page_config(page_title="ابزارهای بدون هوش مصنوعی | ResearchMate", page_icon="🔬", layout="wide")
st.markdown("""
<style>
html, body, [data-testid="stAppViewContainer"] { direction: rtl; }
[data-testid="stSidebar"] * { direction: rtl; text-align: right; }
[data-testid="stMarkdownContainer"], label, p, h1, h2, h3, h4 { text-align: right; }
[data-testid="stDataFrame"] { direction: ltr; }
.block-container { padding-top: 1.3rem; }
</style>
""", unsafe_allow_html=True)

st.title("🔬 ابزارهای قطعی؛ بدون هوش مصنوعی")
st.caption("جست‌وجو، بازیابی قانونی متن آزاد، استخراج فنی و ساخت فایل‌های داده — بدون Gemini، DeepSeek، ChatGPT یا API مدل زبانی.")
st.success("این صفحه هیچ مدل زبانی را فراخوانی نمی‌کند. تمام نتایج آن از APIهای علمی، متن مقاله و قواعد قابل تکرار استخراج می‌شوند.")

with st.expander("این بخش دقیقاً چه کارهایی را انجام می‌دهد؟", expanded=True):
    st.markdown("""
    **قابلیت‌های اجرایی و غیرهوش‌مصنوعی**
    - جست‌وجوی اکتشافی مقاله و مقاله‌مرور در OpenAlex با فیلتر صریح Open Access.
    - دریافت فراداده کتاب‌شناختی، DOI، سال، نویسندگان، مجله، تعداد استناد و وضعیت دسترسی.
    - تلاش برای دریافت متن کامل فقط از محل‌هایی که منبع آن‌ها دسترسی‌آزاد را تأیید کرده است؛ بدون دور زدن paywall.
    - استخراج متن PDF/XML/HTML، شماره صفحه در صورت وجود، و جدول‌های قابل استخراج.
    - شناسایی واژه‌محور روش‌ها و مواد، الگوهای عددی/واحدها، و پاراگراف‌های حاوی نشانه‌های نتیجه یا محدودیت.
    - ساخت CSV، Excel، JSON، BibTeX، متن استخراج‌شده و بسته ZIP.

    **این صفحه انجام نمی‌دهد:** تفسیر معنایی، توضیح مکانیسم، تشخیص شکاف پژوهشی، داوری محتوایی، ارزیابی قطعی کیفیت مطالعه یا نگارش مقاله قابل انتشار. خروجی واژه‌محور فقط «سرنخ/نامزد استخراج» است و باید با منبع اصلی تطبیق داده شود.
    """)

def _secret(name: str) -> str:
    try:
        return str(st.secrets.get(name, "") or "")
    except Exception:
        return ""

with st.form("deterministic_search_form"):
    st.subheader("۱. جست‌وجو و بازیابی منابع")
    query = st.text_area(
        "موضوع یا عبارت جست‌وجو",
        value="machine learning steelmaking energy optimization",
        height=85,
        help="OpenAlex جست‌وجوی اکتشافی انجام می‌دهد؛ این جست‌وجو معادل جست‌وجوی جامع نظام‌مند در همه پایگاه‌ها نیست."
    )
    c1, c2, c3 = st.columns(3)
    this_year = datetime.now().year
    year_from = c1.number_input("از سال انتشار", min_value=1900, max_value=this_year, value=max(2018, this_year - 8))
    year_to = c2.number_input("تا سال انتشار", min_value=1900, max_value=this_year, value=this_year)
    max_papers = c3.slider("حداکثر مقاله", min_value=1, max_value=50, value=10)
    openalex_key = st.text_input(
        "کلید رایگان OpenAlex (توصیه‌شده)", value=_secret("OPENALEX_API_KEY"), type="password",
        help="کلید API رایگان OpenAlex سهمیه روزانه را نسبت به درخواست ناشناس افزایش می‌دهد. بدون کلید هم می‌توانی امتحان کنی، اما 429 زودتر رخ می‌دهد. کلید فقط برای پایگاه مقالات است، نه هوش مصنوعی."
    )
    st.markdown(
        "[ساخت حساب و دریافت کلید رایگان OpenAlex](https://openalex.org/settings/api) · "
        "[توضیح سهمیه و خطاهای 429](https://help.openalex.org/api/authentication/)"
    )
    unpaywall_email = st.text_input(
        "ایمیل Unpaywall (اختیاری)", value=_secret("UNPAYWALL_EMAIL"), type="password",
        help="برای بازیابی تکمیلی بر اساس DOI؛ فقط ایمیل خودت را وارد کن. هیچ کلید یا ایمیلی در مخزن ذخیره نمی‌شود."
    )
    submit = st.form_submit_button("جست‌وجو و استخراج بدون AI", type="primary", use_container_width=True)

if submit:
    if not query.strip():
        st.error("ابتدا عبارت جست‌وجو را وارد کن.")
    elif year_from > year_to:
        st.error("سال شروع نباید از سال پایان بزرگ‌تر باشد.")
    else:
        progress = st.progress(0)
        status = st.empty()

        def update_progress(message, done, total):
            status.write(message)
            progress.progress(min(0.98, done / max(total, 1)))

        try:
            with st.spinner("در حال جست‌وجوی Open Access و استخراج متن/جدول‌ها؛ هیچ مدل زبانی فراخوانی نمی‌شود..."):
                bundle = run_research(
                    query.strip(), int(year_from), int(year_to), int(max_papers),
                    api_key="", model="", openalex_api_key=openalex_key.strip(),
                    consent_to_send=False, progress=update_progress,
                    unpaywall_email=unpaywall_email.strip()
                )
            if bundle.get("llm_enabled"):
                raise RuntimeError("کنترل ایمنی: مسیر غیرهوش‌مصنوعی نباید مدل زبانی را فعال کند.")
            st.session_state["non_ai_bundle"] = bundle
            st.session_state.pop("non_ai_exports", None)
            progress.progress(1.0)
            status.success("بازیابی و استخراج قطعی پایان یافت. سطح دسترسی و کیفیت استخراج هر مقاله را بررسی کن.")
        except Exception as exc:
            message = str(exc)
            st.error(f"اجرای جست‌وجو ناموفق بود: {message}")
            if "HTTP 429" in message:
                st.info("راهنما: کلید رایگان OpenAlex را از پیوند بالای فرم بگیر، پس از تأیید ایمیل آن را در فیلد کلید وارد کن. این کلید رایگان است و با API هوش مصنوعی ارتباطی ندارد.")
            elif "HTTP 403" in message and "key_not_activated" in message:
                st.info("کلید OpenAlex هنوز فعال نشده است؛ پیوند تأیید ارسال‌شده به ایمیل صاحب کلید را باز کن.")

bundle = st.session_state.get("non_ai_bundle")
if bundle:
    papers = bundle.get("papers", [])
    docs = bundle.get("documents", [])
    analyses = bundle.get("analyses", [])
    full_count = sum(d.get("status") == "full_text" for d in docs)
    abstract_count = sum(d.get("status") == "abstract_only" for d in docs)
    metadata_count = sum(d.get("status") == "metadata_only" for d in docs)
    st.divider()
    st.subheader("۲. نتایج بازیابی و سطح شواهد")
    m1, m2, m3, m4 = st.columns(4)
    m1.metric("مقاله‌های یافت‌شده", len(papers))
    m2.metric("متن کامل استخراج‌شده", full_count)
    m3.metric("فقط چکیده", abstract_count)
    m4.metric("فقط فراداده", metadata_count)

    metadata_rows = []
    for paper, doc in zip(papers, docs):
        metadata_rows.append({
            "عنوان": paper.get("title"), "سال": paper.get("year"),
            "نویسندگان": paper.get("authors_text"), "مجله": paper.get("journal"),
            "DOI": paper.get("doi"), "استناد": paper.get("citations"),
            "نوع": paper.get("type"), "Open Access": paper.get("oa_status"),
            "مجوز": paper.get("license"), "وضعیت متن": doc.get("status"),
            "قالب متن": doc.get("format"), "تعداد جدول": len(doc.get("tables") or []),
            "منبع متن": doc.get("source_url")
        })
    tabs = st.tabs(["فراداده و منابع", "استخراج واژه‌محور", "متن و جدول‌ها", "ساخت خروجی"])
    with tabs[0]:
        st.dataframe(pd.DataFrame(metadata_rows), use_container_width=True, hide_index=True)
        for paper, doc in zip(papers, docs):
            with st.expander(paper.get("title") or "بدون عنوان"):
                st.write(f"نویسندگان: {paper.get('authors_text') or 'ثبت نشده'}")
                st.write(f"سال: {paper.get('year')} | مجله: {paper.get('journal') or 'ثبت نشده'}")
                st.write(f"DOI: {paper.get('doi') or 'ثبت نشده'} | استناد ثبت‌شده: {paper.get('citations', 0)}")
                st.write(f"دسترسی: {paper.get('oa_status')} | مجوز: {paper.get('license')}")
                st.write(f"وضعیت متن: {doc.get('status')} | قالب: {doc.get('format')} | دلیل/یادداشت: {doc.get('reason')}")
                if doc.get("source_url"):
                    st.markdown(f"[باز کردن منبع متن استخراج‌شده]({doc['source_url']})")
    with tabs[1]:
        st.warning("این موارد بر اساس تطبیق واژه و الگو استخراج شده‌اند؛ تشخیص معنایی یا تأیید علمی نیستند.")
        for paper, analysis in zip(papers, analyses):
            with st.expander(paper.get("title") or "بدون عنوان"):
                st.write("**روش‌های دارای نشانه واژگانی**")
                st.json(analysis.get("methodology") or [])
                st.write("**مواد/کلیدواژه‌های شناسایی‌شده**")
                st.write(", ".join(analysis.get("materials") or []) or "موردی پیدا نشد.")
                st.write("**الگوهای عددی و واحدها**")
                st.write(analysis.get("conditions_metrics") or [])
                st.write("**پاراگراف‌های دارای نشانه نتیجه**")
                for item in analysis.get("findings") or []:
                    st.write("•", item)
                st.write("**پاراگراف‌های دارای نشانه محدودیت**")
                for item in analysis.get("limitations") or []:
                    st.write("•", item)
    with tabs[2]:
        for paper, doc in zip(papers, docs):
            with st.expander(f"{paper.get('year')} | {paper.get('title','')}", expanded=False):
                text = doc.get("text") or ""
                st.caption(f"وضعیت: {doc.get('status')} | {len(text):,} نویسه | {len(doc.get('tables') or [] )} جدول استخراج‌شده")
                if text:
                    st.text_area("متن استخراج‌شده", text, height=260, key=f"paper_text_{paper.get('id')}")
                else:
                    st.info("متن قابل استخراج در دسترس نیست؛ فقط فراداده یا چکیده موجود است.")
                for index, table in enumerate(doc.get("tables") or [], 1):
                    st.markdown(f"**جدول استخراج‌شده {index}**")
                    st.dataframe(pd.DataFrame(table), use_container_width=True, hide_index=True)
    with tabs[3]:
        st.subheader("۳. انتخاب محتوای بسته خروجی")
        st.caption("همه خروجی‌ها ماشینی و قابل ردیابی هستند؛ هیچ تفسیر معنایی توسط مدل انجام نشده است.")
        e1, e2, e3 = st.columns(3)
        include = set()
        if e1.checkbox("فراداده کتاب‌شناختی", value=True): include.add("metadata")
        if e1.checkbox("نامزدهای استخراج واژه‌محور", value=True): include.add("signals")
        if e1.checkbox("گزیده‌های شواهد", value=True): include.add("evidence")
        if e2.checkbox("جدول‌های استخراج‌شده", value=True): include.add("tables")
        if e2.checkbox("متن استخراج‌شده مقاله‌ها", value=True): include.add("text")
        if e2.checkbox("منابع BibTeX", value=True): include.add("bibtex")
        if e3.checkbox("داده ساختاریافته JSON", value=True): include.add("json")
        if st.button("ساخت فایل‌های انتخاب‌شده", type="primary", use_container_width=True):
            if not include:
                st.warning("حداقل یک نوع داده را برای خروجی انتخاب کن.")
            else:
                try:
                    with st.spinner("در حال ساخت CSV، Excel، BibTeX، JSON و بسته ZIP..."):
                        st.session_state["non_ai_exports"] = build_non_ai_exports(bundle, include)
                    st.success("خروجی‌ها با موفقیت ساخته شدند.")
                except Exception as exc:
                    st.error(f"ساخت خروجی ناموفق بود: {exc}")
        export_files = st.session_state.get("non_ai_exports", {})
        if export_files:
            st.markdown("**دانلود فایل‌ها**")
            labels = {
                "researchmate_evidence_workbook.xlsx": "دفترکل شواهد و فراداده (Excel)",
                "researchmate_deterministic_package.zip": "بسته کامل انتخاب‌شده (ZIP)",
                "literature_metadata.csv": "فراداده مقالات (CSV)",
                "rule_based_candidates.csv": "سرنخ‌های واژه‌محور (CSV)",
                "evidence_excerpts.csv": "گزیده‌های شواهد (CSV)",
                "extracted_tables.csv": "جدول‌های استخراج‌شده (CSV)",
                "references.bib": "منابع (BibTeX)",
                "researchmate_data.json": "داده ساختاریافته (JSON)",
                "run_summary.txt": "خلاصه اجرای پژوهش (TXT)",
            }
            for filename, data in export_files.items():
                st.download_button(
                    labels.get(filename, filename), data=data, file_name=filename,
                    mime="application/octet-stream", key=f"non_ai_download_{filename}",
                    use_container_width=True
                )

    st.caption(f"شناسه اجرا: {bundle.get('run_id')} | حالت: fallback_rules_only | تعداد رکورد: {len(papers)}")
    st.page_link("pages/2_سازنده_پرامپت.py", label="برای تحلیل عمیق‌تر، رفتن به سازنده پرامپت")
else:
    st.info("برای شروع، عبارت جست‌وجو و بازه سال انتشار را مشخص کن. این مسیر مستقل از کلید API مدل زبانی است.")

st.divider()
st.caption("ResearchMate AI — Deterministic workspace | OpenAlex + legal OA retrieval + text/table extraction + auditable exports")
