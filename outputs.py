"""Independent scientific deliverables for ResearchMate AI."""
from __future__ import annotations
import os, io, re, zipfile, textwrap
from datetime import datetime
from xml.sax.saxutils import escape


def _as_text(value):
    if value is None: return ""
    if isinstance(value, dict):
        return "; ".join(f"{k}: {_as_text(v)}" for k,v in value.items())
    if isinstance(value, (list, tuple)):
        return "؛ ".join(_as_text(x) for x in value)
    return str(value)


def _paper_ref_map(papers):
    return {p.get("id"): i+1 for i,p in enumerate(papers)}


def _format_authors(p):
    return ", ".join(p.get("authors", []) or []) or "نویسندگان ثبت نشده‌اند"


def _citation_for_ids(ids, refmap):
    nums = sorted({refmap[x] for x in ids if x in refmap})
    return " [" + ", ".join(str(x) for x in nums) + "]" if nums else ""


def review_sections(bundle):
    s = bundle["synthesis"]
    papers = bundle["papers"]
    refmap = _paper_ref_map(papers)
    sections = [
        ("چکیده", _as_text(s.get("abstract"))),
        ("کلیدواژه‌ها", _as_text(s.get("keywords", []))),
        ("۱. مقدمه و زمینه علمی", _as_text(s.get("background"))),
        ("۲. روش‌شناسی مرور", "جست‌وجوی اولیه از طریق OpenAlex انجام شد و برای رکوردهای دارای شناسه PMC از Europe PMC نیز جهت دریافت متن آزاد استفاده شد. رکوردهای بازیابی‌شده بر اساس تاریخ انتشار و ارتباط جست‌وجویی مرتب شدند. برای هر رکورد، شناسه DOI، چکیده، وضعیت دسترسی آزاد و نسخه‌های آزاد ثبت‌شده بررسی شد. متن کامل فقط از پیوندهای قانونی و آزاد قابل دریافت دانلود شد. رکوردهایی که متن کامل آن‌ها دریافت نشد از مجموعه حذف نشدند، اما سطح شواهد آن‌ها به چکیده/فراداده محدود شد. تعداد رکوردها: " + str(len(papers)) + ". متن کامل قابل استخراج: " + str(sum(1 for d in bundle.get("documents", []) if d.get("status")=="full_text")) + "."),
        ("۳. سنتز موضوعی و چارچوب مفهومی", _as_text(s.get("thematic_synthesis"))),
        ("۴. مقایسه روش‌شناسی مطالعات", "\n".join("• " + _as_text(x) for x in s.get("methods_comparison", [])) or "اطلاعات کافی برای مقایسه روش‌ها تولید نشده است."),
        ("۵. مقایسه نتایج", "\n".join("• " + _as_text(x) for x in s.get("results_comparison", [])) or "مقایسه نتایج در این اجرا به‌صورت قابل اتکا انجام نشده است."),
        ("۶. شواهد همسو", "\n".join("• " + _as_text(x) for x in s.get("convergent_evidence", [])) or "شواهد همسوی مشخصی گزارش نشده است."),
        ("۷. شواهد متناقض و تبیین اختلاف‌ها", "\n".join("• " + _as_text(x) for x in s.get("conflicting_evidence", [])) + "\n" + "\n".join("تبیین: " + _as_text(x) for x in s.get("explanations_for_conflicts", [])) or "تناقض مشخصی با شواهد کافی گزارش نشده است."),
        ("۸. شکاف‌های پژوهشی و پرسش‌های آینده", "\n".join("• " + _as_text(x) for x in s.get("research_gaps", [])) or "شکاف مستند کافی در این اجرا تعیین نشد."),
        ("۹. مسیرهای پیشنهادی آینده", "\n".join("• " + _as_text(x) for x in s.get("future_directions", [])) or "پیشنهاد مشخصی گزارش نشده است."),
        ("۱۰. نتیجه‌گیری", _as_text(s.get("conclusion"))),
        ("۱۱. کیفیت مطالعات، بازتولیدپذیری و کاربرد صنعتی", "کیفیت مطالعات: " + _as_text(s.get("study_quality")) + "\nبازتولیدپذیری: " + _as_text(s.get("reproducibility_gaps")) + "\nکاربرد صنعتی: " + _as_text(s.get("industrial_applicability")) + "\nامکان فراتحلیل: " + _as_text(s.get("meta_analysis_feasibility"))),
        ("۱۲. محدودیت‌های مرور", _as_text(s.get("limitations_of_review")) + "\nقدرت شواهد برآوردشده: " + _as_text(s.get("evidence_strength"))),
    ]
    claims = []
    for claim in s.get("claims", []):
        if isinstance(claim, dict):
            claims.append(f"{claim.get('claim','')} (قدرت پشتیبانی: {claim.get('support','نامشخص')}; دلیل: {claim.get('reason','')}){_citation_for_ids(claim.get('paper_ids', []), refmap)}")
        else: claims.append(_as_text(claim))
    if claims:
        sections.insert(-2, ("ادعاهای کلیدی و ردیابی شواهد", "\n".join("• "+x for x in claims)))
    appendix=[]
    for i,(paper,analysis,doc) in enumerate(zip(papers,bundle.get("analyses",[]),bundle.get("documents",[])),1):
        methods=analysis.get("methods") or analysis.get("methodology") or []
        materials=analysis.get("materials",[])
        findings=analysis.get("quantitative_findings") or analysis.get("conditions_metrics") or analysis.get("findings") or []
        limitations=analysis.get("limitations",[])
        appendix.append(f"مطالعه [{i}] {paper.get('title','')} ({paper.get('year','')}) | سطح متن: {doc.get('status')}\n"
                        f"مسئله/هدف: {_as_text(analysis.get('problem'))} / {_as_text(analysis.get('objective'))}\n"
                        f"طرح مطالعه/نمونه/داده: {_as_text(analysis.get('study_design'))} / {_as_text(analysis.get('sample_size'))} / {_as_text(analysis.get('dataset'))}\n"
                        f"مواد: {_as_text(materials)} | روش‌ها: {_as_text(methods)}\n"
                        f"اعتبارسنجی/تجهیزات: {_as_text(analysis.get('validation_strategy',[]))} / {_as_text(analysis.get('equipment_instrumentation',[]))}\n"
                        f"شرایط/یافته‌ها: {_as_text(analysis.get('process_conditions',[]))} | {_as_text(findings)}\n"
                        f"محدودیت‌ها: {_as_text(limitations)}")
    sections.append(("ضمیمه الف. شناسنامه تحلیلی مطالعات و سطح شواهد", "\n\n".join(appendix)))
    return sections


