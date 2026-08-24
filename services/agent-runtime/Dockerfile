FROM ghcr.io/astral-sh/uv:0.12.5-python3.13-trixie-slim AS build
ENV UV_COMPILE_BYTECODE=1 UV_LINK_MODE=copy
WORKDIR /app
COPY services/agent-runtime/pyproject.toml services/agent-runtime/uv.lock* ./
RUN uv sync --frozen --no-dev --no-install-project
COPY services/agent-runtime/src ./src
COPY services/agent-runtime/README.md ./README.md
RUN uv sync --frozen --no-dev --no-editable

FROM python:3.13-slim-trixie
RUN useradd --system --uid 10001 vextis
WORKDIR /app
COPY --from=build --chown=vextis:vextis /app/.venv /app/.venv
ENV PATH="/app/.venv/bin:$PATH" PYTHONUNBUFFERED=1
USER 10001
EXPOSE 8081
CMD ["uvicorn", "vextis_agents.app.main:app", "--host", "0.0.0.0", "--port", "8081"]
