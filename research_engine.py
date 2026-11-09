"""ResearchMate AI research engine. Automatic OA retrieval + evidence-aware synthesis."""
from __future__ import annotations
import json, re, time, hashlib, textwrap
from typing import Any, Callable
from urllib.parse import urlparse, quote
import requests

OPENALEX = "https://api.openalex.org/works"
EUROPE_PMC = "https://www.ebi.ac.uk/europepmc/webservices/rest"
UNPAYWALL = "https://api.unpaywall.org/v2"
GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/models"
USER_AGENT = "ResearchMateAI/1.0 (open-access scholarly literature review; contact via app)"
MAX_PDF_BYTES = 12 * 1024 * 1024

METHOD_TERMS = {
    "آزمایشگاهی": ["experimental", "laboratory", "tensile test", "hardness test", "xrd", "sem", "tem", "characterization", "specimen"],
    "شبیه‌سازی عددی": ["finite element", "finite-element", "computational fluid dynamics", "cfd", "simulation", "numerical model", "phase-field", "procast", "thermo-calc"],
    "یادگیری ماشین": ["machine learning", "random forest", "support vector", "neural network", "deep learning", "xgboost", "gradient boosting", "regression model"],
    "تحلیل آماری": ["anova", "design of experiments", "response surface", "statistical analysis", "regression analysis"],
    "مرور/کتاب‌سنجی": ["systematic review", "meta-analysis", "bibliometric", "scoping review", "literature review"],
    "مطالعه صنعتی/موردی": ["case study", "industrial data", "plant data", "industrial scale", "production line", "steel plant"]
}
LIMITATION_TERMS = ["limitation", "limitations", "future work", "further research", "future studies", "remains unclear", "not investigated", "not considered", "lack of", "challenge", "however"]
RESULT_TERMS = ["result", "results", "finding", "findings", "showed", "demonstrated", "increased", "decreased", "improved", "reduced", "outperformed", "accuracy", "rmse", "mae", "r2", "r²", "yield", "strength", "efficiency"]
MATERIAL_TERMS = ["steel", "carbon steel", "stainless steel", "slag", "iron", "ore", "coke", "scrap", "molten metal", "alloy", "powder", "specimen", "sample"]


def clean_text(value: Any) -> str:
    return re.sub(r"\s+", " ", str(value or "")).strip()


def reconstruct_abstract(inverted: dict | None) -> str:
    if not isinstance(inverted, dict) or not inverted:
        return ""
    words = []
    for word, positions in inverted.items():
        for pos in positions:
            words.append((int(pos), word))
    return " ".join(w for _, w in sorted(words))


def _get_json(url: str, params: dict | None = None, timeout: int = 25) -> dict:
    """GET JSON with bounded transient retries and actionable OpenAlex 429 guidance."""
    max_attempts = 3
    for attempt in range(max_attempts):
        try:
            response = requests.get(
                url, params=params, timeout=timeout,
                headers={"User-Agent": USER_AGENT}
            )
            status = getattr(response, "status_code", None)

            if status == 429:
                retry_after = response.headers.get("Retry-After")
                try:
                    wait_seconds = float(retry_after) if retry_after is not None else None
                except (TypeError, ValueError):
                    wait_seconds = None

                # Retry only an explicit short server-directed delay. A daily-budget
                # exhaustion is not fixed by blind rapid retries.
                if attempt < max_attempts - 1 and wait_seconds is not None and 0 <= wait_seconds <= 5:
                    time.sleep(max(0.25, wait_seconds))
                    continue

                has_key = bool((params or {}).get("api_key"))
                try:
                    body = response.json()
                except (ValueError, AttributeError):
                    body = {}
                provider_message = clean_text(body.get("message") or body.get("error") or "")
                if has_key:
                    guidance = (
                        "کلید OpenAlex ارسال شده است؛ ممکن است سهمیه روزانه تمام شده باشد یا محدودیت سرعت رخ داده باشد. "
                        "در صورت وجود Retry-After همان مدت صبر کن؛ سهمیه رایگان روزانه در نیمه‌شب UTC بازنشانی می‌شود."
                    )
                else:
                    guidance = (
                        "برای استفاده پایدار، یک کلید رایگان OpenAlex بساز و در همین صفحه وارد کن؛ "
                        "این کلید متعلق به پایگاه مقالات است و کلید هوش مصنوعی نیست. "
                        "بدون کلید، سهمیه ناشناس بسیار محدودتر است."
                    )
                extra = f" پیام سرویس: {provider_message}" if provider_message else ""
                raise RuntimeError(
                    f"OpenAlex محدودیت درخواست یا سهمیه را اعمال کرد (HTTP 429). {guidance}{extra}"
                )

            if status in {500, 502, 503, 504} and attempt < max_attempts - 1:
                time.sleep(1.5 * (2 ** attempt))
                continue

            response.raise_for_status()
            return response.json()

        except requests.RequestException as exc:
            # Avoid echoing request URLs: OpenAlex API keys are supplied as query parameters.
            status = getattr(getattr(exc, "response", None), "status_code", None)
            detail = f"HTTP {status}" if status else "خطای شبکه یا timeout"
            raise RuntimeError(f"ارتباط با OpenAlex ناموفق بود ({detail}). اتصال اینترنت را بررسی کن.") from None
        except RuntimeError:
            raise
        except ValueError:
            raise RuntimeError("پاسخ OpenAlex در قالب JSON معتبر نبود.") from None

    raise RuntimeError("OpenAlex پس از چند تلاش محدود پاسخ نداد. کمی بعد دوباره تلاش کن.")


