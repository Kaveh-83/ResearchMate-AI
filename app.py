
import streamlit as st
import requests, re, io, json, os
import pandas as pd
from datetime import datetime
from collections import Counter

st.set_page_config(page_title="ResearchMate AI 0.3", page_icon="🔬", layout="wide")

st.markdown("""
<style>
html, body, [class*="css"] { direction: rtl; text-align: right; }
[data-testid="stSidebar"] * { direction: rtl; text-align: right; }
h1,h2,h3,p,div,label { text-align: right; }
.small-note {color:#64748b;font-size:.9rem}
</style>
""", unsafe_allow_html=True)

st.title("🔬 ResearchMate AI")
st.caption("نسخه ۰٫۳ | دستیار تحلیل ادبیات پژوهش برای پژوهشگر")
st.info("این نسخه از تحلیل متن و روش‌های آماری/زبانی شفاف استفاده می‌کند. پیشنهاد شکاف پژوهشی، نتیجه قطعی علمی نیست و باید با متن کامل مقالات و نظر متخصص بررسی شود.")

# ---------- Helpers ----------
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
        "search": query, "filter": ",".join(filters), "per-page": limit,
        "sort": "relevance_score:desc",
        "select": "id,doi,title,publication_year,authorships,primary_location,open_access,cited_by_count,abstract_inverted_index,type"
    }
    r = requests.get("https://api.openalex.org/works", params=params, timeout=35,
                     headers={"User-Agent":"ResearchMateAI/0.2 (literature review)"})
    r.raise_for_status()
    return r.json().get("results", [])

def normalize_work(w):
    authors = []
    for a in (w.get("authorships") or [])[:6]:
        n = (a.get("author") or {}).get("display_name")
        if n: authors.append(n)
    loc = w.get("primary_location") or {}
    source = (loc.get("source") or {}).get("display_name") if loc else ""
    oa = w.get("open_access") or {}
    doi = w.get("doi") or ""
    return {
        "عنوان": safe_text(w.get("title")), "سال": w.get("publication_year"),
        "نویسندگان": "; ".join(authors), "نشریه/منبع": safe_text(source),
        "استنادها": w.get("cited_by_count", 0),
        "دسترسی آزاد": "بله" if oa.get("is_oa") else "خیر",
        "نسخه آزاد": oa.get("oa_url") or "", "DOI": doi.replace("https://doi.org/",""),
        "چکیده": reconstruct_abstract(w.get("abstract_inverted_index")),
        "پیوند مقاله": doi or w.get("id","")
    }

METHODS = {
    "آزمایشگاهی": ["experimental", "laboratory", "specimen", "microstructure", "tensile test", "hardness", "characterization"],
    "شبیه‌سازی عددی": ["finite element", "finite-element", "simulation", "numerical model", "computational fluid dynamics", "cfd", "procast", "thermo-calc"],
    "یادگیری ماشین": ["machine learning", "random forest", "support vector", "neural network", "deep learning", "xgboost", "regression model"],
    "تحلیل آماری": ["statistical analysis", "anova", "design of experiments", "response surface", "regression analysis"],
    "مرور نظام‌مند": ["systematic review", "meta-analysis", "bibliometric", "scoping review"],
    "مطالعه صنعتی/موردی": ["case study", "industrial data", "plant data", "industrial scale", "production line"]
}
LIMITATION_TERMS = ["limitation", "limitations", "future work", "further research", "remains unclear",
                    "not investigated", "not considered", "lack of", "however", "challenge", "future studies"]
TOPIC_TERMS = {
    "فولادسازی": ["steelmaking", "steel production", "blast furnace", "electric arc furnace", "eaf", "basic oxygen furnace", "bof"],
    "ریخته‌گری": ["casting", "continuous casting", "billet", "slab", "mold", "solidification"],
    "ریزساختار": ["microstructure", "grain size", "phase transformation", "martensite", "pearlite", "precipitation"],
    "خواص مکانیکی": ["mechanical properties", "strength", "hardness", "toughness", "ductility", "fatigue"],
    "پوشش": ["coating", "surface engineering", "wear", "corrosion", "thermal barrier"],
    "هوش مصنوعی": ["machine learning", "artificial intelligence", "neural network", "data-driven", "deep learning"],
    "محیط‌زیست": ["carbon emission", "co2", "decarbonization", "energy efficiency", "slag", "waste"]
}

