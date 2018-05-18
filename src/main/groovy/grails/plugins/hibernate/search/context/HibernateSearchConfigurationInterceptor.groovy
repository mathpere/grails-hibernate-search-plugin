package grails.plugins.hibernate.search.context

import grails.core.GrailsApplication
import grails.core.GrailsClass
import grails.plugins.hibernate.search.HibernateSearchGrailsPlugin
import grails.plugins.hibernate.search.config.SearchMappingEntityConfig
import grails.plugins.hibernate.search.config.SearchMappingGlobalConfig
import grails.util.Environment
import org.grails.core.artefact.DomainClassArtefactHandler
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher
import org.hibernate.HibernateException
import org.hibernate.cfg.Configuration
import org.hibernate.search.annotations.Indexed
import org.hibernate.search.cfg.PropertyDescriptor
import org.hibernate.search.cfg.SearchMapping
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.jndi.JndiTemplate

import java.lang.annotation.ElementType

import static grails.plugins.hibernate.search.HibernateSearchGrailsPlugin.INDEXED_ENTITIES_GRAILS_APP_CONFIG_KEY

/**
 * This bean inherits GORM session factory bean in order to initialize Hibernate
 * Search right before sessionFactory instantiation
 *
 * @author lgrignon
 */
class HibernateSearchConfigurationInterceptor {

    private final static Logger log = LoggerFactory.getLogger(HibernateSearchConfigurationInterceptor)
    private final static Logger pluginLogger = LoggerFactory.getLogger(HibernateSearchGrailsPlugin)

    private static final String DIRECTORY_PROVIDER = 'hibernate.search.default.directory_provider'
    private static final String INDEX_BASE = 'hibernate.search.default.indexBase'
    private static final String INDEX_BASE_JNDI_NAME = 'hibernate.search.default.indexBaseJndiName'

    private final GrailsApplication grailsApplication

    private final Map<String, Map<String, PropertyDescriptor>> indexedPropertiesByName

    HibernateSearchConfigurationInterceptor(GrailsApplication grailsApplication) {
        this.grailsApplication = grailsApplication
        this.indexedPropertiesByName = [:]
    }

    void configureHibernateSearch(Configuration hibernateConfiguration) throws HibernateException {

        log.debug('Configure HibernateSearch for {}', hibernateConfiguration)

        try {

            if (!hibernateConfiguration.getProperty(DIRECTORY_PROVIDER)) {
                hibernateConfiguration.setProperty(DIRECTORY_PROVIDER, 'filesystem')
            }

            String jndiName = hibernateConfiguration.getProperty(INDEX_BASE_JNDI_NAME)
            if (jndiName) {
                hibernateConfiguration.setProperty(INDEX_BASE, (String) new JndiTemplate().lookup(jndiName))
            }

            if (!hibernateConfiguration.getProperty(INDEX_BASE)) {
                StringBuilder indexPathBuilder = new StringBuilder()
                        .append(System.getProperty('user.home'))
                        .append(File.separator)
                        .append('.grails')
                        .append(File.separator)
                        .append(grailsApplication.metadata.getGrailsVersion())
                        .append(File.separator)
                        .append('projects')
                        .append(File.separator)
                        .append(grailsApplication.metadata.getApplicationName())
                        .append(File.separator)
                        .append('lucene-index')
                        .append(File.separator)
                        .append(Environment.getCurrent().name())

                hibernateConfiguration.setProperty(INDEX_BASE, indexPathBuilder.toString())
            }

            SearchMapping searchMapping = new SearchMapping()
            log.debug('Configuring SearchMapping')
            // global config
            Object hibernateSearchConfig = grailsApplication.config.grails.plugins.hibernatesearch

            if (hibernateSearchConfig && hibernateSearchConfig instanceof Closure) {

                SearchMappingGlobalConfig searchMappingGlobalConfig = new SearchMappingGlobalConfig(searchMapping)

                Closure hibernateSearchConfigClosure = (Closure) hibernateSearchConfig
                hibernateSearchConfigClosure.setDelegate(searchMappingGlobalConfig)
                hibernateSearchConfigClosure.setResolveStrategy(Closure.DELEGATE_FIRST)
                hibernateSearchConfigClosure.call()

            }

            log.debug('Configuring entities for indexing')
            // entities config
            Collection<GrailsClass> domainClasses = grailsApplication.getArtefacts(DomainClassArtefactHandler.TYPE)

            for (GrailsClass domainClass : domainClasses) {

                Closure searchClosure = ClassPropertyFetcher.forClass(domainClass.getClazz())
                        .getStaticPropertyValue(SearchMappingEntityConfig.INDEX_CONFIG_NAME, Closure)

                if (searchClosure != null) {
                    pluginLogger.info '* {} is indexed', domainClass.name
                    SearchMappingEntityConfig searchMappingEntityConfig = new SearchMappingEntityConfig(searchMapping, domainClass)

                    searchClosure.setDelegate(searchMappingEntityConfig)
                    searchClosure.setResolveStrategy(Closure.DELEGATE_FIRST)
                    searchClosure.call()

                    Map<String, PropertyDescriptor> indexedProperties = [:]
                    for (String indexedPropertyName : searchMappingEntityConfig.getIndexedPropertyNames()) {
                        PropertyDescriptor indexedPropertyDescriptor = searchMapping
                                .getEntityDescriptor(domainClass.getClazz())
                                .getPropertyDescriptor(indexedPropertyName, ElementType.FIELD)
                        if (indexedPropertyDescriptor) {
                            indexedProperties[indexedPropertyDescriptor.getName()] = indexedPropertyDescriptor
                        }
                    }

                    if (indexedProperties.size() > 0) {
                        indexedPropertiesByName[domainClass.getName()] = indexedProperties
                    }
                }
                else if (AnnotationUtils.isAnnotationDeclaredLocally(Indexed, domainClass.clazz)) {
                    pluginLogger.info '* {} is indexed using annotations', domainClass.name
                }
            }

            log.debug('Registering indexed properties to Grails config key {}', INDEXED_ENTITIES_GRAILS_APP_CONFIG_KEY)
            log.trace('Indexed properties {}', indexedPropertiesByName)
            grailsApplication.config[INDEXED_ENTITIES_GRAILS_APP_CONFIG_KEY] = indexedPropertiesByName

            log.debug('Registering SearchMapping to HibernateConfiguration key {}', org.hibernate.search.cfg.Environment.MODEL_MAPPING)
            hibernateConfiguration.getProperties()[org.hibernate.search.cfg.Environment.MODEL_MAPPING] = searchMapping

        } catch (Exception e) {
            log.error('Error while configuring entities for indexing. Hibernate search is not available.', e)
        }
    }
}
