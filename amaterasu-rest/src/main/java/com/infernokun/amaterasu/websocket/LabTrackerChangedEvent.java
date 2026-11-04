package com.infernokun.amaterasu.websocket;

import com.infernokun.amaterasu.models.entities.lab.LabTracker;

/** Published after a lab tracker is saved or removed, so signed-in team members see it without a refresh. */
public record LabTrackerChangedEvent(LabTracker labTracker, boolean removed) {
    public static LabTrackerChangedEvent updated(LabTracker labTracker) {
        return new LabTrackerChangedEvent(labTracker, false);
    }

    public static LabTrackerChangedEvent removed(LabTracker labTracker) {
        return new LabTrackerChangedEvent(labTracker, true);
    }
}