def extract_methods(text):
    low = (text or "").lower()
    found = []
    for label, terms in METHODS.items():
        hits = [t for t in terms if t in low]
        if hits: found.append((label, hits[:4]))
    return found

def extract_limitations(text):
    low = (text or "").lower()
    found = []
    for term in LIMITATION_TERMS:
        for m in re.finditer(re.escape(term), low):
            snippet = safe_text((text or "")[max(0,m.start()-100):m.start()+230])
            if snippet and snippet not in found: found.append(snippet)
            if len(found) >= 5: return found
    return found

def topic_coverage(texts):
    counts = Counter()
    for text in texts:
        low = (text or "").lower()
        for label, terms in TOPIC_TERMS.items():
            if any(t in low for t in terms): counts[label] += 1
    return counts

def similarity_matrix(texts):
    try:
        from sklearn.feature_extraction.text import TfidfVectorizer
        from sklearn.metrics.pairwise import cosine_similarity
        clean = [t if t.strip() else "no abstract available" for t in texts]
        X = TfidfVectorizer(stop_words="english", ngram_range=(1,2), max_features=6000).fit_transform(clean)
        return cosine_similarity(X)
    except Exception:
        return None

def build_analysis(papers, extra_text=""):
    texts = [(p.get("عنوان","") + ". " + p.get("چکیده","")) for p in papers]
    if extra_text.strip(): texts.append(extra_text[:100000])
    coverage = topic_coverage(texts)
    method_counts = Counter()
    limitation_rows = []
    for i, t in enumerate(texts):
        for method, hits in extract_methods(t):
            method_counts[method] += 1
        for snip in extract_limitations(t):
            limitation_rows.append((i+1, snip))
    sim = similarity_matrix(texts) if len(texts) > 1 else None
    return texts, coverage, method_counts, limitation_rows, sim

