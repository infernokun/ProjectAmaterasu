package com.infernokun.amaterasu.components;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks lab trackers that have a start, pause, stop or delete in progress. */
@Component
public class LabActionGuard {
    private final Set<String> busy = ConcurrentHashMap.newKeySet();

    public boolean tryAcquire(String labTrackerId) {
        return labTrackerId == null || busy.add(labTrackerId);
    }

    public void release(String labTrackerId) {
        if (labTrackerId != null) {
            busy.remove(labTrackerId);
        }
    }

    public boolean isBusy(String labTrackerId) {
        return labTrackerId != null && busy.contains(labTrackerId);
    }
}