def build_review_docx(bundle, out_path):
    from docx import Document
    from docx.shared import Inches, Pt, RGBColor
    from docx.enum.text import WD_ALIGN_PARAGRAPH
    from docx.enum.section import WD_SECTION
    from docx.oxml import OxmlElement
    from docx.oxml.ns import qn

    def rtl_paragraph(paragraph):
        pPr = paragraph._p.get_or_add_pPr()
        bidi = OxmlElement('w:bidi'); bidi.set(qn('w:val'), '1'); pPr.append(bidi)
        paragraph.alignment = WD_ALIGN_PARAGRAPH.RIGHT

    def set_run(run, size=10, bold=False):
        run.font.name = 'Arial'; run._element.rPr.rFonts.set(qn('w:eastAsia'), 'Arial')
        run.font.size = Pt(size); run.bold = bold

    doc = Document()
    sec = doc.sections[0]
    sec.top_margin = Inches(.72); sec.bottom_margin = Inches(.72)
    sec.left_margin = Inches(.8); sec.right_margin = Inches(.8)
    title = doc.add_paragraph(); rtl_paragraph(title)
    r = title.add_run("مقاله مروری علمی | ResearchMate AI"); set_run(r, 22, True)
    sub = doc.add_paragraph(); rtl_paragraph(sub)
    r = sub.add_run("موضوع: " + bundle["query"] + f" | بازه: {bundle['year_from']} تا {bundle['year_to']}"); set_run(r, 11, True)
    note = doc.add_paragraph(); rtl_paragraph(note)
    r = note.add_run("پیش‌نویس پژوهشی مبتنی بر بازیابی منابع و سنتز شواهد؛ پیش از ارسال به مجله نیازمند بازبینی متخصص و کنترل ارجاعات است."); set_run(r, 9)

    for heading, body in review_sections(bundle):
        h = doc.add_heading(heading, level=1); rtl_paragraph(h)
        for para in str(body).split("\n"):
            p = doc.add_paragraph(); rtl_paragraph(p)
            run = p.add_run(para or " "); set_run(run, 10)

    h = doc.add_heading("۱۲. منابع", level=1); rtl_paragraph(h)
    for idx,paper in enumerate(bundle["papers"], 1):
        p = doc.add_paragraph(); rtl_paragraph(p)
        ref = f"[{idx}] {_format_authors(paper)} ({paper.get('year','n.d.')}). {paper.get('title','')}. {paper.get('journal','')}. DOI: {paper.get('doi') or 'ثبت نشده'}. {paper.get('oa_url') or ''}"
        run = p.add_run(ref); set_run(run, 8)
    doc.save(out_path)
    return out_path


