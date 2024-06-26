package org.gradle.fixtures.executer;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.logging.configuration.ConsoleOutput;
import org.gradle.api.logging.configuration.WarningMode;
import org.gradle.fixtures.ExecutionResult;
import org.gradle.fixtures.file.TestDirectoryProvider;
import org.gradle.fixtures.file.TestFile;
import org.gradle.fixtures.internal.BuildProcessState;
import org.gradle.fixtures.internal.NativeServicesTestFixture;
import org.gradle.internal.ImmutableActionSet;
import org.gradle.internal.MutableActionSet;
import org.gradle.internal.agents.AgentStatus;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.jvm.JavaHomeException;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.jvm.inspection.JvmVersionDetector;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.services.DefaultLoggingManagerFactory;
import org.gradle.internal.logging.services.LoggingServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.util.GradleVersion;
import org.gradle.util.internal.ClosureBackedAction;
import org.gradle.util.internal.CollectionUtils;
import org.gradle.util.internal.GFileUtils;

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.gradle.fixtures.executer.AbstractGradleExecuter.CliDaemonArgument.*;
import static org.gradle.util.internal.DefaultGradleVersion.VERSION_OVERRIDE_VAR;

public abstract class AbstractGradleExecuter implements GradleExecuter {
    private static final String DEBUG_SYSPROP = "org.gradle.integtest.debug";
    private static final String LAUNCHER_DEBUG_SYSPROP = "org.gradle.integtest.launcher.debug";
    private static final String PROFILE_SYSPROP = "org.gradle.integtest.profile";
    private static final String ALLOW_INSTRUMENTATION_AGENT_SYSPROP = "org.gradle.integtest.agent.allowed";

    protected static final ServiceRegistry GLOBAL_SERVICES = new BuildProcessState(
            true,
            AgentStatus.of(isAgentInstrumentationEnabled()),
            ClassPath.EMPTY,
            newCommandLineProcessLogging(),
            NativeServicesTestFixture.getInstance(),
            ValidationServicesFixture.getServices()
    ).getServices();

    private static final JvmVersionDetector JVM_VERSION_DETECTOR = GLOBAL_SERVICES.get(JvmVersionDetector.class);

    protected final IntegrationTestBuildContext buildContext;

    private final Logger logger;

    private final List<String> args = new ArrayList<>();
    private final List<String> tasks = new ArrayList<>();
    private boolean allowExtraLogging = true;
    protected ConsoleAttachment consoleAttachment = ConsoleAttachment.NOT_ATTACHED;
    private File workingDir;
    private boolean quiet;
    private boolean taskList;
    private boolean dependencyList;
    private final Map<String, String> environmentVars = new HashMap<>();
    private final List<File> initScripts = new ArrayList<>();
    private String executable;
    private TestFile gradleUserHomeDir;
    private File userHomeDir;
    private String javaHome;
    private File buildScript;
    private File projectDir;
    private File settingsFile;
    private boolean ignoreMissingSettingsFile;
    private boolean ignoreCleanupAssertions;
    private PipedOutputStream stdinPipe;
    private String defaultCharacterEncoding;
    private Locale defaultLocale;
    private int daemonIdleTimeoutSecs = 120;
    private boolean requireDaemon;
    private File daemonBaseDir;
    private final List<String> buildJvmOpts = new ArrayList<>();
    private final List<String> commandLineJvmOpts = new ArrayList<>();
    private boolean useOnlyRequestedJvmOpts;
    private boolean useOwnUserHomeServices;
    private ConsoleOutput consoleType;
    protected WarningMode warningMode = WarningMode.All;
    private boolean showStacktrace = false;
    private boolean renderWelcomeMessage;
    private boolean disableToolchainDownload = true;
    private boolean disableToolchainDetection = true;
    private boolean disablePluginRepositoryMirror = false;

