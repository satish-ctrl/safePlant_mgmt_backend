# SafeOps AI Backend - WebSocket Integration Guide

This guide describes how to integrate the frontend with the SafeOps AI real-time safety evaluation system using WebSockets.

---

## 1. Connection Details

The backend utilizes **STOMP (Simple Text Oriented Messaging Protocol)** over WebSockets. It provides two connection endpoints with **SockJS** fallback compatibility enabled to support older browsers or strict proxies.

*   **WebSocket Endpoint:** `/ws` or `/ws-message`
*   **Default Base URL:** `http://localhost:8080`
*   **WebSocket URLs:**
    *   Raw WebSocket: `ws://localhost:8080/ws` or `ws://localhost:8080/ws-message`
    *   SockJS connection: `http://localhost:8080/ws` or `http://localhost:8080/ws-message`

> [!NOTE]
> The WebSocket endpoints are configured with CORS allowed origin patterns set to `*` to allow connections from any origin. Handshake endpoints are permitted publicly in Spring Security.

---

## 2. Topic Subscription

Once connected, the frontend can subscribe to real-time safety evaluations for a specific industrial zone.

*   **Topic Destination:** `/topic/safety/{zoneId}`
*   **Example Zone IDs:** `ZONE_1`, `ZONE_2`, `ZONE_3`
*   **Subscription URL Example:** `/topic/safety/ZONE_3`

The backend does not require incoming client messages (read-only real-time stream). When the background sensor simulator runs (typically generating a safety evaluation every 5 seconds), it pushes the result to this topic.

---

## 3. Safety Evaluation Payload Format

The real-time payload contains the output of the AI safety engine. Here is the JSON structure you will receive on subscription:

```json
{
  "zone_id": "ZONE_3",
  "current_time": "2026-07-21T20:04:25.432406300Z",
  "shift_risk_factor": 0.1,
  "sensor_raw_history": [
    [17.87, 27.98, 1.22],
    [17.82, 27.99, 1.22],
    [17.84, 28.07, 1.21],
    [17.91, 28.03, 1.2],
    [17.95, 28.0, 1.2],
    [18.03, 27.98, 1.2],
    [18.05, 28.0, 1.19],
    [18.07, 28.02, 1.19],
    [18.09, 28.0, 1.18],
    [18.14, 28.02, 1.18],
    [18.17, 28.05, 1.17],
    [18.17, 28.05, 1.17]
  ],
  "cv_raw_frame": {
    "workers_detected": 1,
    "zone_id": "ZONE_3",
    "violations": []
  },
  "active_permits_raw": [],
  "sensor_anomaly_out": {
    "agent": "sensor_anomaly",
    "sensor_id": "GAS_Z3_001",
    "zone_id": "ZONE_3",
    "sensor_type": "gas",
    "current_value": 18.17,
    "normal_baseline": 18.0,
    "anomaly_score": 0.1,
    "severity": "LOW",
    "trend": "STABLE",
    "predicted_threshold_breach_minutes": -1,
    "timestamp": "2026-07-21T20:04:25.642995Z"
  },
  "cv_safety_out": {
    "agent": "cv_safety",
    "zone_id": "ZONE_3",
    "frame_timestamp": "2026-07-21T20:04:25.643339Z",
    "workers_detected": 1,
    "violations": [],
    "headcount_in_hazard_zone": 1,
    "timestamp": "2026-07-21T20:04:25.643339Z"
  },
  "permit_intel_out": {
    "agent": "permit_intel",
    "active_permits": [],
    "conflicts": [],
    "timestamp": "2026-07-21T20:04:25.643616Z"
  },
  "risk_fusion_out": {
    "score": 0.15,
    "severity": "NORMAL",
    "kg_amplified": true,
    "breakdown": {
      "sensor_score": 0.1,
      "cv_score": 0.0,
      "permit_score": 0.0,
      "shift_score": 0.1
    }
  },
  "action_taken": "MONITOR",
  "notifications_sent": [],
  "report_generated": false
}
```