def _reportlab_styles():
    from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
    from reportlab.lib.enums import TA_RIGHT, TA_CENTER
    from reportlab.pdfbase import pdfmetrics
    from reportlab.pdfbase.ttfonts import TTFont
    from matplotlib import font_manager
    fp = font_manager.findfont("DejaVu Sans")
    try: pdfmetrics.registerFont(TTFont("RMDejaVu", fp))
    except Exception: pass
    try:
        import arabic_reshaper
        from bidi.algorithm import get_display
    except Exception as exc:
        raise RuntimeError("برای تولید PDF فارسی، کتابخانه‌های arabic-reshaper و python-bidi باید نصب باشند؛ از خروجی خراب با مربع‌های سیاه جلوگیری شد.") from exc
    def rtl(txt):
        return get_display(arabic_reshaper.reshape(str(txt)))
    ss = getSampleStyleSheet()
    body = ParagraphStyle("RMBody", parent=ss["BodyText"], fontName="RMDejaVu", fontSize=9, leading=14, alignment=TA_RIGHT, spaceAfter=5)
    heading = ParagraphStyle("RMHeading", parent=ss["Heading2"], fontName="RMDejaVu", fontSize=14, leading=18, alignment=TA_RIGHT, spaceBefore=9, spaceAfter=5)
    title = ParagraphStyle("RMTitle", parent=ss["Title"], fontName="RMDejaVu", fontSize=20, leading=26, alignment=TA_CENTER, spaceAfter=10)
    small = ParagraphStyle("RMSmall", parent=body, fontSize=7, leading=10)
    return rtl, body, heading, title, small


def _para(reportlab_para, text, style, rtl):
    from reportlab.platypus import Paragraph
    safe = escape(str(text)).replace("\n", " | ")
    return Paragraph(rtl(safe), style)


def build_review_pdf(bundle, out_path):
    from reportlab.lib.pagesizes import A4
    from reportlab.lib.units import mm
    from reportlab.platypus import SimpleDocTemplate, Spacer, PageBreak
    rtl, body, heading, title, small = _reportlab_styles()
    doc = SimpleDocTemplate(out_path, pagesize=A4, rightMargin=17*mm, leftMargin=17*mm, topMargin=16*mm, bottomMargin=16*mm)
    story = [_para(None, "مقاله مروری علمی | ResearchMate AI", title, rtl),
             _para(None, "موضوع: "+bundle["query"]+f" | بازه: {bundle['year_from']} تا {bundle['year_to']}", body, rtl),
             _para(None, "پیش‌نویس پژوهشی؛ نیازمند بازبینی متخصص و راستی‌آزمایی منابع پیش از انتشار.", small, rtl)]
    for h,b in review_sections(bundle):
        story.append(_para(None,h,heading,rtl))
        for part in str(b).split("\n"):
            story.append(_para(None,part,body,rtl))
    story.append(_para(None,"منابع",heading,rtl))
    for i,p in enumerate(bundle["papers"],1):
        ref = f"[{i}] {_format_authors(p)} ({p.get('year','')}). {p.get('title','')}. {p.get('journal','')}. DOI: {p.get('doi') or 'ثبت نشده'}"
        story.append(_para(None,ref,small,rtl))
    doc.build(story)
    return out_path


