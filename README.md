# SafeOps AI — Full Stack Backend

An enterprise-grade backend for the **SafeOps AI** industrial safety platform, consisting of a Spring Boot API gateway and a Python FastAPI model microservice.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        External Clients                        │
│                    (Web App, Mobile, Postman)                   │
└───────────────────────────┬─────────────────────────────────────┘
                            │ HTTPS (port 8080)
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Spring Boot Backend                          │
│              (JWT Auth, Rate Limiting, Async Jobs)              │
│  /api/v1/auth/*  /api/v1/analysis/*  /api/v1/sensor/*          │
│  /api/v1/permits/*  /api/v1/admin/*  /actuator/*               │
└───────────────────────────┬─────────────────────────────────────┘
                            │ HTTP (internal, port 8000)
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                   FastAPI Model Service                         │
│         (LangGraph Orchestrator, LSTM, RAG, Agents)             │
│  /health  /analyze  /sensor/anomaly  /permits/check  /rag/query│
└───────────────────────────┬─────────────────────────────────────┘
                            │
              ┌─────────────┼──────────────┐
              ▼                            ▼
         PostgreSQL                    ChromaDB
          (Data)                      (Vectors)
```

## Quick Start

### Prerequisites
- Docker & Docker Compose
- (Optional) Gemini API Key for live RAG

### 1. Configure Environment

```bash
cp .env.example .env
# Edit .env and set your GEMINI_API_KEY (optional — system works in fallback mode without it)
```

### 2. Start All Services

```bash
docker-compose up --build
```

This starts 3 services:
| Service | Port | Description |
|---|---|---|
| `backend` | 8080 | Spring Boot API (public-facing) |
| `model-service` | 8000 | FastAPI model service (internal) |
| `postgres` | 5432 | PostgreSQL database |

### 3. Verify Health

```bash
# Spring Boot health (includes model service check)
curl http://localhost:8080/actuator/health

# Model service health
curl http://localhost:8000/health
```

### 4. API Usage

#### Register & Login

```bash
# Register
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username": "operator1", "email": "op1@safeops.ai", "password": "SecurePass123"}'

# Login (returns JWT tokens)
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "operator1", "password": "SecurePass123"}'
```

#### Submit Analysis (Async)

```bash
# Submit job
curl -X POST http://localhost:8080/api/v1/analysis/submit \
  -H "Authorization: Bearer <YOUR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "zoneId": "ZONE_3",
    "shiftRiskFactor": 0.8,
    "sensorRawHistory": [[18.0,28.0,1.2],[25.0,29.2,1.21],[38.0,31.0,1.24]],
    "cvRawFrame": {"zone_id":"ZONE_3","workers_detected":3,"violations":[{"worker_id":"W_101","violation_type":"NO_HELMET","confidence":0.9}]},
    "activePermitsRaw": [{"permit_id":"HW_042","type":"HOT_WORK","zone_id":"ZONE_3","start_time":"2026-07-01T12:00:10Z","expiry":"2026-07-01T20:00:00Z"}]
  }'

# Poll for results
curl http://localhost:8080/api/v1/analysis/status/<JOB_ID> \
  -H "Authorization: Bearer <YOUR_TOKEN>"
```

### 5. Swagger UI

Open [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) for interactive API documentation.

## Seeding Data

To seed the ChromaDB vector database with regulatory documents and historical incidents:

```bash
docker exec safeops-model-service python -m scripts.seed_rag
```

## Project Structure

```
├── model-service/          # FastAPI Python microservice
│   ├── app/
│   │   ├── main.py         # FastAPI entry point
│   │   ├── routers/        # REST endpoints
│   │   ├── schemas/        # Pydantic models
│   │   └── core/           # AI agents, models, orchestrator
│   ├── Dockerfile
│   └── requirements.txt
├── backend/                # Spring Boot 3.x (Java 21)
│   ├── src/main/java/com/safeops/backend/
│   │   ├── config/         # Security, WebClient, OpenAPI
│   │   ├── controller/     # REST controllers
│   │   ├── service/        # Business logic
│   │   ├── dto/            # Request/Response DTOs
│   │   ├── entity/         # JPA entities
│   │   ├── repository/     # Data access
│   │   ├── security/       # JWT, filters
│   │   ├── exception/      # Global error handling
│   │   └── health/         # Custom health indicators
│   ├── src/main/resources/
│   │   ├── application.yml # Main config
│   │   └── db/migration/   # Database migrations
│   ├── Dockerfile
│   └── pom.xml
├── docker-compose.yml
├── .env.example
└── README.md
```

## Key Features

- **JWT Authentication** with refresh token rotation
- **Role-Based Access Control** (USER / ADMIN)
- **Async Job Pattern** for long-running AI analysis (submit → poll)
- **Actuator Health Checks** with custom model service health indicator
- **Swagger UI** with grouped API documentation
- **Structured Logging** with SLF4J/Logback
