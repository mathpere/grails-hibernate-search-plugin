import org.codehaus.groovy.grails.commons.ClassPropertyFetcher
import org.hibernate.search.annotations.Indexed
import org.springframework.core.annotation.AnnotationUtils
import org.codehaus.groovy.grails.commons.GrailsApplication

includeTargets << grailsScript( '_GrailsBootstrap' )

USAGE = """
    Usage:
       grails hs-create-index

       >> Create Lucene index for all indexed entities

       grails hs-create-indexer <domain-class-name>

       >> Create Lucene index for the given domain class

    Example: grails hs-create-index com.yourapp.Book

"""

isSearchable = { domainClass ->
    ClassPropertyFetcher.forClass( domainClass ).getStaticPropertyValue( "search", Closure ) || AnnotationUtils.isAnnotationDeclaredLocally( Indexed, domainClass )
}

target( hsCreateIndex: "Create Lucene index for all domain classes or the given domain class" ) {

    depends( configureProxy, packageApp, classpath, loadApp, configureApp )

    def grailsApplication = appCtx.getBean( GrailsApplication )

    def args = argsMap.params

    if ( args.size() == 1 ) {

        def domainClass = grailsApplication.mainContext.getBean( "${args[0]}DomainClass" ).clazz

        if ( isSearchable( domainClass ) ) {

            grailsConsole.addStatus "Creating Lucene index for entity [${domainClass.name}]...."

            def start = new Date()
            domainClass.search().startIndexerAndWait()

            grailsConsole.addStatus "   built in ${( new Date().time - start.time ) / 1000}s"

        } else {

            grailsConsole.error "[${domainClass.name}] is not an indexed entity"

        }

    } else {

        def total = new Date()

        grailsApplication.domainClasses*.clazz.findAll( isSearchable ).each {

            grailsConsole.addStatus "Creating Lucene index for entity [${it.name}]...."

            def start = new Date()

            it.search().startIndexerAndWait()

            grailsConsole.addStatus "   built in ${( new Date().time - start.time ) / 1000}s\n"

        }

        grailsConsole.addStatus "Total time: ${( new Date().time - total.time ) / 1000}s"

    }
}


setDefaultTarget 'hsCreateIndex'
