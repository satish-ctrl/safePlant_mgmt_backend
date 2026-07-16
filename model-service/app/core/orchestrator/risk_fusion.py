from app.core.config import Config
from app.core.graph.plant_graph import PlantGraph

class RiskFusionEngine:
    def __init__(self, plant_graph: PlantGraph):
        self.graph = plant_graph
        self.weights = Config.RISK_WEIGHTS
        self.thresholds = Config.SEVERITY_THRESHOLDS
        
    def compute_score(self, 
                      sensor_anomaly_score: float, 
                      cv_violations: list, 
                      permit_conflicts: list, 
                      shift_risk_factor: float, 
                      zone_id: str) -> dict:
        """Computes the compound risk score and severity level."""
        s = max(0.0, min(1.0, sensor_anomaly_score))
        c = min(len(cv_violations) / 3.0, 1.0)
        
        p = 0.0
        if permit_conflicts:
            severities = [conf.get("severity", "LOW") for conf in permit_conflicts]
            if "CRITICAL" in severities:
                p = 1.0
            elif "HIGH" in severities:
                p = 0.8
            elif "MED" in severities:
                p = 0.5
            else:
                p = 0.3
                
        sh = max(0.0, min(1.0, shift_risk_factor))
        
        compound = (
            self.weights['sensor'] * s
            + self.weights['cv'] * c
            + self.weights['permit'] * p
            + self.weights['shift'] * sh
        )
        
        kg_amplified = False
        if self.graph.is_equipment_critical(zone_id):
            compound += 0.1
            kg_amplified = True
            
        compound = max(0.0, min(1.0, compound))
        
        severity = "NORMAL"
        for name, thresh in sorted(self.thresholds.items(), key=lambda item: item[1], reverse=True):
            if compound >= thresh:
                severity = name
                break
                
        return {
            "score": round(compound, 4),
            "severity": severity,
            "kg_amplified": kg_amplified,
            "breakdown": {
                "sensor_score": round(s, 4),
                "cv_score": round(c, 4),
                "permit_score": round(p, 4),
                "shift_score": round(sh, 4)
            }
        }
