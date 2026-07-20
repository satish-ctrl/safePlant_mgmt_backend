package com.safeops.backend.simulator;

import com.safeops.backend.dto.request.SimulatorModeRequest;
import com.safeops.backend.dto.response.SimulatorStatusResponse;

public interface SensorSimulatorControl {
    SimulatorStatusResponse getStatus();
    SimulatorStatusResponse updateConfiguration(SimulatorModeRequest request);
}

