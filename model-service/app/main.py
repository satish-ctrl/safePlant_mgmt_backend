from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.routers import health, analyze, sensor, permits, rag

app = FastAPI(
    title="SafeOps AI Model Service",
    description="Internal FastAPI microservice wrapping the SafeOps AI multi-agent pipeline. "
                "This service is NOT exposed directly to clients — it is called internally by "
                "the Spring Boot backend via WebClient.",
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc",
)

# CORS — only needed for development; in production, this service is Docker-internal
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Register routers
app.include_router(health.router)
app.include_router(analyze.router)
app.include_router(sensor.router)
app.include_router(permits.router)
app.include_router(rag.router)


@app.on_event("startup")
async def startup_event():
    """Validate configuration and pre-load models on startup."""
    from app.core.config import Config
    Config.validate()
    print("SafeOps AI Model Service started successfully.")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app.main:app", host="0.0.0.0", port=8000, reload=True)