def build_poster_pptx(bundle, out_path):
    from pptx import Presentation
    from pptx.util import Inches, Pt
    from pptx.dml.color import RGBColor
    from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
    from pptx.enum.shapes import MSO_SHAPE
    prs = Presentation()
    prs.slide_width = Inches(16.54); prs.slide_height = Inches(23.39)  # A1 portrait
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    bg = slide.background.fill; bg.solid(); bg.fore_color.rgb = RGBColor(246,249,252)
    navy = RGBColor(22,48,74); teal = RGBColor(20,112,132); ink = RGBColor(35,49,63)

    def add_box(x,y,w,h,heading,body,fontsize=14):
        shape = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, Inches(x), Inches(y), Inches(w), Inches(h))
        shape.fill.solid(); shape.fill.fore_color.rgb = RGBColor(255,255,255)
        shape.line.color.rgb = RGBColor(210,222,231)
        tf = shape.text_frame; tf.clear(); tf.word_wrap = True
        tf.margin_left = Inches(.18); tf.margin_right = Inches(.18); tf.margin_top = Inches(.12)
        tf.vertical_anchor = MSO_ANCHOR.TOP
        p = tf.paragraphs[0]; p.alignment = PP_ALIGN.RIGHT
        r = p.add_run(); r.text = heading; r.font.name = "Arial"; r.font.size = Pt(fontsize+5); r.font.bold=True; r.font.color.rgb=teal
        for line in str(body).split("\n"):
            p = tf.add_paragraph(); p.alignment=PP_ALIGN.RIGHT; p.space_after=Pt(4)
            r=p.add_run(); r.text=line; r.font.name="Arial"; r.font.size=Pt(fontsize); r.font.color.rgb=ink
        return shape

    title_shape=slide.shapes.add_textbox(Inches(.45), Inches(.35), Inches(15.6), Inches(1.25))
    tf=title_shape.text_frame; tf.clear()
    p=tf.paragraphs[0]; p.alignment=PP_ALIGN.CENTER
    r=p.add_run(); r.text="پوستر علمی | "+bundle["query"]; r.font.name="Arial"; r.font.size=Pt(30); r.font.bold=True; r.font.color.rgb=navy
    p=tf.add_paragraph(); p.alignment=PP_ALIGN.CENTER
    r=p.add_run(); r.text=f"مرور شواهد علمی | {len(bundle['papers'])} رکورد | {sum(1 for d in bundle['documents'] if d.get('status')=='full_text')} متن کامل"; r.font.name="Arial"; r.font.size=Pt(15); r.font.color.rgb=ink

    s=bundle["synthesis"]
    add_box(.45,1.8,7.75,3.2,"مسئله و زمینه علمی",_as_text(s.get("background")),15)
    add_box(8.35,1.8,7.75,3.2,"خلاصه و سنتز موضوعی",_as_text(s.get("abstract"))+"\n"+_as_text(s.get("thematic_synthesis")),14)
    add_box(.45,5.2,7.75,4.2,"مقایسه روش‌ها", "\n".join("• "+_as_text(x) for x in s.get("methods_comparison",[])) or "شواهد کافی استخراج نشد",14)
    add_box(8.35,5.2,7.75,4.2,"مقایسه نتایج و تناقض‌ها", "\n".join("• "+_as_text(x) for x in s.get("results_comparison",[])+s.get("conflicting_evidence",[])) or "نتایج قابل مقایسه کافی نیستند",14)
    add_box(.45,9.6,7.75,4.2,"شکاف‌های پژوهشی", "\n".join("• "+_as_text(x) for x in s.get("research_gaps",[])) or "شکاف مستند تعیین نشد",14)
    add_box(8.35,9.6,7.75,4.2,"مسیرهای آینده", "\n".join("• "+_as_text(x) for x in s.get("future_directions",[])) or "پیشنهاد خاصی گزارش نشد",14)    refs=[]
    for i,p in enumerate(bundle["papers"][:10],1): refs.append(f"[{i}] {p.get('title','')} ({p.get('year','')}) DOI:{p.get('doi') or '-'}")
    add_box(.45,14.0,7.75,6.5,"منابع منتخب", "\n".join(refs),10)
    note="محدودیت‌ها: "+_as_text(s.get("limitations_of_review"))+"\nقدرت شواهد: "+_as_text(s.get("evidence_strength"))+"\nپوستر خودکار است؛ ادعاها را با منابع اصلی تطبیق دهید."
    add_box(8.35,14.0,7.75,6.5,"محدودیت‌ها و نتیجه‌گیری",_as_text(s.get("conclusion"))+"\n\n"+note+"\nکاربرد صنعتی: "+_as_text(s.get("industrial_applicability")),12)
    prs.save(out_path)
    return out_path


