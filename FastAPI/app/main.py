import asyncio
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.v1.router import api_router
from app.core.config import settings
from app.core.errors import install_error_handlers
from app.db import models  # noqa: F401  (enregistre les tables)
from app.db.session import Base, SessionLocal, engine
from app.services import devices as devices_svc
from app.services import simulator as sim
from app.services.seed import seed_dev_data

log = logging.getLogger("stella")


async def _offline_watchdog():
    while True:
        await asyncio.sleep(settings.DEVICE_HEARTBEAT_INTERVAL_SECONDS)
        try:
            def run():
                db = SessionLocal()
                try:
                    devices_svc.watchdog_offline_alerts(db)
                finally:
                    db.close()
            await asyncio.to_thread(run)
        except Exception:
            log.exception("watchdog hors ligne en échec")


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings.validate_for_runtime()
    if settings.is_dev and settings.DATABASE_URL.startswith("sqlite"):
        Base.metadata.create_all(engine)       # confort dev SQLite ; PostgreSQL => Alembic
    db = SessionLocal()
    try:
        seed_dev_data(db)
    finally:
        db.close()
    task = asyncio.create_task(_offline_watchdog())
    yield
    task.cancel()
    sim.shutdown_all()


app = FastAPI(title=settings.APP_NAME, version="0.1.0", lifespan=lifespan)
install_error_handlers(app)
app.add_middleware(CORSMiddleware, allow_origins=settings.cors_origins_list, allow_credentials=False,
                   allow_methods=["*"], allow_headers=["*"])
app.include_router(api_router, prefix=settings.API_V1_PREFIX)


@app.get("/health", tags=["health"])
def health():
    """Ne divulgue aucune donnée sensible."""
    return {"status": "ok"}
