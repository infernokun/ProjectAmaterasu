package com.infernokun.amaterasu.services.entity;

import com.infernokun.amaterasu.models.entities.lab.LabTracker;
import com.infernokun.amaterasu.models.enums.LabStatus;
import com.infernokun.amaterasu.models.proxmox.ProxmoxVM;
import com.infernokun.amaterasu.repositories.lab.LabTrackerRepository;
import com.infernokun.amaterasu.services.alt.DockerService;
import com.infernokun.amaterasu.services.alt.RemoteCommandService;
import com.infernokun.amaterasu.services.entity.lab.LabTrackerService;
import com.infernokun.amaterasu.websocket.LabTrackerChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LabTrackerServiceEventsTest {
    private LabTrackerRepository repository;
    private ApplicationEventPublisher events;
    private LabTrackerService service;

    @BeforeEach
    void setUp() {
        repository = mock(LabTrackerRepository.class);
        events = mock(ApplicationEventPublisher.class);
        service = new LabTrackerService(mock(TeamService.class), repository, mock(RemoteCommandService.class),
                mock(DockerService.class), events);
    }

    private static LabTracker tracker(LabStatus status) {
        LabTracker tracker = LabTracker.builder().labStatus(status).build();
        tracker.setId("lt1");
        return tracker;
    }

    private LabTrackerChangedEvent published() {
        ArgumentCaptor<LabTrackerChangedEvent> event = ArgumentCaptor.forClass(LabTrackerChangedEvent.class);
        verify(events).publishEvent(event.capture());
        return event.getValue();
    }

    @Test
    void publishesSavedTrackers() {
        LabTracker tracker = tracker(LabStatus.PAUSED);
        when(repository.save(tracker)).thenReturn(tracker);

        service.updateLabTracker(tracker);

        assertThat(published()).isEqualTo(LabTrackerChangedEvent.updated(tracker));
    }

    @Test
    void publishesDeletedTrackers() {
        LabTracker tracker = tracker(LabStatus.STOPPED);
        when(repository.findById("lt1")).thenReturn(Optional.of(tracker));

        service.deleteLabTracker("lt1");

        assertThat(published().removed()).isTrue();
    }

    @Test
    void publishesConditionalVmUpdatesWithoutTouchingTheManagedTracker() {
        LabTracker tracker = tracker(LabStatus.ACTIVE);
        List<ProxmoxVM> vms = List.of(new ProxmoxVM());
        when(repository.updateVmsIfStatus(eq("lt1"), anyList(), eq(LabStatus.DEGRADED), eq(LabStatus.ACTIVE))).thenReturn(1);

        service.updateVmsIfStatus(tracker, vms, LabStatus.DEGRADED, LabStatus.ACTIVE);

        LabTracker sent = published().labTracker();
        assertThat(sent.getLabStatus()).isEqualTo(LabStatus.DEGRADED);
        assertThat(sent.getVms()).isSameAs(vms);
        assertThat(sent.getId()).isEqualTo("lt1");
        assertThat(tracker.getLabStatus()).isEqualTo(LabStatus.ACTIVE);
    }

    @Test
    void staysQuietWhenTheConditionalUpdateMisses() {
        when(repository.updateVmsIfStatus(any(), anyList(), any(), any())).thenReturn(0);

        service.updateVmsIfStatus(tracker(LabStatus.PAUSED), List.of(), LabStatus.ACTIVE, LabStatus.ACTIVE);

        verify(events, never()).publishEvent(any(Object.class));
    }
}
