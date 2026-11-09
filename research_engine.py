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
    return re.sub(r"\\s+", " ", str(value or "")).strip()


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
