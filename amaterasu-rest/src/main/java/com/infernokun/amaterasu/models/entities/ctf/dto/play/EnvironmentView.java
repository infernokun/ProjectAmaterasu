package com.infernokun.amaterasu.models.entities.ctf.dto.play;

import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.enums.EnvironmentMode;
import com.infernokun.amaterasu.models.enums.EnvironmentStatus;
import com.infernokun.amaterasu.models.enums.LabType;

import java.util.List;

/**
 * A lab's environment as one viewer sees it: the copy they work in (the shared one, their team's,
 * or the solo copy when they have no team) and its status. {@code tracker} is null until that copy
 * exists. Only those who run the lab may control it, and only they get {@code copies}: every copy
 * with its status. {@code note} says why the viewer cannot act, when they cannot.
 */
public record EnvironmentView(
        String labId,
        String labName,
        LabType labType,
        EnvironmentMode mode,
        EnvironmentStatus status,
        String trackerId,
        LabTracker tracker,
        String copyLabel,
        boolean canControl,
        String note,
        int copiesRunning,
        List<EnvironmentCopy> copies) {

    /**
     * One copy for those who run the lab. {@code target} names it in start, pause and stop:
     * "shared", "solo" or "team:" and the team's id.
     */
    public record EnvironmentCopy(String target, String label, String teamId, boolean solo, int members,
                                  EnvironmentStatus status, String trackerId, LabTracker tracker) {
    }
}
