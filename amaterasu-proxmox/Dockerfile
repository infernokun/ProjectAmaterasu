FROM python:3.14-slim

WORKDIR /app

COPY requirements.txt .

# Hash-locked requirements (compiled from requirements.in with `npm run deps:lock` at the repo
# root). uv is mounted for this step only, then pip and wheel are removed since nothing uses them
# at runtime and they are what image scans flag.
RUN --mount=from=ghcr.io/astral-sh/uv:0.13.7,source=/uv,target=/usr/local/bin/uv \
    uv pip install --system --no-cache --require-hashes -r requirements.txt && \
    python -m pip uninstall -y pip wheel && \
    rm -rf /usr/local/lib/python3.14/ensurepip/_bundled

COPY main.py .

RUN useradd --create-home --uid 1000 appuser
USER appuser

ENV PYTHONUNBUFFERED=1 PYTHONDONTWRITEBYTECODE=1

ARG GIT_SHA=unknown
LABEL org.opencontainers.image.revision=$GIT_SHA \
      org.opencontainers.image.source="https://github.com/infernokun/ProjectAmaterasu"

# Proxmox lab helper CLI. Needs TOKEN_ID and TOKEN_SECRET at runtime.
ENTRYPOINT ["python", "main.py"]
