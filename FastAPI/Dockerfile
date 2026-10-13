# Image du backend Stella — FastAPI / Python
# Construire depuis la racine du dépôt :
#   docker build -t stella-api:local ./FastAPI

FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    APP_ENV=production \
    SIMULATOR_ENABLED=false \
    SEED_DEV_DATA=false

WORKDIR /app

# Le processus applicatif ne tourne pas avec root.
RUN groupadd --system stella \
    && useradd --system --gid stella --home-dir /app --shell /usr/sbin/nologin stella

# Installer les dépendances séparément pour mieux exploiter le cache Docker.
COPY requirements.txt ./requirements.txt
RUN python -m pip install --no-cache-dir --upgrade pip \
    && python -m pip install --no-cache-dir -r requirements.txt

# Le contexte de build est le dossier FastAPI ; on n'embarque pas les tests ni les fichiers locaux.
COPY --chown=stella:stella app ./app

USER stella

EXPOSE 8000

# Vérifie que le processus HTTP répond. Ce contrôle ne valide pas la disponibilité de PostgreSQL.
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=2)" || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--proxy-headers"]
