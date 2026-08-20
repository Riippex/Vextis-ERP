FROM ghcr.io/astral-sh/uv:0.11.17-python3.13-bookworm-slim AS build
ENV UV_COMPILE_BYTECODE=1 UV_LINK_MODE=copy
WORKDIR /app
COPY pyproject.toml uv.lock* ./
RUN uv sync --frozen --no-dev --no-install-project
COPY src ./src
COPY README.md ./README.md
RUN uv sync --frozen --no-dev

FROM python:3.13-slim-bookworm
RUN useradd --system --uid 10001 vextis
WORKDIR /app
COPY --from=build --chown=vextis:vextis /app/.venv /app/.venv
ENV PATH="/app/.venv/bin:$PATH" PYTHONUNBUFFERED=1
USER 10001
EXPOSE 8081
CMD ["uvicorn", "vextis_agents.app.main:app", "--host", "0.0.0.0", "--port", "8081"]