def search_openalex(query: str, year_from: int, year_to: int, limit: int = 30, api_key: str = "") -> list[dict]:
    requested_limit = min(max(int(limit), 1), 50)
    params = {
        "search": query,
        "filter": f"open_access.is_oa:true,type:article|review,from_publication_date:{year_from}-01-01,to_publication_date:{year_to}-12-31",
        "per-page": requested_limit,
        "sort": "relevance_score:desc",
        "select": "id,doi,title,publication_year,publication_date,authorships,primary_location,best_oa_location,locations,open_access,cited_by_count,abstract_inverted_index,ids,type,has_content,content_urls"
    }
    if api_key:
        params["api_key"] = api_key
    data = _get_json(OPENALEX, params=params)
    results = []
    for item in data.get("results", []):
        if not item.get("title"):
            continue
        work = normalize_work(item)
        # Defense in depth: retain only explicitly OA journal articles and reviews.
        if work.get("is_oa") is not True or work.get("type") not in {"article", "review"}:
            continue
        results.append(work)
        if len(results) >= requested_limit:
            break
    return results


def normalize_work(item: dict) -> dict:
    authors = []
    for entry in (item.get("authorships") or [])[:8]:
        author = (entry.get("author") or {}).get("display_name")
        if author:
            authors.append(author)
    best = item.get("best_oa_location") or {}
    primary = item.get("primary_location") or {}
    oa = item.get("open_access") or {}
    source = ((primary.get("source") or {}).get("display_name")
              or (best.get("source") or {}).get("display_name") or "")
    locations = item.get("locations") or []
    # Never attempt a PDF/landing-page URL from a closed-access location.
    oa_locations = []
    for loc in [best, *locations, primary]:
        if not loc:
            continue
        if loc is not best and loc.get("is_oa") is not True:
            continue
        if loc not in oa_locations:
            oa_locations.append(loc)
    urls = []
    for loc in oa_locations:
        u = loc.get("pdf_url")
        if u and u not in urls:
            urls.append(u)
    doi = (item.get("doi") or "").replace("https://doi.org/", "")
    ids = item.get("ids") or {}
    pmcid = ids.get("pmcid") or ""
    return {
        "id": item.get("id", ""), "title": clean_text(item.get("title")),
        "year": item.get("publication_year"), "date": item.get("publication_date", ""),
        "authors": authors, "authors_text": "; ".join(authors), "journal": source,
        "citations": int(item.get("cited_by_count") or 0), "doi": doi,
        "abstract": reconstruct_abstract(item.get("abstract_inverted_index")),
        "is_oa": bool(oa.get("is_oa")), "oa_status": oa.get("oa_status", "unknown"),
        "oa_url": oa.get("oa_url") or best.get("landing_page_url") or "",
        "license": best.get("license") or "unknown", "pdf_urls": urls,
        "oa_location_count": len(oa_locations),
        "pmcid": pmcid, "type": item.get("type", "article"),
        "has_content": item.get("has_content") or {}, "content_urls": item.get("content_urls") or {}, "source_work": item
    }


def _download_bytes(url: str, max_bytes: int = MAX_PDF_BYTES) -> tuple[bytes | None, str]:
    try:
        if urlparse(url).scheme not in {"http", "https"}:
            return None, "نشانی دانلود نامعتبر بود"
        with requests.get(url, stream=True, timeout=(10, 35), headers={"User-Agent": USER_AGENT}, allow_redirects=True) as r:
            r.raise_for_status()
            chunks, total = [], 0
            for chunk in r.iter_content(1024 * 128):
                if not chunk:
                    continue
                total += len(chunk)
                if total > max_bytes:
                    return None, "حجم فایل از سقف ۱۲ مگابایت بیشتر بود"
                chunks.append(chunk)
            data = b"".join(chunks)
            return data, r.headers.get("Content-Type", "")
    except Exception as exc:
        return None, str(exc)[:240]


def _extract_pdf(data: bytes) -> tuple[str, list[dict], list[list[list[str]]]]:
    import fitz
    doc = fitz.open(stream=data, filetype="pdf")
    pages, all_tables, full = [], [], []
    for page_no, page in enumerate(doc, 1):
        if page_no > 55:
            break
        txt = page.get_text("text") or ""
        pages.append({"page": page_no, "text": txt})
        full.append(txt)
        try:
            finder = page.find_tables()
            for table in finder.tables[:8]:
                rows = table.extract()
                if rows:
                    all_tables.append([[clean_text(x) for x in row] for row in rows if row])
        except Exception:
            pass
    doc.close()
    return "\n\n".join(full)[:100000], pages, all_tables


def _find_europe_pmc_pmcid(doi: str) -> str:
    """Resolve a DOI to a Europe PMC record only when the record is explicitly OA."""
    if not doi:
        return ""
    try:
        response = requests.get(
            f"{EUROPE_PMC}/search",
            params={"query": f'DOI:"{doi.strip()}"', "format": "json", "resultType": "core", "pageSize": 3},
            timeout=20, headers={"User-Agent": USER_AGENT}
        )
        response.raise_for_status()
        payload = response.json()
        for item in (payload.get("resultList") or {}).get("result", []):
            pmcid = item.get("pmcid") or ""
            is_oa = str(item.get("isOpenAccess", "")).upper() == "Y"
            if pmcid and is_oa:
                return pmcid
    except Exception:
        return ""
    return ""


