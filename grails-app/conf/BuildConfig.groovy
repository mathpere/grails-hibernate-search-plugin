grails.project.class.dir = "target/classes"
grails.project.test.class.dir = "target/test-classes"
grails.project.test.reports.dir = "target/test-reports"
grails.project.source.level = 1.6
grails.project.target.level = 1.6

grails.project.dependency.resolution = {
    inherits "global"
    log "warn"
    repositories {
        inherits true
        grailsPlugins()
        grailsHome()
        grailsCentral()

        mavenCentral()
        mavenRepo "https://repository.jboss.org"
    }

    dependencies {
        compile('org.hibernate:hibernate-search:4.1.1.Final') {
            excludes "hibernate-core"
        }
    }

    plugins {
        compile ":hibernate4:4.1.11.2"

        build(":tomcat:7.0.42") {
            export = false
        }

        build ':release:2.2.1', ':rest-client-builder:1.0.3', {
            export = false
        }
    }
}
