package org.gradle.internal;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.plugins.quality.internal.AntWorkParameters;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

public interface CheckstyleActionParameters extends AntWorkParameters {
    RegularFileProperty getConfig();

    ConfigurableFileCollection getSource();

    Property<Integer> getMaxErrors();

    Property<Integer> getMaxWarnings();

    Property<Boolean> getIgnoreFailures();

    DirectoryProperty getConfigDirectory();

    Property<Boolean> getShowViolations();

    Property<Boolean> getIsXmlRequired();

    Property<Boolean> getIsHtmlRequired();

    Property<Boolean> getIsSarifRequired();

    RegularFileProperty getXmlOuputLocation();

    RegularFileProperty getHtmlOuputLocation();

    RegularFileProperty getSarifOutputLocation();

    DirectoryProperty getTemporaryDir();

    MapProperty<String, Object> getConfigProperties();

    Property<String> getStylesheetString();
}
