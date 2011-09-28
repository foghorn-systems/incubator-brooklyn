package brooklyn.launcher

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import org.testng.annotations.Test

import brooklyn.management.internal.LocalManagementContext
import brooklyn.util.internal.TimeExtras

public class WebAppRunnerTest {
    static { TimeExtras.init() }

    private static final int TIMEOUT_MS = 5000;
    
    /**
     * This test requires the web-console.war to work.
     */
    @Test
    public void ping() {
        WebAppRunner launcher = new WebAppRunner(new LocalManagementContext(), 8090, "/web-console.war");
        assertNotNull(launcher);
        
        launcher.start();
        
        executeUntilSucceeds(timeout:TIMEOUT_MS, maxAttempts:50) {
            assertNotNull(new URL("http://localhost:8090/").getContent())
        }

        launcher.stop();
    }
}
