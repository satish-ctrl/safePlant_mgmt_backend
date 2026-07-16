from typing import TypedDict, List, Dict, Any
from langgraph.graph import StateGraph, END

from app.core.config import Config
from app.core.graph.plant_graph import PlantGraph
from app.core.agents.sensor_agent import SensorAgent
from app.core.agents.cv_agent import CVAgent
from app.core.agents.permit_agent import PermitAgent
from app.core.agents.rag_agent import RAGAgent
from app.core.orchestrator.risk_fusion import RiskFusionEngine

# 1. Define the LangGraph State Schema
class AgentState(TypedDict):
    zone_id: str
    current_time: str
    shift_risk_factor: float
    
    # Raw inputs received from data layer
    sensor_raw_history: List[Any]
    cv_raw_frame: Dict[str, Any]
    active_permits_raw: List[Dict[str, Any]]
    
    # Agent evaluation outputs
    sensor_anomaly_out: Dict[str, Any]
    cv_safety_out: Dict[str, Any]
    permit_intel_out: Dict[str, Any]
    
    # Risk calculation and compliance outputs
    risk_fusion_out: Dict[str, Any]
    rag_compliance_out: Dict[str, Any]
    
    # Integration hooks for other team members
    action_taken: str
    notifications_sent: List[str]
    report_generated: bool

# Initialize core wrappers
plant_graph = PlantGraph()
sensor_agent = SensorAgent()
cv_agent = CVAgent()
permit_agent = PermitAgent()
rag_agent = RAGAgent()
risk_fusion = RiskFusionEngine(plant_graph)

# 2. Define Node Functions
def run_sensor_node(state: AgentState) -> Dict[str, Any]:
    """Evaluates raw sensor history to detect multivariate anomalies."""
    print("[LangGraph Node] Running sensor_node...")
    out = sensor_agent.run(state["sensor_raw_history"])
    return {"sensor_anomaly_out": out}

def run_cv_node(state: AgentState) -> Dict[str, Any]:
    """Evaluates CCTV violations from YOLOv8 wrapper."""
    print("[LangGraph Node] Running cv_node...")
    out = cv_agent.run(state["cv_raw_frame"])
    return {"cv_safety_out": out}

def run_permit_node(state: AgentState) -> Dict[str, Any]:
    """Analyzes active work permits for conflicts and updates the Plant Graph."""
    print("[LangGraph Node] Running permit_node...")
    permits = state["active_permits_raw"]
    
    existing_permits = [n for n, d in plant_graph.G.nodes(data=True) if d.get('type') == 'Permit']
    for ep in existing_permits:
        plant_graph.remove_expired_permit(ep)
        
    for p in permits:
        regs = []
        if p["type"] == "HOT_WORK":
            regs = ["OISD_105_4.3", "OISD_105_7.3.2"]
        elif p["type"] == "CONFINED_SPACE":
            regs = ["FACTORIES_ACT_36"]
        plant_graph.add_active_permit(p["permit_id"], p["type"], p["zone_id"], regs)
        
    sensor_val = state.get("sensor_anomaly_out", {}).get("current_value", 0.0)
    out = permit_agent.run(permits, current_time=state.get("current_time"), current_gas=sensor_val)
    return {"permit_intel_out": out}

def run_risk_fusion_node(state: AgentState) -> Dict[str, Any]:
    """Runs the Risk Fusion Engine to formulate the compound risk score."""
    print("[LangGraph Node] Running risk_fusion_node...")
    sensor_out = state["sensor_anomaly_out"]
    cv_out = state["cv_safety_out"]
    permit_out = state["permit_intel_out"]
    
    fusion_out = risk_fusion.compute_score(
        sensor_anomaly_score=sensor_out["anomaly_score"],
        cv_violations=cv_out["violations"],
        permit_conflicts=permit_out["conflicts"],
        shift_risk_factor=state["shift_risk_factor"],
        zone_id=state["zone_id"]
    )
    
    score = fusion_out["score"]
    action_taken = "MONITOR"
    notifications = []
    report_generated = False
    
    if score >= Config.SEVERITY_THRESHOLDS["CRITICAL"]:
        action_taken = "TRIGGER_EVACUATION"
        notifications = ["SMS_SENT", "WHATSAPP_SENT", "IN_APP_PUSH"]
        report_generated = True
    elif score >= Config.SEVERITY_THRESHOLDS["HIGH"]:
        action_taken = "DISPATCH_ALERT"
        notifications = ["WHATSAPP_SENT", "IN_APP_PUSH"]
    elif score >= Config.SEVERITY_THRESHOLDS["MED"]:
        action_taken = "LOG_WARNING"
        notifications = ["IN_APP_PUSH"]
        
    return {
        "risk_fusion_out": fusion_out,
        "action_taken": action_taken,
        "notifications_sent": notifications,
        "report_generated": report_generated
    }

def run_rag_compliance_node(state: AgentState) -> Dict[str, Any]:
    """Runs the RAG Copilot Agent to search regulatory standards and historical precedents."""
    print("[LangGraph Node] Running rag_compliance_node...")
    
    context = {
        "zone_id": state["zone_id"],
        "sensor_reading": state["sensor_anomaly_out"]["current_value"],
        "cv_violations": state["cv_safety_out"]["violations"],
        "active_permits": state["permit_intel_out"]["active_permits"]
    }
    out = rag_agent.run(context)
    return {"rag_compliance_out": out}

# 3. Create the LangGraph Workflow Machine
builder = StateGraph(AgentState)

builder.add_node("sensor_node", run_sensor_node)
builder.add_node("cv_node", run_cv_node)
builder.add_node("permit_node", run_permit_node)
builder.add_node("risk_fusion_node", run_risk_fusion_node)
builder.add_node("rag_compliance_node", run_rag_compliance_node)

builder.set_entry_point("sensor_node")

builder.add_edge("sensor_node", "cv_node")
builder.add_edge("cv_node", "permit_node")
builder.add_edge("permit_node", "risk_fusion_node")

def route_after_fusion(state: AgentState):
    score = state["risk_fusion_out"]["score"]
    if score >= 0.50:
        return "rag_compliance_node"
    return END

builder.add_conditional_edges(
    "risk_fusion_node",
    route_after_fusion,
    {
        "rag_compliance_node": "rag_compliance_node",
        END: END
    }
)

builder.add_edge("rag_compliance_node", END)

# Compile the LangGraph Application
flow_app = builder.compile()
