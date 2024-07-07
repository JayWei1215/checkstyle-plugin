package org.gradle

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir


class CheckstylePluginDependenciesIntegrationTest extends Specification {
    @TempDir File testProjectDir
    File buildFile

    def setup() {
        buildFile = new File(testProjectDir, 'build.gradle')

        writeBuildFile()
        writeConfigFile()
        badCode()
    }

    def "allows configuring tool dependencies explicitly"() {
        given: "Default locale is set to English"
        defaultLocale('en')

        expect: "Checkstyle configuration is correct"
        def result1 = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("dependencies", "--configuration", "checkstyle")
                .withPluginClasspath()
                .build()
        result1.output.contains("com.puppycrawl.tools:checkstyle:")

        when: "Checkstyle dependency version is downgraded"
        addCheckstyleDependency()

        then: "Checkstyle configuration reflects the downgraded version"
        def result3 = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("dependencies", "--configuration", "checkstyle")
                .withPluginClasspath()
                .build()
        result3.output.contains("com.puppycrawl.tools:checkstyle:5.5")
    }

    private void writeBuildFile() {
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

allprojects {
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
        """
    }

    private void addCheckstyleDependency() {
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

allprojects {
    apply plugin: 'java'
    apply plugin: 'org.gradle.checkstyle-plugin'

    dependencies {
        checkstyle "com.puppycrawl.tools:checkstyle:5.5"
    }
    
    repositories {
        mavenLocal()
        mavenCentral()
    }
    
    checkstyle {
        toolVersion = '8.8'
        configFile rootProject.file("checkstyle.xml")
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

    private void badCode() {
        new File(testProjectDir, "src/main/java/org/gradle/class1.java").with {
            parentFile.mkdirs()
            it << """
package org.gradle; class class1 { }
            """
        }
    }

    private void defaultLocale(String defaultLocale) {
        Locale.setDefault(new Locale(defaultLocale))
    }
}
