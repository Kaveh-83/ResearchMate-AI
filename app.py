import streamlit as st
import requests
import pandas as pd
import re
from urllib.parse import quote
from datetime import datetime

st.set_page_config(page_title="ResearchMate AI", page_icon="🔬", layout="wide")

st.markdown("""
<style>
html, body, [class*="css"] { direction: rtl; text-align: right; }
[data-testid="stSidebar"] * { direction: rtl; text-align: right; }
h1,h2,h3,p,div,label { text-align: right; }
.rm-sub {color:#64748b; font-size:0.95rem}
.rm-card {padding:1rem;border:1px solid #e2e8f0;border-radius:14px;margin:.5rem 0}
</style>
""", unsafe_allow_html=True)

st.title("🔬 ResearchMate AI")
st.markdown('<p class="rm-sub">دستیار پژوهشی نیمه‌مستقل | نسخه اولیه آزمایشی</p>', unsafe_allow_html=True)
st.info("این نسخه از OpenAlex برای بازیابی فراداده مقالات استفاده می‌کند. خروجی‌های تحلیلی اولیه مبتنی بر چکیده/متن در دسترس‌اند و هنوز جایگزین داوری پژوهشگر یا بررسی متن کامل مقاله نیستند.")

with st.sidebar:
    st.header("تنظیمات جست‌وجو")
    topic = st.text_input("موضوع یا کلیدواژه", value="machine learning in steelmaking")
    from_year = st.number_input("از سال", min_value=1900, max_value=datetime.now().year, value=2018)
    to_year = st.number_input("تا سال", min_value=1900, max_value=datetime.now().year, value=datetime.now().year)
    max_results = st.slider("تعداد نتایج", 5, 50, 20, step=5)
    open_access_only = st.checkbox("فقط مقالات دسترسی آزاد", value=False)
    search_btn = st.button("جست‌وجوی مقالات", type="primary", use_container_width=True)

def safe_text(x):
    return re.sub(r"\s+", " ", str(x or "")).strip()

def reconstruct_abstract(inv):
    if not isinstance(inv, dict) or not inv:
        return ""
    words = []
    for word, positions in inv.items():
        for pos in positions:
            words.append((pos, word))
    return " ".join(w for _, w in sorted(words))

def search_openalex(query, y1, y2, limit, oa_only):
    filters = [f"from_publication_date:{y1}-01-01", f"to_publication_date:{y2}-12-31"]
    if oa_only:
        filters.append("is_oa:true")
    params = {
        "search": query,
        "filter": ",".join(filters),
        "per-page": limit,
        "sort": "relevance_score:desc",
        "select": "id,doi,title,publication_year,authorships,primary_location,open_access,cited_by_count,abstract_inverted_index,type"
    }
    response = requests.get("https://api.openalex.org/works", params=params, timeout=30,
                            headers={"User-Agent":"ResearchMateAI/0.1 (academic literature discovery)"})
    response.raise_for_status()
    return response.json().get("results", [])

def normalize_work(w):
    authors = []
    for a in (w.get("authorships") or [])[:6]:
        name = (a.get("author") or {}).get("display_name")
        if name: authors.append(name)
    loc = w.get("primary_location") or {}
    source = (loc.get("source") or {}).get("display_name") if loc else ""
    oa = w.get("open_access") or {}
    doi = w.get("doi") or ""
    return {
        "عنوان": safe_text(w.get("title")),
        "سال": w.get("publication_year"),
        "نویسندگان": "; ".join(authors),
        "نشریه/منبع": safe_text(source),
        "استنادها": w.get("cited_by_count", 0),
        "دسترسی آزاد": "بله" if oa.get("is_oa") else "خیر",
        "نسخه آزاد": oa.get("oa_url") or "",
        "DOI": doi.replace("https://doi.org/",""),
        "چکیده": reconstruct_abstract(w.get("abstract_inverted_index")),
        "پیوند مقاله": doi or w.get("id","")
    }

if search_btn:
    if not topic.strip():
        st.warning("لطفاً موضوع جست‌وجو را وارد کن.")
    elif from_year > to_year:
        st.error("سال شروع نباید از سال پایان بزرگ‌تر باشد.")
    else:
        try:
            with st.spinner("در حال جست‌وجو در OpenAlex..."):
                works = search_openalex(topic, int(from_year), int(to_year), int(max_results), open_access_only)
                st.session_state["papers"] = [normalize_work(w) for w in works]
                st.session_state["query"] = topic
        except Exception as e:
            st.error(f"جست‌وجو انجام نشد: {e}")

