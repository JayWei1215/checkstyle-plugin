package org.gradle.integrationTest

import org.gradle.testkit.runner.GradleRunner
import spock.lang.TempDir
import spock.lang.Specification
import org.gradle.util.internal.VersionNumber
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class CheckstylePluginHtmlReportIntegrationTest extends Specification {

    @TempDir
    File testProjectDir
    File buildFile

    def setup() {
        buildFile = new File(testProjectDir, 'build.gradle')
        writeInitialBuildFile()
        writeConfigFiles()
    }

    def "generates HTML report with good code"() {
        given:
        writeGoodCode()

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments('checkstyleMain')
                .withPluginClasspath()
                .build()

        then:
        def html = parseReport()
        def head = html.selectFirst("head")
        // Title of report is Checkstyle Violations
        head.select("title").text() == "Checkstyle Violations"
        // Has some CSS styling
        head.select("style").size() == 1
        def body = html.selectFirst("body")
        def summaryTable = parseTable(body.selectFirst(".summary"))
        summaryTable[0] == ["Total files checked", "Total violations", "Files with violations"]
        summaryTable[1] == ["3", "0", "0"]

        // Good code produces no violations
        body.select(".filelist").size() == 0
        def violations = body.selectFirst(".violations")
        violations.selectFirst("p").text() == "No violations were found."
        violations.select(".file-violation").size() == 0
    }

    def "generates HTML report with bad code"() {
        setup:
        writeBadCode()

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments('checkstyleMain')
                .withPluginClasspath()
                .buildAndFail()

        then:
        def html = parseReport()
        def body = html.selectFirst("body")
        def summaryTable = parseTable(body.selectFirst(".summary"))
        summaryTable[1] == ["3", "4", "2"]

        // Bad code produces violations in Foo.java and Bar.java, but not Baz.java
        def fileList = parseTable(body.selectFirst(".filelist"))
        fileList[0] == ["File", "Total violations"]
        fileList[1][0].endsWith("Foo.java")
        fileList[1][1] == "3"
        fileList[2][0].endsWith("Bar.java")
        fileList[2][1] == "1"

        def violations = body.selectFirst(".violations")
        def fileViolations = violations.select(".file-violation")
        fileViolations.size() == 2

        // Bar.java violations
        fileViolations[0].selectFirst("h3").text().endsWith("Bar.java")
        def barViolations = parseTable(fileViolations[0].selectFirst(".violationlist"))
        barViolations[0] == ["Severity", "Description", "Line Number"]
        barViolations[1] == ["error", "Missing a Javadoc comment.", "5"]

        // Foo.java violations
        fileViolations[1].selectFirst("h3").text().endsWith("Foo.java")

        def fooViolations = parseTable(fileViolations[1].selectFirst(".violationlist"))
        fooViolations[0] == ["Severity", "Description", "Line Number"]
        fooViolations[1] == ["error", "Missing a Javadoc comment.", "5"]
        fooViolations[2] == ["error", "Missing a Javadoc comment.", "8"]
        fooViolations[3] == ["error", "Missing a Javadoc comment.", "11"]

        // Sanity check that the anchor link is correct
        def fooAnchorLink = body.selectXpath('//table[@class="filelist"]//tr[2]/td/a').attr("href")
        def fooAnchorName = "#" + fileViolations[1].selectFirst("a").attr("name")
        fooAnchorName == fooAnchorLink
    }

    private void writeInitialBuildFile() {
        buildFile.text = """
plugins {
    id 'java'
    id 'org.gradle.checkstyle-plugin' version '1.0-SNAPSHOT'
}

repositories {
        mavenLocal()
        mavenCentral()
}

checkstyle {
    toolVersion = '8.8'
}
        """
    }

    private void writeConfigFiles() {
        new File(testProjectDir, 'config/checkstyle').mkdirs()
        new File(testProjectDir, 'config/checkstyle/checkstyle.xml').text = """
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

    private void writeGoodCode() {
        new File(testProjectDir, 'src/main/java/com/example').mkdirs()
        new File(testProjectDir, 'src/main/java/com/example/Foo.java').text = """
            package com.example;

            public class Foo {
                /**
                 * This returns a bar.
                 *
                 * @return return the bar
                 */
                public String getBar() {
                    return "bar";
                }
            }
        """
        new File(testProjectDir, 'src/main/java/com/example/Bar.java').text = """
            package com.example;

            public class Bar {
                /**
                 * This returns a bar.
                 *
                 * @return return the bar
                 */
                public String getBar() {
                    return "bar";
                }
            }
        """
        new File(testProjectDir, 'src/main/java/com/example/Baz.java').text = """
            package com.example;

            public class Baz {
                /**
                 * This returns a bar.
                 *
                 * @return return the bar
                 */
                public String getBar() {
                    return "bar";
                }
            }
        """
    }

    private void writeBadCode() {
        new File(testProjectDir, 'src/main/java/com/example').mkdirs()
        new File(testProjectDir, 'src/main/java/com/example/Foo.java').text = """
            package com.example;

            public class Foo {
                public String getBar() {
                    return "bar";
                }
                public String getBar2() {
                    return "bar";
                }
                public String getBar3() {
                    return "bar";
                }
            }
        """
        new File(testProjectDir, 'src/main/java/com/example/Bar.java').text = """
            package com.example;

            public class Bar {
                public String getBar() {
                    return "bar";
                }
            }
        """
        new File(testProjectDir, 'src/main/java/com/example/Baz.java').text = """
            package com.example;

            public class Baz {
                /**
                 * This returns a bar.
                 *
                 * @return return the bar
                 */
                public String getBar() {
                    return "bar";
                }
            }
        """
    }

    private Document parseReport() {
        def htmlReport = new File(testProjectDir, "build/reports/checkstyle/main.html")
        assert htmlReport.exists()
        return Jsoup.parse(htmlReport, "UTF-8")
    }

    private List<List<String>> parseTable(Element table) {
        def result = []
        def rows = table.select("tr")
        rows.each { row ->
            def rowResult = []
            def cols = row.select("td")
            if (cols.isEmpty()) {
                cols = row.select("th")
            }
            cols.each { col ->
                rowResult << col.text()
            }
            result << rowResult
        }
        return result
    }

    private String mavenCentralRepository() {
        return """
repositories {
    mavenCentral()
}
        """
    }
}