### Key Field Descriptions:
*   `zone_id`: The ID of the industrial zone being evaluated.
*   `sensor_raw_history`: 2D list of historical gas, temperature, and pressure readings.
*   `cv_raw_frame`: Current raw video analytics metadata (count of workers and violation snapshots).
*   `sensor_anomaly_out`: Evaluated gas/temperature/pressure anomalies, baseline, trend, and predicted breach minutes.
*   `cv_safety_out`: Computed headcount in hazard zone and safety violation results.
*   `permit_intel_out`: Active safety permits and conflicts (such as HOT_WORK active during gas spike).
*   `risk_fusion_out`: Fused safety risk score (`score` from 0.0 to 1.0) and overall fused risk classification (`severity`).
*   `action_taken`: Suggested mitigation action (`MONITOR`, `DISPATCH_ALERT`, `TRIGGER_EVACUATION`).


---

## 4. Frontend Integration Examples

### Option A: React (TypeScript) with `@stomp/stompjs` & `sockjs-client`

First, install the required dependencies:
```bash
npm install @stomp/stompjs sockjs-client
npm install --save-dev @types/sockjs-client
```

Create a custom Hook `useSafetyStream.ts`:

```typescript
import { useEffect, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

export interface SafetyEvaluation {
  zone_id: string;
  current_time: string;
  shift_risk_factor: number;
  sensor_raw_history: number[][];
  cv_raw_frame: {
    workers_detected: number;
    zone_id: string;
    violations: any[];
  };
  active_permits_raw: any[];
  sensor_anomaly_out: {
    agent: string;
    sensor_id: string;
    zone_id: string;
    sensor_type: string;
    current_value: number;
    normal_baseline: number;
    anomaly_score: number;
    severity: string;
    trend: string;
    predicted_threshold_breach_minutes: number;
    timestamp: string;
  };
  cv_safety_out: {
    agent: string;
    zone_id: string;
    frame_timestamp: string;
    workers_detected: number;
    violations: any[];
    headcount_in_hazard_zone: number;
    timestamp: string;
  };
  permit_intel_out: {
    agent: string;
    active_permits: any[];
    conflicts: any[];
    timestamp: string;
  };
  risk_fusion_out: {
    score: number;
    severity: string;
    kg_amplified: boolean;
    breakdown: {
      sensor_score: number;
      cv_score: number;
      permit_score: number;
      shift_score: number;
    };
  };
  action_taken: string;
  notifications_sent: any[];
  report_generated: boolean;
}

export const useSafetyStream = (zoneId: string, baseUrl: string = 'http://localhost:8080') => {
  const [data, setData] = useState<SafetyEvaluation | null>(null);
  const [connected, setConnected] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    // Create the STOMP Client
    const stompClient = new Client({
      // Configure SockJS as the transport factory
      webSocketFactory: () => new SockJS(`${baseUrl}/ws`),
      reconnectDelay: 5000, // Auto reconnect every 5s if disconnected
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    });

    stompClient.onConnect = (frame) => {
      setConnected(true);
      setError(null);
      console.log('Connected to WebSocket broker: ' + frame);

      // Subscribe to safety updates for the selected zone
      stompClient.subscribe(`/topic/safety/${zoneId}`, (message) => {
        if (message.body) {
          try {
            const evaluation: SafetyEvaluation = JSON.parse(message.body);
            setData(evaluation);
          } catch (e) {
            console.error('Failed to parse evaluation payload:', e);
          }
        }
      });
    };

    stompClient.onStompError = (frame) => {
      console.error('Broker reported error: ' + frame.headers['message']);
      console.error('Additional details: ' + frame.body);
      setError(frame.headers['message'] || 'STOMP Protocol Error');
    };

    stompClient.onWebSocketClose = () => {
      setConnected(false);
    };

    // Connect to the broker
    stompClient.activate();

    // Clean up on component unmount
    return () => {
      stompClient.deactivate();
    };
  }, [zoneId, baseUrl]);

  return { data, connected, error };
};
```

