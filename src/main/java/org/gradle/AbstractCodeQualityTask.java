package org.gradle;

import org.gradle.api.Incubating;
import org.gradle.api.JavaVersion;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.internal.CurrentJvmToolchainSpec;
import org.gradle.process.JavaForkOptions;
import org.gradle.work.DisableCachingByDefault;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;

/**
 * Base class for code quality tasks.
 *
 * @since 8.4
 */
@Incubating
@DisableCachingByDefault(because = "Super-class, not to be instantiated directly")
public abstract class AbstractCodeQualityTask extends SourceTask implements VerificationTask {
    private static final String OPEN_MODULES_ARG = "java.prefs/java.util.prefs=ALL-UNNAMED";

    @Inject
    public AbstractCodeQualityTask() {
        getIgnoreFailuresProperty().convention(false);
        getJavaLauncher().convention(getToolchainService().launcherFor(getObjectFactory().newInstance(CurrentJvmToolchainSpec.class)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getIgnoreFailures() {
        return getIgnoreFailuresProperty().get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setIgnoreFailures(boolean ignoreFailures) {
        this.getIgnoreFailuresProperty().set(ignoreFailures);
    }

    @Internal
    abstract protected Property<Boolean> getIgnoreFailuresProperty();

    @Inject
    abstract protected ObjectFactory getObjectFactory();

    @Inject
    abstract protected JavaToolchainService getToolchainService();

    @Inject
    abstract protected WorkerExecutor getWorkerExecutor();

    protected void configureForkOptions(JavaForkOptions forkOptions) {
        forkOptions.setMinHeapSize(getMinHeapSize().getOrNull());
        forkOptions.setMaxHeapSize(getMaxHeapSize().getOrNull());
        forkOptions.setExecutable(getJavaLauncher().get().getExecutablePath().getAsFile().getAbsolutePath());
        maybeAddOpensJvmArgs(getJavaLauncher().get(), forkOptions);
    }

    private static void maybeAddOpensJvmArgs(JavaLauncher javaLauncher, JavaForkOptions forkOptions) {
        if (JavaVersion.toVersion(javaLauncher.getMetadata().getJavaRuntimeVersion()).isJava9Compatible()) {
            forkOptions.jvmArgs("--add-opens", OPEN_MODULES_ARG);
        }
    }

    /**
     * Java launcher used to start the worker process
     */
    @Nested
    public abstract Property<JavaLauncher> getJavaLauncher();

    /**
     * The minimum heap size for the worker process.  When unspecified, no minimum heap size is set.
     *
     * Supports units like the command-line option {@code -Xms} such as {@code "1g"}.
     *
     * @return The minimum heap size.
     */
    @Optional
    @Input
    public abstract Property<String> getMinHeapSize();

    /**
     * The maximum heap size for the worker process.  If unspecified, a maximum heap size will be provided by Gradle.
     *
     * Supports units like the command-line option {@code -Xmx} such as {@code "1g"}.
     *
     * @return The maximum heap size.
     */
    @Optional
    @Input
    public abstract Property<String> getMaxHeapSize();
}
