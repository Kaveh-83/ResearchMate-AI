"""Deterministic no-network tests for the ResearchMate research pipeline."""
from unittest.mock import patch
from research_engine import run_research, rule_based_analysis, normalize_work, select_analysis_text

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

if __name__=="__main__":
    test_fallback_pipeline(); test_llm_pipeline(); test_text_selection_and_normalization()