def _extract_pmc_xml(pmcid: str) -> tuple[str, list[dict], list[list[list[str]]]] | None:
    if not pmcid:
        return None
    pid = pmcid.rsplit("/", 1)[-1].replace("PMC", "")
    url = f"{EUROPE_PMC}/PMC{pid}/fullTextXML"
    try:
        response = requests.get(url, timeout=25, headers={"User-Agent": USER_AGENT})
        if response.status_code != 200 or "<article" not in response.text[:1000]:
            return None
        from bs4 import BeautifulSoup
        soup = BeautifulSoup(response.text, "xml")
        sections = []
        for sec in soup.find_all("sec"):
            title = sec.find("title", recursive=False)
            body = " ".join(clean_text(p.get_text(" ")) for p in sec.find_all("p"))
            if body:
                sections.append((clean_text(title.get_text(" ")) if title else "", body))
        if not sections:
            body = clean_text(soup.get_text(" "))[:100000]
        else:
            body = "\n\n".join((t + "\n" if t else "") + b for t, b in sections)[:100000]
        pages = [{"page": None, "text": body}]
        tables = []
        for table in soup.find_all("table-wrap"):
            rows = []
            for tr in table.find_all("tr"):
                cells = [clean_text(c.get_text(" ")) for c in tr.find_all(["th", "td"])]
                if cells:
                    rows.append(cells)
            if rows:
                tables.append(rows)
        return body, pages, tables
    except Exception:
        return None


def _extract_openalex_tei(work_id: str, api_key: str) -> tuple[str, list[dict], list[list[list[str]]]] | None:
    if not work_id or not api_key:
        return None
    short_id = work_id.rsplit("/", 1)[-1]
    url = f"https://content.openalex.org/works/{short_id}.grobid-xml"
    try:
        response = requests.get(url, params={"api_key": api_key}, timeout=35,
                                headers={"User-Agent": USER_AGENT})
        if response.status_code != 200 or len(response.content) < 700:
            return None
        from bs4 import BeautifulSoup
        soup = BeautifulSoup(response.content, "xml")
        # OpenAlex notes that GROBID exports can use either TEI or legacy HTML-wrapped TEI.
        body = soup.find("body")
        if body is None:
            body = soup.find("text")
        root = body or soup
        paragraphs = [clean_text(p.get_text(" ")) for p in root.find_all(["p", "head", "formula"])]
        text = "\n\n".join(p for p in paragraphs if p)
        if len(text) < 700:
            text = clean_text(root.get_text(" "))
        tables = []
        for table in soup.find_all("table"):
            rows=[]
            for tr in table.find_all("row") + table.find_all("tr"):
                cells=[clean_text(cell.get_text(" ")) for cell in tr.find_all(["cell", "td", "th"])]
                if cells: rows.append(cells)
            if rows: tables.append(rows)
        if len(text) > 700:
            return text[:100000], [{"page": None, "text": text[:100000]}], tables
    except Exception:
        return None
    return None


def _extract_openalex_pdf(work_id: str, api_key: str) -> tuple[str, list[dict], list[list[list[str]]]] | None:
    """Download OpenAlex's cached PDF representation for an eligible OA work."""
    if not work_id or not api_key:
        return None
    short_id = work_id.rsplit("/", 1)[-1]
    url = f"https://content.openalex.org/works/{short_id}.pdf"
    try:
        with requests.get(url, params={"api_key": api_key}, stream=True, timeout=(10, 40),
                          headers={"User-Agent": USER_AGENT}) as response:
            response.raise_for_status()
            chunks, total = [], 0
            for chunk in response.iter_content(128 * 1024):
                if not chunk:
                    continue
                total += len(chunk)
                if total > MAX_PDF_BYTES:
                    return None
                chunks.append(chunk)
            data = b"".join(chunks)
        if not data.startswith(b"%PDF"):
            return None
        body, pages, tables = _extract_pdf(data)
        if len(body.strip()) > 700:
            return body, pages, tables
    except Exception:
        return None
    return None


def _unpaywall_oa_urls(doi: str, email: str) -> list[str]:
    """Return only URLs from an Unpaywall record explicitly marked Open Access."""
    if not doi or not email:
        return []
    try:
        doi_path = quote(doi.strip(), safe="/")
        response = requests.get(f"{UNPAYWALL}/{doi_path}", params={"email": email.strip()},
                                timeout=20, headers={"User-Agent": USER_AGENT})
        response.raise_for_status()
        record = response.json()
        if not record.get("is_oa"):
            return []
        locations = [record.get("best_oa_location"), *(record.get("oa_locations") or [])]
        urls = []
        for location in locations:
            if not isinstance(location, dict):
                continue
            for key in ("url_for_pdf", "url", "url_for_landing_page"):
                url = location.get(key)
                if url and urlparse(url).scheme in {"http", "https"} and url not in urls:
                    urls.append(url)
        return urls
    except Exception:
        # Resolver failure is non-fatal; never expose the supplied email in logs.
        return []


