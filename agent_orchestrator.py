"""Small, auditable multi-agent task planner and reviewer."""
from __future__ import annotations

import json
from typing import Any

from llm_providers import ProviderError, generate_with_fallback


AGENT_ROLES = {
    "methods_extractor": "روش پژوهش، مواد، تجهیزات، پارامترهای فرایند و طراحی آزمایش را استخراج کن.",
    "results_extractor": "نتایج کمی، واحدها، شرایط آزمون، معیارها و شواهد نقل‌قولی را استخراج کن.",
    "critical_reviewer": "محدودیت‌ها، خطر سوگیری، ضعف اعتبارسنجی، قابلیت بازتولید و کاربرد صنعتی را نقد کن.",
    "chief_reviewer": "خروجی عامل‌ها را با شواهد مقاله تطبیق بده؛ توافق، تعارض و موارد بی‌پشتوانه را جدا کن.",
}


def build_agent_prompt(role: str, paper: dict[str, Any], source_text: str,
                       prior_outputs: dict[str, Any] | None = None) -> str:
    if role not in AGENT_ROLES:
        raise ValueError(f"Unknown agent role: {role}")
    return f"""نقش: {AGENT_ROLES[role]}
قواعد سخت‌گیرانه:
- فقط از متن منبع و داده‌های داده‌شده استفاده کن؛ عدد، روش یا ارجاع نساز.
- هر داده ناموجود را «گزارش نشده» بنویس.
- سطح منبع را مشخص کن و نقل‌قول‌ها را عین متن بیاور.
- خروجی فقط JSON معتبر باشد.
شناسه مقاله: {paper.get('id')}
عنوان: {paper.get('title')}
DOI: {paper.get('doi')}
سال: {paper.get('year')}
متن منبع:
{source_text[:30000]}
خروجی‌های سایر عامل‌ها (صرفاً برای نقد، نه به‌عنوان منبع حقیقت):
{json.dumps(prior_outputs or {}, ensure_ascii=False)[:16000]}
"""


def run_agent(role: str, paper: dict[str, Any], source_text: str,
              provider_chain: list[dict[str, str]],
              prior_outputs: dict[str, Any] | None = None) -> dict[str, Any]:
    prompt = build_agent_prompt(role, paper, source_text, prior_outputs)
    text, provider = generate_with_fallback(prompt, provider_chain)
    from research_engine import _parse_json
    result = _parse_json(text)
    return {
        "role": role,
        "provider": provider,
        "paper_id": paper.get("id"),
        "result": result,
        "source_level": paper.get("evidence_level", "unknown"),
    }


def run_paper_agents(paper: dict[str, Any], source_text: str,
                     provider_chains: dict[str, list[dict[str, str]]]) -> dict[str, Any]:
    """Run independent specialist tasks; final reviewer receives their structured outputs."""
    outputs: dict[str, Any] = {}
    errors: dict[str, str] = {}
    for role in ("methods_extractor", "results_extractor", "critical_reviewer"):
        chain = provider_chains.get(role, [])
        if not chain:
            errors[role] = "برای این نقش هیچ ارائه‌دهنده‌ای پیکربندی نشده است."
            continue
        try:
            outputs[role] = run_agent(role, paper, source_text, chain)
        except Exception as exc:
            errors[role] = str(exc)[:400]

    chief_chain = provider_chains.get("chief_reviewer", [])
    if chief_chain and outputs:
        try:
            outputs["chief_reviewer"] = run_agent(
                "chief_reviewer", paper, source_text, chief_chain, outputs
            )
        except Exception as exc:
            errors["chief_reviewer"] = str(exc)[:400]

    return {
        "paper_id": paper.get("id"),
        "agent_outputs": outputs,
        "agent_errors": errors,
        "completed_roles": list(outputs),
        "is_complete": len(outputs) == 4,
        "warning": "خروجی عامل‌ها فرضیه/استخراج ماشینی است؛ شواهد باید با متن اصلی راستی‌آزمایی شوند.",
    }
