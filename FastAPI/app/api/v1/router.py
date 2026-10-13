from fastapi import APIRouter

from app.api.v1.routes import alerts, auth, commands, devices, events, simulator, users

api_router = APIRouter()
api_router.include_router(auth.router)
api_router.include_router(devices.router)
api_router.include_router(commands.router)
api_router.include_router(events.router)
api_router.include_router(alerts.router)
api_router.include_router(users.router)
if simulator.simulator_enabled():
    api_router.include_router(simulator.router)