def build_poster_pdf(bundle, out_path):
    from reportlab.lib.pagesizes import A1
    from reportlab.lib.units import mm
    from reportlab.lib import colors
    from reportlab.platypus import SimpleDocTemplate, Table, TableStyle, Spacer
    rtl,body,heading,title,small=_reportlab_styles()
    poster_title = type(title)("PosterTitleA1", parent=title, fontSize=26, leading=33)
    poster_head = type(heading)("PosterHeadA1", parent=heading, fontSize=18, leading=23)
    poster_body = type(body)("PosterBodyA1", parent=body, fontSize=12, leading=19)
    poster_small = type(small)("PosterSmallA1", parent=small, fontSize=10, leading=15)
    doc=SimpleDocTemplate(out_path,pagesize=A1,rightMargin=18*mm,leftMargin=18*mm,topMargin=16*mm,bottomMargin=16*mm)
    s=bundle["synthesis"]
    story=[_para(None,"پوستر علمی | "+bundle["query"],poster_title,rtl),
           _para(None,f"مرور شواهد علمی | {len(bundle['papers'])} رکورد | {sum(1 for d in bundle['documents'] if d.get('status')=='full_text')} متن کامل",poster_body,rtl),Spacer(1,7*mm)]
    def cell_block(label, content, style=poster_body):
        out=[_para(None,label,poster_head,rtl)]
        if isinstance(content,list):
            for x in content: out.append(_para(None,"• "+_as_text(x),style,rtl))
        else: out.append(_para(None,_as_text(content) or "اطلاعات کافی در این اجرا موجود نیست.",style,rtl))
        return out
    col1=[]; col2=[]
    col1 += cell_block("مسئله و زمینه علمی",s.get("background"))
    col1 += cell_block("سنتز موضوعی",s.get("thematic_synthesis"))
    col1 += cell_block("مقایسه روش‌ها",s.get("methods_comparison",[]))
    col1 += cell_block("شکاف‌های پژوهشی",s.get("research_gaps",[]))
    col1 += cell_block("کیفیت مطالعات و بازتولیدپذیری",_as_text(s.get("study_quality"))+"\n"+_as_text(s.get("reproducibility_gaps")),poster_small)
    col2 += cell_block("چکیده",s.get("abstract"))
    col2 += cell_block("مقایسه نتایج",s.get("results_comparison",[]))
    col2 += cell_block("شواهد همسو و متناقض",s.get("convergent_evidence",[])+s.get("conflicting_evidence",[]))
    col2 += cell_block("مسیرهای آینده",s.get("future_directions",[]))
    col2 += cell_block("نتیجه و محدودیت‌ها",_as_text(s.get("conclusion"))+"\n"+_as_text(s.get("limitations_of_review")),poster_small)
    refs=[]
    for i,p in enumerate(bundle["papers"][:12],1):
        refs.append(f"[{i}] {p.get('title','')} ({p.get('year','')}) | DOI: {p.get('doi') or '-'}")
    col2 += cell_block("منابع منتخب",refs,poster_small)
    table=Table([[col1,col2]],colWidths=[doc.width*0.49,doc.width*0.49])
    table.setStyle(TableStyle([("VALIGN",(0,0),(-1,-1),"TOP"),("BOX",(0,0),(-1,-1),1,colors.HexColor("#B6C8D4")),
                               ("INNERGRID",(0,0),(-1,-1),1,colors.HexColor("#D6E1E8")),
                               ("BACKGROUND",(0,0),(-1,-1),colors.HexColor("#F8FBFD")),
                               ("LEFTPADDING",(0,0),(-1,-1),9*mm),("RIGHTPADDING",(0,0),(-1,-1),9*mm),
                               ("TOPPADDING",(0,0),(-1,-1),8*mm),("BOTTOMPADDING",(0,0),(-1,-1),8*mm)]))
    story.append(table)
    doc.build(story)
    return out_path


