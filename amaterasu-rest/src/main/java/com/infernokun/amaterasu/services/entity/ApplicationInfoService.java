package com.infernokun.amaterasu.services.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.entities.ApplicationInfo;
import com.infernokun.amaterasu.repositories.ApplicationInfoRepository;
import com.infernokun.amaterasu.services.BaseService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ApplicationInfoService extends BaseService {
    /** Banner markings an admin can pick; NONE hides the banner. The web app holds their colors. */
    public static final List<String> CLASSIFICATIONS = List.of("NONE", "UNCLASSIFIED", "CUI", "CONFIDENTIAL",
            "SECRET", "TOP_SECRET", "TOP_SECRET_SCI");
    public static final String CLASSIFICATION_KEY = "classification";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ApplicationInfoRepository applicationInfoRepository;
    private final AuditService auditService;

    public ApplicationInfoService(ApplicationInfoRepository applicationInfoRepository, AuditService auditService) {
        this.applicationInfoRepository = applicationInfoRepository;
        this.auditService = auditService;
    }

    public ApplicationInfo getApplicationInfo() {
        List<ApplicationInfo> appInfo = applicationInfoRepository.findAll();
        return appInfo.size() == 1 ? appInfo.getFirst() : null;
    }

    public ApplicationInfo createApplicationInfo(ApplicationInfo applicationInfo) {
        if (getApplicationInfo() != null) throw new RuntimeException("Application info can only be defined once.");

        return applicationInfoRepository.save(applicationInfo);
    }

    public ApplicationInfo updateApplicationInfo(ApplicationInfo applicationInfo) {
        applicationInfo.setUpdatedAt(LocalDateTime.now());
        return applicationInfoRepository.save(applicationInfo);
    }

    /** Stores the banner marking in the settings JSON, keeping every other setting. */
    public ApplicationInfo setClassification(String classification) {
        String value = classification == null ? "" : classification.trim().toUpperCase();
        if (!CLASSIFICATIONS.contains(value)) {
            throw new IllegalArgumentException("Pick one of " + String.join(", ", CLASSIFICATIONS));
        }
        ApplicationInfo info = getApplicationInfo();
        if (info == null) {
            throw new ResourceNotFoundException("Finish the first-run setup before changing the banner");
        }
        Map<String, Object> settings = settingsOf(info);
        Object previous = settings.getOrDefault(CLASSIFICATION_KEY, "UNCLASSIFIED");
        settings.put(CLASSIFICATION_KEY, value);
        info.setSettings(settings);
        ApplicationInfo saved = updateApplicationInfo(info);
        auditService.record("CLASSIFICATION_CHANGED", "SETTINGS", saved.getId(), "Classification banner", null,
                previous + " to " + value);
        return saved;
    }

    /** The settings JSON as a map; older installs may hold it as a JSON string of JSON. */
    Map<String, Object> settingsOf(ApplicationInfo info) {
        String raw = info.getSettings();
        try {
            Object parsed = raw == null || raw.isBlank() ? null : JSON.readValue(raw, Object.class);
            if (parsed instanceof String nested) {
                parsed = nested.isBlank() ? null : JSON.readValue(nested, Object.class);
            }
            if (parsed instanceof Map<?, ?>) {
                return JSON.convertValue(parsed, new TypeReference<LinkedHashMap<String, Object>>() { });
            }
        } catch (JsonProcessingException | IllegalArgumentException e) {
            LOGGER.warn("Settings were not valid JSON; starting from empty settings");
        }
        return new LinkedHashMap<>();
    }
}
