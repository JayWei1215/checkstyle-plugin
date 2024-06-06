package org.gradle.internal;

import org.gradle.api.Task;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.CheckstyleReports;
import org.gradle.api.reporting.CustomizableHtmlReport;
import org.gradle.api.reporting.SingleFileReport;
import org.gradle.api.reporting.internal.CustomizableHtmlReportImpl;
import org.gradle.api.reporting.internal.TaskGeneratedSingleFileReport;
import org.gradle.api.reporting.internal.TaskReportContainer;

import javax.inject.Inject;

public class CheckstyleReportsImpl extends TaskReportContainer<SingleFileReport> implements CheckstyleReports {
    @Inject
    public CheckstyleReportsImpl(Task task, CollectionCallbackActionDecorator callbackActionDecorator) {
        super(SingleFileReport.class, task, callbackActionDecorator);

        add(CustomizableHtmlReportImpl.class, "html", task);
        add(TaskGeneratedSingleFileReport.class, "xml", task);
        add(TaskGeneratedSingleFileReport.class, "sarif", task);
    }

    @Override
    public CustomizableHtmlReport getHtml() {
        return withType(CustomizableHtmlReport.class).getByName("html");
    }

    @Override
    public SingleFileReport getXml() {
        return getByName("xml");
    }

    @Override
    public SingleFileReport getSarif() {
        return getByName("sarif");
    }
}
