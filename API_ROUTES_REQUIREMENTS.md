# Complete API Routes Requirements (Updated from Backend Analysis)

This document outlines the **exact** API routes implemented in the `safePlant_mgmt_backend`. It serves as the definitive guide for frontend-backend integration.

---

## 1. Authentication & Authorization

| Route                   | Method | Description                           | Access | Payload/Parameters              |
| :---------------------- | :----: | :------------------------------------ | :----: | :------------------------------ |
| `/api/v1/auth/register` | `POST` | Register a new user account.          | Public | `username`, `email`, `password` |
| `/api/v1/auth/login`    | `POST` | Authenticate user and receive tokens. | Public | `username`, `password`          |
| `/api/v1/auth/refresh`  | `POST` | Refresh expired access token.         | Public | `refreshToken`                  |

---

---

## 5. Simulator

| Route                    | Method | Description                           | Access | Payload/Parameters |
| :----------------------- | :----: | :------------------------------------ | :----: | :----------------- |
| `/api/v1/simulator/mode` | `PUT`  | Update the simulator's configuration. | Admin  | `simulationMode`   |

---

## 6. Admin Control Panel

| Route                   | Method | Description                      | Access | Payload/Parameters    |
| :---------------------- | :----: | :------------------------------- | :----: | :-------------------- |
| `/api/v1/admin/users`   | `GET`  | List all users with pagination.  | Admin  | Query: `page`, `size` |
| `/api/v1/admin/logs`    | `GET`  | Retrieve safety evaluation logs. | Admin  | Query: `page`, `size` |
| `/api/v1/admin/metrics` | `GET`  | Retrieve system-wide metrics.    | Admin  | None                  |

---

## 7. WebSockets (STOMP)

| Endpoint / Topic                     | Description                         | Payload Format                  |
| :----------------------------------- | :---------------------------------- | :------------------------------ |
| **Endpoint:** `/ws` or `/ws-message` | STOMP connection endpoints.         | (Connection Handshake)          |
| **Topic:** `/topic/safety/{zoneId}`  | Real-time safety evaluation pushes. | JSON Object (See Details Below) |

---

## 8. Detailed Request & Response Payloads

### 1. Authentication

#### `POST /api/v1/auth/register`

**Request Payload:**

```json
{
  "username": "testuser",
  "email": "test@example.com",
  "password": "securepassword"
}
```

### 5. Simulator

#### `PUT /api/v1/simulator/mode`

**Request Payload:**

```json
{
  "simulationMode": "AUTO",
  "zoneId": "ZONE_3",
  "shiftRiskFactor": 0.2
}
```

### WebSocket Payload

#### Topic: `/topic/safety/`

**Push Payload Structure:**

```json
{
  "risk_level": "LOW",
  "anomaly_detected": false,
  "safety_score": 0.95,
  "violations": [],
  "recommendations": [
    "Ensure workers maintain a safe distance from hazard zone"
  ],
  "action_taken": "NONE",
  "risk_fusion_out": {
    "score": 0.15,
    "severity": "LOW"
  }
}
```

---

## Important Implementation Notes

1. **Base Path:** All API routes are prefixed with `/api/v1`.
2. **Security:** Secure endpoints require an `Authorization` header formatted as `Bearer {{accessToken}}`.
3. **WebSockets:** STOMP client must connect to the base URL `ws://localhost:8080/ws` and subscribe dynamically based on the active `zoneId`.
