from __future__ import annotations
import os, tempfile, json
import streamlit as st
import pandas as pd
from research_engine import run_research
from outputs import package_outputs

st.set_page_config(page_title="ResearchMate AI | Scientific Research Agent", page_icon="🔬", layout="wide")
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
st.caption("نسخه ۱٫۱ | جست‌وجوی Open Access و تحلیل شواهد علمی")
st.info("جست‌وجو فقط در میان مقاله‌ها و مقاله‌مرورهای دارای برچسب دسترسی‌آزاد (Open Access) انجام می‌شود. برنامه سپس نسخه‌های قانونی و آزاد متن کامل را دریافت می‌کند. آزاد بودن رکورد لزوماً به معنی موفقیت دانلود/استخراج متن نیست؛ موارد ناموفق با سطح شواهد چکیده یا فراداده باقی می‌مانند.")

def _read_secret(name: str) -> str:
    try:
        return str(st.secrets.get(name, "") or "")
    except Exception:
        return ""


with st.sidebar:
    st.header("تعریف پروژه پژوهشی")
    topic = st.text_area("موضوع یا سؤال پژوهش", value="کاربرد یادگیری ماشین در فولادسازی و بهینه‌سازی مصرف انرژی", height=100,
                         help="سؤال دقیق‌تر، نتایج مرتبط‌تری می‌دهد. می‌توانی حوزه، فرایند، ماده و خروجی موردنظر را بنویسی.")
    c1,c2 = st.columns(2)
    year_from = c1.number_input("از سال", min_value=1900, max_value=2026, value=2018)
    year_to = c2.number_input("تا سال", min_value=1900, max_value=2026, value=2026)
    max_papers = st.slider("حداکثر تعداد مقاله برای این اجرا", min_value=5, max_value=15, value=8)
    st.divider()
    st.subheader("دریافت متن کامل آزاد از OpenAlex")
    openalex_api_key = st.text_input("کلید رایگان OpenAlex (اختیاری)", value=_read_secret("OPENALEX_API_KEY"), type="password", help="برای افزایش سهمیه API و دسترسی به فایل‌های ذخیره‌شده OpenAlex. می‌توانی آن را در Streamlit App settings → Secrets با نام OPENALEX_API_KEY ذخیره کنی.")
    st.caption("جست‌وجوی مقاله‌های Open Access بدون این کلید هم انجام می‌شود؛ کلید فقط سهمیه و مسیرهای بازیابی متن ذخیره‌شده را بهبود می‌دهد.")
    st.divider()
    st.subheader("تحلیل عمیق با مدل زبانی")
    api_key = st.text_input("کلید API رایگان Gemini", value=_read_secret("GEMINI_API_KEY"), type="password", help="برای تحلیل عمیق مقاله‌به‌مقاله و سنتز بین‌مقاله‌ای لازم است. می‌توانی آن را در Streamlit App settings → Secrets با نام GEMINI_API_KEY ذخیره کنی؛ هرگز کلید را در GitHub یا کد قرار نده.")
    model = st.selectbox("مدل", ["gemini-3.8-flash", "gemini-3.7-flash", "gemini-3.5-flash-lite"], index=0)
    consent = st.checkbox("رضایت می‌دهم متن مقاله‌های آزاد و فراداده آن‌ها برای تحلیل به Google Gemini ارسال شود. از شرایط استفاده و حریم خصوصی پلن رایگان آگاه هستم.", value=False)
    if not api_key.strip():
        st.warning("کلید Gemini وارد نشده است؛ اجرا در حالت استخراج ساختاری خواهد بود و تحلیل عمیق انجام نمی‌شود.")
    elif not consent:
        st.info("کلید موجود است، اما تا زمانی که رضایت ارسال متن مقاله به Gemini را فعال نکنی، تحلیل مدل‌محور اجرا نمی‌شود.")
    else:
        st.caption("کلید وارد شده و رضایت فعال است؛ پس از اجرا، وضعیت واقعی فراخوانی مدل در گزارش نمایش داده می‌شود.")
    run_btn = st.button("اجرای پژوهش کامل", type="primary", use_container_width=True)