    private int expectedGenericDeprecationWarnings;
    private final List<ExpectedDeprecationWarning> expectedDeprecationWarnings = new ArrayList<>();
    private boolean eagerClassLoaderCreationChecksOn = true;
    private boolean stackTraceChecksOn = true;
    private boolean jdkWarningChecksOn = false;

    private final MutableActionSet<GradleExecuter> beforeExecute = new MutableActionSet<>();
    private ImmutableActionSet<GradleExecuter> afterExecute = ImmutableActionSet.empty();

    private JavaDebugOptionsInternal debug = new JavaDebugOptionsInternal(Boolean.getBoolean(DEBUG_SYSPROP));

    private JavaDebugOptionsInternal debugLauncher = new JavaDebugOptionsInternal(Boolean.getBoolean(LAUNCHER_DEBUG_SYSPROP));

    private String profiler = System.getProperty(PROFILE_SYSPROP, "");

    protected boolean interactive;

    private DurationMeasurement durationMeasurement;

    protected final GradleVersion gradleVersion;
    protected final TestDirectoryProvider testDirectoryProvider;
    protected final GradleDistribution distribution;
    private GradleVersion gradleVersionOverride;

    protected boolean noExplicitNativeServicesDir;
    private boolean fullDeprecationStackTrace;
    private boolean checkDeprecations = true;
    private boolean checkDaemonCrash = true;

    enum CliDaemonArgument {
        NOT_DEFINED,
        DAEMON,
        NO_DAEMON,
        FOREGROUND
    }

    protected AbstractGradleExecuter(GradleDistribution distribution, TestDirectoryProvider testDirectoryProvider) {
        this(distribution, testDirectoryProvider, GradleVersion.current());
    }

    protected AbstractGradleExecuter(GradleDistribution distribution, TestDirectoryProvider testDirectoryProvider, GradleVersion gradleVersion) {
        this(distribution, testDirectoryProvider, gradleVersion, IntegrationTestBuildContext.INSTANCE);
    }

    protected AbstractGradleExecuter(GradleDistribution distribution, TestDirectoryProvider testDirectoryProvider, GradleVersion gradleVersion, IntegrationTestBuildContext buildContext) {
        this.distribution = distribution;
        this.testDirectoryProvider = testDirectoryProvider;
        this.gradleVersion = gradleVersion;
        this.logger = Logging.getLogger(getClass());
        this.buildContext = buildContext;
        this.gradleUserHomeDir = buildContext.getGradleUserHomeDir();
        this.daemonBaseDir = buildContext.getDaemonBaseDir();
        //this.daemonCrashLogsBeforeTest = ImmutableSet.copyOf(DaemonLogsAnalyzer.findCrashLogs(daemonBaseDir));
    }

    protected Action<ExecutionResult> getResultAssertion() {
        return new ResultAssertion(
                expectedGenericDeprecationWarnings, expectedDeprecationWarnings,
                !stackTraceChecksOn, checkDeprecations, jdkWarningChecksOn
        );
    }

    private boolean errorsShouldAppearOnStdout() {
        // If stdout and stderr are attached to the console
        return consoleAttachment.isStderrAttached() && consoleAttachment.isStdoutAttached();
    }

    public String getDefaultCharacterEncoding() {
        return defaultCharacterEncoding == null ? Charset.defaultCharset().name() : defaultCharacterEncoding;
    }

    public PipedOutputStream getStdinPipe() {
        return stdinPipe;
    }

    protected DurationMeasurement getDurationMeasurement() {
        return durationMeasurement;
    }

    public File getWorkingDir() {
        return workingDir == null ? getTestDirectoryProvider().getTestDirectory() : workingDir;
    }

    protected static class GradleInvocation {
        final Map<String, String> environmentVars = new HashMap<>();
        final List<String> args = new ArrayList<>();
        // JVM args that must be used for the build JVM
        final List<String> buildJvmArgs = new ArrayList<>();
        // JVM args that must be used to fork a JVM
        final List<String> launcherJvmArgs = new ArrayList<>();
        // Implicit JVM args that should be used to fork a JVM
        final List<String> implicitLauncherJvmArgs = new ArrayList<>();

