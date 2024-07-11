package org.gradle.integrationTest

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir


class CheckstylePluginVersionIntegrationTest extends Specification {

    @TempDir
    File testProjectDir
    File buildFile

    void setup() {
        buildFile = new File(testProjectDir, 'build.gradle')
        writeBuildFile()
        writeConfigFile()
    }

    def "analyze good code"() {
        given:
        goodCode()

        when:
        BuildResult result = runGradleTask('check')

        then:
        result.task(":check").outcome == TaskOutcome.SUCCESS
        expect:
        assertFileDoesNotExist("build/reports/checkstyle/main.sarif")
        assertFileDoesNotExist("build/reports/checkstyle/test.sarif")
        assertFileContents("build/reports/checkstyle/main.xml", "org/gradle/Class1")
        assertFileContents("build/reports/checkstyle/main.xml", "org/gradle/Class2")
        assertFileContents("build/reports/checkstyle/test.xml", "org/gradle/TestClass1")
        assertFileContents("build/reports/checkstyle/test.xml", "org/gradle/TestClass2")

        assertFileExists("build/reports/checkstyle/main.html")
        assertFileContents("build/reports/checkstyle/main.html", "No violations were found.")
    }

    def "supports fallback when configDirectory does not exist"() {
        given:
        goodCode()
        buildFile << """
            checkstyle {
                config = project.resources.text.fromString('''<!DOCTYPE module PUBLIC "-//Puppy Crawl//DTD Check Configuration 1.3//EN"
                        "https://www.puppycrawl.com/dtds/configuration_1_3.dtd">
                <module name="Checker">

                    <module name="FileTabCharacter"/>

                    <module name="SuppressionFilter">
                        <property name="file" value="\${config_loc}/suppressions.xml" default=""/>
                        <property name="optional" value="true"/>
                    </module>
                </module>''')

                configDirectory = file("config/does-not-exist")
            }
        """

        when:
        BuildResult result = runGradleTask('check')

        then:
        result.task(":check").outcome == TaskOutcome.SUCCESS
    }

    def "changes to files in config dir causes task to be out-of-date"() {
        given:
        goodCode()

        when:
        BuildResult result = runGradleTask('check')

        then:
        result.task(":check").outcome == TaskOutcome.SUCCESS

        when:
        BuildResult result1 = runGradleTask('check')

        then:
        result1.task(":checkstyleMain").outcome == TaskOutcome.UP_TO_DATE

        when:
        new File(testProjectDir, "config/checkstyle/new-file.xml").createNewFile()
        BuildResult result2 = runGradleTask('check')

        then:
        result2.task(':checkstyleMain').outcome == TaskOutcome.SUCCESS
    }

    def "analyze bad code"() {
        given:
        defaultLanguage('en')
        badCode()

        when:
        runGradleTaskAndFail('check')

        then:
        assertFileDoesNotExist("build/reports/checkstyle/main.sarif")
        assertFileContents("build/reports/checkstyle/main.xml", "org/gradle/class1")
        assertFileContents("build/reports/checkstyle/main.xml","org/gradle/class2")

        assertFileContents("build/reports/checkstyle/main.html", "Checkstyle Violations")
    }

    def "can analyse a single source file"() {
        given:
        buildFile << """
            checkstyleMain.source = ['src/main/java/org/gradle/Class1.java']
        """
        goodCode()

        expect:
        runGradleTask('check').task(':check').outcome == TaskOutcome.SUCCESS
        assertFileContents("build/reports/checkstyle/main.xml", "org/gradle/Class1")
    }

    def "can suppress console output"() {

        given:
        defaultLanguage('en')
        badCode()

        when:
        BuildResult result = runGradleTaskAndFail('check')

        then:
        result.output.contains("Execution failed for task ':checkstyleMain'.")
        result.output.contains("Checkstyle rule violations were found. See the report at:")
        result.output.contains("Name 'class1' must match pattern")
        assertFileContents("build/reports/checkstyle/main.xml", "org/gradle/class1")
        assertFileContents("build/reports/checkstyle/main.xml", "org/gradle/class2")

        assertFileContents("build/reports/checkstyle/main.html", "Checkstyle Violations")
    }