def obtain_fulltext(paper: dict, openalex_api_key: str = "", unpaywall_email: str = "") -> dict:
    """Try OA XML/PDF routes automatically. Never attempts paywall bypass."""
    if openalex_api_key and (paper.get("has_content", {}).get("grobid_xml") or paper.get("content_urls", {}).get("grobid_xml")):
        tei = _extract_openalex_tei(paper.get("id", ""), openalex_api_key)
        if tei and len(tei[0]) > 800:
            body, pages, tables = tei
            return {"status": "full_text", "source_url": f"https://content.openalex.org/works/{paper.get('id','').rsplit('/',1)[-1]}.grobid-xml",
                    "format": "tei-xml", "text": body, "pages": pages, "tables": tables,
                    "reason": "متن ساختاریافته GROBID از مخزن OpenAlex دریافت شد"}

    if openalex_api_key and (paper.get("has_content", {}).get("pdf") or paper.get("content_urls", {}).get("pdf")):
        cached_pdf = _extract_openalex_pdf(paper.get("id", ""), openalex_api_key)
        if cached_pdf:
            body, pages, tables = cached_pdf
            return {"status": "full_text", "source_url": f"https://content.openalex.org/works/{paper.get('id','').rsplit('/',1)[-1]}.pdf",
                    "format": "pdf", "text": body, "pages": pages, "tables": tables,
                    "reason": "PDF دسترسی‌آزاد از مخزن متن کامل OpenAlex دریافت و استخراج شد"}

    pmcid = paper.get("pmcid") or ""
    # If OpenAlex has no PMCID, resolve the DOI through Europe PMC and require isOpenAccess=Y.
    if not pmcid:
        pmcid = _find_europe_pmc_pmcid(paper.get("doi", ""))
    if pmcid:
        xml_result = _extract_pmc_xml(pmcid)
        if xml_result and len(xml_result[0]) > 800:
            body, pages, tables = xml_result
            return {"status": "full_text", "source_url": f"{EUROPE_PMC}/PMC{pmcid.rsplit('/',1)[-1]}/fullTextXML",
                    "format": "xml", "text": body, "pages": pages, "tables": tables, "reason": "متن XML آزاد از Europe PMC"}

    candidates = list(paper.get("pdf_urls") or [])
    # Supplement OpenAlex locations with Unpaywall, but only when its own record confirms OA.
    for unpaywall_url in _unpaywall_oa_urls(paper.get("doi", ""), unpaywall_email):
        if unpaywall_url not in candidates:
            candidates.append(unpaywall_url)
    # OpenAlex OA landing page may expose a PDF link.
    if paper.get("oa_url") and paper["oa_url"] not in candidates:
        candidates.append(paper["oa_url"])
    last_reason = "نسخه متن کامل آزاد در رکورد پیدا نشد"
    for url in candidates[:6]:
        data, content_type = _download_bytes(url)
        if not data:
            last_reason = content_type
            continue
        if data.startswith(b"%PDF") or "application/pdf" in content_type.lower():
            try:
                body, pages, tables = _extract_pdf(data)
                if len(body.strip()) > 700:
                    return {"status": "full_text", "source_url": url, "format": "pdf", "text": body,
                            "pages": pages, "tables": tables, "reason": "PDF آزاد دریافت و استخراج شد"}
                last_reason = "فایل PDF متن قابل استخراج کافی نداشت؛ ممکن است اسکن‌شده باشد"
            except Exception as exc:
                last_reason = f"استخراج PDF ناموفق بود: {str(exc)[:160]}"
        elif "text/html" in content_type.lower() or data[:100].lstrip().startswith(b"<!doctype html") or b"<html" in data[:300].lower():
            try:
                from bs4 import BeautifulSoup
                soup = BeautifulSoup(data, "html.parser")
                for tag in soup(["script", "style", "nav", "footer", "header"]): tag.decompose()
                pdf_links = []
                for link in soup.find_all("a", href=True):
                    href = link["href"]
                    if ".pdf" in href.lower():
                        from urllib.parse import urljoin
                        pdf_links.append(urljoin(url, href))
                for pdf_url in pdf_links[:3]:
                    pdf_data, pdf_type = _download_bytes(pdf_url)
                    if pdf_data and pdf_data.startswith(b"%PDF"):
                        body, pages, tables = _extract_pdf(pdf_data)
                        if len(body.strip()) > 700:
                            return {"status": "full_text", "source_url": pdf_url, "format": "pdf", "text": body,
                                    "pages": pages, "tables": tables, "reason": "PDF آزاد از صفحه مخزن پیدا و دریافت شد"}
                html_text = soup.get_text("\n", strip=True)
                html_low = html_text.lower()
                blocked_markers = ["sign in to access", "log in to access", "purchase this article", "subscribe to read", "access denied", "institutional login"]
                substantive_paras = [clean_text(p.get_text(" ")) for p in soup.find_all("p") if len(clean_text(p.get_text(" "))) > 110]
                if len(html_text) > 1800 and len(substantive_paras) >= 3 and not any(x in html_low for x in blocked_markers):
                    return {"status": "full_text", "source_url": url, "format": "html", "text": html_text[:100000],
                            "pages": [{"page": None, "text": html_text[:100000]}], "tables": [], "reason": "متن مقاله از صفحه HTML آزاد استخراج شد"}
                last_reason = "صفحه بازیابی‌شده احتمالاً صفحه ورود/خرید یا محتوای ناکامل بود؛ به‌عنوان متن کامل پذیرفته نشد"
            except Exception as exc:
                last_reason = f"استخراج صفحه آزاد ناموفق بود: {str(exc)[:160]}"
    return {"status": "abstract_only" if paper.get("abstract") else "metadata_only", "source_url": "",
            "format": "abstract", "text": paper.get("abstract", ""), "pages": [], "tables": [],
            "reason": last_reason}


def _paragraphs(text: str) -> list[str]:
    parts = re.split(r"\n+|(?<=[.!?])\s{2,}", text or "")
    return [clean_text(p) for p in parts if len(clean_text(p)) > 45]


def _has_term(text: str, term: str) -> bool:
    # Avoid false positives such as "tem" inside "temperature" or "ore" inside "more".
    return re.search(r"(?<![a-z0-9])" + re.escape(term.lower()) + r"(?![a-z0-9])", text.lower()) is not None


