package com.infernokun.amaterasu.services.alt;

import com.infernokun.amaterasu.config.AmaterasuConfig;
import com.infernokun.amaterasu.exceptions.RemoteCommandException;
import com.infernokun.amaterasu.models.RemoteCommandResponse;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

@Service
public class RemoteCommandService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteCommandService.class);


    public RemoteCommandResponse handleRemoteCommand(String cmd, AmaterasuConfig amaterasuConfig) {
        Session session = null;
        try {
            JSch jSch = new JSch();
            session = jSch.getSession(amaterasuConfig.getDockerUser(),
                    amaterasuConfig.getDockerHost(), 22);
            session.setPassword(amaterasuConfig.getDockerPass());
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            CommandResult result = executeRemoteCommand(session, cmd);

            /*if (result.getError() != null && !result.getError().isEmpty()) {
                throw new RemoteCommandException("Error while executing command: " +
                        result.getError());
            }*/

            return new RemoteCommandResponse(result.getOutput(), result.getError());
        } catch (Exception e) {
            throw new RemoteCommandException("Exception while running command: " + e.getMessage(), e);
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    private CommandResult executeRemoteCommand(Session session, String command) {
        try {
            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            InputStream inputStream = channel.getInputStream();
            InputStream errorStream = channel.getErrStream();

            channel.connect();

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

    private static class CommandResult {
        private final String output;
        private final String error;

        public CommandResult(String output, String error) {
            this.output = output;
            this.error = error;
        }

        public String getOutput() {
            return output;
        }

        public String getError() {
            return error;
        }

        public boolean isSuccess() {
            return error.isEmpty();
        }
    }
}
