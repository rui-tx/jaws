package org.ruitx.jaws.simulation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.ruitx.jaws.components.Urd;

class TyrSimulationRunnerTest {

  @Test
  @DisplayName("runAllSimulations completes without assertion failures")
  void runAllSimulations_shouldComplete() {
    // Ensure clean logical time and state for deterministic behavior
    Urd.getInstance().resetTime();
    Urd.getInstance().clearSimulationState();

    TyrSimulationRunner runner = new TyrSimulationRunner();

    // The runner's internal steps throw if expectations fail.
    assertDoesNotThrow(runner::runAllSimulations);
  }
}