#### Usage in a Component:
```tsx
import React from 'react';
import { useSafetyStream } from './hooks/useSafetyStream';

export const LiveSafetyMonitor: React.FC = () => {
  const { data, connected, error } = useSafetyStream('ZONE_3');

  return (
    <div style={{ padding: 20, fontFamily: 'sans-serif' }}>
      <h2>
        Safety Monitoring Status: 
        <span style={{ color: connected ? 'green' : 'red' }}>
          {connected ? ' ● Connected' : ' ○ Disconnected'}
        </span>
      </h2>

      {error && <div style={{ color: 'red' }}>Error: {error}</div>}

      {data ? (
        <div style={{ border: '1px solid #ccc', borderRadius: 8, padding: 15, marginTop: 15 }}>
          <h3>Zone: {data.zone_id}</h3>
          <p><strong>Risk Score:</strong> {(data.risk_fusion_out.score * 100).toFixed(1)}%</p>
          <p><strong>Severity:</strong> <span style={{ fontWeight: 'bold' }}>{data.risk_fusion_out.severity}</span></p>
          <p><strong>Workers Detected (CCTV):</strong> {data.cv_safety_out.workers_detected}</p>
          <p><strong>Sensor Value (Current Gas):</strong> {data.sensor_anomaly_out.current_value}</p>
          <p><strong>Auto-Triggered Action:</strong> {data.action_taken}</p>
          {data.permit_intel_out.conflicts.length > 0 && (
            <div>
              <p style={{ color: 'red' }}><strong>Permit Conflicts:</strong></p>
              <ul>
                {data.permit_intel_out.conflicts.map((conflict: any, i: number) => (
                  <li key={i}>{conflict.reason}</li>
                ))}
              </ul>
            </div>
          )}
        </div>
      ) : (
        <p>Waiting for sensor simulator data broadcast...</p>
      )}
    </div>
  );
};
```

---

### Option B: Vanilla JavaScript

For vanilla web applications, include the CDN links for SockJS and STOMP:

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Live Safety Stream Monitor</title>
    <!-- Include SockJS and Stomp Client libraries -->
    <script src="https://cdnjs.cloudflare.com/ajax/libs/sockjs-client/1.6.1/sockjs.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/stomp.js/2.3.3/stomp.min.js"></script>
</head>
<body>
    <h1>Real-time Zone Safety Monitor</h1>
    <div id="status">Connecting...</div>
    <pre id="data-container">No updates yet.</pre>

    <script>
        const socket = new SockJS('http://localhost:8080/ws');
        const stompClient = Stomp.over(socket);

        stompClient.connect({}, function (frame) {
            document.getElementById('status').innerText = 'Connected to SafeOps WebSocket Server';
            document.getElementById('status').style.color = 'green';
            
            // Subscribe to updates for ZONE_3
            stompClient.subscribe('/topic/safety/ZONE_3', function (message) {
                if (message.body) {
                    const parsedData = JSON.parse(message.body);
                    document.getElementById('data-container').innerText = JSON.stringify(parsedData, null, 2);
                }
            });
        }, function (error) {
            document.getElementById('status').innerText = 'Connection error: ' + error;
            document.getElementById('status').style.color = 'red';
        });
    </script>
</body>
</html>
```

---

## 5. Troubleshooting & Tips

1.  **Connecting but no data?** Ensure the background sensor simulator is enabled. You can enable it or check the status using the HTTP endpoints:
    *   Check simulator status: `GET /api/v1/simulator/mode`
    *   Set simulator mode to automatic: `PUT /api/v1/simulator/mode` with body `{"simulationMode": "AUTO"}`
2.  **No class found or build issues?** Ensure you run a clean compile of the project:
    ```bash
    mvn clean compile
    ```
3.  **Authentication Handshakes:** If you decide to add strict security to the socket endpoints in the future, configure a STOMP channel interceptor or send the token in the STOMP connection headers during the Stomp connection phase.
