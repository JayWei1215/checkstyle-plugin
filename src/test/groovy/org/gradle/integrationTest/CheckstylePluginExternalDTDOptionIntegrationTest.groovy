package org.gradle.integrationTest

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

class CheckstylePluginExternalDTDOptionIntegrationTest extends Specification {

    @TempDir
    File testProjectDir
    File buildFile
    File settingsFile

    def setup() {
        buildFile = new File(testProjectDir, 'build.gradle')
        settingsFile = new File(testProjectDir, 'settings.gradle')
        writeInitialBuildFile()
        writeProjectFiles()
    }

    def "can use enable_external_dtd_load feature on extension"() {
        given:
        buildFile << """
            checkstyle {
                enableExternalDtdLoad = true
            }
        """

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments('checkstyleMain')
                .withPluginClasspath()
                .buildAndFail()

        then:
        assertFailedWithCheckstyleVerificationErrors(result)
    }

    def "can use enable_external_dtd_load feature on task to override extension value for a task"() {
        given:
        buildFile << """
            checkstyle {
                enableExternalDtdLoad = true
            }
            tasks.withType(Checkstyle) {
                enableExternalDtdLoad = true
            }
        """

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments('checkstyleMain')
                .withPluginClasspath()
                .buildAndFail()

        then:
        assertFailedWithCheckstyleVerificationErrors(result)
    }

    def "if use enable_external_dtd_load feature NOT enabled, error if feature used in rules XML"() {
        setup:
        buildFile << """
            checkstyle {
                enableExternalDtdLoad = false
            }
        """

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments('checkstyleMain')
                .withPluginClasspath()
                .buildAndFail()

        then:
        assertFailedWithDtdProcessingError(result)
    }

    def "enable_external_dtd_load feature NOT enabled by default"() {
        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments('checkstyleMain')
                .withPluginClasspath()
                .buildAndFail()

        then:
        assertFailedWithDtdProcessingError(result)
    }

    private void assertFailedWithCheckstyleVerificationErrors(def result) {
        assert result.output.contains("Checkstyle rule violations were found. See the report at:")
        assert result.output.contains("Checkstyle files with violations: 1")
        assert result.output.contains("Checkstyle violations by severity: [error:1]")
        assert new File(testProjectDir, "build/reports/checkstyle/main.xml").text.contains("org/sample/MyClass")
    }

    private void assertFailedWithDtdProcessingError(def result) {
        assert result.output.contains("A failure occurred while executing org.gradle.internal.CheckstyleAction")
        assert result.output.contains("An unexpected error occurred configuring and executing Checkstyle.")
        assert result.output.contains("java.lang.NullPointerException")
    }

    private void writeInitialBuildFile() {
        settingsFile << """
            pluginManagement {
                repositories {
                    mavenLocal()
                    gradlePluginPortal()
                }
            }
            rootProject.name = 'testProjectDir'
        """

        buildFile << """
            plugins {
                id 'java'
                id 'org.gradle.checkstyle-plugin' version '1.0-SNAPSHOT'
            }
            
            repositories {
                mavenLocal()
                mavenCentral()
            }
        """
    }

    private void writeProjectFiles() {
        new File(testProjectDir, 'src/main/java/org/sample').mkdirs()
        new File(testProjectDir, 'src/main/java/org/sample/MyClass.java').text = multilineJavaClass()
        new File(testProjectDir, 'config/checkstyle').mkdirs()
        new File(testProjectDir, 'config/checkstyle/checkstyle-common.xml').text = checkStyleCommonXml()
        new File(testProjectDir, 'config/checkstyle/checkstyle.xml').text = checkStyleMainXml()
    }

    private String multilineJavaClass() {
        return """
            package org.sample;

            public class MyClass {
                public static void main(String[] args) {
                    System.out.println("Hello, world!");
                }
            }
        """
    }

    private String checkStyleCommonXml() {
        return """
            <module name="FileLength">
                <property name="max" value="1"/>
            </module>
        """
    }

    private String checkStyleMainXml() {
        return """<?xml version="1.0"?>
            <!DOCTYPE module PUBLIC
                      "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
                      "https://checkstyle.org/dtds/configuration_1_3.dtd" [
                <!ENTITY common SYSTEM "checkstyle-common.xml">
            ]>
            <module name="Checker">
                &common;
                <module name="TreeWalker">
                    <module name="MemberName">
                        <property name="format" value="^[a-z][a-zA-Z]+\$"/>
                    </module>
                </module>
            </module>
        """
    }
}
