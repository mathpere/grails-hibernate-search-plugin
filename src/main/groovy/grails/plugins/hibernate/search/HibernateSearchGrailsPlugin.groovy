package grails.plugins.hibernate.search

import org.grails.core.util.ClassPropertyFetcher
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.HibernateMappingContext;
import org.hibernate.Session
import org.hibernate.search.annotations.Indexed
import org.slf4j.Logger;
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils

import grails.plugins.*;
import groovy.lang.Closure;

class HibernateSearchGrailsPlugin extends Plugin {
	
	private final static Logger log = LoggerFactory.getLogger(this)
	
	def grailsVersion = "3.1.12 > *"

	def profiles = ['web']

	def loadAfter =	 ['hibernate', 'hibernate5']
	def title = "Hibernate Search Plugin"
	def author = "Mathieu Perez, Julie Ingignoli, Louis Grignon"
	def authorEmail = "mathieu.perez@novacodex.net, julie.ingignoli@novacodex.net, "
	def description = 'Integrates Hibernate Search features to Grails'
	def documentation = "http://grails.org/plugin/hibernate-search"
	def license = 'APACHE'
	def organization = [name: 'NovaCodex', url: 'http://www.novacodex.net/']
	def developers = [[name: 'Mathieu Perez', email: 'mathieu.perez@novacodex.net'],
		[name: 'Julie Ingignoli', email: 'julie.ingignoli@novaco	dex.net'],
		[name: 'Louis Grignon', email: 'louis.grignon@gmail.com']]
	def issueManagement = [system: 'github', url: 'https://github.com/mathpere/grails-hibernate-search-plugin/issues']
	def scm = [url: 'https://github.com/mathpere/grails-hibernate-search-plugin']

	Closure doWithSpring() {
		{  -> 
			
			ApplicationContext applicationContext = grailsApplication.getMainContext();
			
			sessionFactory( 
				HibernateSearchCapableSessionFactoryBean, 
				grailsApplication, 
				grailsApplication.domainClasses, 
				ref('entityInterceptor'), 
				ref('hibernateProperties'), 
				ref('hibernateEventListeners'),
				ref('eventTriggeringInterceptor'), 
				ref('grailsDomainClassMappingContext'), 
				ref('dataSource') ) {
				bean -> 
				
				log.info "HibernateSearchCapableSessionFactoryBean initialized"
			}
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
					
					new HibernateSearchQueryBuilder( clazz, applicationContext.sessionFactory.getCurrentSession() )
				}

				grailsClass.metaClass.search = {
					def instance = delegate
					new HibernateSearchQueryBuilder( clazz, instance, applicationContext.sessionFactory.getCurrentSession() )
				}
			}
		}

		// config
		def hibernateSearchConfig = grailsApplication.config.grails.plugins.hibernatesearch

		if ( hibernateSearchConfig && hibernateSearchConfig instanceof Closure ) {

			log.debug 'hibernatesearch config found'
			Session session = applicationContext.sessionFactory.openSession();

			hibernateSearchConfig.delegate = new HibernateSearchConfig( session )
			hibernateSearchConfig.resolveStrategy = Closure.DELEGATE_FIRST
			hibernateSearchConfig.call()
		}
	}
}
