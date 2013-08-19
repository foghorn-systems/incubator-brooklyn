package brooklyn.entity.basic;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.SshTasks.AbstractSshTaskFactory;
import brooklyn.entity.basic.SshTasks.SshTaskWrapper;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.ManagementContext;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.exceptions.PropagatedRuntimeException;

import com.google.common.io.Files;

public class SshEffectorTasksTest {

    private static final Logger log = LoggerFactory.getLogger(SshEffectorTasksTest.class);
    
    TestApplication app;
    ManagementContext mgmt;
    
    boolean failureExpected;
    
    @BeforeMethod(alwaysRun=true)
    public void setup() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        mgmt = app.getManagementContext();
        
        LocalhostMachineProvisioningLocation lhc = mgmt.getLocationManager().createLocation(LocationSpec.create(LocalhostMachineProvisioningLocation.class));
        SshMachineLocation lh = lhc.obtain();
        app.start(Arrays.asList(lh));
        clearExpectedFailure();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (mgmt != null) Entities.destroyAll(mgmt);
        mgmt = null;
        checkExpectedFailure();
    }

    protected void checkExpectedFailure() {
        if (failureExpected) {
            clearExpectedFailure();
            Assert.fail("Test should have thrown an exception but it did not.");
        }
    }
    
    protected void clearExpectedFailure() {
        failureExpected = false;
    }

    protected void setExpectingFailure() {
        failureExpected = true;
    }


    protected <U,T extends AbstractSshTaskFactory<?,U>> SshTaskWrapper<U> submit(final T task) {
        final Semaphore s = new Semaphore(0);
        final AtomicReference<SshTaskWrapper<U>> result = new AtomicReference<SshTaskWrapper<U>>(); 
        app.getExecutionContext().execute(new Runnable() {
            @Override
            public void run() {
                SshTaskWrapper<U> t = task.newTask();
                app.getExecutionContext().submit(t);
                result.set(t);
                s.release();
            }
        });
        try {
            s.acquire();
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        }
        return result.get();
    }
    
    @Test(groups="Integration")
    public void testNonRunningPid() {
        SshTaskWrapper<Integer> t = submit(SshEffectorTasks.codePidRunning(99999));
        Assert.assertNotEquals(t.getTask().getUnchecked(), (Integer)0);
        Assert.assertNotEquals(t.getExitCode(), (Integer)0);
        SshTaskWrapper<Boolean> t2 = submit(SshEffectorTasks.isPidRunning(99999));
        Assert.assertFalse(t2.getTask().getUnchecked());
    }

    @Test(groups="Integration")
    public void testNonRunningPidRequired() {
        SshTaskWrapper<?> t = submit(SshEffectorTasks.requirePidRunning(99999));
        setExpectingFailure();
        try {
            t.getTask().getUnchecked();
        } catch (Exception e) {
            log.info("The error if required PID is not found is: "+e);
            clearExpectedFailure();
            Assert.assertTrue(e.toString().contains("Process with PID"), "Expected nice clue in error but got: "+e);
        }
        checkExpectedFailure();
    }

    public static Integer getMyPid() {
        try {
            java.lang.management.RuntimeMXBean runtime = 
                    java.lang.management.ManagementFactory.getRuntimeMXBean();
            java.lang.reflect.Field jvm = runtime.getClass().getDeclaredField("jvm");
            jvm.setAccessible(true);
//            sun.management.VMManagement mgmt = (sun.management.VMManagement) jvm.get(runtime);
            Object mgmt = jvm.get(runtime);
            java.lang.reflect.Method pid_method =  
                    mgmt.getClass().getDeclaredMethod("getProcessId");
            pid_method.setAccessible(true);

            return (Integer) pid_method.invoke(mgmt);
        } catch (Exception e) {
            throw new PropagatedRuntimeException("Test depends on (fragile) getMyPid method which does not work here", e);
        }
    }
    
    @Test(groups="Integration")
    public void testRunningPid() {
        SshTaskWrapper<Integer> t = submit(SshEffectorTasks.codePidRunning(getMyPid()));
        Assert.assertEquals(t.getTask().getUnchecked(), (Integer)0);
        SshTaskWrapper<Boolean> t2 = submit(SshEffectorTasks.isPidRunning(getMyPid()));
        Assert.assertTrue(t2.getTask().getUnchecked());
    }

    @Test(groups="Integration")
    public void testRunningPidFromFile() throws IOException {
        File f = File.createTempFile("testBrooklynPid", ".pid");
        Files.write( (""+getMyPid()).getBytes(), f );
        SshTaskWrapper<Integer> t = submit(SshEffectorTasks.codePidFromFileRunning(f.getPath()));
        Assert.assertEquals(t.getTask().getUnchecked(), (Integer)0);
        SshTaskWrapper<Boolean> t2 = submit(SshEffectorTasks.isPidFromFileRunning(f.getPath()));
        Assert.assertTrue(t2.getTask().getUnchecked());
    }

    @Test(groups="Integration")
    public void testRequirePidFromFileOnFailure() throws IOException {
        File f = File.createTempFile("testBrooklynPid", ".pid");
        Files.write( "99999".getBytes(), f );
        SshTaskWrapper<?> t = submit(SshEffectorTasks.requirePidFromFileRunning(f.getPath()));
        
        setExpectingFailure();
        try {
            t.getTask().getUnchecked();
        } catch (Exception e) {
            log.info("The error if required PID is not found is: "+e);
            clearExpectedFailure();
            Assert.assertTrue(e.toString().contains("Process with PID"), "Expected nice clue in error but got: "+e);
            Assert.assertEquals(t.getExitCode(), (Integer)1);
        }
        checkExpectedFailure();
    }

    @Test(groups="Integration")
    public void testRequirePidFromFileOnFailureNoSuchFile() throws IOException {
        SshTaskWrapper<?> t = submit(SshEffectorTasks.requirePidFromFileRunning("/path/does/not/exist/SADVQW"));
        
        setExpectingFailure();
        try {
            t.getTask().getUnchecked();
        } catch (Exception e) {
            log.info("The error if required PID is not found is: "+e);
            clearExpectedFailure();
            Assert.assertTrue(e.toString().contains("Process with PID"), "Expected nice clue in error but got: "+e);
            Assert.assertEquals(t.getExitCode(), (Integer)1);
        }
        checkExpectedFailure();
    }

    @Test(groups="Integration")
    public void testRequirePidFromFileOnFailureTooManyFiles() throws IOException {
        SshTaskWrapper<?> t = submit(SshEffectorTasks.requirePidFromFileRunning("/*"));
        
        setExpectingFailure();
        try {
            t.getTask().getUnchecked();
        } catch (Exception e) {
            log.info("The error if required PID is not found is: "+e);
            clearExpectedFailure();
            Assert.assertTrue(e.toString().contains("Process with PID"), "Expected nice clue in error but got: "+e);
            Assert.assertEquals(t.getExitCode(), (Integer)2);
        }
        checkExpectedFailure();
    }

    @Test(groups="Integration")
    public void testRequirePidFromFileOnSuccess() throws IOException {
        File f = File.createTempFile("testBrooklynPid", ".pid");
        Files.write( (""+getMyPid()).getBytes(), f );
        SshTaskWrapper<?> t = submit(SshEffectorTasks.requirePidFromFileRunning(f.getPath()));
        
        t.getTask().getUnchecked();
    }

    @Test(groups="Integration")
    public void testRequirePidFromFileOnSuccessAcceptsWildcards() throws IOException {
        File f = File.createTempFile("testBrooklynPid", ".pid");
        Files.write( (""+getMyPid()).getBytes(), f );
        SshTaskWrapper<?> t = submit(SshEffectorTasks.requirePidFromFileRunning(f.getPath()+"*"));
        
        t.getTask().getUnchecked();
    }

}