        protected Map<String, String> getEnvironmentVars() {
            return environmentVars;
        }

        protected List<String> getArgs() {
            return args;
        }

        protected List<String> getBuildJvmArgs() {
            return buildJvmArgs;
        }

        protected List<String> getLauncherJvmArgs() {
            return launcherJvmArgs;
        }

        protected List<String> getImplicitLauncherJvmArgs() {
            return implicitLauncherJvmArgs;
        }
    }

    protected GradleInvocation buildInvocation() {
        validateDaemonVisibility();

        GradleInvocation gradleInvocation = new GradleInvocation();
        gradleInvocation.environmentVars.putAll(environmentVars);
        if (gradleVersionOverride != null) {
            gradleInvocation.environmentVars.put(VERSION_OVERRIDE_VAR, gradleVersionOverride.getVersion());
        }
        if (!useOnlyRequestedJvmOpts) {
            gradleInvocation.buildJvmArgs.addAll(getImplicitBuildJvmArgs());
        }
        gradleInvocation.buildJvmArgs.addAll(buildJvmOpts);
        calculateLauncherJvmArgs(gradleInvocation);
        gradleInvocation.args.addAll(getAllArgs());

        transformInvocation(gradleInvocation);

        if (!gradleInvocation.implicitLauncherJvmArgs.isEmpty()) {
            throw new IllegalStateException("Implicit JVM args have not been handled.");
        }

        return gradleInvocation;
    }

    protected void validateDaemonVisibility() {
        if (isUseDaemon() && isSharedDaemons()) {
            throw new IllegalStateException("Daemon that will be visible to other tests has been requested.");
        }
    }

    /**
     * Returns additional JVM args that should be used to start the build JVM.
     */
    protected List<String> getImplicitBuildJvmArgs() {
        List<String> buildJvmOpts = new ArrayList<>();
        buildJvmOpts.add("-ea");

        if (isDebug()) {
            if (System.getenv().containsKey("CI")) {
                throw new IllegalArgumentException("Builds cannot be started with the debugger enabled on CI. This will cause tests to hang forever. Remove the call to startBuildProcessInDebugger().");
            }
            buildJvmOpts.add(debug.toDebugArgument());
        }
        if (isProfile()) {
            buildJvmOpts.add(profiler);
        }

        if (isSharedDaemons()) {
            buildJvmOpts.add("-Xms256m");
            buildJvmOpts.add("-Xmx1024m");
        } else {
            buildJvmOpts.add("-Xms256m");
            buildJvmOpts.add("-Xmx512m");
        }
        if (getJavaVersionFromJavaHome().compareTo(JavaVersion.VERSION_1_8) < 0) {
            // Although Gradle isn't supported on earlier versions, some tests do run it using Java 6 and 7 to verify it behaves well in this case
            buildJvmOpts.add("-XX:MaxPermSize=320m");
        } else {
            buildJvmOpts.add("-XX:MaxMetaspaceSize=512m");
        }
        buildJvmOpts.add("-XX:+HeapDumpOnOutOfMemoryError");
        buildJvmOpts.add("-XX:HeapDumpPath=" + buildContext.getGradleUserHomeDir());
        return buildJvmOpts;
    }

    protected boolean isSharedDaemons() {
        return daemonBaseDir.equals(buildContext.getDaemonBaseDir());
    }

    protected final JavaVersion getJavaVersionFromJavaHome() {
        try {
            return JavaVersion.toVersion(JVM_VERSION_DETECTOR.getJavaVersionMajor(Jvm.forHome(getJavaHomeLocation())));
        } catch (IllegalArgumentException | JavaHomeException e) {
            return JavaVersion.current();
        }
    }

