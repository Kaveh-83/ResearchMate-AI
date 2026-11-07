"""Deterministic no-network tests for the ResearchMate research pipeline."""
from unittest.mock import patch
from research_engine import run_research, rule_based_analysis, normalize_work, select_analysis_text, search_openalex, analyze_one_with_llm, _unpaywall_oa_urls

PAPERS = [
    {"id":"https://openalex.org/W101","title":"Random forest prediction of BOF endpoint temperature","year":2024,"doi":"10.5555/a","authors":["A Author"],"authors_text":"A Author","journal":"Steel Journal","citations":8,"oa_status":"gold","oa_url":"https://oa.example/a.pdf","abstract":"This study used random forest to predict BOF endpoint temperature. Results showed RMSE 8.5 °C using 1200 heats. Limitations include plant-specific data.","is_oa":True},
    {"id":"https://openalex.org/W102","title":"Energy optimization in electric arc furnace","year":2023,"doi":"10.5555/b","authors":["B Author"],"authors_text":"B Author","journal":"Metallurgy Letters","citations":12,"oa_status":"unknown","oa_url":"","abstract":"A statistical analysis showed energy consumption reduced by 7.2% after process optimization. Further research is needed for different scrap mixes.","is_oa":False},
]
DOCS = [
    {"status":"full_text","source_url":"https://oa.example/a.pdf","format":"pdf","text":"Introduction. Random forest prediction of BOF endpoint temperature. Materials and Methods. 1200 heats were included. Results. RMSE was 8.5 °C. Discussion. The model was plant-specific.","pages":[{"page":1,"text":"Introduction. Random forest prediction of BOF endpoint temperature. Materials and Methods. 1200 heats were included. Results. RMSE was 8.5 °C."}],"tables":[[["Metric","Value"],["RMSE","8.5 °C"]]],"reason":"test OA full text"},
    {"status":"abstract_only","source_url":"","format":"abstract","text":PAPERS[1]["abstract"],"pages":[],"tables":[],"reason":"no accessible full text"},
]

def test_fallback_pipeline():
    with patch("research_engine.search_openalex", return_value=PAPERS), patch("research_engine.obtain_fulltext", side_effect=DOCS):
        result=run_research("machine learning steelmaking",2020,2026,2)
    assert len(result["papers"])==2
    assert result["documents"][0]["status"]=="full_text"
    assert result["documents"][1]["status"]=="abstract_only"
    assert result["analyses"][0]["evidence_level"]=="full_text"
    assert result["synthesis"]["analysis_mode"]=="fallback_rules_only"
    assert not result["llm_enabled"]
    assert "متن کامل" in result["synthesis"]["abstract"]
    print("PASS fallback pipeline, full-text/abstract-only levels, conservative synthesis")

def test_llm_pipeline():
    fake_one={"paper_id":PAPERS[0]["id"],"evidence_level":"full_text","problem":"problem","objective":"objective","methods":["random forest"],"quantitative_findings":["RMSE 8.5 °C"],"evidence_quotes":[{"claim":"RMSE","quote":"RMSE was 8.5 °C","page":1}],"limitations":[],"future_work":[]}
    fake_synthesis={"abstract":"Review","keywords":["steel"],"background":"background","thematic_synthesis":"synthesis","methods_comparison":["methods"],"results_comparison":["results"],"convergent_evidence":[],"conflicting_evidence":[],"explanations_for_conflicts":[],"research_gaps":["gap"],"future_directions":["future"],"conclusion":"conclusion","limitations_of_review":"limitations","evidence_strength":"moderate","study_quality":"quality","reproducibility_gaps":"gaps","industrial_applicability":"industrial","meta_analysis_feasibility":"not possible","analysis_mode":"llm_cross_paper_synthesis","claims":[{"claim":"supported claim","paper_ids":[PAPERS[0]["id"],"FAKE"],"support":"moderate","reason":"reason"}]}
    with patch("research_engine.search_openalex", return_value=PAPERS), patch("research_engine.obtain_fulltext", side_effect=DOCS), patch("research_engine.analyze_one_with_llm", return_value=fake_one), patch("research_engine.synthesize_with_llm", return_value=fake_synthesis):
        result=run_research("machine learning steelmaking",2020,2026,2,api_key="fake-key",consent_to_send=True)
    assert result["llm_enabled"]
    assert result["synthesis"]["analysis_mode"]=="llm_cross_paper_synthesis"
    print("PASS consented LLM pipeline branch with stubbed model responses")

def test_text_selection_and_normalization():
    source={"pages":[{"page":1,"text":"Intro."},{"page":2,"text":"Materials and Methods. Results. Discussion."}],"tables":[[["A","B"],["1","2"]]]}
    selected=select_analysis_text(source,"abstract")
    assert "[PAGE 1]" in selected and "[PAGE 2]" in selected and "TABLE DATA" in selected
    norm=normalize_work({"id":"https://openalex.org/WX","title":"A paper","abstract_inverted_index":{"steel":[1],"making":[0]},"open_access":{"is_oa":True}})
    assert norm["abstract"]=="making steel" and norm["is_oa"]
    print("PASS page-aware text selection, table context, OpenAlex abstract reconstruction")

