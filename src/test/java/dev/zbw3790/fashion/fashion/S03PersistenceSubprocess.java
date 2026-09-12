package dev.zbw3790.fashion.fashion;

import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod;

/** 独立普通测试 JVM 的入口；由 Fabric JUnit 应用补丁，不启动游戏入口。 */
public final class S03PersistenceSubprocess {
    public static void main(String[] args) {
        System.setProperty("fashion3790.s03RestartFile",args[0]);
        try(var session=LauncherFactory.openSession()) {
            var listener=new SummaryGeneratingListener();
            session.getLauncher().registerTestExecutionListeners(listener);
            session.getLauncher().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectMethod(
                    "dev.zbw3790.fashion.fashion.FullFashionPersistenceTest#independentProcessReadsWrittenV2")).build());
            var summary=listener.getSummary();summary.printTo(new java.io.PrintWriter(System.out));
            if(summary.getTestsFoundCount()!=1 || summary.getTestsSucceededCount()!=1 || summary.getTotalFailureCount()!=0) {
                summary.printFailuresTo(new java.io.PrintWriter(System.err));System.exit(1);
            }
        }
    }
}
