package org.gradle.integrationTest

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.GradleRunner
import org.hamcrest.Matcher
import spock.lang.Specification
import spock.lang.TempDir

import static org.gradle.util.internal.TextUtil.getPlatformLineSeparator
import static org.hamcrest.CoreMatchers.containsString

class CheckstylePluginMultiProjectTest extends Specification {
    @TempDir
    File testProjectDir
    File buildFile
    File settingsFile

    def setup() {
        settingsFile = new File(testProjectDir, 'settings.gradle')
        buildFile = new File(testProjectDir, 'build.gradle')
    }

    def "configures checkstyle extension to read config from root project in a single project build"() {
        given:
        writeBuildFile(buildFile, javaProjectUsingCheckstyle())
        writeJavaClass('src/main/java/Dummy.java', javaClassWithNewLineAtEnd())
        writeConfigFile('config/checkstyle/checkstyle.xml', simpleCheckStyleConfig())

        when:
        BuildResult result = runGradleTask('checkstyleMain')

        then:
        result.task(":checkstyleMain").outcome == TaskOutcome.SUCCESS
        checkStyleReportFile(testProjectDir).exists()
    }

    def "fails when root project does not contain config in default location"() {
        given:
        settingsFile.text = "include 'child'"

        def childDir = new File(testProjectDir, 'child')
        childDir.mkdirs()
        def childBuildFile = new File(testProjectDir, 'child/build.gradle')

        writeBuildFile(childBuildFile, javaProjectUsingCheckstyle())
        writeJavaClass('child/src/main/java/Dummy.java', javaClassWithNewLineAtEnd())
        writeConfigFile('child/config/checkstyle/checkstyle.xml', simpleCheckStyleConfig())

        when:
        BuildResult result = runFailedGradleTask(':child:checkstyleMain')

        then:
        result.task(":child:checkstyleMain").outcome == TaskOutcome.FAILED
        !checkStyleReportFile(childDir).exists()
    }

    def "configures checkstyle extension to read config from root project in a flat multi-project build"() {
        given:
        settingsFile.text = "include 'child:grand'"
        def grandDir = new File(testProjectDir, 'child/grand')
        grandDir.mkdirs()
        def grandBuildFile = new File(testProjectDir, 'child/grand/build.gradle')
        writeBuildFile(grandBuildFile, javaProjectUsingCheckstyle())
        writeJavaClass('child/grand/src/main/java/Dummy.java', javaClassWithNewLineAtEnd())
        writeConfigFile('config/checkstyle/checkstyle.xml', simpleCheckStyleConfig())

        when:
        BuildResult result = runGradleTask(':child:grand:checkstyleMain')

        then:
        result.task(":child:grand:checkstyleMain").outcome == TaskOutcome.SUCCESS
        checkStyleReportFile(grandDir).exists()
    }

    def "configures checkstyle extension to read config from root project in a deeply nested multi-project build"() {
        given:
        settingsFile.text = "include 'a:b:c'"
        def cDir = new File(testProjectDir, 'a/b/c')
        cDir.mkdirs()
        def cBuildFile = new File(testProjectDir, 'a/b/c/build.gradle')
        writeBuildFile(cBuildFile, javaProjectUsingCheckstyle())
        writeJavaClass('a/b/c/src/main/java/Dummy.java', javaClassWithNewLineAtEnd())
        writeConfigFile('config/checkstyle/checkstyle.xml', simpleCheckStyleConfig())

        when:
        BuildResult result = runGradleTask(':a:b:c:checkstyleMain')

        then:
        result.task(":a:b:c:checkstyleMain").outcome == TaskOutcome.SUCCESS
        checkStyleReportFile(cDir).exists()
    }

