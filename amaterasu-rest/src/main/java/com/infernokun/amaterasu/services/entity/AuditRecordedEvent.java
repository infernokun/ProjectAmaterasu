package com.infernokun.amaterasu.services.entity;

import com.infernokun.amaterasu.models.entities.AuditEvent;

/** Published after an audit event is stored, for listeners such as notifications. */
public record AuditRecordedEvent(AuditEvent event) {
}
