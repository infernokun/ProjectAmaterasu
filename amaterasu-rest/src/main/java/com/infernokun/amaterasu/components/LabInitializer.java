package com.infernokun.amaterasu.components;

import com.infernokun.amaterasu.config.AmaterasuConfig;
import com.infernokun.amaterasu.models.entities.Lab;
import com.infernokun.amaterasu.services.LabService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Component
public class LabInitializer {

    @Autowired
    private LabService labService;

    @Autowired
    private AmaterasuConfig amaterasuConfig;

    private final Logger LOGGER = LoggerFactory.getLogger(LabInitializer.class);

    @PostConstruct
    public void init() {
        List<Lab> labs = labService.findAllLabs();

        if (!labs.isEmpty()) {
            labs.forEach(lab -> {
                // Construct the directory path
                String labDirPath = amaterasuConfig.getUploadDir() + File.separator + lab.getId();
                File labDir = new File(labDirPath);

                // Create the upload directory if it doesn't exist
                if (!labDir.exists()) {
                    boolean created = labDir.mkdirs();
                    if (created) {
                        LOGGER.info("Created lab directory: {}", labDirPath);
                    }
                }

                // Create the docker-compose.yml file if it doesn't exist
                File dockerComposeFile = new File(labDir, "docker-compose.yml");
                if (!dockerComposeFile.exists()) {
                    try {
                        boolean fileCreated = dockerComposeFile.createNewFile();
                        if (fileCreated) {
                            LOGGER.info("Created docker-compose.yml for lab: {}", lab.getId());
                        }
                    } catch (IOException e) {
                        LOGGER.error("Error creating docker-compose.yml file for lab: {}", lab.getId());
                    }
                }
            });
        }
    }
}