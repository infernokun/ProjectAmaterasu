package com.infernokun.amaterasu.repositories;

import com.infernokun.amaterasu.models.entities.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, String>, JpaSpecificationExecutor<AuditEvent> {
    List<AuditEvent> findTop500ByTargetTypeAndTargetIdInAndActionInOrderByAtDesc(String targetType, Collection<String> targetIds,
                                                                                Collection<String> actions);
}