def make_pdf(title, query, papers, coverage, method_counts, gaps, feedback_rows):
    from reportlab.lib.pagesizes import A4
    from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, PageBreak
    from reportlab.lib import colors
    from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
    from reportlab.pdfbase import pdfmetrics
    from reportlab.pdfbase.ttfonts import TTFont
    from reportlab.lib.enums import TA_RIGHT, TA_LEFT
    from reportlab.lib.units import mm
    buf = io.BytesIO()
    doc = SimpleDocTemplate(buf, pagesize=A4, rightMargin=17*mm, leftMargin=17*mm, topMargin=16*mm, bottomMargin=16*mm)
    styles = getSampleStyleSheet()
    font_name = "Helvetica"
    # Use Matplotlib's bundled DejaVu Sans; Streamlit Cloud may lack system font paths.
    font_path_candidates = []
    try:
        from matplotlib import font_manager
        font_path_candidates.append(font_manager.findfont("DejaVu Sans"))
    except Exception:
        pass
    font_path_candidates += [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSansCondensed.ttf"
    ]
    for fp in font_path_candidates:
        if fp and os.path.exists(fp):
            try:
                pdfmetrics.registerFont(TTFont("DejaVu", fp)); font_name = "DejaVu"; break
            except Exception: pass
    try:
        import arabic_reshaper
        from bidi.algorithm import get_display
        def rtl(s): return get_display(arabic_reshaper.reshape(str(s)))
    except Exception:
        rtl = lambda s: str(s)
    body = ParagraphStyle("BodyRTL", parent=styles["BodyText"], fontName=font_name, fontSize=8.5, leading=13, alignment=TA_RIGHT)
    heading = ParagraphStyle("HeadingRTL", parent=styles["Heading2"], fontName=font_name, fontSize=13, leading=17, alignment=TA_RIGHT)
    title_style = ParagraphStyle("TitleRTL", parent=styles["Title"], fontName=font_name, fontSize=19, leading=24, alignment=TA_RIGHT)
    story = [Paragraph(rtl("گزارش پژوهشی ResearchMate AI"), title_style), Spacer(1,8),
             Paragraph(rtl("موضوع پژوهش: " + query), body),
             Paragraph(rtl("تاریخ تولید: " + datetime.now().strftime("%Y-%m-%d %H:%M")), body), Spacer(1,10),
             Paragraph(rtl("۱. دامنه بررسی"), heading),
             Paragraph(rtl(f"تعداد مقالات بررسی‌شده: {len(papers)}. این گزارش بر اساس فراداده و چکیده‌های در دسترس و متن PDF بارگذاری‌شده تهیه شده است؛ پوشش پایگاه کامل نیست."), body), Spacer(1,8)]
    story.append(Paragraph(rtl("۲. مقالات بررسی‌شده"), heading))
    for i,p in enumerate(papers,1):
        story.append(Paragraph(rtl(f"{i}. {p.get('عنوان','بدون عنوان')} ({p.get('سال','')})"), body))
        story.append(Paragraph(rtl("نویسندگان: "+p.get("نویسندگان","نامشخص")+" | DOI: "+p.get("DOI","ثبت نشده")), body))
        story.append(Spacer(1,4))
    story += [Spacer(1,8), Paragraph(rtl("۳. روش‌های پژوهشی شناسایی‌شده"), heading)]
    if method_counts:
        for k,v in method_counts.most_common():
            story.append(Paragraph(rtl(f"{k}: در {v} متن/مقاله نشانه‌هایی از این روش یافت شد."), body))
    else: story.append(Paragraph(rtl("روش مشخصی با واژگان کلیدی ساده شناسایی نشد؛ متن کامل را بررسی کنید."), body))
    story += [Spacer(1,8), Paragraph(rtl("۴. موضوعات پرتکرار"), heading)]
    for k,v in coverage.most_common():
        story.append(Paragraph(rtl(f"{k}: {v} مورد"), body))
    story += [Spacer(1,8), Paragraph(rtl("۵. شکاف‌های پژوهشی احتمالی"), heading)]
    for g in gaps:
        story.append(Paragraph(rtl("• "+g), body))
    story += [Spacer(1,8), Paragraph(rtl("۶. بازخورد و یادداشت پژوهشگر"), heading)]
    if feedback_rows:
        for row in feedback_rows:
            story.append(Paragraph(rtl(str(row)), body))
    else: story.append(Paragraph(rtl("هنوز بازخوردی ثبت نشده است."), body))
    story += [Spacer(1,12), Paragraph(rtl("محدودیت و هشدار علمی: این گزارش، مرور نظام‌مند ثبت‌شده یا داوری تخصصی نیست. روش‌ها و شکاف‌ها با تطبیق واژگان و شباهت متن پیشنهاد شده‌اند؛ پیش از استناد علمی باید متن کامل، کیفیت طراحی مطالعه، نتایج و منابع اصلی توسط پژوهشگر ارزیابی شوند."), body)]
    doc.build(story)
    return buf.getvalue()