def test_openalex_search_is_open_access_only():
    oa_work = {
        "id": "https://openalex.org/W201", "title": "Open access steel paper",
        "publication_year": 2024, "open_access": {"is_oa": True, "oa_status": "gold", "oa_url": "https://repo.example/oa"},
        "best_oa_location": {"is_oa": True, "pdf_url": "https://repo.example/oa.pdf", "landing_page_url": "https://repo.example/oa"},
        "locations": [
            {"is_oa": True, "pdf_url": "https://repo.example/oa.pdf"},
            {"is_oa": False, "pdf_url": "https://publisher.example/paywalled.pdf"}
        ],
        "primary_location": {"is_oa": False, "pdf_url": "https://publisher.example/paywalled.pdf"}
    }
    closed_work = {
        "id": "https://openalex.org/W202", "title": "Closed access steel paper",
        "publication_year": 2024, "open_access": {"is_oa": False, "oa_status": "closed"},
        "best_oa_location": None, "locations": [],
    }
    with patch("research_engine._get_json", return_value={"results": [oa_work, closed_work]}) as api:
        found = search_openalex("steel", 2020, 2026, 10)
    used_filter = api.call_args.kwargs["params"]["filter"]
    assert "open_access.is_oa:true" in used_filter
    assert "type:article|review" in used_filter
    assert len(found) == 1 and found[0]["id"].endswith("W201")
    assert found[0]["pdf_urls"] == ["https://repo.example/oa.pdf"]
    print("PASS OpenAlex OA-only filter and closed-location URL exclusion")


def test_model_quotes_are_verified_against_source():
    paper = {"id": "W301", "title": "Steel strength study", "year": 2024, "doi": "10.5555/test"}
    doc = {
        "status": "full_text", "text": "The tensile strength was 420 MPa.",
        "pages": [{"page": 2, "text": "The tensile strength was 420 MPa."}],
        "tables": []
    }
    model_result = {
        "evidence_quotes": [
            {"claim": "strength", "quote": "The tensile strength was 420 MPa.", "page": 2},
            {"claim": "fabricated", "quote": "The elongation was 90 percent.", "page": 9}
        ],
        "materials": [], "alloy_composition": [], "methods": [], "equipment_instrumentation": [],
        "process_conditions": [], "input_variables": [], "output_variables": [], "measured_properties": [],
        "metrics": [], "baseline_comparators": [], "validation_strategy": [], "uncertainty_statistics": [],
        "quantitative_findings": [], "findings": [], "risk_of_bias": [], "reproducibility": [],
        "industrial_applicability": [], "limitations": [], "future_work": []
    }
    with patch("research_engine.gemini_generate", return_value=__import__("json").dumps(model_result)):
        result = analyze_one_with_llm(paper, doc, "fake-key", "gemini-3.8-flash")
    assert len(result["evidence_quotes"]) == 1
    assert result["evidence_quotes"][0]["quote_verified"] is True
    assert result["evidence_quotes"][0]["page_verified"] is True
    assert result["unverified_quotes_rejected"] == 1
    print("PASS model quotations and page numbers are checked against source text")


def test_unpaywall_resolver_requires_oa_confirmation():
    class FakeResponse:
        def __init__(self, payload):
            self.payload = payload
        def raise_for_status(self):
            return None
        def json(self):
            return self.payload

    oa_payload = {
        "is_oa": True,
        "best_oa_location": {
            "url_for_pdf": "https://repository.example/paper.pdf",
            "url": "https://repository.example/paper"
        },
        "oa_locations": [{"url_for_landing_page": "https://publisher.example/open"}]
    }
    closed_payload = {"is_oa": False, "best_oa_location": None, "oa_locations": []}
    with patch("research_engine.requests.get", side_effect=[FakeResponse(oa_payload), FakeResponse(closed_payload)]) as client:
        urls = _unpaywall_oa_urls("10.1234/example", "researcher@example.org")
        closed_urls = _unpaywall_oa_urls("10.1234/closed", "researcher@example.org")
    assert urls == [
        "https://repository.example/paper.pdf",
        "https://repository.example/paper",
        "https://publisher.example/open"
    ]
    assert closed_urls == []
    assert client.call_args_list[0].kwargs["params"]["email"] == "researcher@example.org"
    print("PASS Unpaywall URLs are used only after explicit OA confirmation")


if __name__=="__main__":
    test_fallback_pipeline(); test_llm_pipeline(); test_text_selection_and_normalization()
    test_openalex_search_is_open_access_only(); test_model_quotes_are_verified_against_source(); test_unpaywall_resolver_requires_oa_confirmation()