    public static boolean isAgentInstrumentationEnabled() {
        return Boolean.parseBoolean(System.getProperty(ALLOW_INSTRUMENTATION_AGENT_SYSPROP, "true"));
    }

    private static LoggingServiceRegistry newCommandLineProcessLogging() {
        LoggingServiceRegistry loggingServices = LoggingServiceRegistry.newEmbeddableLogging();
        LoggingManagerInternal rootLoggingManager = loggingServices.get(DefaultLoggingManagerFactory.class).getRoot();
        rootLoggingManager.attachSystemOutAndErr();
        return loggingServices;
    }

    @Override
    public GradleExecuter reset() {
        args.clear();
        tasks.clear();
        initScripts.clear();
        workingDir = null;
        projectDir = null;
        buildScript = null;
        settingsFile = null;
        ignoreMissingSettingsFile = false;
        // ignoreCleanupAssertions is intentionally sticky
        // ignoreCleanupAssertions = false;
        quiet = false;
        taskList = false;
        dependencyList = false;
        executable = null;
        javaHome = null;
        environmentVars.clear();
        stdinPipe = null;
        defaultCharacterEncoding = null;
        defaultLocale = null;
        commandLineJvmOpts.clear();
        buildJvmOpts.clear();
        useOnlyRequestedJvmOpts = false;
        expectedGenericDeprecationWarnings = 0;
        expectedDeprecationWarnings.clear();
        stackTraceChecksOn = true;
        jdkWarningChecksOn = false;
        renderWelcomeMessage = false;
        disableToolchainDownload = true;
        disableToolchainDetection = true;
        debug = new JavaDebugOptionsInternal(Boolean.getBoolean(DEBUG_SYSPROP));
        debugLauncher = new JavaDebugOptionsInternal(Boolean.getBoolean(LAUNCHER_DEBUG_SYSPROP));
        profiler = System.getProperty(PROFILE_SYSPROP, "");
        interactive = false;
        checkDeprecations = true;
        durationMeasurement = null;
        consoleType = null;
        warningMode = WarningMode.All;
        return this;
    }

    @Override
    public void beforeExecute(Action<? super GradleExecuter> action) {
        beforeExecute.add(action);
    }

    @Override
    public GradleExecuter withTasks(String... names) {
        return withTasks(Arrays.asList(names));
    }

    @Override
    public GradleExecuter withTasks(List<String> names) {
        tasks.clear();
        tasks.addAll(names);
        return this;
    }

    @Override
    public GradleExecuter withArguments(String... args) {
        return withArguments(Arrays.asList(args));
    }

    @Override
    public GradleExecuter withArguments(List<String> args) {
        this.args.clear();
        this.args.addAll(args);
        return this;
    }

    @Override
    public GradleExecuter withArgument(String arg) {
        this.args.add(arg);
        return this;
    }

    @Override
    public void stop() {

    }

    @Override
    public final ExecutionResult run() {
        return run(() -> {
            ExecutionResult result = doRun();
            if (errorsShouldAppearOnStdout()) {
                result = new ErrorsOnStdoutScrapingExecutionResult(result);
            }
            return result;
        });
    }

    protected abstract ExecutionResult doRun();