def make_poster_pdf(query, papers, coverage, method_counts, gaps, limitations):
    """Build a one-page A3 Persian research summary poster."""
    from reportlab.lib.pagesizes import A3
    from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle
    from reportlab.lib import colors
    from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
    from reportlab.pdfbase import pdfmetrics
    from reportlab.pdfbase.ttfonts import TTFont
    from reportlab.lib.enums import TA_RIGHT, TA_CENTER
    from reportlab.lib.units import mm

    font_name = "Helvetica"
    try:
        from matplotlib import font_manager
        fp = font_manager.findfont("DejaVu Sans")
        pdfmetrics.registerFont(TTFont("PosterDejaVu", fp))
        font_name = "PosterDejaVu"
    except Exception:
        pass
    try:
        import arabic_reshaper
        from bidi.algorithm import get_display
        def rtl(x): return get_display(arabic_reshaper.reshape(str(x)))
    except Exception:
        rtl = lambda x: str(x)

    buf = io.BytesIO()
    doc = SimpleDocTemplate(buf, pagesize=A3, rightMargin=15*mm, leftMargin=15*mm,
                            topMargin=13*mm, bottomMargin=13*mm)
    ss = getSampleStyleSheet()
    title = ParagraphStyle("PosterTitle", parent=ss["Title"], fontName=font_name,
                           fontSize=25, leading=31, alignment=TA_CENTER,
                           textColor=colors.HexColor("#12304A"), spaceAfter=6*mm)
    head = ParagraphStyle("PosterHead", parent=ss["Heading2"], fontName=font_name,
                          fontSize=15, leading=20, alignment=TA_RIGHT,
                          textColor=colors.HexColor("#126078"), spaceBefore=3*mm, spaceAfter=2*mm)
    body = ParagraphStyle("PosterBody", parent=ss["BodyText"], fontName=font_name,
                          fontSize=10, leading=15, alignment=TA_RIGHT, spaceAfter=2*mm)
    small = ParagraphStyle("PosterSmall", parent=body, fontSize=8, leading=12)
    story = [
        Paragraph(rtl("پوستر خلاصه پژوهش | ResearchMate AI"), title),
        Paragraph(rtl("موضوع پژوهش: " + query), head),
        Paragraph(rtl("جمع‌بندی اکتشافی بر پایه فراداده و چکیده‌های مقالات بازیابی‌شده؛ نه مرور نظام‌مند."), body)
    ]
    stat = Table([
        [Paragraph(rtl("مقالات بررسی‌شده"), body), Paragraph(rtl("محورهای موضوعی"), body), Paragraph(rtl("خانواده روش‌ها"), body)],
        [Paragraph(str(len(papers)), body), Paragraph(str(len(coverage)), body), Paragraph(str(len(method_counts)), body)]
    ], colWidths=[doc.width/3]*3, rowHeights=[12*mm,17*mm])
    stat.setStyle(TableStyle([
        ("BACKGROUND",(0,0),(-1,-1),colors.HexColor("#EAF3F7")),
        ("BOX",(0,0),(-1,-1),0.8,colors.HexColor("#B7CBD6")),
        ("INNERGRID",(0,0),(-1,-1),0.5,colors.white),
        ("ALIGN",(0,0),(-1,-1),"CENTER"),("VALIGN",(0,0),(-1,-1),"MIDDLE")
    ]))
    story += [stat, Spacer(1,5*mm)]
    left = [Paragraph(rtl("محورهای پرتکرار"), head)]
    for k,v in coverage.most_common(8):
        left.append(Paragraph(rtl(f"• {k}: {v} نشانه"), body))
    left.append(Paragraph(rtl("روش‌های پژوهشی شناسایی‌شده"), head))
    for k,v in method_counts.most_common(8):
        left.append(Paragraph(rtl(f"• {k}: {v} مورد"), body))
    right = [Paragraph(rtl("فرصت‌ها و پرسش‌های پژوهشی"), head)]
    for i,g in enumerate(gaps[:5],1):
        right.append(Paragraph(rtl(f"{i}. {g}"), body))
    right.append(Paragraph(rtl("محدودیت‌های گزارش‌شده در چکیده‌ها"), head))
    if limitations:
        for _,s in limitations[:4]:
            right.append(Paragraph(rtl("• "+s[:260]), small))
    else:
        right.append(Paragraph(rtl("محدودیت صریحی در چکیده‌های فعلی شناسایی نشد."), body))
    cols = Table([[left,right]], colWidths=[doc.width*0.49,doc.width*0.49])
    cols.setStyle(TableStyle([
        ("VALIGN",(0,0),(-1,-1),"TOP"),("BOX",(0,0),(-1,-1),0.7,colors.HexColor("#C9D8E0")),
        ("INNERGRID",(0,0),(-1,-1),0.7,colors.HexColor("#C9D8E0")),
        ("BACKGROUND",(0,0),(-1,-1),colors.HexColor("#FBFCFD")),
        ("LEFTPADDING",(0,0),(-1,-1),6*mm),("RIGHTPADDING",(0,0),(-1,-1),6*mm),
        ("TOPPADDING",(0,0),(-1,-1),4*mm),("BOTTOMPADDING",(0,0),(-1,-1),4*mm)
    ]))
    story += [cols, Spacer(1,5*mm), Paragraph(rtl("مقالات شاخص در مجموعه"), head)]
    for i,p in enumerate(papers[:7],1):
        story.append(Paragraph(rtl(f"{i}. {p.get('عنوان','بدون عنوان')} ({p.get('سال','')}) | DOI: {p.get('DOI','ثبت نشده')}"), small))
    story += [Spacer(1,4*mm), Paragraph(rtl(
        "هشدار علمی: فراوانی کلیدواژه‌ها و دسته‌بندی روش‌ها تخمینی است. کم‌بودن تعداد نتایج به‌تنهایی "
        "شکاف پژوهشی را اثبات نمی‌کند. برای نتیجه‌گیری معتبر باید متن کامل، طراحی مطالعه، داده‌ها، "
        "نتایج و کیفیت منابع توسط پژوهشگر ارزیابی شود."
    ), small)]
    doc.build(story)
    return buf.getvalue()