    def "configures checkstyle extension to read config from root project in a multi-project build even if sub project config is available"() {
        given:
        settingsFile.text = "include 'child:grand'"
        def grandDir = new File(testProjectDir, 'child/grand')
        grandDir.mkdirs()
        def grandBuildFile = new File(testProjectDir, 'child/grand/build.gradle')
        writeBuildFile(grandBuildFile, javaProjectUsingCheckstyle())
        writeJavaClass('child/grand/src/main/java/Dummy.java', javaClassWithNewLineAtEnd())
        writeConfigFile('child/grand/config/checkstyle/checkstyle.xml', invalidCheckStyleConfig())
        writeConfigFile('config/checkstyle/checkstyle.xml', simpleCheckStyleConfig())

        when:
        BuildResult result = runGradleTask(':child:grand:checkstyleMain')

        then:
        result.task(":child:grand:checkstyleMain").outcome == TaskOutcome.SUCCESS
        checkStyleReportFile(grandDir).exists()
    }

    def "explicitly configures checkstyle extension to point to config directory"() {
        given:
        settingsFile.text = "include 'child'"
        def childDir = new File(testProjectDir, 'child')
        childDir.mkdirs()
        def childBuildFile = new File(testProjectDir, 'child/build.gradle')
        writeBuildFile(childBuildFile, javaProjectUsingCheckstyle())
        appendToBuildFile(childBuildFile, """
            checkstyle {
                configDirectory = file('config/checkstyle')
            }
        """)
        writeJavaClass('child/src/main/java/Dummy.java', javaClassWithNewLineAtEnd())
        writeConfigFile('child/config/checkstyle/checkstyle.xml', simpleCheckStyleConfig())

        when:
        BuildResult result = runGradleTask(':child:checkstyleMain')

        then:
        result.task(":child:checkstyleMain").outcome == TaskOutcome.SUCCESS
        checkStyleReportFile(childDir).exists()
    }

    def "configures checkstyle extension to read config from root project with isolated projects"() {
        given:
        settingsFile.text = "include 'child:grand'"
        def grandDir = new File(testProjectDir, 'child/grand')
        grandDir.mkdirs()
        def grandBuildFile = new File(testProjectDir, 'child/grand/build.gradle')
        writeBuildFile(grandBuildFile, javaProjectUsingCheckstyle())
        writeJavaClass('child/grand/src/main/java/Dummy.java', javaClassWithNewLineAtEnd())
        writeConfigFile('config/checkstyle/checkstyle.xml', simpleCheckStyleConfig())

        when:
        BuildResult result = runGradleTask(':child:grand:checkstyleMain')

        then:
        result.task(":child:grand:checkstyleMain").outcome == TaskOutcome.SUCCESS
        checkStyleReportFile(grandDir).exists()
    }

    private void writeBuildFile(File file, String content) {
        file.text = content
    }

    private void appendToBuildFile(File file, String content) {
        file << content
    }

    private void writeJavaClass(String path, String content) {
        def javaFile = new File(testProjectDir, path)
        javaFile.parentFile.mkdirs()
        javaFile.text = content
    }

    private void writeConfigFile(String path, String content) {
        def configFile = new File(testProjectDir, path)
        configFile.parentFile.mkdirs()
        configFile.text = content
    }

    private BuildResult runGradleTask(String... tasks) {
        return GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments(tasks)
                .withPluginClasspath()
                .build()
    }

    private BuildResult runFailedGradleTask(String... tasks) {
        return GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments(tasks)
                .withPluginClasspath()
                .buildAndFail()
    }

    private static String simpleCheckStyleConfig() {
        """
<!DOCTYPE module PUBLIC
        "-//Puppy Crawl//DTD Check Configuration 1.2//EN"
        "http://www.puppycrawl.com/dtds/configuration_1_2.dtd">
<module name="Checker">
    <module name="NewlineAtEndOfFile"/>
</module>
        """
    }

    private static String invalidCheckStyleConfig() {
        'INVALID AND SHOULD NEVER BE READ'
    }

    private static File checkStyleReportFile(File projectDir) {
        new File(projectDir, 'build/reports/checkstyle/main.html')
    }

    private static String javaProjectUsingCheckstyle() {
        """
            plugins {
                id 'java'
                id 'org.gradle.checkstyle-plugin' version '1.0-SNAPSHOT'
            }

            ${mavenCentralRepository()}
        """
    }

    private static String javaClassWithNewLineAtEnd() {
        "public class Dummy {}${System.lineSeparator()}"
    }

    private static String mavenCentralRepository() {
        """
repositories {
    mavenLocal()
    mavenCentral()
}
        """
    }

}
