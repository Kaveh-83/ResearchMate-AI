import json
from unittest.mock import patch

from llm_providers import PROVIDERS, ProviderError, configured_providers, generate_with_fallback
from agent_orchestrator import build_agent_prompt, AGENT_ROLES


def test_provider_registry_and_env():
    assert {"gemini", "groq", "openrouter", "mistral"} <= set(PROVIDERS)
    assert configured_providers({"GROQ_API_KEY": "x"}) == ["groq"]


def test_fallback_moves_to_next_provider():
    calls = []
    def fake_generate(prompt, provider, model=None, api_key=None, timeout=120):
        calls.append(provider)
        if provider == "groq":
            raise ProviderError("rate limited")
        return '{"ok": true}'
    with patch("llm_providers.generate", side_effect=fake_generate):
        result, selected = generate_with_fallback(
            "{}", [{"provider": "groq"}, {"provider": "openrouter"}]
        )
    assert json.loads(result)["ok"] is True
    assert selected == "openrouter"
    assert calls == ["groq", "openrouter"]


def test_agent_prompt_is_source_bound():
    paper = {"id": "W1", "title": "Steel", "doi": "10.1/x", "year": 2025}
    prompt = build_agent_prompt("results_extractor", paper, "yield strength = 500 MPa")
    assert "500 MPa" in prompt
    assert "W1" in prompt
    assert "عدد، روش یا ارجاع نساز" in prompt
    assert set(AGENT_ROLES) == {
        "methods_extractor", "results_extractor", "critical_reviewer", "chief_reviewer"
    }


if __name__ == "__main__":
    test_provider_registry_and_env()
    test_fallback_moves_to_next_provider()
    test_agent_prompt_is_source_bound()
    print("PASS multi-agent provider foundation tests")