def rule_based_analysis(paper: dict, fulltext: dict) -> dict:
    text = fulltext.get("text") or paper.get("abstract") or ""
    low = text.lower()
    paragraphs = _paragraphs(text)
    methods = []
    for name, terms in METHOD_TERMS.items():
        hits = [term for term in terms if _has_term(low, term)]
        if hits:
            methods.append({"category": name, "signals": hits[:8]})
    limitations = [p for p in paragraphs if any(_has_term(p.lower(), t) for t in LIMITATION_TERMS)][:12]
    findings = [p for p in paragraphs if any(_has_term(p.lower(), t) for t in RESULT_TERMS)][:14]
    materials = sorted({term for term in MATERIAL_TERMS if _has_term(low, term)})
    # capture conditions/metrics as candidate evidence, not as normalized measurements
    numeric = []
    patterns = [r"\b\d+(?:\.\d+)?\s?(?:°\s?C|K|MPa|GPa|%|wt\.?\s?%|vol\.?\s?%|min|minutes|s|h|hours|kWh/t|MJ/t|ppm|mg/L)\b",
                r"\b(?:R2|R²|RMSE|MAE|accuracy|precision|recall|F1)\s*(?:=|of|:)?\s*\d+(?:\.\d+)?\b"]
    for pattern in patterns:
        numeric += re.findall(pattern, text, flags=re.I)
    evidence = []
    for label, rows in [("نتایج", findings), ("محدودیت", limitations)]:
        for row in rows[:5]:
            page = None
            for pg in fulltext.get("pages", []):
                if row[:70] and row[:70] in (pg.get("text") or ""):
                    page = pg.get("page"); break
            evidence.append({"type": label, "quote": row[:650], "page": page})
    abstract = paper.get("abstract", "")
    return {
        "paper_id": paper.get("id"), "evidence_level": fulltext.get("status"),
        "problem": abstract[:900] if abstract else "از چکیده قابل تعیین نیست",
        "objective": "از چکیده و متن استخراج‌شده باید توسط مرورگر علمی تکمیل شود",
        "methodology": methods, "materials": materials, "conditions_metrics": sorted(set(numeric))[:40],
        "findings": findings[:8], "limitations": limitations[:8],
        "future_work": [p for p in limitations if any(_has_term(p.lower(), k) for k in ["future", "further", "remain"])][:5],
        "evidence_quotes": evidence,
        "analysis_mode": "rule_based_preliminary"
    }


def gemini_generate(prompt: str, api_key: str, model: str = "gemini-3.8-flash", timeout: int = 90) -> str:
    url = f"{GEMINI_BASE}/{model}:generateContent"
    payload_request = {
        "contents": [{"role": "user", "parts": [{"text": prompt}]}],
        "generationConfig": {"responseMimeType": "application/json", "maxOutputTokens": 8192}
    }
    last_error = None
    for attempt in range(3):
        try:
            response = requests.post(url, headers={"x-goog-api-key": api_key}, json=payload_request, timeout=timeout)
            if response.status_code in (429, 500, 502, 503, 504) and attempt < 2:
                time.sleep(2 ** (attempt + 1))
                continue
            response.raise_for_status()
            payload = response.json()
            break
        except requests.HTTPError as exc:
            response = exc.response
            status = getattr(response, "status_code", None)
            message = ""
            try:
                error_payload = response.json()
                message = clean_text((error_payload.get("error") or {}).get("message", ""))
            except Exception:
                message = ""
            if attempt == 2 or status not in (429, 500, 502, 503, 504):
                detail = f"HTTP {status}" if status else "HTTP error"
                if message:
                    detail += f": {message[:350]}"
                raise RuntimeError(f"درخواست Gemini ناموفق بود ({detail}). کلید، دسترسی مدل و سهمیه API را بررسی کن.") from None
            last_error = exc
            time.sleep(2 ** (attempt + 1))
        except requests.RequestException as exc:
            last_error = exc
            if attempt == 2:
                raise RuntimeError("ارتباط با Gemini ناموفق بود؛ اتصال اینترنت یا محدودیت شبکه را بررسی کن.") from None
            time.sleep(2 ** (attempt + 1))
    else:
        raise RuntimeError(str(last_error or "درخواست مدل ناموفق بود"))
    try:
        return payload["candidates"][0]["content"]["parts"][0]["text"]
    except (KeyError, IndexError, TypeError):
        raise RuntimeError("مدل پاسخی در قالب قابل‌خواندن برنگرداند")


def _parse_json(text: str) -> dict:
    raw = text.strip()
    raw = re.sub(r"^```(?:json)?\s*|\s*```$", "", raw, flags=re.I)
    start, end = raw.find("{"), raw.rfind("}")
    if start < 0 or end <= start:
        raise ValueError("پاسخ مدل JSON معتبر نداشت")
    return json.loads(raw[start:end+1])


def select_analysis_text(fulltext: dict, abstract: str, max_chars: int = 36000) -> str:
    """Prioritize intro, methods, results, discussion and limitations under token budget."""
    pages = fulltext.get("pages") or []
    chunks = []
    for item in pages:
        page = item.get("page")
        prefix = f"\n[PAGE {page}]\n" if page is not None else "\n[PAGE UNKNOWN]\n"
        chunks.append(prefix + (item.get("text") or ""))
    text = "\n".join(chunks) if chunks else (fulltext.get("text") or abstract or "")
    text = text.strip()
    if len(text) <= max_chars:
        result = text
    else:
        selected = [text[:5000]]
        patterns = [r"materials?\s+and\s+methods?", r"experimental\s+(?:procedure|methods?)", r"methodology", r"results?", r"discussion", r"conclusions?", r"limitations?", r"future\s+work"]
        spans=[]
        for pattern in patterns:
            for match in re.finditer(pattern, text, flags=re.I):
                start=max(0,match.start()-900); end=min(len(text),match.end()+4200)
                if not any(start < b and end > a for a,b in spans): spans.append((start,end))
        for start,end in sorted(spans):
            snippet=text[start:end]
            if sum(len(x) for x in selected)+len(snippet)>max_chars: break
            selected.append(snippet)
        if len(selected)==1:
            selected.append(text[-min(8000,max_chars-5000):])
        result = "\n\n[EXTRACTED ARTICLE SECTIONS]\n".join(selected)
    table_text = "\n\n[TABLE DATA]\n" + json.dumps(fulltext.get("tables", [])[:12], ensure_ascii=False)[:6000]
    if fulltext.get("tables") and len(result)+len(table_text)<max_chars+6500:
        result += table_text
    return result[:max_chars+6500]


