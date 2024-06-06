package org.gradle;

import org.gradle.api.Incubating;
import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.reporting.CustomizableHtmlReport;
import org.gradle.api.reporting.ReportContainer;
import org.gradle.api.reporting.SingleFileReport;
import org.gradle.api.tasks.Internal;

/**
 * The reporting configuration for the {@link Checkstyle} task.
 */
public interface CheckstyleReports extends ReportContainer<SingleFileReport> {
    /**
     * The checkstyle HTML report.
     * <p>
     * This report IS enabled by default.
     * <p>
     * Enabling this report will also cause the XML report to be generated, as the HTML is derived from the XML.
     *
     * @return The checkstyle HTML report
     * @since 2.10
     */
    @Internal
    CustomizableHtmlReport getHtml();

    /**
     * The checkstyle XML report
     * <p>
     * This report IS enabled by default.
     *
     * @return The checkstyle XML report
     */
    @Internal
    SingleFileReport getXml();

    /**
     * The checkstyle SARIF report
     * <p>
     * This report is NOT enabled by default.
     *
     * @return The checkstyle SARIF report
     * @since 8.1
     */
    @Internal
    @Incubating
    SingleFileReport getSarif();
}