    @Override
    public GradleExecuter copyTo(GradleExecuter executer) {
        executer.withGradleUserHomeDir(gradleUserHomeDir);
        executer.withDaemonIdleTimeoutSecs(daemonIdleTimeoutSecs);
        executer.withDaemonBaseDir(daemonBaseDir);

        if (workingDir != null) {
            executer.inDirectory(workingDir);
        }
        if (projectDir != null) {
            executer.usingProjectDirectory(projectDir);
        }
        if (buildScript != null) {
            executer.usingBuildScript(buildScript);
        }
        if (settingsFile != null) {
            executer.usingSettingsFile(settingsFile);
        }
        if (ignoreMissingSettingsFile) {
            executer.ignoreMissingSettingsFile();
        }
        if (ignoreCleanupAssertions) {
            executer.ignoreCleanupAssertions();
        }
        if (javaHome != null) {
            executer.withJavaHome(javaHome);
        }
        for (File initScript : initScripts) {
            executer.usingInitScript(initScript);
        }
        executer.withTasks(tasks);
        executer.withArguments(args);
        executer.withEnvironmentVars(environmentVars);
        executer.usingExecutable(executable);
        if (quiet) {
            executer.withQuietLogging();
        }
        if (taskList) {
            executer.withTaskList();
        }
        if (dependencyList) {
            executer.withDependencyList();
        }

        if (userHomeDir != null) {
            executer.withUserHomeDir(userHomeDir);
        }

        if (stdinPipe != null) {
            executer.withStdinPipe(stdinPipe);
        }

        if (defaultCharacterEncoding != null) {
            executer.withDefaultCharacterEncoding(defaultCharacterEncoding);
        }
        if (noExplicitNativeServicesDir) {
            executer.withNoExplicitNativeServicesDir();
        }
        if (fullDeprecationStackTrace) {
            executer.withFullDeprecationStackTraceEnabled();
        }
        if (defaultLocale != null) {
            executer.withDefaultLocale(defaultLocale);
        }
        executer.withCommandLineGradleOpts(commandLineJvmOpts);
        executer.withBuildJvmOpts(buildJvmOpts);
        if (useOnlyRequestedJvmOpts) {
            executer.useOnlyRequestedJvmOpts();
        }
        executer.noExtraLogging();

        if (expectedGenericDeprecationWarnings > 0) {
            executer.expectDeprecationWarnings(expectedGenericDeprecationWarnings);
        }
        expectedDeprecationWarnings.forEach(executer::expectDeprecationWarning);
        if (!eagerClassLoaderCreationChecksOn) {
            executer.withEagerClassLoaderCreationCheckDisabled();
        }
        if (!stackTraceChecksOn) {
            executer.withStackTraceChecksDisabled();
        }
        if (jdkWarningChecksOn) {
            executer.withJdkWarningChecksEnabled();
        }
        if (useOwnUserHomeServices) {
            executer.withOwnUserHomeServices();
        }
        if (requireDaemon) {
            executer.requireDaemon();
        }
        if (!checkDaemonCrash) {
            executer.noDaemonCrashChecks();
        }
        if (gradleVersionOverride != null) {
            executer.withGradleVersionOverride(gradleVersionOverride);
        }

        executer.startBuildProcessInDebugger(opts -> debug.copyTo(opts))
                .startLauncherInDebugger(opts -> debugLauncher.copyTo(opts))
                .withProfiler(profiler)
                .withForceInteractive(interactive);

        if (!checkDeprecations) {
            executer.noDeprecationChecks();
        }

        if (durationMeasurement != null) {
            executer.withDurationMeasurement(durationMeasurement);
        }

        if (consoleType != null) {
            executer.withConsole(consoleType);
        }

        executer.withWarningMode(warningMode);

        if (showStacktrace) {
            executer.withStacktraceEnabled();
        }

        if (renderWelcomeMessage) {
            executer.withWelcomeMessageEnabled();
        }

        if (!disableToolchainDetection) {
            executer.withToolchainDetectionEnabled();
        }
        if (!disableToolchainDownload) {
            executer.withToolchainDownloadEnabled();
        }

        executer.withTestConsoleAttached(consoleAttachment);

        if (disablePluginRepositoryMirror) {
            executer.withPluginRepositoryMirrorDisabled();
        }

        return executer;
    }

    @Override
    public GradleExecuter withDaemonIdleTimeoutSecs(int secs) {
        daemonIdleTimeoutSecs = secs;
        return this;
    }

    @Override
    public GradleExecuter withGradleUserHomeDir(File userHomeDir) {
        this.gradleUserHomeDir = userHomeDir == null ? null : new TestFile(userHomeDir);
        return this;
    }

