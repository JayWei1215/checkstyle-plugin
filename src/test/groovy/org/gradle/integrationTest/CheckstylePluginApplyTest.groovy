package org.gradle.integrationTest

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification

class CheckstylePluginApplyTest extends Specification {
    def "checkstyle plugin applies correctly"() {
        given:
        File projectDir = new File("build/tmp/testProject")
        projectDir.mkdirs()
        new File(projectDir, 'build.gradle') << """
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

        new File(projectDir, 'settings.gradle') << """
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
    }
}
rootProject.name = 'testProject'
        """

        new File(projectDir, 'src/main/java').mkdirs()
        new File(projectDir, 'src/main/java/Example.java') << """
            public class Example {
                public static void main(String[] args) {
                    System.out.println("Hello, world!");
                }
            }
        """

        new File(projectDir, 'config/checkstyle').mkdirs()
        new File(projectDir, 'config/checkstyle/checkstyle.xml') << """
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
                .withProjectDir(projectDir)
                .withArguments('checkstyleMain')
                .withPluginClasspath()
                .build()

        then:
        result.output.contains('Welcome to the checkstyle plugin!')
    }
}
