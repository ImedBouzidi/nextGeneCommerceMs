FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app

# system deps (if needed) and cleanup to keep image small
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential curl \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements and optional wheels first for better caching
COPY requirements.txt requirements.txt
COPY requirements_local.txt requirements_local.txt
COPY wheels /wheels

# Install Python deps: prefer local requirements file; use wheels if provided, otherwise fallback to PyPI
RUN pip install --upgrade pip setuptools wheel && \
    if [ -s requirements_local.txt ]; then REQ=requirements_local.txt; else REQ=requirements.txt; fi && \
    # Try installing from local wheels first; if that fails, fall back to PyPI
    (pip install --no-index --find-links=/wheels -r "$REQ" || pip install -r "$REQ")

# Copy app code
COPY . /app

EXPOSE 5000

ENV PORT=5000

HEALTHCHECK --interval=30s --timeout=5s --retries=3 CMD curl -f http://localhost:${PORT}/health || exit 1

CMD ["python", "app.py"]