# ---------- Sidebar ----------
with st.sidebar:
    st.header("۱) جست‌وجوی ادبیات")
    topic = st.text_input("موضوع یا کلیدواژه", value="machine learning in steelmaking")
    y1 = st.number_input("از سال", 1900, datetime.now().year, 2018)
    y2 = st.number_input("تا سال", 1900, datetime.now().year, datetime.now().year)
    limit = st.slider("تعداد مقاله", 5, 50, 25, 5)
    oa_only = st.checkbox("فقط دسترسی آزاد")
    if st.button("جست‌وجوی مقالات", type="primary", use_container_width=True):
        if y1 > y2: st.error("سال شروع نباید از سال پایان بزرگ‌تر باشد.")
        elif not topic.strip(): st.warning("موضوع را وارد کن.")
        else:
            try:
                with st.spinner("در حال دریافت مقالات از OpenAlex..."):
                    raw = search_openalex(topic, int(y1), int(y2), int(limit), oa_only)
                    st.session_state["papers"] = [normalize_work(x) for x in raw]
                    st.session_state["query"] = topic
                    st.session_state["feedback"] = []
            except Exception as e: st.error(f"خطا در جست‌وجو: {e}")

papers = st.session_state.get("papers", [])
query = st.session_state.get("query", topic)
tabs = st.tabs(["نمای کلی", "ارتباط مقالات", "روش‌شناسی", "شکاف‌های پژوهشی", "یادگیری و گزارش", "پوستر پژوهشی"])

if not papers:
    with tabs[0]:
        st.markdown("### شروع")
        st.write("از ستون کناری موضوع را جست‌وجو کن. نمونه: `machine learning for continuous casting`")
        st.write("پس از دریافت مقاله‌ها، تب‌های تحلیل فعال می‌شوند.")
