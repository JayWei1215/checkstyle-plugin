package org.gradle.integrationTest

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

class CheckstylePluginIntegrationTest extends Specification {
    @TempDir File testProjectDir
    File settingsFile
    File buildFile

    def setup() {
        settingsFile = new File(testProjectDir, 'settings.gradle')
        buildFile = new File(testProjectDir, 'build.gradle')
    }

    def "checkstyle plugin applies correctly"() {
        given:
        buildFile << """
plugins {
    id 'java'
    id 'org.gradle.checkstyle-plugin' version '1.0-SNAPSHOT'
}

group = 'org.gradle'
version = '1.0-SNAPSHOT'

repositories {
    mavenLocal()
    mavenCentral()
}

checkstyle {
    toolVersion = '8.8'
}

test {
    useJUnitPlatform()
}
        """

        settingsFile << """
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
    }
}
rootProject.name = 'testProjectDir'
        """

        new File(testProjectDir, 'src/main/java').mkdirs()
        new File(testProjectDir, 'src/main/java/Example.java') << """
            public class Example {
                public static void main(String[] args) {
                    System.out.println("Hello, world!");
                }
            }
        """

        new File(testProjectDir, 'config/checkstyle').mkdirs()
        new File(testProjectDir, 'config/checkstyle/checkstyle.xml') << """
            <!DOCTYPE module PUBLIC
                "-//Puppy Crawl//DTD Check Configuration 1.3//EN"
                "https://checkstyle.org/dtds/configuration_1_3.dtd">
            <module name="Checker">
                <module name="TreeWalker">
                    <module name="EmptyBlock"/>
                </module>
            </module>
        """

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments('checkstyleMain')
                .withPluginClasspath()
                .build()

        then:
        result.output.contains('Welcome to the checkstyle plugin!')
    }
}