if run_btn:
    if not topic.strip():
        st.error("لطفاً موضوع پژوهش را بنویس.")
    elif year_from > year_to:
        st.error("سال شروع نباید از سال پایان بزرگ‌تر باشد.")
    else:
        progress = st.progress(0)
        status = st.empty()
        def update_progress(message, done, total):
            status.write(message)
            progress.progress(min(0.98, done / max(total,1)))
        try:
            with st.spinner("در حال جست‌وجو، دریافت متن کامل و تحلیل شواهد..."):
                bundle = run_research(topic.strip(), int(year_from), int(year_to), int(max_papers),
                                      api_key=api_key.strip(), model=model, openalex_api_key=openalex_api_key.strip(), consent_to_send=consent,
                                      progress=update_progress)
            st.session_state["research_bundle"] = bundle
            st.session_state.pop("research_files", None)
            progress.progress(1.0)
            status.success("بازیابی و تحلیل اولیه تمام شد. اکنون شواهد و سطح دسترسی هر مقاله را بررسی کن.")
        except Exception as exc:
            st.error(f"اجرای پژوهش ناموفق بود: {exc}")

bundle = st.session_state.get("research_bundle")
if not bundle:
    st.subheader("جریان کاری پژوهش")
    st.markdown("""
    1. **جست‌وجوی ادبیات:** بازیابی رکوردهای علمی در بازه زمانی انتخاب‌شده.
    2. **دریافت خودکار متن:** تلاش برای دریافت PDF یا XML آزاد از مخازن علمی.
    3. **خواندن مقاله:** استخراج متن، بخش‌ها، جدول‌های قابل‌خواندن و شواهد عددی.
    4. **تحلیل و سنتز:** تحلیل مقاله‌به‌مقاله و سپس مقایسه شواهد بین مقالات.
    5. **خروجی علمی:** پیش‌نویس مقاله مروری، پوستر قابل ویرایش، ماتریس شواهد و نقشه ارتباط مقالات.
    """)
    st.caption("برای شروع، موضوع را در ستون کناری وارد و «اجرای پژوهش کامل» را انتخاب کن.")
