package grails.plugins.hibernate.search

import grails.plugins.Plugin
import grails.plugins.hibernate.search.config.HibernateSearchConfig
import grails.plugins.hibernate.search.config.SearchMappingEntityConfig
import grails.plugins.hibernate.search.context.HibernateSearchMappingContextConfiguration
import org.grails.core.artefact.DomainClassArtefactHandler
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher
import org.grails.orm.hibernate.cfg.HibernateMappingContextConfiguration
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.search.annotations.Indexed
import org.hibernate.search.cfg.PropertyDescriptor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.core.annotation.AnnotationUtils

class HibernateSearchGrailsPlugin extends Plugin {

    public static final String INDEXED_ENTITIES_GRAILS_APP_CONFIG_KEY = "hibernate.search.indexedPropertiesBy"

    private final static Logger log = LoggerFactory.getLogger(this)

    public static HibernateSearchConfig pluginConfig

    def grailsVersion = "3.2 > *"

    // resources that are excluded from plugin packaging
    def pluginExcludes = [

    ]

    def observe = ["domainClass"]
    def dependsOn = [hibernate: "* > 6.0"]
    def loadAfter = ['hibernate']
    def title = "Hibernate Search Plugin"
    def author = "Mathieu Perez, Julie Ingignoli, Louis Grignon"
    def authorEmail = "mathieu.perez@novacodex.net, julie.ingignoli@novacodex.net, "
    def description = 'Integrates Hibernate Search features to Grails'
    def documentation = "http://grails.org/plugin/hibernate-search"
    def license = 'APACHE'
    def organization = [name: 'NovaCodex', url: 'http://www.novacodex.net/']
    def developers = [
            [name: 'Mathieu Perez', email: 'mathieu.perez@novacodex.net'],
            [name: 'Julie Ingignoli', email: 'julie.ingignoli@novaco	dex.net'],
            [name: 'Louis Grignon', email: 'louis.grignon@gmail.com'],
            [name: 'Ollie Freeman', email: 'ollie.freeman@gmail.com']
    ]
    def issueManagement = [system: 'github', url: 'https://github.com/mathpere/grails-hibernate-search-plugin/issues']
    def scm = [url: 'https://github.com/mathpere/grails-hibernate-search-plugin']

    Closure doWithSpring() {
        {->

            Class projectConfigClass = grailsApplication.config.hibernate?.configClass ?: null

            Class delegateConfigClass = HibernateMappingContextConfiguration
            if (projectConfigClass != null) {
                if (!HibernateMappingContextConfiguration.class.isAssignableFrom(projectConfigClass)) {
                    log.warn 'Project\'s hibernate.configClass ({})) should inherit {}',
                             projectConfigClass, HibernateMappingContextConfiguration.name
                }

                delegateConfigClass = projectConfigClass
            }

            HibernateSearchMappingContextConfiguration.grailsApplication = grailsApplication
            HibernateSearchMappingContextConfiguration.delegateConfigClass = delegateConfigClass

            log.info 'Hibernate Configuration override but delegated to {}', delegateConfigClass

            grailsApplication.config.hibernate.configClass = HibernateSearchMappingContextConfiguration
        }
    }

    @Override
    void doWithApplicationContext() {

        // config
        def hibernateSearchConfig = grailsApplication.config.grails.plugins.hibernatesearch

        if (hibernateSearchConfig && hibernateSearchConfig instanceof Closure) {

            log.debug 'hibernatesearch config found'
            Session session = applicationContext.sessionFactory.openSession()

            Map<String, Map<String, PropertyDescriptor>> indexedPropertiesByEntity = getConfig()[INDEXED_ENTITIES_GRAILS_APP_CONFIG_KEY]

            pluginConfig = new HibernateSearchConfig(session, indexedPropertiesByEntity)
            hibernateSearchConfig.delegate = pluginConfig
            hibernateSearchConfig.resolveStrategy = Closure.DELEGATE_FIRST
            hibernateSearchConfig.call()
        }
    }

    @Override
    void doWithDynamicMethods() {
        log.debug "Adding search DSL to indexed domains"
        addSearchDsl(applicationContext)
    }

    @Override
    void onChange(Map<String, Object> event) {
        addSearchDsl(event.ctx)
    }

    void addSearchDsl(ApplicationContext applicationContext) {
        // add search() method to indexed domain classes:
        grailsApplication.getArtefacts(DomainClassArtefactHandler.TYPE).each {domainClass ->

            Class clazz = domainClass.getClazz()

            if (ClassPropertyFetcher.forClass(clazz).getStaticPropertyValue(SearchMappingEntityConfig.INDEX_CONFIG_NAME, Closure) ||
                AnnotationUtils.isAnnotationDeclaredLocally(Indexed, clazz)) {
                domainClass.metaClass.static.search = {
                    new HibernateSearchApi(domainClass, applicationContext.getBean(SessionFactory).getCurrentSession(), pluginConfig)
                }

                domainClass.metaClass.search = {
                    def instance = delegate
                    new HibernateSearchApi(domainClass, instance, applicationContext.getBean(SessionFactory).getCurrentSession(), pluginConfig)
                }
            }
        }
    }
}
