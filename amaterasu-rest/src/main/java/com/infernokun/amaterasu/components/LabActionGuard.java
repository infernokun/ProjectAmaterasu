package com.infernokun.amaterasu.components;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks lab trackers that have a start, pause, stop or delete in progress, and which one. */
@Component
public class LabActionGuard {
    public static final String STARTING = "STARTING";
    public static final String RESUMING = "RESUMING";
    public static final String PAUSING = "PAUSING";
    public static final String STOPPING = "STOPPING";
    public static final String REMOVING = "REMOVING";
    public static final String BUSY = "BUSY";

    private final Map<String, String> busy = new ConcurrentHashMap<>();

    public boolean tryAcquire(String labTrackerId) {
        return tryAcquire(labTrackerId, BUSY);
    }

    public boolean tryAcquire(String labTrackerId, String action) {
        return labTrackerId == null || busy.putIfAbsent(labTrackerId, action) == null;
    }

    public void release(String labTrackerId) {
        if (labTrackerId != null) {
            busy.remove(labTrackerId);
        }
    }

    public boolean isBusy(String labTrackerId) {
        return labTrackerId != null && busy.containsKey(labTrackerId);
    }

    /** The action in progress, or null. */
    public String actionFor(String labTrackerId) {
        return labTrackerId == null ? null : busy.get(labTrackerId);
    }
}
