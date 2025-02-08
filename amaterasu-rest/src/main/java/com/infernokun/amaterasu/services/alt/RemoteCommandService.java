package com.infernokun.amaterasu.services.alt;

import com.infernokun.amaterasu.config.AmaterasuConfig;
import com.infernokun.amaterasu.exceptions.RemoteCommandException;
import com.infernokun.amaterasu.models.RemoteCommandResponse;
import com.infernokun.amaterasu.services.base.BaseService;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

@Service
public class RemoteCommandService extends BaseService {

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

            return new RemoteCommandResponse(result.output(), result.error());
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
