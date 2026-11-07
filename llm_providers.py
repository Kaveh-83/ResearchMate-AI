"""Provider-agnostic LLM gateway for ResearchMate AI.

Only official provider APIs are used. Keys are read from environment variables;
they must never be committed to the repository or written to logs.
"""
from __future__ import annotations

import os
import time
from dataclasses import dataclass
from typing import Any

import requests


@dataclass(frozen=True)
class ProviderConfig:
    name: str
    env_key: str
    endpoint: str
    default_model: str
    protocol: str = "openai"


PROVIDERS = {
    "gemini": ProviderConfig("gemini", "GEMINI_API_KEY",
        "https://generativelanguage.googleapis.com/v1beta/models",
        "gemini-2.5-flash", "gemini"),
    "groq": ProviderConfig("groq", "GROQ_API_KEY",
        "https://api.groq.com/openai/v1/chat/completions",
        "openai/gpt-oss-20b"),
    "openrouter": ProviderConfig("openrouter", "OPENROUTER_API_KEY",
        "https://openrouter.ai/api/v1/chat/completions",
        "openrouter/free"),
    "mistral": ProviderConfig("mistral", "MISTRAL_API_KEY",
        "https://api.mistral.ai/v1/chat/completions",
        "mistral-small-latest"),
}


class ProviderError(RuntimeError):
    """Safe provider error; never contains an API key."""


def configured_providers(env: dict[str, str] | None = None) -> list[str]:
    env = env or os.environ
    return [name for name, cfg in PROVIDERS.items() if env.get(cfg.env_key, "").strip()]


def _request(provider: str, prompt: str, api_key: str, model: str | None = None,
             timeout: int = 120, session: Any = requests) -> str:
    if provider not in PROVIDERS:
        raise ProviderError(f"ارائه‌دهنده ناشناخته است: {provider}")
    if not api_key or not api_key.strip():
        raise ProviderError(f"کلید API برای {provider} تنظیم نشده است.")
    cfg = PROVIDERS[provider]
    selected_model = (model or cfg.default_model).strip()

    if cfg.protocol == "gemini":
        url = f"{cfg.endpoint}/{selected_model}:generateContent"
        headers = {"x-goog-api-key": api_key}
        payload = {
            "contents": [{"role": "user", "parts": [{"text": prompt}]}],
            "generationConfig": {"responseMimeType": "application/json", "maxOutputTokens": 8192},
        }
    else:
        url = cfg.endpoint
        headers = {"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"}
        payload = {
            "model": selected_model,
            "messages": [{"role": "user", "content": prompt}],
            "temperature": 0.1,
            "response_format": {"type": "json_object"},
        }

    last_error = None
    for attempt in range(3):
        try:
            response = session.post(url, headers=headers, json=payload, timeout=timeout)
            if response.status_code in (429, 500, 502, 503, 504):
                if attempt < 2:
                    retry_after = response.headers.get("retry-after")
                    try:
                        delay = min(max(float(retry_after), 1), 20) if retry_after else 2 ** (attempt + 1)
                    except ValueError:
                        delay = 2 ** (attempt + 1)
                    time.sleep(delay)
                    continue
            response.raise_for_status()
            data = response.json()
            if cfg.protocol == "gemini":
                return data["candidates"][0]["content"]["parts"][0]["text"]
            return data["choices"][0]["message"]["content"]
        except requests.HTTPError as exc:
            status = getattr(getattr(exc, "response", None), "status_code", None)
            raise ProviderError(f"فراخوانی {provider} ناموفق بود (HTTP {status}). سهمیه، مدل و کلید را بررسی کن.") from None
        except (requests.RequestException, KeyError, IndexError, TypeError, ValueError) as exc:
            last_error = exc
            if attempt == 2:
                break
            time.sleep(2 ** (attempt + 1))
    raise ProviderError(f"پاسخ معتبر از {provider} دریافت نشد: {type(last_error).__name__ if last_error else 'unknown error'}")


def generate(prompt: str, provider: str, model: str | None = None,
             api_key: str | None = None, timeout: int = 120) -> str:
    cfg = PROVIDERS.get(provider)
    if cfg is None:
        raise ProviderError(f"ارائه‌دهنده ناشناخته است: {provider}")
    key = api_key if api_key is not None else os.environ.get(cfg.env_key, "")
    return _request(provider, prompt, key, model=model, timeout=timeout)


def generate_with_fallback(prompt: str, providers: list[dict[str, str]],
                           timeout: int = 120) -> tuple[str, str]:
    """Try explicitly configured providers in order; return (text, provider_name)."""
    errors = []
    for item in providers:
        name = item.get("provider", "")
        try:
            result = generate(prompt, name, model=item.get("model"),
                              api_key=item.get("api_key"), timeout=timeout)
            return result, name
        except ProviderError as exc:
            errors.append(f"{name}: {exc}")
    raise ProviderError("هیچ ارائه‌دهنده‌ای پاسخ نداد. " + " | ".join(errors))
