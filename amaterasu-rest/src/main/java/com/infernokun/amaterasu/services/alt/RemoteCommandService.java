package com.infernokun.amaterasu.services.alt;

import com.infernokun.amaterasu.config.AmaterasuConfig;
import com.infernokun.amaterasu.exceptions.RemoteCommandException;
import com.infernokun.amaterasu.models.RemoteCommandResponse;
import com.infernokun.amaterasu.models.entities.RemoteServer;
import com.infernokun.amaterasu.services.BaseService;
import com.infernokun.amaterasu.utils.AESUtil;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.Normalizer;

@Service
public class RemoteCommandService extends BaseService {
    private final AESUtil aesUtil;

    public RemoteCommandService(AESUtil aesUtil) {
        this.aesUtil = aesUtil;
    }

    public RemoteCommandResponse handleRemoteCommand(String cmd, RemoteServer remoteServer) {
        Session session = null;
        JSch jSch = null; // Create a new JSch instance for each connection
        try {
            jSch = new JSch(); // Create a new JSch instance
            session = jSch.getSession(remoteServer.getUsername(), remoteServer.getIpAddress(), 22);

            String decryptedPassword = null;
            String originalPassword = remoteServer.getPassword(); // Get the original password

            try {
                decryptedPassword = aesUtil.decrypt(originalPassword);

                if (decryptedPassword == null || decryptedPassword.isEmpty()) {
                    LOGGER.error("Decrypted password is null or empty for server: {}", remoteServer.getId());
                    throw new IllegalArgumentException("Decrypted password is null or empty!");
                }

                // Normalize the decrypted password
                decryptedPassword = Normalizer.normalize(decryptedPassword, Normalizer.Form.NFKC);

            } catch (Exception e) {
                LOGGER.error("Error decrypting password for server: {}", remoteServer.getId(), e);
                throw new RemoteCommandException("Error decrypting password: " + e.getMessage(), e);
            }
            
            session.setPassword(decryptedPassword);
            session.setConfig("StrictHostKeyChecking", "no");
            session.setConfig("session.connect", "false"); // Disable session reuse
            session.setConfig("PreferredAuthentications", "publickey,password,keyboard-interactive"); // Explicit auth methods

            // Try connecting with proper error handling
            try {
                session.connect(60000);
                LOGGER.info("Connected successfully to {}@{} for server: {}", remoteServer.getUsername(), remoteServer.getIpAddress(), remoteServer.getId());
                session.setPassword(""); // Clear the password after use
            } catch (JSchException e) {
                LOGGER.error("SSH connection failed for server {}: {}", remoteServer.getId(), e.getMessage());
                throw new RemoteCommandException("SSH connection failed: " + e.getMessage(), e);
            }

            CommandResult result = executeRemoteCommand(session, cmd);

            return new RemoteCommandResponse(result.output(), result.error());
        } catch (Exception e) {
            LOGGER.error("Exception while running command for server {}: {}", remoteServer.getId(), e.getMessage(), e);
            throw new RemoteCommandException("Exception while running command: " + e.getMessage(), e);
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
                LOGGER.debug("Disconnected from {}@{} for server: {}", remoteServer.getUsername(), remoteServer.getIpAddress(), remoteServer.getId());
            }
        }
    }

    private CommandResult executeRemoteCommand(Session session, String command) {
        try {
            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            InputStream inputStream = channel.getInputStream();
            InputStream errorStream = channel.getErrStream();

            channel.connect(60000);

            String output = readStream(inputStream);
            String errorOutput = readStream(errorStream);

            channel.disconnect();

            return new CommandResult(output, errorOutput);
        } catch (JSchException | IOException e) {
            throw new RemoteCommandException("Exception while running command: " + command, e);
        }
    }

    private String readStream(InputStream stream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }
        return output.toString().trim();
    }

    private record CommandResult(String output, String error) {
        public boolean isSuccess() {
            return error.isEmpty();
        }
    }
}
