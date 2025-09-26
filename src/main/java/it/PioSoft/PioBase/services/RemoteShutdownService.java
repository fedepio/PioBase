package it.PioSoft.PioBase.services;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.ChannelExec;
import it.PioSoft.PioBase.configs.PcMappingConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RemoteShutdownService {

    @Autowired
    private PcMappingConfig pcMappingConfig;

    public void shutdownPC(String ipAddress) throws Exception {
        JSch jsch = new JSch();
        Session session = null;

        try {
            PcMappingConfig.Ssh sshConfig = pcMappingConfig.getSsh();

            session = jsch.getSession(sshConfig.getUsername(), ipAddress, sshConfig.getPort());
            session.setPassword(sshConfig.getPassword());
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(30000);

            String command = "shutdown /s /t 0";

            ChannelExec channelExec = (ChannelExec) session.openChannel("exec");
            channelExec.setCommand(command);
            channelExec.connect();

            while (channelExec.isConnected()) {
                Thread.sleep(100);
            }

            channelExec.disconnect();
        } finally {
            if (session != null) {
                session.disconnect();
            }
        }
    }
}