def analyze_one_with_llm(paper: dict, fulltext: dict, api_key: str, model: str) -> dict:
    source_text = select_analysis_text(fulltext, paper.get("abstract", ""))
    prompt = f"""نقش تو: پژوهشگر ارشد و داور علمی در حوزه مهندسی مواد/متالورژی و روش پژوهش. فقط بر اساس سند زیر تحلیل کن؛ هیچ ماده، عدد، نتیجه یا روشی را حدس نزن. اگر داده‌ای موجود نیست دقیقاً بنویس «گزارش نشده/قابل استخراج نیست». زبان خروجی فارسی علمی باشد. نقل‌قول‌های شواهد باید عیناً از متن منبع باشند و شماره صفحه فقط وقتی واقعاً موجود است داده شود.
خروجی فقط JSON با کلیدهای زیر باشد:
problem, objective, hypothesis, study_design, sample_size, dataset, materials, alloy_composition, methods, equipment_instrumentation, process_conditions, input_variables, output_variables, measured_properties, metrics, baseline_comparators, validation_strategy, uncertainty_statistics, quantitative_findings, findings, risk_of_bias, reproducibility, industrial_applicability, limitations, future_work, evidence_quotes, evidence_level, confidence.
materials و alloy_composition و methods و equipment_instrumentation و process_conditions و input_variables و output_variables و measured_properties و metrics و baseline_comparators و validation_strategy و uncertainty_statistics و quantitative_findings و findings و risk_of_bias و reproducibility و industrial_applicability و limitations و future_work آرایه رشته باشند. study_design و sample_size و dataset رشته باشند. evidence_quotes آرایه‌ای از آبجکت‌های {{"claim":"...","quote":"نقل قول عینی از منبع","page":عدد یا null}}. evidence_level یکی از full_text یا abstract_only. confidence عدد 0 تا 1.
در quantitative_findings فقط اعداد همراه واحد و زمینه/شرط را بیاور. در findings نتایج را با شرایط آزمایش و نوع مقایسه ذکر کن. عنوان/DOI را دستکاری نکن.
شناسه منبع: {paper['id']}
عنوان: {paper['title']}
سال: {paper.get('year')}
DOI: {paper.get('doi')}
وضعیت متن: {fulltext.get('status')}
متن مقاله/چکیده:
{source_text}
"""
    result = _parse_json(gemini_generate(prompt, api_key, model))
    array_fields = ["materials", "alloy_composition", "methods", "equipment_instrumentation", "process_conditions",
                    "input_variables", "output_variables", "measured_properties", "metrics", "baseline_comparators",
                    "validation_strategy", "uncertainty_statistics", "quantitative_findings", "findings", "risk_of_bias",
                    "reproducibility", "industrial_applicability", "limitations", "future_work"]
    for field in array_fields:
        value = result.get(field, [])
        if isinstance(value, str): result[field] = [value] if value.strip() else []
        elif not isinstance(value, list): result[field] = []
    for field in ["problem", "objective", "hypothesis", "study_design", "sample_size", "dataset"]:
        if not isinstance(result.get(field), str): result[field] = _as_json_text(result.get(field, ""))
    quotes = result.get("evidence_quotes", [])
    if not isinstance(quotes, list): quotes = []
    # Verify every model-provided quotation against the source text before exposing it.
    source_norm = clean_text(source_text).casefold()
    page_texts = {str(pg.get("page")): clean_text(pg.get("text", "")).casefold()
                  for pg in (fulltext.get("pages") or []) if pg.get("page") is not None}
    verified_quotes, rejected_quotes = [], 0
    for quote_item in quotes:
        if not isinstance(quote_item, dict) or not quote_item.get("quote"):
            rejected_quotes += 1
            continue
        quote_text = clean_text(quote_item.get("quote"))
        quote_norm = quote_text.casefold()
        if not quote_norm or quote_norm not in source_norm:
            rejected_quotes += 1
            continue
        quote_item["quote"] = quote_text
        quote_item["quote_verified"] = True
        if quote_item.get("page") is not None:
            page_text = page_texts.get(str(quote_item["page"]))
            quote_item["page_verified"] = bool(page_text and quote_norm in page_text)
            if not quote_item["page_verified"]:
                quote_item["page"] = None
        verified_quotes.append(quote_item)
    result["evidence_quotes"] = verified_quotes
    result["unverified_quotes_rejected"] = rejected_quotes
    result["paper_id"] = paper["id"]
    result["analysis_mode"] = "llm_evidence_extraction"
    result["evidence_level"] = fulltext.get("status")
    return result


def _as_json_text(value: Any) -> str:
    if value is None: return ""
    if isinstance(value, (dict, list)): return json.dumps(value, ensure_ascii=False)
    return str(value)


