package grails.plugins.hibernate.search

import org.grails.core.util.ClassPropertyFetcher
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.HibernateMappingContext;
import org.grails.orm.hibernate.cfg.HibernateMappingContextConfiguration
import org.hibernate.Session
import org.hibernate.search.annotations.Indexed
import org.hibernate.search.cfg.PropertyDescriptor
import org.slf4j.Logger;
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils

import grails.plugins.*;
import groovy.lang.Closure;

class HibernateSearchGrailsPlugin extends Plugin {

	public static final String INDEXED_ENTITIES_GRAILS_APP_CONFIG_KEY = "hibernate.search.indexedPropertiesBy";

	private final static Logger log = LoggerFactory.getLogger(this)

	public static HibernateSearchConfig pluginConfig;

	def grailsVersion = "3.2 > *"

	def dependsOn = [hibernate: "* > 6.0"]
	def loadAfter =	 ['hibernate']
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
		[name: 'Louis Grignon', email: 'louis.grignon@gmail.com']
	]
	def issueManagement = [system: 'github', url: 'https://github.com/mathpere/grails-hibernate-search-plugin/issues']
	def scm = [url: 'https://github.com/mathpere/grails-hibernate-search-plugin']

	@Override
	void onStartup(Map<String, Object> event) {
		log.info("\n\nstartup plugin\n\n")
	}

	Closure doWithSpring() { {

			->
			
			Class projectConfigClass = grailsApplication.config.hibernate?.configClass ?: null;
			
			Class delegateConfigClass = HibernateMappingContextConfiguration.class;
			if (projectConfigClass != null) {
				if (!HibernateMappingContextConfiguration.class.isAssignableFrom(projectConfigClass)) {
					log.warn "project's hibernate.configClass ($projectConfigClass) should inherit ${HibernateMappingContextConfiguration.class.getName()}"
				}
				
				delegateConfigClass = projectConfigClass;
			}

			HibernateMappingContextConfigurationDelegate.grailsApplication = grailsApplication;
			HibernateMappingContextConfigurationDelegate.delegateConfigClass = delegateConfigClass;
			
			log.info "plugin ready - hibernate configClass delegated to $delegateConfigClass"
			
			grailsApplication.config.hibernate.configClass = HibernateMappingContextConfigurationDelegate.class;
		}
	}

	@Override
	public void doWithApplicationContext() {

		// add search() method to indexed domain classes:
		log.info "initialize search from domain class static DSL"
		grailsApplication.domainClasses.each { grailsClass ->
			def clazz = grailsClass.clazz

			if ( ClassPropertyFetcher.forClass( clazz ).getStaticPropertyValue( "search", Closure ) || AnnotationUtils.isAnnotationDeclaredLocally( Indexed, clazz ) ) {

				log.info "* " + clazz.getSimpleName() + " is indexed"
				grailsClass.metaClass.static.search = {
					def searchApi = new HibernateSearchApi( grailsClass, applicationContext.sessionFactory.getCurrentSession(), pluginConfig )
					return searchApi
				}

				grailsClass.metaClass.search = {
					def instance = delegate
					def searchApi = new HibernateSearchApi( grailsClass, instance, applicationContext.sessionFactory.getCurrentSession(), pluginConfig )
					return searchApi
				}
			}
		}

		// config
		def hibernateSearchConfig = grailsApplication.config.grails.plugins.hibernatesearch

		if ( hibernateSearchConfig && hibernateSearchConfig instanceof Closure ) {

			log.debug 'hibernatesearch config found'
			Session session = applicationContext.sessionFactory.openSession();

			Map<String, Map<String, PropertyDescriptor>> indexedPropertiesByEntity = getConfig().get(INDEXED_ENTITIES_GRAILS_APP_CONFIG_KEY);
			
			pluginConfig = new HibernateSearchConfig( session, indexedPropertiesByEntity )
			hibernateSearchConfig.delegate = pluginConfig
			hibernateSearchConfig.resolveStrategy = Closure.DELEGATE_FIRST
			hibernateSearchConfig.call()
		}
	}
}