def build_evidence_xlsx(bundle, out_path):
    from openpyxl import Workbook
    from openpyxl.styles import Font, PatternFill, Alignment
    from openpyxl.utils import get_column_letter
    wb=Workbook(); ws=wb.active; ws.title="مطالعات"
    headers=["شناسه","عنوان","سال","نویسندگان","نشریه","DOI","استنادها","دسترسی آزاد","سطح متن","طرح مطالعه","نمونه/داده","روش‌ها","مواد/ترکیب","تجهیزات/شرایط","اعتبارسنجی/خط مبنا","خواص/معیارها","نتایج کمی/کیفی","سوگیری/محدودیت‌ها","بازتولیدپذیری/کاربرد صنعتی","لینک متن آزاد"]
    ws.append(headers)
    for p,d,a in zip(bundle["papers"],bundle["documents"],bundle["analyses"]):
        methods=a.get("methods") or a.get("methodology") or []
        if methods and isinstance(methods[0],dict): methods=[m.get("category","") for m in methods]
        ws.append([p.get("id"),p.get("title"),p.get("year"),_format_authors(p),p.get("journal"),p.get("doi"),p.get("citations"),p.get("oa_status"),d.get("status"),_as_text(a.get("study_design")),_as_text(a.get("sample_size"))+" | "+_as_text(a.get("dataset")),_as_text(methods),_as_text(a.get("materials",[]))+" | "+_as_text(a.get("alloy_composition",[])),_as_text(a.get("equipment_instrumentation",[]))+" | "+_as_text(a.get("process_conditions",[])),_as_text(a.get("validation_strategy",[]))+" | "+_as_text(a.get("baseline_comparators",[])),_as_text(a.get("measured_properties",[]))+" | "+_as_text(a.get("metrics",[])),_as_text(a.get("quantitative_findings",[]))+" | "+_as_text(a.get("findings",[])),_as_text(a.get("risk_of_bias",[]))+" | "+_as_text(a.get("limitations",[])),_as_text(a.get("reproducibility",[]))+" | "+_as_text(a.get("industrial_applicability",[])),d.get("source_url") or p.get("oa_url")])
    ev=wb.create_sheet("شواهد")
    ev.append(["شناسه مقاله","عنوان مقاله","نوع شاهد","ادعا/یافته","نقل‌قول مستند","صفحه"])
    for p,a in zip(bundle["papers"],bundle["analyses"]):
        for e in a.get("evidence_quotes",[]):
            ev.append([p.get("id"),p.get("title"),e.get("type",e.get("claim","")),e.get("claim",e.get("type","")),e.get("quote",""),e.get("page")])
    cmp=wb.create_sheet("سنتز و مقایسه")
    cmp.append(["بخش","یافته/جمع‌بندی"])
    for key,label in [("methods_comparison","مقایسه روش‌ها"),("results_comparison","مقایسه نتایج"),("convergent_evidence","شواهد همسو"),("conflicting_evidence","شواهد متناقض"),("explanations_for_conflicts","تبیین اختلاف"),("research_gaps","شکاف پژوهشی"),("future_directions","آینده پژوهش")]:
        for item in bundle["synthesis"].get(key,[]): cmp.append([label,_as_text(item)])
    for sheet in wb.worksheets:
        sheet.freeze_panes="A2"; sheet.auto_filter.ref=sheet.dimensions
        for cell in sheet[1]:
            cell.font=Font(bold=True,color="FFFFFF"); cell.fill=PatternFill("solid",fgColor="173B57"); cell.alignment=Alignment(horizontal="center",vertical="center",wrap_text=True)
        for col in sheet.columns:
            cells=list(col); maxlen=min(50,max(12,max((len(str(c.value or "")) for c in cells[1:]),default=12)))
            sheet.column_dimensions[get_column_letter(cells[0].column)].width=maxlen+2
        for row in sheet.iter_rows(min_row=2):
            for cell in row: cell.alignment=Alignment(vertical="top",wrap_text=True)
    wb.save(out_path); return out_path