def synthesize_with_llm(query: str, papers: list[dict], analyses: list[dict], api_key: str, model: str) -> dict:
    corpus = []
    for p, a in zip(papers, analyses):
        corpus.append({
            "paper_id": p["id"], "title": p["title"], "year": p.get("year"), "doi": p.get("doi"),
            "access_level": a.get("evidence_level"), "study_design": a.get("study_design"),
            "sample_size": a.get("sample_size"), "dataset": a.get("dataset"),
            "materials": a.get("materials", []), "alloy_composition": a.get("alloy_composition", []),
            "methods": a.get("methods", a.get("methodology", [])),
            "process_conditions": a.get("process_conditions", []),
            "validation_strategy": a.get("validation_strategy", []),
            "baseline_comparators": a.get("baseline_comparators", []),
            "metrics": a.get("metrics", []), "quantitative_findings": a.get("quantitative_findings", []),
            "findings": a.get("findings", []), "limitations": a.get("limitations", []),
            "risk_of_bias": a.get("risk_of_bias", []), "reproducibility": a.get("reproducibility", []),
            "industrial_applicability": a.get("industrial_applicability", []),
            "evidence_quotes": a.get("evidence_quotes", [])[:5]
        })
    prompt = f"""تو نویسنده یک مقاله مروری انتقادی در حوزه علوم مهندسی هستی. موضوع مرور: {query}
فقط از داده‌ها و شناسه مقالات زیر استفاده کن؛ هیچ مرجع یا نتیجه ساختگی تولید نکن. هر ادعای مهم باید حداقل یک paper_id از همین مجموعه داشته باشد. تفاوت شرایط مطالعه را توضیح بده و از مقایسه مستقیم نتایجی که واحد/شرایط/داده متفاوت دارند خودداری کن. بین شواهد متن کامل و چکیده فرق بگذار. شکاف پژوهشی را به‌عنوان فرضیه قابل آزمون بنویس، نه حقیقت قطعی.
فقط JSON با کلیدهای زیر:
abstract, keywords, background, thematic_synthesis, methods_comparison, results_comparison, convergent_evidence, conflicting_evidence, explanations_for_conflicts, research_gaps, future_directions, conclusion, limitations_of_review, evidence_strength, study_quality, reproducibility_gaps, industrial_applicability, meta_analysis_feasibility, claims.
abstract و background و thematic_synthesis و conclusion و limitations_of_review و study_quality و reproducibility_gaps و industrial_applicability و meta_analysis_feasibility رشته فارسی علمی باشند. در study_quality کیفیت طراحی، حجم نمونه، اعتبارسنجی و خطر سوگیری را بدون نمره ساختگی ارزیابی کن. در meta_analysis_feasibility توضیح بده آیا تعریف متغیرها، واحدها و پراکندگی داده‌ها اجازه ترکیب کمی می‌دهد. keywords و methods_comparison و results_comparison و convergent_evidence و conflicting_evidence و explanations_for_conflicts و research_gaps و future_directions آرایه‌ای از رشته‌ها یا آبجکت‌های ساده باشند. claims آرایه‌ای از آبجکت‌های {{"claim":"...","paper_ids":["..."],"support":"strong|moderate|limited","reason":"..."}} باشد. paper_ids فقط از شناسه‌های داده‌شده انتخاب شوند.
داده‌های مقاله‌ها:
{json.dumps(corpus, ensure_ascii=False)[:52000]}
"""
    out = _parse_json(gemini_generate(prompt, api_key, model, timeout=150))
    allowed = {p["id"] for p in papers}
    valid_claims = []
    raw_claims = out.get("claims", [])
    if not isinstance(raw_claims, list): raw_claims = []
    for claim in raw_claims:
        if isinstance(claim, dict):
            raw_ids = claim.get("paper_ids", [])
            if isinstance(raw_ids, str): raw_ids = [raw_ids]
            if not isinstance(raw_ids, list): raw_ids = []
            claim["paper_ids"] = [pid for pid in raw_ids if pid in allowed]
            valid_claims.append(claim)
    out["claims"] = valid_claims
    array_fields = ["keywords", "methods_comparison", "results_comparison", "convergent_evidence",
                    "conflicting_evidence", "explanations_for_conflicts", "research_gaps", "future_directions"]
    for field in array_fields:
        value = out.get(field, [])
        if isinstance(value, str): out[field] = [value] if value.strip() else []
        elif not isinstance(value, list): out[field] = []
    scalar_fields = ["abstract", "background", "thematic_synthesis", "conclusion", "limitations_of_review", "evidence_strength", "study_quality", "reproducibility_gaps", "industrial_applicability", "meta_analysis_feasibility"]
    for field in scalar_fields:
        if not isinstance(out.get(field), str): out[field] = _as_json_text(out.get(field, ""))
    out["analysis_mode"] = "llm_cross_paper_synthesis"
    return out


