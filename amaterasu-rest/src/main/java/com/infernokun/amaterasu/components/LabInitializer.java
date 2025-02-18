package com.infernokun.amaterasu.components;

import com.infernokun.amaterasu.config.AmaterasuConfig;
import com.infernokun.amaterasu.services.entity.LabService;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LabInitializer {

    private final LabService labService;

    private final AmaterasuConfig amaterasuConfig;

    private final Logger LOGGER = LoggerFactory.getLogger(LabInitializer.class);
    private final JSch jSch = new JSch();

    public LabInitializer(LabService labService, AmaterasuConfig amaterasuConfig) {
        this.labService = labService;
        this.amaterasuConfig = amaterasuConfig;
    }

    @PostConstruct
    public void init() {
        /*
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
                        LOGGER.info("Created local lab directory: {}", labDirPath);
                    }
                }

                // SSH to create directories and docker-compose.yml on remote host
                try {
                    Session session = jSch.getSession(amaterasuConfig.getDockerUser(), amaterasuConfig.getDockerHost(), 22);
                    session.setPassword(amaterasuConfig.getDockerPass());
                    session.setConfig("StrictHostKeyChecking", "no");

                    session.connect();

                    // Create remote directory
                    String command = String.format("mkdir -p /home/%s/app/amaterasu/%s", amaterasuConfig.getDockerUser(), lab.getId());
                    executeRemoteCommand(session, command);

                    String touchCommand = String.format("touch /home/%s/app/amaterasu/%s/docker-compose.yml", amaterasuConfig.getDockerUser(), lab.getId());
                    executeRemoteCommand(session, touchCommand);

                    // Create docker-compose.yml file remotely if it doesn't exist
                    File dockerComposeFile = new File(labDir, "docker-compose.yml");
                    if (!dockerComposeFile.exists()) {
                        boolean fileCreated = dockerComposeFile.createNewFile();
                        if (fileCreated) {
                            LOGGER.info("Created docker-compose.yml locally for lab: {}", lab.getId());
                        }
                    }
                } catch (JSchException | IOException e) {
                    LOGGER.error("Error setting up lab: {}", lab.getId(), e);
                }
            });
        }*/
    }


    private void executeRemoteCommand(Session session, String command) {
        ChannelExec channel = null;
        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.connect();

            // Optionally, you can log the output or handle it
            int exitStatus = channel.getExitStatus();
            if (exitStatus != 0) {
                LOGGER.error("Failed to execute command: {}", command);
            }
        } catch (JSchException e) {
            LOGGER.error("Error executing remote command: {}", command, e);
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
        }
    }
}