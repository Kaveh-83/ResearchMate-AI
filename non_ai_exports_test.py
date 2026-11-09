"""Tests for deterministic ResearchMate exports."""
import io
import zipfile
from non_ai_exports import build_bibtex, build_non_ai_exports


BUNDLE = {
    "query": "steel heat treatment",
    "year_from": 2020,
    "year_to": 2026,
    "run_id": "test-run",
    "search_scope": "open_access_only",
    "papers": [{
        "id": "https://openalex.org/W1", "title": "Heat treatment of steel",
        "year": 2024, "authors": ["A Author"], "authors_text": "A Author",
        "journal": "Materials Journal", "doi": "10.1000/example", "citations": 5,
        "type": "article", "is_oa": True, "oa_status": "gold", "license": "cc-by",
        "oa_url": "https://example.org/oa", "source_work": {"secret": "excluded"}
    }],
    "documents": [{
        "status": "full_text", "format": "pdf", "source_url": "https://example.org/paper.pdf",
        "text": "Tensile strength was 500 MPa. Further research is needed.",
        "tables": [[["Property", "Value"], ["Strength", "500 MPa"]]],
        "reason": "test"
    }],
    "analyses": [{
        "methodology": [{"category": "آزمایشگاهی", "signals": ["tensile test"]}],
        "materials": ["steel"], "conditions_metrics": ["500 MPa"],
        "findings": ["Tensile strength was 500 MPa."],
        "limitations": ["Further research is needed."],
        "evidence_quotes": [{"type": "نتایج", "quote": "Tensile strength was 500 MPa.", "page": 1}]
    }]
}


def test_bibtex_and_selected_exports():
    bib = build_bibtex(BUNDLE["papers"])
    assert "@article{" in bib and "10.1000/example" in bib
    exports = build_non_ai_exports(BUNDLE, {"metadata", "signals", "evidence", "tables", "bibtex", "json"})
    assert "literature_metadata.csv" in exports
    assert "rule_based_candidates.csv" in exports
    assert "evidence_excerpts.csv" in exports
    assert "extracted_tables.csv" in exports
    assert "references.bib" in exports
    assert "researchmate_data.json" in exports
    assert "full_text/" not in "\n".join(exports)
    assert "researchmate_evidence_workbook.xlsx" in exports
    assert "researchmate_deterministic_package.zip" in exports
    with zipfile.ZipFile(io.BytesIO(exports["researchmate_deterministic_package.zip"])) as zf:
        assert "references.bib" in zf.namelist()
        assert "run_summary.txt" in zf.namelist()


def test_text_export_and_ai_boundary():
    exports = build_non_ai_exports(BUNDLE, {"text", "json"})
    text_files = [name for name in exports if name.startswith("full_text/")]
    assert len(text_files) == 1
    assert b"500 MPa" in exports[text_files[0]]
    assert b"no AI interpretation" in exports["researchmate_data.json"]


if __name__ == "__main__":
    test_bibtex_and_selected_exports()
    test_text_export_and_ai_boundary()
    print("PASS deterministic exports, ZIP/workbook generation, and no-LLM boundary tests")