    @Override
    public GradleExecuter withDaemonBaseDir(File daemonBaseDir) {
        this.daemonBaseDir = daemonBaseDir;
        return this;
    }

    @Override
    public GradleExecuter inDirectory(File directory) {
        workingDir = directory;
        return this;
    }

    @Override
    @Deprecated
    public GradleExecuter usingBuildScript(File buildScript) {
        this.buildScript = buildScript;
        return this;
    }

    @Override
    public GradleExecuter usingProjectDirectory(File projectDir) {
        this.projectDir = projectDir;
        return this;
    }

    @Override
    @Deprecated
    public GradleExecuter usingSettingsFile(File settingsFile) {
        this.settingsFile = settingsFile;
        return this;
    }

    @Override
    public GradleExecuter ignoreCleanupAssertions() {
        this.ignoreCleanupAssertions = true;
        return this;
    }

    @Override
    public GradleExecuter ignoreMissingSettingsFile() {
        ignoreMissingSettingsFile = true;
        return this;
    }

    @Override
    public GradleExecuter withJavaHome(String javaHome) {
        this.javaHome = javaHome;
        return this;
    }

    @Override
    public GradleExecuter withJavaHome(File javaHome) {
        this.javaHome = javaHome == null ? null : javaHome.getAbsolutePath();
        return this;
    }

    @Override
    public GradleExecuter usingInitScript(File initScript) {
        initScripts.add(initScript);
        return this;
    }

    @Override
    public final GradleExecuter withEnvironmentVars(Map<String, ?> environment) {
        Preconditions.checkArgument(!environment.containsKey("JAVA_HOME"), "Cannot provide JAVA_HOME to withEnvironmentVars, use withJavaHome instead");
        environmentVars.clear();
        for (Map.Entry<String, ?> entry : environment.entrySet()) {
            environmentVars.put(entry.getKey(), entry.getValue().toString());
        }
        return this;
    }

    @Override
    public GradleExecuter usingExecutable(String script) {
        this.executable = script;
        return this;
    }

    @Override
    public GradleExecuter withQuietLogging() {
        quiet = true;
        return this;
    }

    @Override
    public GradleExecuter withTaskList() {
        taskList = true;
        return this;
    }

    @Override
    public GradleExecuter requireDaemon() {
        this.requireDaemon = true;
        return this;
    }

    @Override
    public GradleExecuter withDependencyList() {
        dependencyList = true;
        return this;
    }

    @Override
    public GradleExecuter withUserHomeDir(File userHomeDir) {
        this.userHomeDir = userHomeDir;
        return this;
    }

    @Override
    public GradleExecuter withGradleVersionOverride(GradleVersion gradleVersion) {
        this.gradleVersionOverride = gradleVersion;
        return this;
    }

    @Override
    public GradleExecuter withStdinPipe() {
        return withStdinPipe(new PipedOutputStream());
    }

    @Override
    public GradleExecuter withStdinPipe(PipedOutputStream stdInPipe) {
        this.stdinPipe = stdInPipe;
        return this;
    }

    @Override
    public GradleExecuter withPluginRepositoryMirrorDisabled() {
        disablePluginRepositoryMirror = true;
        return this;
    }

    @Override
    public GradleExecuter withToolchainDetectionEnabled() {
        disableToolchainDetection = false;
        return this;
    }

    @Override
    public GradleExecuter withToolchainDownloadEnabled() {
        disableToolchainDownload = false;
        return this;
    }

    @Override
    public GradleExecuter withOwnUserHomeServices() {
        useOwnUserHomeServices = true;
        return this;
    }

    @Override
    public GradleExecuter withWarningMode(WarningMode warningMode) {
        this.warningMode = warningMode;
        return this;
    }

