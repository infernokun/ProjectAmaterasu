FROM python:3.11-slim

WORKDIR /app

# Deps install from prebuilt wheels — no compiler toolchain needed.
COPY requirements.txt .

RUN pip install --no-cache-dir --upgrade pip && \
    pip install --no-cache-dir -r requirements.txt

COPY main.py .

RUN useradd --create-home --uid 1000 appuser
USER appuser

# Experimental Proxmox lab helper CLI (not a service). Runs safe/no-op with no
# args; pass --quick-delete (optionally --dry-run) to act. Proxmox credentials
# are supplied at runtime via TOKEN_ID / TOKEN_SECRET (e.g. an env file).
ENTRYPOINT ["python", "main.py"]
