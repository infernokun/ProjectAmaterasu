package com.infernokun.amaterasu.models.entities.ctf.dto.play;

import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.enums.EnvironmentMode;
import com.infernokun.amaterasu.models.enums.EnvironmentStatus;
import com.infernokun.amaterasu.models.enums.LabType;

/**
 * A lab's environment as one viewer sees it: the copy they work in (the shared one, or their
 * team's), its status, and whether they may start or stop it. {@code tracker} is null until a copy
 * exists; {@code note} says why the viewer cannot act, when they cannot.
 */
public record EnvironmentView(
        String labId,
        String labName,
        LabType labType,
        EnvironmentMode mode,
        EnvironmentStatus status,
        String trackerId,
        LabTracker tracker,
        boolean canControl,
        String note,
        int copiesRunning) {
}