else:
    papers = bundle["papers"]
    docs = bundle["documents"]
    analyses = bundle["analyses"]
    synthesis = bundle["synthesis"]
    full_count = sum(1 for d in docs if d.get("status")=="full_text")
    abstract_count = sum(1 for d in docs if d.get("status")=="abstract_only")
    metadata_count = sum(1 for d in docs if d.get("status")=="metadata_only")
    a,b,c,d = st.columns(4)
    a.metric("رکوردهای بازیابی‌شده", len(papers))
    b.metric("متن کامل دریافت‌شده", full_count)
    c.metric("فقط چکیده", abstract_count)
    d.metric("فقط فراداده", metadata_count)
    if bundle.get("llm_enabled"):
        st.success("تحلیل مدل‌محور و سنتز بین‌مقاله‌ای اجرا شده است. نقل‌قول‌ها با متن منبع تطبیق داده شده‌اند؛ با این حال، نتیجه‌گیری‌ها همچنان به بازبینی پژوهشگر نیاز دارند.")
    else:
        llm_error = synthesis.get("llm_error")
        if llm_error:
            st.error(f"تحلیل Gemini اجرا نشد: {llm_error}")
        elif not api_key.strip():
            st.warning("تحلیل عمیق اجرا نشده است، چون کلید Gemini ارائه نشده. جست‌وجوی OA و استخراج اولیه مستقل از Gemini انجام شده‌اند.")
        elif not consent:
            st.warning("تحلیل عمیق اجرا نشده است، چون رضایت ارسال متن مقاله به Gemini فعال نشده است.")
        else:
            st.warning("تحلیل عمیق مدل‌محور کامل نشد؛ گزارش اجرا را برای علت دقیق بررسی کن. خروجی فعلی تحلیل نهایی علمی نیست.")

    tabs = st.tabs(["نمای کلی و دسترسی", "تحلیل تک‌مقاله‌ای", "مقایسه و سنتز", "شکاف‌ها و نتیجه", "خروجی‌های علمی", "گزارش اجرا"])
    with tabs[0]:
        rows=[]
        for p,doc in zip(papers,docs):
            rows.append({"عنوان":p.get("title"),"سال":p.get("year"),"نشریه":p.get("journal"),"استناد":p.get("citations"),
                         "وضعیت OA":p.get("oa_status"),"مجوز":p.get("license"),"پیوندهای OA":p.get("oa_location_count", 0),
                         "وضعیت متن":doc.get("status"),"DOI":p.get("doi"),"منبع متن":doc.get("source_url") or p.get("oa_url")})
        st.dataframe(pd.DataFrame(rows), use_container_width=True, hide_index=True)
        st.caption("دامنه جست‌وجو: OpenAlex با فیلتر Open Access و نوع مقاله/مقاله‌مرور. رکوردهای OA که دانلود متن کامل آن‌ها ناموفق باشد حذف نمی‌شوند؛ تفاوت «متن کامل»، «فقط چکیده» و «فراداده» در قدرت شواهد مهم است.")
        for p,doc in zip(papers,docs):
            with st.expander(p.get("title","مقاله بدون عنوان")):
                st.write(f"سال: {p.get('year')} | نویسندگان: {p.get('authors_text') or 'نامشخص'}")
                st.write(f"DOI: {p.get('doi') or 'ثبت نشده'} | مجله: {p.get('journal') or 'نامشخص'}")
                st.write(f"وضعیت دسترسی در OpenAlex: {p.get('oa_status') or 'نامشخص'} | مجوز ثبت‌شده: {p.get('license') or 'نامشخص'}")
                st.write(f"وضعیت: {doc.get('status')} | علت: {doc.get('reason')}")
                if doc.get("source_url"): st.markdown(f"[مشاهده نسخه آزاد دریافت‌شده]({doc['source_url']})")
                if p.get("abstract"): st.markdown("**چکیده:** " + p["abstract"][:2500])
                if doc.get("tables"): st.write(f"تعداد جدول‌های استخراج‌شده: {len(doc['tables'])}")
    with tabs[1]:
        for p,doc,analysis in zip(papers,docs,analyses):
            with st.expander(f"{p.get('year')} | {p.get('title','')}", expanded=False):
                st.write("**سطح شواهد:**", analysis.get("evidence_level"))
                for key,label in [("problem","مسئله پژوهش"),("objective","هدف"),("hypothesis","فرضیه"),("materials","مواد"),("methods","روش‌ها"),("methodology","روش‌های شناسایی‌شده"),("process_conditions","شرایط فرایند"),("input_variables","متغیرهای ورودی"),("output_variables","متغیرهای خروجی"),("metrics","معیارها"),("study_design","طرح مطالعه"),("sample_size","حجم نمونه"),("dataset","مجموعه‌داده"),("alloy_composition","ترکیب آلیاژ"),("equipment_instrumentation","تجهیزات و ابزار"),("validation_strategy","راهبرد اعتبارسنجی"),("baseline_comparators","روش‌های مبنا"),("measured_properties","خواص اندازه‌گیری‌شده"),("risk_of_bias","خطر سوگیری"),("reproducibility","بازتولیدپذیری"),("industrial_applicability","کاربرد صنعتی"),("quantitative_findings","یافته‌های کمی"),("findings","یافته‌ها"),("limitations","محدودیت‌ها"),("future_work","کار آینده")]:
                    value=analysis.get(key)
                    if value:
                        st.markdown(f"**{label}:** {value if isinstance(value,str) else json.dumps(value,ensure_ascii=False)}")
                quotes=analysis.get("evidence_quotes",[])
                if quotes:
                    st.markdown("**شواهد و نقل‌قول‌ها:**")
                    for ev in quotes:
                        st.markdown(f"> {ev.get('quote','')}  \n> صفحه: {ev.get('page') or 'نامشخص'}")
    with tabs[2]:
        st.subheader("سنتز بین‌مقاله‌ای")
        for key,label in [("thematic_synthesis","سنتز موضوعی"),("methods_comparison","مقایسه روش‌ها"),("results_comparison","مقایسه نتایج"),("convergent_evidence","شواهد همسو"),("conflicting_evidence","شواهد متناقض"),("explanations_for_conflicts","تبیین اختلاف‌ها")]:
            st.markdown(f"### {label}")
            value=synthesis.get(key,[])
            if isinstance(value,list):
                for item in value: st.write("•", item if isinstance(item,str) else json.dumps(item,ensure_ascii=False))
            else: st.write(value or "برای این بخش شواهد کافی تولید نشده است.")
        if synthesis.get("claims"):
            st.markdown("### ادعاهای کلیدی و ردیابی منبع")
            for claim in synthesis["claims"]:
                st.write(f"**{claim.get('claim','')}** | پشتیبانی: {claim.get('support','نامشخص')} | منابع: {', '.join(claim.get('paper_ids',[]))}")
                if claim.get("reason"): st.caption(claim["reason"])
    with tabs[3]:
        st.markdown("### شکاف‌های پژوهشی")
        gaps=synthesis.get("research_gaps",[])
        if gaps:
            for i,g in enumerate(gaps,1): st.markdown(f"**{i}.** {g if isinstance(g,str) else json.dumps(g,ensure_ascii=False)}")
        else: st.info("شکاف پژوهشی مستند کافی در این اجرا تعیین نشده است.")
        st.markdown("### مسیرهای آینده")
        for item in synthesis.get("future_directions",[]): st.write("•",item)
        st.markdown("### نتیجه‌گیری")
        st.write(synthesis.get("conclusion","نتیجه‌گیری موجود نیست."))
        st.markdown("### محدودیت مرور")
        st.write(synthesis.get("limitations_of_review","محدودیت ثبت نشده است."))
        st.caption("هر شکاف پیشنهادی باید با جست‌وجوی تکمیلی، بررسی متن کامل و نظر متخصص راستی‌آزمایی شود.")
    with tabs[4]:
        st.subheader("تولید بسته خروجی‌های علمی")
        st.write("هر خروجی متناسب با کاربرد خود ساخته می‌شود؛ مقاله مروری، پوستر مستقل، ماتریس شواهد، نقشه ارتباط مقالات، گزارش روش‌شناسی و فایل منابع.")
        if st.button("ساخت همه خروجی‌ها", type="primary", key="build_all_outputs"):
            try:
                with st.spinner("در حال تولید DOCX، PDF، PPTX، XLSX و نمودارها..."):
                    output_dir=tempfile.mkdtemp(prefix="researchmate_")
                    files=package_outputs(bundle,output_dir)
                    st.session_state["research_files"]={k:open(v,"rb").read() for k,v in files.items()}
                st.success("همه خروجی‌ها تولید شدند.")
            except Exception as exc:
                st.error(f"تولید خروجی ناموفق بود: {exc}")
        files=st.session_state.get("research_files",{})
        labels={"review_docx":"مقاله مروری قابل ویرایش (DOCX)","review_pdf":"مقاله مروری (PDF)","poster_pptx":"پوستر علمی قابل ویرایش (PPTX)","poster_pdf":"پوستر علمی (PDF A1)","evidence_xlsx":"ماتریس مقایسه و شواهد (XLSX)","evidence_map":"نقشه ارتباط مقالات (PNG)","bibtex":"منابع (BibTeX)","methodology_pdf":"گزارش روش‌شناسی و ممیزی شواهد (PDF)","output_warnings":"گزارش خطاهای تولید خروجی (TXT)","package_zip":"دانلود همه خروجی‌ها (ZIP)"}
        extensions={"review_docx":"docx","review_pdf":"pdf","poster_pptx":"pptx","poster_pdf":"pdf","evidence_xlsx":"xlsx","evidence_map":"png","bibtex":"bib","methodology_pdf":"pdf","output_warnings":"txt","package_zip":"zip"}
        for key,label in labels.items():
            if key in files:
                st.download_button(label,files[key],file_name=f"ResearchMate_{key}.{extensions[key]}",mime="application/octet-stream",key=f"download_{key}",use_container_width=True)
    with tabs[5]:
        st.markdown("### ممیزی اجرا")
        for log in bundle.get("logs",[]): st.write("•",log)
        st.write("**شناسه اجرا:**",bundle.get("run_id"))
        st.write("**حالت تحلیل:**",synthesis.get("analysis_mode"))
        st.write("**تاریخ اجرا بر اساس زمان سرویس:**",pd.Timestamp.now().isoformat())
        if st.button("پاک‌کردن نتایج این نشست"):
            for key in ["research_bundle","research_files"]: st.session_state.pop(key,None)
            st.rerun()

st.divider()
st.caption("ResearchMate AI 1.0 experimental | OpenAlex + Europe PMC + OA full-text extraction + evidence-aware synthesis")
