package com.infernokun.amaterasu.services.alt;

import com.infernokun.amaterasu.exceptions.RemoteCommandException;
import com.infernokun.amaterasu.models.RemoteCommandResponse;
import com.infernokun.amaterasu.models.entities.lab.RemoteServer;
import com.infernokun.amaterasu.services.BaseService;
import com.infernokun.amaterasu.utils.AESUtil;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;

@Service
public class RemoteCommandService extends BaseService {
    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(15);
    private static final int CONNECT_TIMEOUT_MS = 30_000;

    private final AESUtil aesUtil;

    public RemoteCommandService(AESUtil aesUtil) {
        this.aesUtil = aesUtil;
    }

    @Transactional(noRollbackFor = RemoteCommandException.class)
    public RemoteCommandResponse handleRemoteCommand(String cmd, RemoteServer remoteServer) throws RemoteCommandException {
        return handleRemoteCommand(cmd, remoteServer, null, DEFAULT_TIMEOUT);
    }

    public RemoteCommandResponse handleRemoteCommand(String cmd, RemoteServer remoteServer, byte[] stdin,
                                                     Duration timeout) throws RemoteCommandException {
        Session session = null;
        try {
            session = getConnectedSession(remoteServer, CONNECT_TIMEOUT_MS);
            return executeRemoteCommand(session, cmd, stdin, timeout);
        } catch (RemoteCommandException e) {
            LOGGER.error("Exception while running command for server {}: {}", remoteServer.getId(), e.getMessage());
            return new RemoteCommandResponse("", e.getMessage(), 1);
        } catch (Exception e) {
            throw new RemoteCommandException("Unexpected error occurred: " + e.getMessage());
        } finally {
            disconnectSession(session, remoteServer);
        }
    }

    /** Writes a remote file by streaming the bytes over stdin, creating parent directories. */
    public RemoteCommandResponse writeRemoteFile(String remotePath, byte[] content, RemoteServer remoteServer) {
        String path = shellQuote(remotePath);
        String cmd = String.format("mkdir -p \"$(dirname %s)\" && cat > %s", path, path);
        return handleRemoteCommand(cmd, remoteServer, content, DEFAULT_TIMEOUT);
    }

    public RemoteCommandResponse writeRemoteFile(String remotePath, String content, RemoteServer remoteServer) {
        return writeRemoteFile(remotePath, content.getBytes(StandardCharsets.UTF_8), remoteServer);
    }

    public boolean validateConnection(RemoteServer remoteServer) {
        Session session = null;
        try {
            session = getConnectedSession(remoteServer, CONNECT_TIMEOUT_MS);
            RemoteCommandResponse result = executeRemoteCommand(session, "echo CONNECTION_TEST_SUCCESSFUL",
                    null, Duration.ofSeconds(30));
            boolean isValid = result.getOutput().contains("CONNECTION_TEST_SUCCESSFUL");
            LOGGER.info("Connection validation for server {}: {}", remoteServer.getName(),
                    isValid ? "SUCCESSFUL" : "FAILED");
            return isValid;
        } catch (Exception e) {
            LOGGER.error("Connection validation failed for server {} ({}:{}): {}", remoteServer.getName(),
                    remoteServer.getIpAddress(), remoteServer.getPort(), e.getMessage());
            return false;
        } finally {
            disconnectSession(session, remoteServer);
        }
    }

    /** Wraps a value in single quotes for safe use as one POSIX shell word. */
    public static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private Session getConnectedSession(RemoteServer remoteServer, int timeout) throws RemoteCommandException {
        JSch jSch = new JSch();
        Session session = null;

        try {
            session = jSch.getSession(remoteServer.getUsername(), remoteServer.getIpAddress(), remoteServer.getPort());

            String decryptedPassword = remoteServer.getId() != null ? decryptPassword(remoteServer) : remoteServer.getPassword();

            session.setPassword(decryptedPassword);
            session.setConfig("StrictHostKeyChecking", "no");
            session.setConfig("PreferredAuthentications", "publickey,password,keyboard-interactive");

            session.connect(timeout);

            session.setPassword("");
            return session;
        } catch (JSchException e) {
            disconnectSession(session, remoteServer);
            throw new RemoteCommandException("RemoteCommandException: SSH connection failed: " + e.getMessage());
        }
    }

    private String decryptPassword(RemoteServer remoteServer) throws RemoteCommandException {
        try {
            String decryptedPassword = aesUtil.decrypt(remoteServer.getPassword());

            if (decryptedPassword == null || decryptedPassword.isEmpty()) {
                LOGGER.error("Decrypted password is null or empty for server: {}", remoteServer.getId());
                throw new IllegalArgumentException("Decrypted password is null or empty!");
            }

            return Normalizer.normalize(decryptedPassword, Normalizer.Form.NFKC);
        } catch (Exception e) {
            LOGGER.error("Error decrypting password for server: {}", remoteServer.getId());
            throw new RemoteCommandException("Error decrypting password: " + e.getMessage());
        }
    }

    private void disconnectSession(Session session, RemoteServer remoteServer) {
        if (session != null && session.isConnected()) {
            session.disconnect();
            LOGGER.debug("Disconnected from {}@{} for server: {}",
                    remoteServer.getUsername(), remoteServer.getIpAddress(), remoteServer.getId());
        }
    }

    private RemoteCommandResponse executeRemoteCommand(Session session, String command, byte[] stdin, Duration timeout) {
        ChannelExec channel = null;
        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);

            // JSch writes both streams from its own thread, so neither can fill up and block the other
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            channel.setOutputStream(stdout, true);
            channel.setErrStream(stderr, true);
            channel.setInputStream(stdin != null ? new ByteArrayInputStream(stdin) : null);

            channel.connect(CONNECT_TIMEOUT_MS);

            long deadline = System.nanoTime() + timeout.toNanos();
            while (!channel.isClosed()) {
                if (System.nanoTime() > deadline) {
                    throw new RemoteCommandException("Command timed out after " + timeout.toSeconds() + "s");
                }
                Thread.sleep(100);
            }

            return new RemoteCommandResponse(
                    stdout.toString(StandardCharsets.UTF_8).trim(),
                    stderr.toString(StandardCharsets.UTF_8).trim(),
                    channel.getExitStatus());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RemoteCommandException("Interrupted while running command");
        } catch (JSchException e) {
            throw new RemoteCommandException("Exception while running command: " + e.getMessage());
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
        }
    }
}