def build_evidence_map(bundle, out_path):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    import networkx as nx
    from sklearn.feature_extraction.text import TfidfVectorizer
    from sklearn.metrics.pairwise import cosine_similarity
    papers=bundle["papers"]
    texts=[(p.get("title","")+" "+p.get("abstract","")).strip() for p in papers]
    G=nx.Graph()
    for i,p in enumerate(papers): G.add_node(i,label=f"{i+1}. {p.get('title','')[:48]}")
    if len(texts)>1:
        try:
            X=TfidfVectorizer(stop_words="english",max_features=4000,ngram_range=(1,2)).fit_transform(texts)
            sims=cosine_similarity(X)
            for i in range(len(papers)):
                neighbors=sorted([(float(sims[i,j]),j) for j in range(len(papers)) if j!=i],reverse=True)[:2]
                for score,j in neighbors:
                    if score>=0.06: G.add_edge(i,j,weight=score)
        except Exception: pass
    fig,ax=plt.subplots(figsize=(14,10))
    pos=nx.spring_layout(G,seed=19,k=1.2)
    nx.draw_networkx_nodes(G,pos,node_size=1700,ax=ax,node_color="#dcecf3",edgecolors="#31556b")
    nx.draw_networkx_edges(G,pos,ax=ax,alpha=.45,width=1.5)
    labels={i:f"{i+1}. {papers[i].get('title','')[:28]}" for i in range(len(papers))}
    nx.draw_networkx_labels(G,pos,labels=labels,font_size=7,ax=ax)
    ax.set_title("ResearchMate AI | Exploratory paper relationship map",fontsize=14); ax.axis("off")
    fig.tight_layout(); fig.savefig(out_path,dpi=180,bbox_inches="tight"); plt.close(fig)
    return out_path


def build_bibtex(bundle, out_path):
    lines=[]
    for i,p in enumerate(bundle["papers"],1):
        key=f"rm{i}_{p.get('year') or 'nd'}"
        fields={"title":p.get("title",""),"author":" and ".join(p.get("authors",[])),"year":p.get("year") or "", "journal":p.get("journal",""),"doi":p.get("doi",""),"url":p.get("oa_url","")}
        lines.append("@article{"+key+",\n"+",\n".join(f"  {k} = {{{str(v).replace('{','').replace('}','')}}}" for k,v in fields.items() if v)+"\n}")
    with open(out_path,"w",encoding="utf-8") as f: f.write("\n\n".join(lines))
    return out_path