def fallback_synthesis(query: str, papers: list[dict], analyses: list[dict]) -> dict:
    method_counts, all_materials = {}, {}
    for p, a in zip(papers, analyses):
        for m in a.get("methodology", []) if isinstance(a.get("methodology"), list) else []:
            label = m.get("category", "نامشخص") if isinstance(m, dict) else str(m)
            method_counts[label] = method_counts.get(label, 0) + 1
        for mat in a.get("materials", []): all_materials[mat] = all_materials.get(mat, 0) + 1
    full = sum(1 for a in analyses if a.get("evidence_level") == "full_text")
    return {
        "abstract": f"این بررسی اکتشافی با هدف گردآوری و سازمان‌دهی شواهد منتشرشده درباره «{query}» انجام شده است. از {len(papers)} رکورد بررسی‌شده، متن کامل آزاد {full} مورد دریافت شد؛ سایر موارد صرفاً بر پایه چکیده/فراداده قابل ارزیابی بودند. در این حالت، جمع‌بندی‌ها توصیفی و مقدماتی‌اند.",
        "keywords": [query, "مرور ادبیات", "تحلیل شواهد", "مهندسی مواد"],
        "background": "برای نگارش مقدمه مروری عمیق، باید مسئله صنعتی/علمی و مبانی نظری با منابع مرجع مستقل تکمیل شود. این نسخه از رکوردهای بازیابی‌شده به‌تنهایی ادعای پوشش جامع ادبیات ندارد.",
        "thematic_synthesis": "سامانه در این اجرا شواهد را به تفکیک مقاله استخراج کرده است. تحلیل بین‌مقاله‌ای تفسیری و داوری‌محور نیازمند فعال‌کردن مدل زبانی و بررسی نقل‌قول‌ها توسط پژوهشگر است.",
        "methods_comparison": [f"{k}: در {v} مقاله نشانه واژگانی/استخراجی دیده شد؛ این شمارش به معنی همسان بودن طراحی روش‌ها نیست." for k,v in method_counts.items()],
        "results_comparison": ["مقایسه عددی معتبر فقط برای نتایجی مجاز است که تعریف متغیر، واحد، شرایط آزمایش و معیار ارزیابی همسان یا قابل تبدیل داشته باشند. این نسخه از نتیجه‌گیری فراتر از داده‌های استخراج‌شده خودداری می‌کند."],
        "convergent_evidence": [], "conflicting_evidence": [], "explanations_for_conflicts": [],
        "research_gaps": ["برای تعیین شکاف واقعی، ابتدا باید پوشش جست‌وجو، کیفیت مطالعات و شواهد متن کامل تکمیل شود؛ نتایج این اجرا به‌تنهایی شکاف اثبات‌شده تولید نمی‌کند."],
        "future_directions": ["بارگذاری/دریافت متن کامل و بررسی جداول نتایج و طراحی آزمایش برای مقالات فاقد متن کامل.", "مقایسه کمی فقط پس از همسان‌سازی شرایط، واحدها و معیارهای عملکرد."],
        "conclusion": f"مجموعه فعلی شامل {len(papers)} مقاله است که {full} مورد متن کامل قابل‌استخراج دارند. این خروجی، نقشه اولیه شواهد است و تا زمان تحلیل مدل‌محور و اعتبارسنجی انسانی نباید به‌عنوان مقاله مروری آماده انتشار تلقی شود.",
        "limitations_of_review": "این اجرا از موتور قواعد و استخراج متن استفاده کرده است؛ ارزیابی کیفیت طراحی مطالعه، خطر سوگیری و فراتحلیل آماری انجام نشده است.",
        "evidence_strength": "محدود؛ موتور LLM در این اجرا فعال نیست.", "study_quality": "کیفیت طراحی مطالعات و خطر سوگیری به‌صورت استاندارد ارزیابی نشده است.", "reproducibility_gaps": "اطلاعات داده، کد و جزئیات آزمون برای سنجش بازتولیدپذیری کافی نیست.", "industrial_applicability": "قابلیت انتقال نتایج به صنعت از چکیده و فراداده قابل داوری نیست.", "meta_analysis_feasibility": "به دلیل نبود داده‌های همگن و استخراج کامل اعداد/واریانس‌ها، فراتحلیل آماری در این اجرا امکان‌پذیر نیست.", "claims": [], "analysis_mode": "fallback_rules_only"
    }


def run_research(query: str, year_from: int, year_to: int, max_papers: int = 12,
                 api_key: str = "", model: str = "gemini-3.8-flash", openalex_api_key: str = "",
                 consent_to_send: bool = False, progress: Callable[[str, int, int], None] | None = None,
                 unpaywall_email: str = "") -> dict:
    if not clean_text(query):
        raise ValueError("موضوع پژوهش خالی است")
    requested_limit = min(max(int(max_papers), 1), 50)
    papers = search_openalex(query, year_from, year_to, requested_limit, api_key=openalex_api_key)
    # Final pipeline guard: never allow non-OA or non-article/review records into analysis.
    papers = [p for p in papers if p.get("is_oa") is True and p.get("type") in {"article", "review"}]
    # Final hard cap, even if a cached/alternate search provider returns excess records.
    papers = papers[:requested_limit]
    if not papers:
        raise RuntimeError("در این بازه، مقاله یا مقاله‌مرورِ دارای برچسب دسترسی‌آزاد (Open Access) در OpenAlex پیدا نشد. عبارت جست‌وجو یا بازه زمانی را تغییر بده.")
    analyses, docs, logs = [], [], []
    total = len(papers)
    for idx, paper in enumerate(papers, 1):
        doc = obtain_fulltext(paper, openalex_api_key=openalex_api_key, unpaywall_email=unpaywall_email)
        docs.append(doc)
        analysis = rule_based_analysis(paper, doc)
        if api_key and consent_to_send:
            try:
                analysis = analyze_one_with_llm(paper, doc, api_key, model)
                logs.append(f"تحلیل مدل زبانی موفق: {paper['title'][:90]}")
                time.sleep(0.25)
            except Exception as exc:
                analysis["llm_error"] = str(exc)[:220]
                logs.append(f"تحلیل مدل زبانی برای یک مقاله ناموفق بود؛ قواعد جایگزین شد: {str(exc)[:120]}")
        analyses.append(analysis)
        if progress:
            progress(f"دریافت و تحلیل مقاله {idx}/{total}: {paper['title'][:85]}", idx, total)
    if api_key and consent_to_send:
        try:
            synthesis = synthesize_with_llm(query, papers, analyses, api_key, model)
            logs.append("سنتز بین‌مقاله‌ای با مدل زبانی انجام شد.")
        except Exception as exc:
            synthesis = fallback_synthesis(query, papers, analyses)
            synthesis["llm_error"] = str(exc)[:250]
            logs.append(f"سنتز مدل زبانی ناموفق بود؛ جمع‌بندی محافظه‌کارانه جایگزین شد: {str(exc)[:140]}")
    else:
        synthesis = fallback_synthesis(query, papers, analyses)
        logs.append("کلید مدل/رضایت ارسال متن فعال نبود؛ تحلیل در حالت قواعد و استخراج شواهد انجام شد.")
    return {
        "query": query, "year_from": year_from, "year_to": year_to,
        "search_scope": "OpenAlex open_access.is_oa:true; type:article|review",
        "requested_max_papers": requested_limit, "returned_papers": len(papers), "papers": papers,
        "documents": docs, "analyses": analyses, "synthesis": synthesis, "logs": logs,
        "run_id": hashlib.sha256((query + str(time.time())).encode()).hexdigest()[:12],
        "llm_enabled": synthesis.get("analysis_mode") == "llm_cross_paper_synthesis"
    }