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
    private final AmaterasuConfig amaterasuConfig;
    private final AESUtil aesUtil;

    public RemoteCommandService(AmaterasuConfig amaterasuConfig, AESUtil aesUtil) {
        this.amaterasuConfig = amaterasuConfig;
        this.aesUtil = aesUtil;
    }

    public RemoteCommandResponse handleRemoteCommand(String cmd, RemoteServer remoteServer) {
        Session session = null;
        try {
            // Get connected session using the shared method
            session = getConnectedSession(remoteServer);

            // Execute the command
            CommandResult result = executeRemoteCommand(session, cmd);
            return new RemoteCommandResponse(result.output(), result.error(), result.exitCode());
        } catch (Exception e) {
            LOGGER.error("Exception while running command for server {}: {}", remoteServer.getId(), e.getMessage());
            throw new RemoteCommandException("Exception while running command: " + e.getMessage());
        } finally {
            disconnectSession(session, remoteServer);
        }
    }

    public boolean validateConnection(RemoteServer remoteServer) {
        Session session = null;
        try {
            // Get connected session using the shared method
            session = getConnectedSession(remoteServer, 30000); // Shorter timeout for validation

            // Run a simple test command
            CommandResult result = executeRemoteCommand(session, "echo CONNECTION_TEST_SUCCESSFUL");

            // Verify the output contains our test string
            boolean isValid = result.output().contains("CONNECTION_TEST_SUCCESSFUL");

            LOGGER.info("Connection validation for server {}: {}", remoteServer.getName(),
                    isValid ? "SUCCESSFUL" : "FAILED");

            return isValid;
        } catch (Exception e) {
            LOGGER.error("Connection validation failed for server {}: {}", remoteServer.getId(), e.getMessage());
            return false;
        } finally {
            disconnectSession(session, remoteServer);
        }
    }

// Private helper methods to reduce duplication

    /**
     * Creates and returns a connected SSH session
     */
    private Session getConnectedSession(RemoteServer remoteServer) throws RemoteCommandException {
        return getConnectedSession(remoteServer, 60000); // Default timeout
    }

    /**
     * Creates and returns a connected SSH session with specified timeout
     */
    private Session getConnectedSession(RemoteServer remoteServer, int timeout) throws RemoteCommandException {
        JSch jSch = new JSch();
        Session session = null;

        try {
            session = jSch.getSession(remoteServer.getUsername(), remoteServer.getIpAddress(), amaterasuConfig.getSshPort());

            String decryptedPassword = remoteServer.getId() != null ? decryptPassword(remoteServer) : remoteServer.getPassword();

            session.setPassword(decryptedPassword);
            session.setConfig("StrictHostKeyChecking", "no");
            session.setConfig("session.connect", "false");
            session.setConfig("PreferredAuthentications", "publickey,password,keyboard-interactive");

            session.connect(timeout);
            LOGGER.info("Connected successfully to {}@{} for server: {}",
                    remoteServer.getUsername(), remoteServer.getIpAddress(), remoteServer.getId());

            session.setPassword(""); // Clear the password after use
            return session;
        } catch (JSchException e) {
            LOGGER.error("SSH connection failed for server {}: {}", remoteServer.getId(), e.getMessage());
            // Cleanup the session if needed
            disconnectSession(session, remoteServer);
            throw new RemoteCommandException("SSH connection failed: " + e.getMessage());
        }
    }

    /**
     * Decrypts and normalizes the server password
     */
    private String decryptPassword(RemoteServer remoteServer) throws RemoteCommandException {
        String originalPassword = remoteServer.getPassword();

        try {
            String decryptedPassword = aesUtil.decrypt(originalPassword);

            if (decryptedPassword == null || decryptedPassword.isEmpty()) {
                LOGGER.error("Decrypted password is null or empty for server: {}", remoteServer.getId());
                throw new IllegalArgumentException("Decrypted password is null or empty!");
            }

            // Normalize the decrypted password
            return Normalizer.normalize(decryptedPassword, Normalizer.Form.NFKC);
        } catch (Exception e) {
            LOGGER.error("Error decrypting password for server: {}", remoteServer.getId());
            throw new RemoteCommandException("Error decrypting password: " + e.getMessage());
        }
    }

    /**
     * Safely disconnects a session if it's connected
     */
    private void disconnectSession(Session session, RemoteServer remoteServer) {
        if (session != null && session.isConnected()) {
            session.disconnect();
            LOGGER.debug("Disconnected from {}@{} for server: {}",
                    remoteServer.getUsername(), remoteServer.getIpAddress(), remoteServer.getId());
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
            int exitCode = channel.getExitStatus();

            channel.disconnect();

            return new CommandResult(output, errorOutput, exitCode);
        } catch (JSchException | IOException e) {
            throw new RemoteCommandException("Exception while running command: " + command);
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

    private record CommandResult(String output, String error, int exitCode) {
        public boolean isSuccess() {
            return error.isEmpty();
        }
    }
}
