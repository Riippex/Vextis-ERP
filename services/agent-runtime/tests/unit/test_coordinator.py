import pytest

from vextis_agents.app.config import Settings
from vextis_agents.coordinator.agent import build_coordinator


def test_coordinator_requires_explicit_model_configuration() -> None:
    with pytest.raises(ValueError, match="VEXTIS_GEMINI_MODEL"):
        build_coordinator(Settings(gemini_model=None))


def test_coordinator_uses_configured_gemini_model() -> None:
    coordinator = build_coordinator(Settings(gemini_model="gemini-test-model"))

    assert coordinator.name == "vextis_coordinator"
    assert coordinator.model == "gemini-test-model"
