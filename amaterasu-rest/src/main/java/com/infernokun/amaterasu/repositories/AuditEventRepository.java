package com.infernokun.amaterasu.repositories;

import com.infernokun.amaterasu.models.entities.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditEventRepository extends JpaRepository<AuditEvent, String>, JpaSpecificationExecutor<AuditEvent> {
}
