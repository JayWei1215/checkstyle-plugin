package org.gradle.internal;

import org.gradle.api.Action;
import org.gradle.api.internal.project.antbuilder.AntBuilderDelegate;
import org.gradle.api.plugins.quality.internal.AntWorkAction;
import org.gradle.api.plugins.quality.internal.CheckstyleActionParameters;
import org.gradle.api.plugins.quality.internal.CheckstyleInvoker;

public abstract class CheckstyleAction extends AntWorkAction<CheckstyleActionParameters> {

    @Override
    protected String getActionName() {
        return "checkstyle";
    }

    @Override
    protected Action<AntBuilderDelegate> getAntAction() {
        return new CheckstyleInvoker(getParameters());
    }
}
