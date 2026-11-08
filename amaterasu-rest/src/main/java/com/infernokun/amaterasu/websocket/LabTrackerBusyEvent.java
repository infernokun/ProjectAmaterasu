package com.infernokun.amaterasu.websocket;

import com.infernokun.amaterasu.models.entities.lab.LabTracker;

/**
 * Published when a lab action starts or ends, before its transaction commits, so every view shows
 * Starting while a copy is being built.
 */
public record LabTrackerBusyEvent(LabTracker labTracker) {
}
