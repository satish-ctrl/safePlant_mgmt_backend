import os
import torch
import datetime
import numpy as np
from app.core.config import Config
from app.core.models.lstm_autoencoder import LSTMAutoencoder


def datetime_str():
    return datetime.datetime.utcnow().isoformat() + "Z"


class SensorAgent:
    def __init__(self):
        self.model = None
        self.threshold = 1.0
        self.means = [0.0, 0.0, 0.0]
        self.stds = [1.0, 1.0, 1.0]
        self.window_size = Config.LSTM_WINDOW_SIZE
        self.features = Config.LSTM_FEATURES
        self.embedding_dim = Config.LSTM_HIDDEN_DIM
        self.load_model()

    def load_model(self):
        """Loads the pre-trained LSTM Autoencoder model weights and normalization scaling factors."""
        model_path = Config.LSTM_MODEL_PATH
        if not os.path.exists(model_path):
            print(f"WARNING: LSTM weights file not found at: {model_path}. Anomaly scoring will run in mock mode.")
            return

        try:
            checkpoint = torch.load(model_path, map_location=torch.device('cpu'), weights_only=False)
            
            self.threshold = checkpoint.get('threshold', 1.0)
            self.means = checkpoint.get('means', [0.0, 0.0, 0.0])
            self.stds = checkpoint.get('stds', [1.0, 1.0, 1.0])
            self.window_size = checkpoint.get('window_size', Config.LSTM_WINDOW_SIZE)
            self.features = checkpoint.get('no_features', Config.LSTM_FEATURES)
            self.embedding_dim = checkpoint.get('embedding_dim', Config.LSTM_HIDDEN_DIM)
            
            self.model = LSTMAutoencoder(
                seq_len=self.window_size,
                no_features=self.features,
                embedding_dim=self.embedding_dim
            )
            self.model.load_state_dict(checkpoint['model_state_dict'])
            self.model.eval()
            print("LSTM Autoencoder model successfully loaded for online anomaly detection.")
        except Exception as e:
            print(f"Error loading LSTM model checkpoint: {e}. Model will run in mock mode.")
            self.model = None

    def run(self, sequence_readings: list) -> dict:
        """Evaluates a sequence of sensor readings and returns a normalized anomaly score.
        
        Args:
            sequence_readings (list): List of dicts or list of float lists. Shape should be (window_size, features).
                Each reading has [Gas, Temp, Pressure].
                
        Returns:
            dict conforming to the frozen output schema for 'sensor_anomaly'.
        """
        raw_seq = []
        for r in sequence_readings:
            if isinstance(r, dict):
                raw_seq.append([r.get('gas', 18.0), r.get('temp', 28.0), r.get('pressure', 1.2)])
            else:
                raw_seq.append(r)
                
        if len(raw_seq) < self.window_size:
            while len(raw_seq) < self.window_size:
                raw_seq.insert(0, raw_seq[0] if raw_seq else [18.0, 28.0, 1.2])
        elif len(raw_seq) > self.window_size:
            raw_seq = raw_seq[-self.window_size:]
            
        raw_arr = np.array(raw_seq)
        
        # 1. Scale input sequence using training scale parameters
        normalized_seq = (raw_arr - self.means) / self.stds
        
        # Get current reading (latest in window)
        current_reading = raw_arr[-1]
        
        # 2. Evaluate using PyTorch model if loaded
        if self.model is not None:
            try:
                tensor_in = torch.tensor([normalized_seq], dtype=torch.float32)
                with torch.no_grad():
                    reconstructed = self.model(tensor_in).numpy()[0]
                    
                mse = float(np.mean((normalized_seq - reconstructed) ** 2))
                
                anomaly_score = min(mse / self.threshold, 1.0)
                severity = 'LOW'
                if anomaly_score >= 0.90:
                    severity = 'CRITICAL'
                elif anomaly_score >= 0.75:
                    severity = 'HIGH'
                elif anomaly_score >= 0.40:
                    severity = 'MED'
                    
                trend = 'STABLE'
                if len(raw_arr) >= 3:
                    diff = raw_arr[-1][0] - raw_arr[-3][0]
                    if diff > 0.5:
                        trend = 'RISING'
                    elif diff < -0.5:
                        trend = 'FALLING'
                        
                return {
                    "agent": "sensor_anomaly",
                    "sensor_id": "GAS_Z3_001",
                    "zone_id": "ZONE_3",
                    "sensor_type": "gas",
                    "current_value": float(current_reading[0]),
                    "normal_baseline": float(self.means[0]),
                    "anomaly_score": float(anomaly_score),
                    "severity": severity,
                    "trend": trend,
                    "predicted_threshold_breach_minutes": int(max(0, (60.0 - current_reading[0]) / (diff/2.0))) if (trend == 'RISING' and diff > 0) else -1,
                    "timestamp": os.getenv("CURRENT_TIME", datetime_str())
                }
            except Exception as e:
                print(f"LSTM inference error: {e}. Falling back to mock scoring.")
                
        # 3. Fallback mock scoring
        gas_val = current_reading[0]
        if gas_val >= 35.0:
            anomaly_score = 0.95
            severity = 'CRITICAL'
        elif gas_val >= 25.0:
            anomaly_score = 0.80
            severity = 'HIGH'
        elif gas_val >= 20.0:
            anomaly_score = 0.50
            severity = 'MED'
        else:
            anomaly_score = 0.10
            severity = 'LOW'
            
        return {
            "agent": "sensor_anomaly",
            "sensor_id": "GAS_Z3_001",
            "zone_id": "ZONE_3",
            "sensor_type": "gas",
            "current_value": float(gas_val),
            "normal_baseline": 18.0,
            "anomaly_score": float(anomaly_score),
            "severity": severity,
            "trend": "RISING" if gas_val > 20.0 else "STABLE",
            "predicted_threshold_breach_minutes": 22 if gas_val > 20.0 else -1,
            "timestamp": datetime_str()
        }
