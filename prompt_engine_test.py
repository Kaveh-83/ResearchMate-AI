"""Tests for the deterministic prompt builder."""
from prompt_engine import PROMPT_FEATURES, REPORT_TYPES, build_research_prompt, estimate_prompt_tokens


def test_prompt_is_english_but_sets_requested_report_language():
    selected = ["خلاصه ساختاریافته", "رابطه ساختار، فرایند و خواص", "داوری علمی مستقل"]
    prompt = build_research_prompt(
        topic="Heat treatment of hypoeutectoid steel",
        sources="https://doi.org/10.1000/example",
        output_language="Persian",
        selected_features=selected,
    )
    assert "# RESEARCHMATE" in prompt
    assert "Required final report language: **Persian (Farsi)**" in prompt
    assert "Analyze structure–processing–property relationships" in prompt
    assert "skeptical journal referee" in prompt
    assert "PASS WITH LIMITATIONS" in prompt
    assert "10.1000/example" in prompt


def test_prompt_includes_selected_report_type_and_iterations():
    prompt = build_research_prompt(
        task="مقایسه چند مقاله",
        report_type="مرور نظام‌مند (Systematic Review)",
        output_language="English",
        selected_features=["استخراج تمام اعداد و واحدها"],
        max_review_rounds=99,
        include_coverage_matrix=True,
    )
    assert "Compare the supplied papers" in prompt
    assert "A systematic review" in prompt
    assert "Required final report language: **English**" in prompt
    assert "up to 5 review-and-revision rounds" in prompt
    assert "coverage matrix" in prompt.lower()
    assert "preserve the original value" in prompt


def test_prompt_rejects_unknown_features_and_handles_empty_sources():
    prompt = build_research_prompt(
        topic="Steel slag valorization",
        sources="",
        selected_features=["not a real feature"],
    )
    assert "No source URL was supplied" in prompt
    assert "Perform the core task" in prompt
    assert "do not bypass paywalls" in prompt.lower()
    assert estimate_prompt_tokens(prompt) > 100
    assert len(REPORT_TYPES) >= 10
    assert sum(len(v) for v in PROMPT_FEATURES.values()) >= 60


if __name__ == "__main__":
    test_prompt_is_english_but_sets_requested_report_language()
    test_prompt_includes_selected_report_type_and_iterations()
    test_prompt_rejects_unknown_features_and_handles_empty_sources()
    print("PASS prompt builder coverage, language, iteration, and safety tests")
