import org.codehaus.groovy.grails.commons.ClassPropertyFetcher
import org.codehaus.groovy.grails.plugins.hibernate.search.SearchMappingConfigurableLocalSessionFactoryBean
import org.codehaus.groovy.grails.plugins.hibernate.search.SearchPersistentMethod
import org.hibernate.search.annotations.Indexed
import org.springframework.core.annotation.AnnotationUtils

class HibernateSearchGrailsPlugin {
    def version = "0.3"
    def grailsVersion = "2.0 > *"
    def loadAfter = ['hibernate']
    def title = "Hibernate Search Plugin"
    def author = "Mathieu Perez, Julie Ingignoli"
    def authorEmail = "mathieu.perez@novacodex.net, julie.ingignoli@novacodex.net"
    def description = 'Integrates Hibernate Search features to Grails'
    def documentation = "http://grails.org/plugin/hibernate-search"
    def license = 'APACHE'
    def organization = [name: 'NovaCodex', url: 'http://www.novacodex.net/']
    def developers = [
            [name: 'Mathieu Perez', email: 'mathieu.perez@novacodex.net'],
            [name: 'Julie Ingignoli', email: 'julie.ingignoli@novacodex.net']]
    def issueManagement = [system: 'github', url: 'https://github.com/mathpere/grails-hibernate-search-plugin/issues']
    def scm = [url: 'https://github.com/mathpere/grails-hibernate-search-plugin']

    def doWithSpring = {
        sessionFactory(SearchMappingConfigurableLocalSessionFactoryBean) { bean ->
            // see org.codehaus.groovy.grails.plugins.orm.hibernate.HibernatePluginSupport:
            bean.parent = 'abstractSessionFactoryBeanConfig'
        }
    }

    def doWithDynamicMethods = { ctx ->

        def sessionFactory = ctx.getBean("sessionFactory")

        // add search() method to indexed domain classes:
        application.domainClasses.each { grailsClass ->
            def clazz = grailsClass.clazz

            if ( ClassPropertyFetcher.forClass(clazz).getStaticPropertyValue("search", Closure) || AnnotationUtils.isAnnotationDeclaredLocally(Indexed, clazz) ) {
                def searchMethod = new SearchPersistentMethod(sessionFactory, application.classLoader, application)

                grailsClass.metaClass.static.search = { Closure closure ->
                    searchMethod.invoke(clazz, "search", [closure] as Object[])
                }
            }
        }
    }
}