    def "can ignore failures"() {
        given:
        badCode()
        buildFile << """
            checkstyle {
                ignoreFailures = true
            }
        """

        when:
        BuildResult result = runGradleTask('check')

        then:
        result.task(':check').outcome == TaskOutcome.SUCCESS
        result.output.contains("Checkstyle rule violations were found. See the report at:")
        result.output.contains("Checkstyle files with violations: 2")
        result.output.contains("Checkstyle violations by severity: [error:2]")
        assertFileContents("build/reports/checkstyle/main.xml", "org/gradle/class1")
        assertFileContents("build/reports/checkstyle/main.xml", "org/gradle/class2")

        assertFileContents("build/reports/checkstyle/main.html", "Checkstyle Violations")
    }

    def "can ignore maximum number of errors"() {
        given:
        badCode()
        buildFile << """
            checkstyle {
                maxErrors = 2
            }
        """

        when:
        BuildResult result = runGradleTask('check')

        then:
        result.task(':check').outcome == TaskOutcome.SUCCESS
        result.output.contains("Checkstyle rule violations were found. See the report at:")
        result.output.contains("Checkstyle files with violations: 2")
        result.output.contains("Checkstyle violations by severity: [error:2]")

        assertFileContents("build/reports/checkstyle/main.xml", "org/gradle/class1")
        assertFileContents("build/reports/checkstyle/main.xml", "org/gradle/class2")

        assertFileContents("build/reports/checkstyle/main.html", "Checkstyle Violations")
    }

    def "can fail on maximum number of warnings"() {
        given:
        writeConfigFileWithWarnings()
        badCode()
        buildFile << """
            checkstyle {
                maxWarnings = 1
            }
        """

        when:
        BuildResult result = runGradleTaskAndFail('check')

        then:
        result.output.contains("Execution failed for task ':checkstyleMain'.")
        result.output.contains("Checkstyle rule violations were found. See the report at:")
        result.output.contains("Checkstyle files with violations: 2")
        result.output.contains("Checkstyle violations by severity: [warning:2]")
        assertFileContents("build/reports/checkstyle/main.xml", "org/gradle/class1")
        assertFileContents("build/reports/checkstyle/main.xml", "org/gradle/class2")

        assertFileContents("build/reports/checkstyle/main.html", "Checkstyle Violations")
    }

    def "is incremental"() {
        given:
        goodCode()

        expect:
        runGradleTask('checkstyleMain').task(':checkstyleMain').outcome == TaskOutcome.SUCCESS

        when:
        BuildResult result = runGradleTask('checkstyleMain',"-Dorg.gradle.internal.volatile-performance.experiment=true")

        then:
        result.task(':checkstyleMain').outcome == TaskOutcome.UP_TO_DATE

        when:
        new File(testProjectDir, 'src/main/java/org/gradle').mkdirs()
        new File(testProjectDir, 'src/main/java/org/gradle/Class1.java').text = """
            package org.gradle;\nclass Class1 {}\n\n\n
        """
        new File(testProjectDir, 'src/main/java/org/gradle/Class2.java').text = """
            package org.gradle;\nclass Class2 {}\n\n\n
        """
        BuildResult result1 = runGradleTask('checkstyleMain')

        then:
        result1.task(':checkstyleMain').outcome == TaskOutcome.SUCCESS
    }

    private void goodCode() {
        new File(testProjectDir, 'src/main/java/org/gradle').mkdirs()
        new File(testProjectDir, 'src/main/java/org/gradle/Class1.java') << """
            package org.gradle;\nclass Class1 {}
        """
        new File(testProjectDir, 'src/main/java/org/gradle/Class2.java') << """
            package org.gradle;\nclass Class2 {}
        """
        new File(testProjectDir, 'src/test/java/org/gradle').mkdirs()
        new File(testProjectDir, 'src/test/java/org/gradle/TestClass1.java') << """
            package org.gradle;\nclass TestClass1 {}
        """
        new File(testProjectDir, 'src/test/java/org/gradle/TestClass2.java') << """
            package org.gradle;\nclass TestClass2 {}
        """
    }