papers = st.session_state.get("papers", [])
if papers:
    df = pd.DataFrame(papers)
    st.subheader(f"نتایج جست‌وجو: {len(df)} مقاله")
    st.caption(f"عبارت جست‌وجو: {st.session_state.get('query','')}")
    display_cols = ["عنوان","سال","نویسندگان","نشریه/منبع","استنادها","دسترسی آزاد","DOI"]
    st.dataframe(df[display_cols], use_container_width=True, hide_index=True)
    c1,c2,c3 = st.columns(3)
    c1.metric("تعداد نتایج", len(df))
    c2.metric("دسترسی آزاد", int((df["دسترسی آزاد"]=="بله").sum()))
    c3.metric("مجموع استنادهای ثبت‌شده", int(pd.to_numeric(df["استنادها"], errors="coerce").fillna(0).sum()))
    st.download_button("دانلود نتایج به‌صورت CSV", df.to_csv(index=False).encode("utf-8-sig"),
                       file_name="researchmate_results.csv", mime="text/csv")
    st.download_button("دانلود فهرست منابع به‌صورت BibTeX",
                       "\n\n".join([f"@article{{rm{i},\n  title={{{p['عنوان']}}},\n  year={{{p['سال']}}},\n  author={{{p['نویسندگان']}}},\n  doi={{{p['DOI']}}}\n}}" for i,p in enumerate(papers,1)]),
                       file_name="researchmate_references.bib", mime="text/plain")
    st.divider()
    st.subheader("بررسی و مقایسه مقاله‌ها")
    options = [f"{i+1}. {p['عنوان'][:100]}" for i,p in enumerate(papers)]
    selected = st.multiselect("مقاله‌هایی را برای بررسی انتخاب کن", options, default=options[:min(3,len(options))])
    chosen = [papers[options.index(x)] for x in selected]
    if chosen:
        for p in chosen:
            with st.expander(f"{p['سال']} | {p['عنوان']}"):
                st.write("**نویسندگان:**", p["نویسندگان"] or "در فراداده ثبت نشده")
                st.write("**منبع:**", p["نشریه/منبع"] or "نامشخص")
                st.write("**DOI:**", p["DOI"] or "ثبت نشده")
                st.write("**دسترسی آزاد:**", p["دسترسی آزاد"])
                st.write("**چکیده:**", p["چکیده"] or "چکیده در OpenAlex موجود نیست.")
                if p["پیوند مقاله"]:
                    st.markdown(f"[بازکردن صفحه مقاله/شناسه]({p['پیوند مقاله']})")
        if len(chosen) > 1:
            comp = pd.DataFrame(chosen)[["عنوان","سال","نشریه/منبع","استنادها","دسترسی آزاد","DOI"]]
            st.markdown("**جدول مقایسه فراداده‌ای**")
            st.dataframe(comp, use_container_width=True, hide_index=True)
else:
    st.markdown("""
    ### شروع کار
    1. عبارت پژوهشی را وارد کن؛ برای نمونه: `machine learning for continuous casting`.
    2. بازه سال و تعداد نتایج را تعیین کن.
    3. جست‌وجو را اجرا کن و مقالات را بررسی یا خروجی بگیر.

    **نکته علمی:** تعداد استناد به‌تنهایی معیار کیفیت مقاله نیست. وضعیت دسترسی آزاد نیز به معنی تأیید کیفیت علمی نیست.
    """)

st.divider()
st.subheader("تحلیل فایل PDF (آزمایشی)")
uploaded = st.file_uploader("یک مقاله PDF بارگذاری کن", type=["pdf"])
if uploaded:
    try:
        import fitz
        doc = fitz.open(stream=uploaded.read(), filetype="pdf")
        pages = [page.get_text("text") for page in doc]
        full_text = "\n".join(pages)
        st.success(f"متن {len(doc)} صفحه استخراج شد.")
        st.caption("این استخراج متن به‌تنهایی تحلیل علمی یا راستی‌آزمایی نتایج نیست.")
        st.text_area("بخشی از متن استخراج‌شده", full_text[:12000], height=300)
        sections = {}
        for heading in ["abstract","introduction","methodology","materials and methods","results","discussion","conclusion","limitations"]:
            m = re.search(rf"(?i)\b{re.escape(heading)}\b", full_text)
            if m: sections[heading] = m.start()
        st.write("**سرفصل‌های احتمالی شناسایی‌شده:**", ", ".join(sections.keys()) if sections else "سرفصل استانداردی شناسایی نشد.")
    except ImportError:
        st.warning("برای استخراج PDF، کتابخانه PyMuPDF باید نصب باشد. در فایل requirements لحاظ شده است.")
    except Exception as e:
        st.error(f"خواندن PDF ناموفق بود: {e}")

st.caption("ResearchMate AI MVP 0.1 | داده‌های کتاب‌شناختی: OpenAlex | نسخه آزمایشی و بدون تضمین کامل بودن نمایه")
