"""Deterministic exports for the no-LLM ResearchMate workflow."""
from __future__ import annotations

import io
import json
import re
import zipfile
from datetime import datetime, timezone
from typing import Any

import pandas as pd


def _safe_name(value: str, fallback: str = "paper") -> str:
    value = re.sub(r"[^A-Za-z0-9._-]+", "_", str(value or "")).strip("._-")
    return (value[:70] or fallback)


def _bib_escape(value: Any) -> str:
    text = str(value or "")
    return text.replace("\\", r"\textbackslash{}").replace("{", r"\{").replace("}", r"\}").replace("%", r"\%")


def build_bibtex(papers: list[dict[str, Any]]) -> str:
    entries = []
    for index, paper in enumerate(papers, 1):
        doi = str(paper.get("doi") or "").strip()
        if not doi:
            continue
        authors = " and ".join(paper.get("authors") or []) or "Unknown"
        title = paper.get("title") or "Untitled"
        year = paper.get("year") or ""
        journal = paper.get("journal") or ""
        first = (paper.get("authors") or ["ref"])[0].split()[-1]
        key = _safe_name(f"{first}{year}{index}", f"ref{index}")
        entries.append(
            f"@article{{{key},\n"
            f"  author = {{{_bib_escape(authors)}}},\n"
            f"  title = {{{_bib_escape(title)}}},\n"
            f"  journal = {{{_bib_escape(journal)}}},\n"
            f"  year = {{{_bib_escape(year)}}},\n"
            f"  doi = {{{_bib_escape(doi)}}}\n"
            f"}}"
        )
    return "\n\n".join(entries) + ("\n" if entries else "")


def _rows(bundle: dict[str, Any]) -> tuple[list[dict], list[dict], list[dict], list[dict]]:
    papers = bundle.get("papers") or []
    docs = bundle.get("documents") or []
    analyses = bundle.get("analyses") or []
    metadata, signals, evidence, tables = [], [], [], []
    for i, paper in enumerate(papers):
        doc = docs[i] if i < len(docs) else {}
        analysis = analyses[i] if i < len(analyses) else {}
        metadata.append({
            "paper_index": i + 1, "paper_id": paper.get("id"), "title": paper.get("title"),
            "year": paper.get("year"), "authors": paper.get("authors_text") or "; ".join(paper.get("authors") or []),
            "journal": paper.get("journal"), "doi": paper.get("doi"), "citations": paper.get("citations"),
            "document_type": paper.get("type"), "is_open_access": paper.get("is_oa"),
            "oa_status": paper.get("oa_status"), "license": paper.get("license"),
            "oa_landing_page": paper.get("oa_url"), "retrieved_text_status": doc.get("status"),
            "retrieved_format": doc.get("format"), "retrieved_source": doc.get("source_url"),
            "extracted_characters": len(doc.get("text") or ""), "extracted_table_count": len(doc.get("tables") or []),
            "retrieval_note": doc.get("reason")
        })
        for method in analysis.get("methodology") or []:
            if isinstance(method, dict):
                signals.append({"paper_index": i + 1, "paper_title": paper.get("title"),
                                "signal_type": "method_category", "candidate": method.get("category"),
                                "matched_terms": "; ".join(method.get("signals") or [])})
        for material in analysis.get("materials") or []:
            signals.append({"paper_index": i + 1, "paper_title": paper.get("title"),
                            "signal_type": "material_keyword", "candidate": material, "matched_terms": ""})
        for number in analysis.get("conditions_metrics") or []:
            signals.append({"paper_index": i + 1, "paper_title": paper.get("title"),
                            "signal_type": "numeric_pattern", "candidate": number, "matched_terms": ""})
        for kind, values in (("finding_like_paragraph", analysis.get("findings") or []),
                             ("limitation_like_paragraph", analysis.get("limitations") or [])):
            for value in values:
                signals.append({"paper_index": i + 1, "paper_title": paper.get("title"),
                                "signal_type": kind, "candidate": value, "matched_terms": ""})
        for item in analysis.get("evidence_quotes") or []:
            evidence.append({"paper_index": i + 1, "paper_title": paper.get("title"),
                             "evidence_type": item.get("type"), "quote": item.get("quote"),
                             "page": item.get("page"), "verification": "rule-extracted candidate; human verification required"})
        for table_index, table in enumerate(doc.get("tables") or [], 1):
            tables.append({"paper_index": i + 1, "paper_title": paper.get("title"),
                           "table_index": table_index, "table_json": json.dumps(table, ensure_ascii=False)})
    return metadata, signals, evidence, tables


