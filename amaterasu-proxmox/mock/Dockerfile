FROM python:3.14-slim

WORKDIR /app

COPY requirements.txt .

# Hash-locked, as in ../Dockerfile
RUN --mount=from=ghcr.io/astral-sh/uv:0.13.7,source=/uv,target=/usr/local/bin/uv \
    uv pip install --system --no-cache --require-hashes -r requirements.txt && \
    python -m pip uninstall -y pip wheel && \
    rm -rf /usr/local/lib/python3.14/ensurepip/_bundled

COPY *.py ./
COPY data/ data/
COPY ssl/cert.pem ssl/key.pem ssl/

# /data keeps the SSH host key across deploys; created here so a new named volume is writable
RUN useradd --create-home --uid 1000 appuser && mkdir /data && chown appuser:appuser /data
USER appuser
VOLUME ["/data"]

ENV PYTHONUNBUFFERED=1 PYTHONDONTWRITEBYTECODE=1 \
    MOCK_HOST=0.0.0.0 MOCK_SSH_BIND=0.0.0.0 MOCK_SSH_HOST_KEY=/data/ssh_host_ed25519_key

EXPOSE 8006 2022

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD python -c "import ssl, urllib.request as u; u.urlopen('https://127.0.0.1:8006/', context=ssl._create_unverified_context(), timeout=4)"

ARG GIT_SHA=unknown
LABEL org.opencontainers.image.revision=$GIT_SHA \
      org.opencontainers.image.source="https://github.com/infernokun/ProjectAmaterasu" \
      description="ProjectAmaterasu Proxmox API mock"

CMD ["python", "server.py"]