    @Override
    public GradleExecuter withConsole(ConsoleOutput consoleType) {
        this.consoleType = consoleType;
        return this;
    }

    @Override
    public GradleExecuter withStacktraceEnabled() {
        showStacktrace = true;
        return this;
    }

    @Override
    public GradleExecuter withWelcomeMessageEnabled() {
        renderWelcomeMessage = true;
        return this;
    }

    @Override
    public GradleExecuter withTestConsoleAttached(ConsoleAttachment consoleAttachment) {
        this.consoleAttachment = consoleAttachment;
        return configureConsoleCommandLineArgs();
    }

    @Override
    public boolean isUseDaemon() {
        CliDaemonArgument cliDaemonArgument = resolveCliDaemonArgument();
        if (cliDaemonArgument == NO_DAEMON || cliDaemonArgument == FOREGROUND) {
            return false;
        }
        return requireDaemon || cliDaemonArgument == DAEMON;
    }

    @Override
    public GradleExecuter withForceInteractive(boolean flag) {
        interactive = flag;
        return this;
    }

    @Override
    public GradleExecuter startLauncherInDebugger(Action<JavaDebugOptionsInternal> action) {
        debugLauncher.setEnabled(true);
        action.execute(debugLauncher);
        return this;
    }

    @Override
    public GradleExecuter withDurationMeasurement(DurationMeasurement durationMeasurement) {
        this.durationMeasurement = durationMeasurement;
        return this;
    }

    @Override
    public GradleExecuter withCommandLineGradleOpts(String... jvmOpts) {
        CollectionUtils.addAll(commandLineJvmOpts, jvmOpts);
        return this;
    }

    @Override
    public GradleExecuter withCommandLineGradleOpts(Iterable<String> jvmOpts) {
        CollectionUtils.addAll(commandLineJvmOpts, jvmOpts);
        return this;
    }

    @Override
    public AbstractGradleExecuter withBuildJvmOpts(String... jvmOpts) {
        CollectionUtils.addAll(buildJvmOpts, jvmOpts);
        return this;
    }

    @Override
    public GradleExecuter withBuildJvmOpts(Iterable<String> jvmOpts) {
        CollectionUtils.addAll(buildJvmOpts, jvmOpts);
        return this;
    }

    @Override
    public GradleExecuter withNoExplicitNativeServicesDir() {
        noExplicitNativeServicesDir = true;
        return this;
    }

    @Override
    public GradleExecuter withFullDeprecationStackTraceEnabled() {
        fullDeprecationStackTrace = true;
        return this;
    }

    @Override
    public GradleExecuter withFileLeakDetection(String... args) {
        return withFileLeakDetection(checkNotNull(Jvm.current().getJavaVersion()), args);
    }

    @Override
    public GradleExecuter withFileLeakDetection(JavaVersion javaVersion, String... args) {
        String leakDetectionVersion = javaVersion.isCompatibleWith(JavaVersion.VERSION_11) ? "1.17" : "1.14";
        String leakDetectionJar = String.format("file-leak-detector-%s-jar-with-dependencies.jar", leakDetectionVersion);
        String leakDetectorUrl = String.format("https://repo.jenkins-ci.org/releases/org/kohsuke/file-leak-detector/%s/%s", leakDetectionVersion, leakDetectionJar);
        this.beforeExecute(executer -> {
            File leakDetectorJar = new File(this.gradleUserHomeDir, leakDetectionJar);
            if (!leakDetectorJar.exists()) {
                // Need to download the jar
                GFileUtils.parentMkdirs(leakDetectorJar);
                GFileUtils.touch(leakDetectorJar);
                try (OutputStream out = Files.newOutputStream(leakDetectorJar.toPath());
                     InputStream in = new URL(leakDetectorUrl).openStream()) {
                    ByteStreams.copy(in, out);
                } catch (IOException e) {
                    throw new RuntimeException("Couldn't download " + leakDetectorUrl, e);
                }
            }

            String joinedArgs;
            if (args.length == 0) {
                // Default arguments to pass to the java agent
                joinedArgs = "http=19999";
            } else {
                joinedArgs = Joiner.on(',').join(args);
            }
            withBuildJvmOpts("-javaagent:" + leakDetectorJar + "=" + joinedArgs);
        });

        return this;
    }

