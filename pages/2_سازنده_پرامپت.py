from __future__ import annotations

import streamlit as st

from prompt_engine import PROMPT_FEATURES, REPORT_TYPES, TASK_MODES, build_research_prompt, estimate_prompt_tokens

st.set_page_config(page_title="سازنده پرامپت تخصصی | ResearchMate", page_icon="✍️", layout="wide")
st.markdown("""
<style>
html, body, [data-testid="stAppViewContainer"] { direction: rtl; }
[data-testid="stSidebar"] * { direction: rtl; text-align: right; }
[data-testid="stMarkdownContainer"], label, p, h1, h2, h3, h4 { text-align: right; }
.block-container { padding-top: 1.3rem; }
</style>
""", unsafe_allow_html=True)

st.title("✍️ سازنده پرامپت پژوهشی تخصصی")
st.caption("خود ResearchMate مدل زبانی را صدا نمی‌زند؛ بر اساس انتخاب‌های تو یک دستورکار انگلیسیِ کامل، خودکفا و قابل کپی می‌سازد.")
st.info("پرامپت به انگلیسی نوشته می‌شود؛ زبان گزارش نهایی را تو تعیین می‌کنی و همان زبان به‌صورت الزام صریح داخل پرامپت درج می‌شود.")

# Reuse the results from the deterministic search page, when available.
bundle = st.session_state.get("non_ai_bundle", {})
available_papers = bundle.get("papers") or []
paper_options = {}
for i, paper in enumerate(available_papers):
    label = f"{paper.get('year','?')} | {paper.get('title','Untitled')}"
    doi = paper.get("doi") or ""
    url = f"https://doi.org/{doi}" if doi else (paper.get("oa_url") or paper.get("id") or "")
    if url:
        paper_options[label] = url

st.subheader("۱. موضوع و منابع")
topic = st.text_area("موضوع یا سؤال پژوهش", placeholder="مثلاً اثر پارامترهای عملیات حرارتی بر ریزساختار و خواص فولاد هیپویوتکتوئید...", height=90)
field = st.text_input("حوزه تخصصی", value="Materials Science and Metallurgical Engineering")
academic_level = st.selectbox(
    "سطح علمی گزارش",
    ["Undergraduate / bachelor's", "Graduate / postgraduate", "Doctoral / expert", "Industrial / professional"]
)
manual_sources = st.text_area(
    "لینک مقاله‌ها، DOI یا شناسه منابع (هر خط یک منبع)",
    placeholder="https://doi.org/...\nhttps://.../article.pdf",
    height=115
)
selected_existing = []
if paper_options:
    with st.expander("افزودن مقاله‌های بازیابی‌شده از بخش بدون AI", expanded=False):
        selected_existing = st.multiselect("مقاله‌های مورد استفاده", list(paper_options.keys()))
        for selected_title in selected_existing:
            st.caption(f"{selected_title} — {paper_options[selected_title]}")

c1, c2 = st.columns(2)
with c1:
    task = st.selectbox("وظیفه اصلی", list(TASK_MODES.keys()), index=0)
    report_type = st.selectbox("نوع مقاله/گزارش خروجی", list(REPORT_TYPES.keys()), index=1)
    output_language_label = st.radio("زبان گزارش نهایی", ["فارسی", "English"], horizontal=True)
with c2:
    citation_style = st.selectbox("سبک ارجاع‌دهی", ["IEEE", "APA", "Vancouver", "Chicago", "Harvard", "Use target journal style"])
    output_format = st.selectbox("قالب خروجی مورد انتظار", [
        "Structured report with tables",
        "Journal manuscript with numbered sections",
        "Evidence matrix + technical report",
        "Poster-ready content and visual plan",
        "Slide-by-slide presentation outline",
        "Thesis chapter with appendices"
    ])
    target_journal = st.text_input("مجله هدف (اختیاری)", placeholder="نام مجله یا DOI راهنمای نویسندگان")

st.divider()
st.subheader("۲. انتخاب امکاناتی که باید در گزارش انجام شوند")
st.caption("هر قابلیت انتخابی به دستورالعمل اختصاصی انگلیسی تبدیل می‌شود. در هر دسته می‌توان همه گزینه‌ها را فعال کرد.")
default_features = {
    "خلاصه ساختاریافته", "تحلیل بخش‌به‌بخش", "مواد، نمونه‌ها و ترکیب شیمیایی",
    "روش ساخت و آماده‌سازی", "پارامترها و شرایط آزمایش", "استخراج تمام اعداد و واحدها",
    "استخراج جدول‌ها", "مقایسه روش‌ها", "داوری علمی مستقل", "کنترل ادعاها و ارجاعات",
    "دفتر ثبت شواهد", "شماره صفحه، شکل و جدول"
}
selected_features = []
for group_index, (group_name, features) in enumerate(PROMPT_FEATURES.items()):
    with st.expander(group_name, expanded=group_index in (0, 1, 8, 11)):
        col_a, col_b = st.columns([1, 5])
        if col_a.button("انتخاب همه", key=f"select_all_{group_index}"):
            for feature_index, (label, _) in enumerate(features):
                st.session_state[f"pf_{group_index}_{feature_index}"] = True
        if col_b.button("پاک‌کردن این دسته", key=f"clear_all_{group_index}"):
            for feature_index, (label, _) in enumerate(features):
                st.session_state[f"pf_{group_index}_{feature_index}"] = False
        for feature_index, (label, _) in enumerate(features):
            key = f"pf_{group_index}_{feature_index}"
            value = st.checkbox(label, value=(label in default_features), key=key)
            if value:
                selected_features.append(label)

