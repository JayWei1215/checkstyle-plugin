package org.gradle.integrationTest

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

class CheckstylePluginClasspathIntegrationTest extends Specification {
    @TempDir File testProjectDir
    File settingsFile
    File buildFile

    def setup() {
        settingsFile = new File(testProjectDir, 'settings.gradle')
        buildFile = new File(testProjectDir, 'build.gradle')

        writeBuildFiles()
        writeConfigFile()
        goodCode()
    }

    def "accepts throwing exception from other project"() {
        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments('checkstyleMain')
                .withPluginClasspath()
                .withDebug(true)
                .build()

        then:
        result.output.contains('BUILD SUCCESSFUL')
    }

    private void writeBuildFiles() {
        settingsFile << """
include "api"
include "client"
        """

        buildFile << """
buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
    }
    dependencies {
        classpath "org.gradle:checkstyle-plugin:1.0-SNAPSHOT"
    }
}

subprojects {
    apply plugin: 'java'
    apply plugin: 'org.gradle.checkstyle-plugin'

    repositories {
        mavenLocal()
        mavenCentral()
    }
    
    checkstyle {
        toolVersion = '8.8'
        configFile rootProject.file("checkstyle.xml")
    }
}

project("client") {
    dependencies {
        implementation project(":api")
    }
}
        """
    }

    private void writeConfigFile() {
        new File(testProjectDir, "checkstyle.xml").with {
            parentFile.mkdirs()
            it << """
<!DOCTYPE module PUBLIC
        "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
        "https://checkstyle.org/dtds/configuration_1_3.dtd">
<module name="Checker">
    <module name="TreeWalker">
        <module name="JavadocMethod"/>
    </module>
</module>
            """
        }
    }

    private void goodCode() {
        new File(testProjectDir, "api/src/main/java/org/gradle/FooException.java").with {
            parentFile.mkdirs()
            it << """
package org.gradle;

class FooException extends Exception { }
            """
        }

        new File(testProjectDir, "client/src/main/java/org/gradle/Iface.java").with {
            parentFile.mkdirs()
            it << """
package org.gradle;

interface Iface {
    /**
     * Method Description.
     *
     * @throws FooException whenever
     * @throws IllegalArgumentException otherwise
     */
    void foo() throws FooException, IllegalArgumentException;
}
            """
        }
    }
}