else:
    df = pd.DataFrame(papers)
    texts, coverage, method_counts, limitations, sim = build_analysis(papers)
    with tabs[0]:
        st.subheader(f"نتایج جست‌وجو: {len(df)} مقاله")
        st.caption("عبارت جست‌وجو: " + query)
        cols = ["عنوان","سال","نویسندگان","نشریه/منبع","استنادها","دسترسی آزاد","DOI"]
        st.dataframe(df[cols], use_container_width=True, hide_index=True)
        c1,c2,c3 = st.columns(3)
        c1.metric("تعداد مقالات",len(df))
        c2.metric("دسترسی آزاد",int((df["دسترسی آزاد"]=="بله").sum()))
        c3.metric("مجموع استنادها",int(pd.to_numeric(df["استنادها"],errors="coerce").fillna(0).sum()))
        st.download_button("دانلود CSV",df.to_csv(index=False).encode("utf-8-sig"),"researchmate_papers.csv","text/csv")
        st.download_button("دانلود BibTeX","\n\n".join([f"@article{{rm{i}, title={{{p['عنوان']}}}, year={{{p['سال']}}}, author={{{p['نویسندگان']}}}, doi={{{p['DOI']}}}}}" for i,p in enumerate(papers,1)]),"references.bib","text/plain")
        st.subheader("بارگذاری متن کامل مقاله‌ها")
        uploads = st.file_uploader("PDF مقاله‌ها را بارگذاری کن (چند فایل مجاز است)", type=["pdf"], accept_multiple_files=True)
        extracted = []
        if uploads:
            for f in uploads:
                try:
                    import fitz
                    doc = fitz.open(stream=f.getvalue(),filetype="pdf")
                    txt = "\n".join(pg.get_text("text") for pg in doc)
                    extracted.append({"filename":f.name,"text":txt})
                except Exception as e: st.warning(f"خواندن {f.name} ناموفق بود: {e}")
            st.session_state["pdf_texts"] = extracted
            st.success(f"متن {len(extracted)} فایل استخراج شد.")
        for item in st.session_state.get("pdf_texts",[]):
            with st.expander(item["filename"]):
                st.text_area("نمونه متن استخراج‌شده",item["text"][:5000],height=160,key="pdf_"+item["filename"])
    with tabs[1]:
        st.subheader("شباهت و ارتباط مفهومی مقالات")
        if sim is not None:
            labels = [f"{i+1}. {p['عنوان'][:65]}" for i,p in enumerate(papers)]
            rows=[]
            for i in range(len(papers)):
                for j in range(i+1,len(papers)):
                    rows.append({"مقاله اول":labels[i],"مقاله دوم":labels[j],"شباهت متنی تقریبی":round(float(sim[i,j]),3)})
            rel = pd.DataFrame(rows).sort_values("شباهت متنی تقریبی",ascending=False) if rows else pd.DataFrame()
            if not rel.empty:
                st.caption("این شاخص بر پایه شباهت واژگان TF-IDF است؛ رابطه علّی یا هم‌ارزی علمی را اثبات نمی‌کند.")
                st.dataframe(rel.head(30),use_container_width=True,hide_index=True)
            else: st.info("برای مقایسه دست‌کم دو مقاله لازم است.")
        else: st.info("برای تحلیل شباهت، دست‌کم دو مقاله دارای متن وارد کن.")
    with tabs[2]:
        st.subheader("روش‌های پژوهشی شناسایی‌شده")
        method_rows=[]
        for i,p in enumerate(papers,1):
            found=extract_methods(p.get("عنوان","")+" "+p.get("چکیده",""))
            method_rows.append({"مقاله":p.get("عنوان","")[:100],"روش‌های احتمالی":", ".join(x[0] for x in found) or "از چکیده قابل تشخیص نیست"})
        st.dataframe(pd.DataFrame(method_rows),use_container_width=True,hide_index=True)
        st.caption("شناسایی روش‌ها با واژگان کلیدی انجام می‌شود. برای ارزیابی دقیق، بخش Materials and Methods یا Methodology متن کامل لازم است.")
    with tabs[3]:
        st.subheader("شکاف‌های پژوهشی احتمالی")
        st.warning("این‌ها فرضیه‌های اولیه برای بررسی‌اند، نه شکاف‌های اثبات‌شده. کم‌بودن تعداد مقاله در یک موضوع به‌تنهایی نشان‌دهنده نوآوری نیست.")
        gaps=[]
        if coverage:
            ranked=coverage.most_common()
            rare=[k for k,v in ranked if v<=max(1,len(papers)//5)]
            common=[k for k,v in ranked if v>=max(2,len(papers)//2)]
            if rare:
                gaps.append("موضوعات کم‌پوشش در مجموعه فعلی: "+ "، ".join(rare)+". بررسی کن آیا واقعاً در ادبیات کمتر مطالعه شده‌اند یا فقط در نتایج جست‌وجوی فعلی دیده نشده‌اند.")
            if len(common)>=2:
                gaps.append("فرصت بررسی ترکیب میان حوزه‌های پرتکرار: "+ " و ".join(common[:3])+". مطالعه‌ای طراحی کن که ارتباط این حوزه‌ها را با داده و فرضیه مشخص آزمایش کند.")
        if method_counts:
            gaps.append("روش‌های شناسایی‌شده را از نظر داده، دقت، هزینه و قابلیت تعمیم مقایسه کن؛ ممکن است مقایسه مستقیم روش‌ها روی یک مجموعه‌داده مشترک ارزشمند باشد.")
        if limitations:
            gaps.append("در متن‌ها نشانه‌هایی از محدودیت/کار آینده دیده شد؛ آن‌ها را در متن کامل بررسی و به پرسش پژوهشی قابل آزمون تبدیل کن.")
        if not gaps: gaps=["اطلاعات چکیده برای پیشنهاد شکاف کافی نیست. چند PDF کامل و مرتبط بارگذاری کن یا جست‌وجو را دقیق‌تر کن."]
        for i,g in enumerate(gaps,1): st.markdown(f"**پیشنهاد {i}:** {g}")
        with st.expander("نشانه‌های محدودیت و کار آینده در چکیده‌ها"):
            if limitations:
                for idx,snip in limitations: st.write(f"مقاله {idx}: …{snip}…")
            else: st.write("در چکیده‌های فعلی عبارت روشنی پیدا نشد.")
    with tabs[4]:
        st.subheader("حافظه پژوهشی و بازخورد")
        st.write("در این نسخه، یادگیری به معنی ذخیره بازخورد در همین نشست و استفاده از مجموعه مقاله‌های فعلی برای بهبود مقایسه است؛ هنوز مدل هوش مصنوعی آموزش داده نمی‌شود و حافظه دائمی بین نشست‌ها فعال نیست.")
        chosen_idx=st.selectbox("مقاله‌ای که می‌خواهی ارزیابی کنی",range(len(papers)),format_func=lambda i:f"{i+1}. {papers[i]['عنوان'][:90]}")
        rating=st.slider("میزان ارتباط مقاله با موضوع پژوهش",1,5,3)
        note=st.text_area("یادداشت یا اصلاح پژوهشگر",placeholder="مثلاً این مقاله واقعاً درباره ریخته‌گری پیوسته است، نه فولادسازی اولیه.")
        if st.button("ثبت بازخورد"):
            st.session_state.setdefault("feedback",[]).append({"مقاله":papers[chosen_idx]["عنوان"],"امتیاز":rating,"یادداشت":note})
            st.success("بازخورد برای همین نشست ثبت شد.")
        fb=st.session_state.get("feedback",[])
        if fb: st.dataframe(pd.DataFrame(fb),use_container_width=True,hide_index=True)
        st.divider()
        st.subheader("تولید گزارش نهایی PDF")
        report_title=st.text_input("عنوان گزارش",value="مرور ادبیات: "+query)
        if st.button("ساخت گزارش PDF",type="primary"):
            try:
                gaps_for_pdf=gaps if 'gaps' in locals() else ["برای شکاف پژوهشی به بررسی متن کامل و مرور نظام‌مند نیاز است."]
                pdf=make_pdf(report_title,query,papers,coverage,method_counts,gaps_for_pdf,fb)
                st.session_state["report_pdf"]=pdf
                st.success("گزارش ساخته شد.")
            except Exception as e: st.error(f"ساخت PDF ناموفق بود: {e}")
        if st.session_state.get("report_pdf"):
            st.download_button("دانلود گزارش پژوهشی PDF",st.session_state["report_pdf"],"ResearchMateAI_report.pdf","application/pdf")

        st.caption("گزارش شامل دامنه بررسی، فهرست مقالات، روش‌های شناسایی‌شده، موضوعات پرتکرار، شکاف‌های احتمالی، بازخورد و محدودیت‌های تحلیل.")
    with tabs[5]:
        st.subheader("ساخت پوستر خلاصه پژوهش")
        st.write("پوستر یک‌صفحه‌ای A3 شامل موضوع، آمار مجموعه، محورهای پرتکرار، روش‌ها، فرصت‌های پژوهشی، محدودیت‌ها و مقالات شاخص است.")
        st.info("پوستر بر اساس داده‌های فعلی و چکیده‌های در دسترس ساخته می‌شود. برای تحلیل عمیق‌تر، متن کامل مقالات را هم بارگذاری کن.")
        if st.button("تولید پوستر خلاصه", type="primary", key="make_poster"):
            try:
                gaps_for_poster = gaps if "gaps" in locals() else ["برای تعیین شکاف معتبر، متن کامل و مرور نظام‌مند لازم است."]
                st.session_state["poster_pdf"] = make_poster_pdf(query, papers, coverage, method_counts, gaps_for_poster, limitations)
                st.success("پوستر آماده شد.")
            except Exception as e:
                st.error(f"ساخت پوستر ناموفق بود: {e}")
        if st.session_state.get("poster_pdf"):
            st.download_button("دانلود پوستر پژوهشی PDF (A3)", st.session_state["poster_pdf"],
                               "ResearchMateAI_poster.pdf", "application/pdf", key="download_poster")


st.divider()
st.caption("ResearchMate AI 0.3 | OpenAlex + TF-IDF + قواعد شناسایی روش‌ها | ابزار کمکی پژوهش، نه جایگزین داوری علمی")
