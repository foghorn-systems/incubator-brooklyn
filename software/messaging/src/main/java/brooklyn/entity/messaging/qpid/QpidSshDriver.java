package brooklyn.entity.messaging.qpid;

import static java.lang.String.format;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.lifecycle.CommonCommands;
import brooklyn.entity.java.JavaSoftwareProcessSshDriver;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.MutableMap;
import brooklyn.util.NetworkUtils;
import brooklyn.util.ResourceUtils;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.collect.ImmutableMap;

public class QpidSshDriver extends JavaSoftwareProcessSshDriver implements QpidDriver{

    private static final Logger log = LoggerFactory.getLogger(QpidSshDriver.class);

    public QpidSshDriver(QpidBroker entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    protected String getLogFileLocation() { return getRunDir()+"/log/qpid.log"; }

    @Override
    public Integer getAmqpPort() { return entity.getAttribute(QpidBroker.AMQP_PORT); }

    @Override
    public String getAmqpVersion() { return entity.getAttribute(QpidBroker.AMQP_VERSION); }
    
    protected String getInstallFilename() { return "qpid-java-broker-"+getVersion()+".tar.gz"; }
    protected String getInstallUrl() { return "http://download.nextag.com/apache/qpid/"+getVersion()+"/"+getInstallFilename(); }
    
    @Override
    public void install() {
        List<String> commands = new LinkedList<String>();
        commands.addAll( CommonCommands.downloadUrlAs(getInstallUrl(), getEntityVersionLabel("/"), getInstallFilename()));
        commands.add(CommonCommands.INSTALL_TAR);
        commands.add("tar xzfv "+getInstallFilename());

        newScript(INSTALLING)
                .failOnNonZeroResultCode()
                .body.append(commands)
                .execute();
    }

    @Override
    public void customize() {
        NetworkUtils.checkPortsValid(MutableMap.of("jmxPort", getJmxPort(), "amqpPort", getAmqpPort()));
        newScript(CUSTOMIZING)
                .body.append(
                    format("cp -R %s/qpid-broker-%s/{bin,etc,lib} .", getInstallDir(), getVersion()),
                    "mkdir lib/opt"
                )
                .execute();
        
        Map<?,?> rtf = entity.getConfig(QpidBroker.RUNTIME_FILES);
        if (rtf != null && rtf.size() > 0) {
            log.info("Customising {} with runtime files {}", entity, rtf);
            
            for (Map.Entry entry : rtf.entrySet()) {
                String dest = (String) entry.getKey();
                Object source = entry.getValue();
                String absoluteDest = getRunDir()+"/"+dest;
                int result;
                if (source instanceof File) {
                    result = getMachine().copyTo((File)source, absoluteDest);
                } else if (source instanceof URL) {
                    try {
                        result = getMachine().copyTo(((URL)source).openStream(), absoluteDest);
                    } catch (IOException e) {
                        throw Exceptions.propagate(e);
                    }
                } else {
                    result = getMachine().copyTo(new ResourceUtils(entity).getResourceFromUrl(""+source), absoluteDest);
                }
                log.debug("copied runtime file for {}: {} to {}/{} - result {}", new Object[] {entity, source, getRunDir(), dest, result});
            }
        }
    }

    @Override
    public void launch() {
        newScript(ImmutableMap.of("usePidFile", false), LAUNCHING)
                .body.append(
                    format("nohup ./bin/qpid-server -b '*' -m %s -p %s > /dev/null 2>&1 &", getJmxPort(), getAmqpPort())
                )
                .execute();
    }

    public String getPidFile() { return "qpid-server.pid"; }
    
    @Override
    public boolean isRunning() {
        return newScript(ImmutableMap.of("usePidFile", getPidFile()), CHECK_RUNNING).execute() == 0;
    }


    @Override
    public void stop() {
        newScript(ImmutableMap.of("usePidFile", getPidFile()), STOPPING).execute();
    }

    @Override
    public void kill() {
        newScript(ImmutableMap.of("usePidFile", getPidFile()), KILLING).execute();
    }

    public Map<String, String> getShellEnvironment() {
        Map<String, String> orig = super.getShellEnvironment();
        return MutableMap.<String, String>builder()
                .putAll(orig)
                .put("QPID_HOME", getRunDir())
                .put("QPID_WORK", getRunDir())
                .put("QPID_OPTS", (orig.containsKey("JAVA_OPTS") ? orig.get("JAVA_OPTS") : ""))
                .build();
    }
}
