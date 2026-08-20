from fastapi.testclient import TestClient

from vextis_agents.app.main import create_app


def test_health_reports_runtime_status() -> None:
    response = TestClient(create_app()).get("/health")

    assert response.status_code == 200
    assert response.json() == {
        "status": "UP",
        "service": "agent-runtime",
        "environment": "local",
    }
