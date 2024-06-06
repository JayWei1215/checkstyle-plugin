package org.gradle;

import org.gradle.api.tasks.SourceSet;

import java.io.File;
import java.util.Collection;

public abstract class CodeQualityExtension {

    private String toolVersion;
    private Collection<SourceSet> sourceSets;
    private boolean ignoreFailures;
    private File reportsDir;

    /**
     * The version of the code quality tool to be used.
     */
    public String getToolVersion() {
        return toolVersion;
    }

    /**
     * The version of the code quality tool to be used.
     */
    public void setToolVersion(String toolVersion) {
        this.toolVersion = toolVersion;
    }

    /**
     * The source sets to be analyzed as part of the <code>check</code> and <code>build</code> tasks.
     */
    public Collection<SourceSet> getSourceSets() {
        return sourceSets;
    }

    /**
     * The source sets to be analyzed as part of the <code>check</code> and <code>build</code> tasks.
     */
    public void setSourceSets(Collection<SourceSet> sourceSets) {
        this.sourceSets = sourceSets;
    }

    /**
     * Whether to allow the build to continue if there are warnings.
     *
     * Example: ignoreFailures = true
     */
    public boolean isIgnoreFailures() {
        return ignoreFailures;
    }

    /**
     * Whether to allow the build to continue if there are warnings.
     *
     * Example: ignoreFailures = true
     */
    public void setIgnoreFailures(boolean ignoreFailures) {
        this.ignoreFailures = ignoreFailures;
    }

    /**
     * The directory where reports will be generated.
     */
    public File getReportsDir() {
        return reportsDir;
    }

    /**
     * The directory where reports will be generated.
     */
    public void setReportsDir(File reportsDir) {
        this.reportsDir = reportsDir;
    }
}