    @Override
    public GradleExecuter useOnlyRequestedJvmOpts() {
        useOnlyRequestedJvmOpts = true;
        return this;
    }

    @Override
    public GradleExecuter withDefaultCharacterEncoding(String defaultCharacterEncoding) {
        this.defaultCharacterEncoding = defaultCharacterEncoding;
        return this;
    }

    @Override
    public GradleExecuter withDefaultLocale(Locale defaultLocale) {
        this.defaultLocale = defaultLocale;
        return this;
    }

    @Override
    public TestDirectoryProvider getTestDirectoryProvider() {
        return testDirectoryProvider;
    }

    @Override
    public GradleExecuter expectDeprecationWarning() {
        return expectDeprecationWarnings(1);
    }

    @Override
    public GradleExecuter expectDeprecationWarning(ExpectedDeprecationWarning warning) {
        expectedDeprecationWarnings.add(warning);
        return this;
    }

    @Override
    public GradleExecuter noDeprecationChecks() {
        checkDeprecations = false;
        return this;
    }

    @Override
    public GradleExecuter noDaemonCrashChecks() {
        checkDaemonCrash = false;
        return this;
    }

    @Override
    public GradleExecuter withEagerClassLoaderCreationCheckDisabled() {
        eagerClassLoaderCreationChecksOn = false;
        return this;
    }

    @Override
    public GradleExecuter withStackTraceChecksDisabled() {
        stackTraceChecksOn = false;
        return this;
    }

    @Override
    public GradleExecuter withJdkWarningChecksEnabled() {
        jdkWarningChecksOn = true;
        return this;
    }

    @Override
    public GradleExecuter noExtraLogging() {
        this.allowExtraLogging = false;
        return this;
    }

    @Override
    public GradleExecuter startBuildProcessInDebugger(Action<JavaDebugOptionsInternal> action) {
        debug.setEnabled(true);
        action.execute(debug);
        return this;
    }

    @Override
    public GradleExecuter withProfiler(String args) {
        profiler = args;
        return this;
    }

    @Override
    public GradleExecuter expectDeprecationWarnings(int count) {
        Preconditions.checkState(expectedGenericDeprecationWarnings == 0, "expected deprecation count is already set for this execution");
        Preconditions.checkArgument(count > 0, "expected deprecation count must be positive");
        expectedGenericDeprecationWarnings = count;
        return this;
    }

    @Override
    public GradleDistribution getDistribution() {
        return distribution;
    }

    @Override
    public boolean isDebug() {
        return debug.isEnabled();
    }

    @Override
    public boolean isProfile() {
        return !profiler.isEmpty();
    }

    protected GradleHandle createGradleHandle() {
        throw new UnsupportedOperationException(String.format("%s does not support running asynchronously.", getClass().getSimpleName()));
    }

    protected GradleExecuter configureConsoleCommandLineArgs() {
        if (consoleAttachment == ConsoleAttachment.NOT_ATTACHED) {
            return this;
        } else {
            return withCommandLineGradleOpts(consoleAttachment.getConsoleMetaData().getCommandLineArgument());
        }
    }

    protected CliDaemonArgument resolveCliDaemonArgument() {
        for (int i = args.size() - 1; i >= 0; i--) {
            final String arg = args.get(i);
            if (arg.equals("--daemon")) {
                return DAEMON;
            }
            if (arg.equals("--no-daemon")) {
                return NO_DAEMON;
            }
            if (arg.equals("--foreground")) {
                return FOREGROUND;
            }
        }
        return NOT_DEFINED;
    }

}