st.divider()
st.subheader("۳. چرخه ساخت، نقد، اصلاح و داوری")
a, b = st.columns(2)
with a:
    max_rounds = st.slider("حداکثر دورهای نقد و اصلاح", min_value=1, max_value=5, value=3)
    include_coverage = st.checkbox("الزام به ماتریس پوشش تمام موضوعات مقاله", value=True)
    include_evidence = st.checkbox("الزام به دفتر ثبت شواهد و محل دقیق هر ادعا", value=True)
with b:
    include_referee = st.checkbox("داوری مستقل نهایی با تصمیم PASS / NOT APPROVED", value=True)
    include_revision = st.checkbox("گزارش تغییرات و اصلاحات انجام‌شده", value=True)
    st.caption("داوری درون همان مدل، ارزیابی انتقادی شبیه‌سازی‌شده است؛ جایگزین داور انسانی یا تأیید صحت علمی نیست.")

sources_from_search = [paper_options[x] for x in selected_existing if x in paper_options]
all_sources = "\n".join([manual_sources.strip(), *sources_from_search]).strip()
output_language = "Persian" if output_language_label == "فارسی" else "English"

if st.button("ساخت پرامپت تخصصی انگلیسی", type="primary", use_container_width=True):
    if not topic.strip() and not all_sources:
        st.error("حداقل موضوع پژوهش یا یک منبع وارد کن.")
    else:
        prompt = build_research_prompt(
            topic=topic,
            sources=all_sources,
            field=field,
            task=task,
            report_type=report_type,
            output_language=output_language,
            selected_features=selected_features,
            academic_level=academic_level,
            citation_style=citation_style,
            target_journal=target_journal,
            output_format=output_format,
            max_review_rounds=max_rounds,
            include_coverage_matrix=include_coverage,
            include_evidence_register=include_evidence,
            include_reviewer_report=include_referee,
            include_revision_log=include_revision,
        )
        st.session_state["researchmate_generated_prompt"] = prompt

prompt = st.session_state.get("researchmate_generated_prompt")
if prompt:
    st.divider()
    st.subheader("۴. پرامپت نهایی آماده استفاده")
    st.success(f"پرامپت به انگلیسی ساخته شد؛ زبان گزارش درخواستی: {output_language_label}.")
    st.caption(f"طول: {len(prompt):,} نویسه | برآورد تقریبی: {estimate_prompt_tokens(prompt):,} توکن (بسته به مدل متفاوت است).")
    if len(prompt) > 28000:
        st.warning("پرامپت طولانی است. پیش از ارسال، محدودیت متن مدل مقصد را بررسی کن یا برخی امکانات غیرضروری را بردار.")
    st.code(prompt, language="text")
    st.download_button(
        "دانلود پرامپت به‌صورت TXT",
        data=prompt.encode("utf-8"),
        file_name="ResearchMate_Research_Prompt.txt",
        mime="text/plain",
        use_container_width=True
    )
    st.markdown("""
    **نحوه استفاده**
    1. پرامپت را در ChatGPT، DeepSeek، Gemini یا مدل موردنظر باز کن.
    2. اگر مقاله‌ای پشت paywall نیست اما لینک مستقیم قابل خواندن ندارد، PDF مجاز آن را نیز همراه پیام بارگذاری کن.
    3. از مدلی استفاده کن که امکان دسترسی وب/خواندن فایل را دارد؛ پرامپت به‌تنهایی دسترسی فنی به لینک ایجاد نمی‌کند.
    4. خروجی داوری و محدودیت‌های اعلام‌شده را بررسی کن؛ PASS خودکار تضمین پذیرش مقاله یا صحت قطعی نیست.
    """)

st.page_link("pages/1_ابزارهای_بدون_AI.py", label="بازگشت به ابزارهای بدون هوش مصنوعی")
st.divider()
st.caption("ResearchMate AI — Prompt Studio | Prompt generation is deterministic and does not use an LLM API.")