    private void badCode() {
        new File(testProjectDir, 'src/main/java/org/gradle').mkdirs()
        new File(testProjectDir, 'src/main/java/org/gradle/class1.java') << """
            package org.gradle;\nclass class1 {}
        """
        new File(testProjectDir, 'src/main/java/org/gradle/class2.java') << """
            package org.gradle;\nclass class2 {}
        """
    }

    private void badResources() {
        new File(testProjectDir, 'src/main/resources').mkdirs()
        new File(testProjectDir, 'src/main/resources/bad.properties') << """
            foo=bar
        """
    }

    private void writeConfigFile() {
        new File(testProjectDir, 'config/checkstyle').mkdirs()
        new File(testProjectDir, 'config/checkstyle/checkstyle.xml') << """
            <!DOCTYPE module PUBLIC
        "-//Puppy Crawl//DTD Check Configuration 1.2//EN"
        "http://www.puppycrawl.com/dtds/configuration_1_2.dtd">
        <module name="Checker">
            <module name="SuppressionFilter">
                <property name="file" value="\${config_loc}/suppressions.xml"/>
            </module>
            <module name="TreeWalker">
                <module name="TypeName"/>
            </module>
        </module>
        """
        new File(testProjectDir, 'config/checkstyle/suppressions.xml') << """
        <!DOCTYPE suppressions PUBLIC
        "-//Puppy Crawl//DTD Suppressions 1.1//EN"
        "http://www.puppycrawl.com/dtds/suppressions_1_1.dtd">

        <suppressions>  
            <suppress checks="TypeName"
                  files="bad_name.java"/>
        </suppressions>
        """
    }

    private void writeConfigFileForResources() {
        new File(testProjectDir, 'config/checkstyle').mkdirs()
        def file = new File(testProjectDir, 'config/checkstyle/checkstyle.xml')
        file.text = """
            <!DOCTYPE module PUBLIC "-//Puppy Crawl//DTD Check Configuration 1.3//EN"
                "https://www.puppycrawl.com/dtds/configuration_1_3.dtd">
            <module name="Checker">
                <module name="TreeWalker">
                    <module name="PropertiesLoader">
                        <property name="fileExtensions" value="properties"/>
                    </module>
                </module>
            </module>
        """
    }

    private void writeConfigFileWithWarnings() {
        new File(testProjectDir, 'config/checkstyle').mkdirs()
        def file = new File(testProjectDir, 'config/checkstyle/checkstyle.xml')
        file.text = """
            <!DOCTYPE module PUBLIC
            "-//Puppy Crawl//DTD Check Configuration 1.3//EN"
            "http://www.puppycrawl.com/dtds/configuration_1_3.dtd">
            <module name="Checker">
                <property name="severity" value="warning"/>
                <module name="TreeWalker">
                    <module name="TypeName"/>
                </module>
            </module>
        """
    }

    private void writeBuildFile() {
        buildFile << '''
            plugins {
                id 'java'
                id 'org.gradle.checkstyle-plugin' version '1.0-SNAPSHOT'
            }

            repositories {
                mavenLocal()
                mavenCentral()
            }

            dependencies {
                checkstyle 'com.puppycrawl.tools:checkstyle:8.42'
            }

            checkstyle {
                toolVersion = '8.42'
            }
        '''
    }

    private void assertFileExists(String path) {
        assert new File(testProjectDir, path).exists()
    }

    private void assertFileDoesNotExist(String path) {
        assert !new File(testProjectDir, path).exists()
    }

    private void assertFileContents(String path, String condition) {
        def file = new File(testProjectDir, path)
        assert file.exists()
        assert file.text.contains(condition)
    }

    private Closure containsClass(String className) {
        return { text -> text.contains("\"$className\"") }
    }

    private Closure containsText(String text) {
        return { content -> content.contains(text) }
    }

    private Closure containsLine(Closure condition) {
        return { text -> text.readLines().any(condition) }
    }

    private BuildResult runGradleTask(String... tasks) {
        return GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments(tasks)
                .withPluginClasspath()
                .build()
    }

    private BuildResult runGradleTaskAndFail(String... tasks) {
        return GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments(tasks)
                .withPluginClasspath()
                .buildAndFail()
    }

    private void defaultLanguage(String defaultLocale) {
        Locale.setDefault(new Locale(defaultLocale))
    }
}