def build_non_ai_exports(bundle: dict[str, Any], include: set[str] | None = None) -> dict[str, bytes]:
    """Create transparent data exports; no semantic synthesis or LLM is performed."""
    include = include or {"metadata", "signals", "evidence", "tables", "text", "bibtex", "json"}
    metadata, signals, evidence, tables = _rows(bundle)
    files: dict[str, bytes] = {}
    members: dict[str, bytes] = {}

    def add(name: str, data: bytes) -> None:
        files[name] = data
        members[name] = data

    if "metadata" in include:
        add("literature_metadata.csv", pd.DataFrame(metadata).to_csv(index=False).encode("utf-8-sig"))
    if "signals" in include:
        add("rule_based_candidates.csv", pd.DataFrame(signals).to_csv(index=False).encode("utf-8-sig"))
    if "evidence" in include:
        add("evidence_excerpts.csv", pd.DataFrame(evidence).to_csv(index=False).encode("utf-8-sig"))
    if "tables" in include:
        add("extracted_tables.csv", pd.DataFrame(tables).to_csv(index=False).encode("utf-8-sig"))
    if "bibtex" in include:
        add("references.bib", build_bibtex(bundle.get("papers") or []).encode("utf-8"))
    if "text" in include:
        for i, (paper, doc) in enumerate(zip(bundle.get("papers") or [], bundle.get("documents") or []), 1):
            title = _safe_name(paper.get("title") or "", f"paper_{i}")
            text = doc.get("text") or ""
            note = (f"Title: {paper.get('title','')}\nDOI: {paper.get('doi','')}\n"
                    f"Status: {doc.get('status','unknown')}\nSource: {doc.get('source_url','')}\n"
                    f"Retrieval note: {doc.get('reason','')}\n\n")
            add(f"full_text/{i:02d}_{title}.txt", (note + text).encode("utf-8"))
    if "json" in include:
        safe_bundle = {
            "query": bundle.get("query"), "year_from": bundle.get("year_from"), "year_to": bundle.get("year_to"),
            "run_id": bundle.get("run_id"), "search_scope": bundle.get("search_scope"),
            "papers": [{k: v for k, v in p.items() if k != "source_work"} for p in bundle.get("papers") or []],
            "documents": bundle.get("documents") or [], "analyses": bundle.get("analyses") or [],
            "note": "Deterministic metadata and rule-based extraction only; no AI interpretation was performed."
        }
        add("researchmate_data.json", json.dumps(safe_bundle, ensure_ascii=False, indent=2, default=str).encode("utf-8"))

    summary = [
        "# ResearchMate deterministic research run",
        f"- Query: {bundle.get('query','')}",
        f"- Publication years: {bundle.get('year_from')}–{bundle.get('year_to')}",
        f"- Records: {len(bundle.get('papers') or [])}",
        f"- Full-text extracted: {sum(1 for d in bundle.get('documents') or [] if d.get('status') == 'full_text')}",
        f"- Abstract only: {sum(1 for d in bundle.get('documents') or [] if d.get('status') == 'abstract_only')}",
        f"- Metadata only: {sum(1 for d in bundle.get('documents') or [] if d.get('status') == 'metadata_only')}",
        "",
        "Scope: exploratory OpenAlex search for records explicitly marked Open Access and article/review.",
        "The rule-based output is a set of candidate signals, not a semantic analysis or peer review.",
        "PDF text/table extraction may be incomplete; verify all evidence against the original publication.",
    ]
    add("run_summary.txt", ("\n".join(summary) + "\n").encode("utf-8"))

    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, data in members.items():
            archive.writestr(name, data)
    files["researchmate_deterministic_package.zip"] = output.getvalue()

    workbook = io.BytesIO()
    with pd.ExcelWriter(workbook, engine="openpyxl") as writer:
        pd.DataFrame(metadata).to_excel(writer, sheet_name="Metadata", index=False)
        pd.DataFrame(signals).to_excel(writer, sheet_name="Rule_Candidates", index=False)
        pd.DataFrame(evidence).to_excel(writer, sheet_name="Evidence_Excerpts", index=False)
        pd.DataFrame(tables).to_excel(writer, sheet_name="Extracted_Tables", index=False)
    files["researchmate_evidence_workbook.xlsx"] = workbook.getvalue()
    return files