def build_methodology_pdf(bundle, out_path):
    from reportlab.lib.pagesizes import A4
    from reportlab.lib.units import mm
    from reportlab.platypus import SimpleDocTemplate, Spacer
    rtl,body,heading,title,small=_reportlab_styles()
    doc=SimpleDocTemplate(out_path,pagesize=A4,rightMargin=17*mm,leftMargin=17*mm,topMargin=16*mm,bottomMargin=16*mm)
    stats={"کل رکوردهای بازیابی‌شده":len(bundle["papers"]),"متن کامل دریافت‌شده":sum(1 for d in bundle['documents'] if d.get('status')=='full_text'),"چکیده בלבד":sum(1 for d in bundle['documents'] if d.get('status')=='abstract_only'),"فراداده بدون چکیده":sum(1 for d in bundle['documents'] if d.get('status')=='metadata_only')}
    story=[_para(None,"گزارش روش‌شناسی و ممیزی شواهد",title,rtl),_para(None,"موضوع: "+bundle["query"],body,rtl)]
    for k,v in stats.items(): story.append(_para(None,f"{k}: {v}",body,rtl))
    story += [_para(None,"سیاست دسترسی",heading,rtl),_para(None,"فقط نسخه‌های قانونی و آزاد دریافت شدند. مقاله‌های فاقد متن کامل حذف نشدند؛ سطح تحلیل آن‌ها به چکیده یا فراداده محدود شد. هیچ تلاش برای دور زدن پرداخت، ورود یا محدودیت ناشر صورت نگرفت.",body,rtl),_para(None,"منابع و ردیابی",heading,rtl)]
    for p,d in zip(bundle['papers'],bundle['documents']):
        story.append(_para(None,f"{p.get('title','')} | DOI: {p.get('doi') or 'نامشخص'} | وضعیت: {d.get('status')} | منبع متن: {d.get('source_url') or 'ندارد'}",small,rtl))
    story += [_para(None,"محدودیت‌ها",heading,rtl),_para(None,"OpenAlex پوشش کامل همه ناشران/مخازن را تضمین نمی‌کند. لینک‌های آزاد ممکن است موقت، خراب، اسکن‌شده یا فاقد متن قابل استخراج باشند. شناسایی متن، جدول و نقل‌قول ممکن است خطا داشته باشد. رکوردهای این اجرا نباید بدون جست‌وجوی تکمیلی به‌عنوان مرور نظام‌مند یا فراتحلیل گزارش شوند.",body,rtl)]
    doc.build(story); return out_path


def package_outputs(bundle, output_dir):
    os.makedirs(output_dir,exist_ok=True)
    files={}
    warnings=[]
    # Editable formats should remain available even when an optional PDF dependency fails.
    files['review_docx']=build_review_docx(bundle,os.path.join(output_dir,"ResearchMate_Review_Article.docx"))
    files['poster_pptx']=build_poster_pptx(bundle,os.path.join(output_dir,"ResearchMate_Scientific_Poster_A1.pptx"))
    files['evidence_xlsx']=build_evidence_xlsx(bundle,os.path.join(output_dir,"ResearchMate_Evidence_Matrix.xlsx"))
    files['evidence_map']=build_evidence_map(bundle,os.path.join(output_dir,"ResearchMate_Evidence_Map.png"))
    files['bibtex']=build_bibtex(bundle,os.path.join(output_dir,"ResearchMate_References.bib"))
    pdf_builders = {
        'review_pdf': (build_review_pdf, "ResearchMate_Review_Article.pdf"),
        'poster_pdf': (build_poster_pdf, "ResearchMate_Scientific_Poster_A1.pdf"),
        'methodology_pdf': (build_methodology_pdf, "ResearchMate_Methodology_Audit.pdf"),
    }
    for key, (builder, filename) in pdf_builders.items():
        try:
            files[key] = builder(bundle, os.path.join(output_dir, filename))
        except Exception as exc:
            warnings.append(f"{key}: {type(exc).__name__}: {exc}")
    if warnings:
        warning_path=os.path.join(output_dir,"ResearchMate_Output_Warnings.txt")
        with open(warning_path,"w",encoding="utf-8") as warning_file:
            warning_file.write("برخی خروجی‌ها به دلیل وابستگی یا خطای تولید ساخته نشدند. فایل‌های موجود معتبر و قابل استفاده‌اند.\n\n"+"\n".join(warnings))
        files['output_warnings']=warning_path
    zip_path=os.path.join(output_dir,"ResearchMate_Complete_Research_Package.zip")
    with zipfile.ZipFile(zip_path,"w",zipfile.ZIP_DEFLATED) as z:
        for name,path in files.items(): z.write(path,arcname=os.path.basename(path))
    files['package_zip']=zip_path
    return files
