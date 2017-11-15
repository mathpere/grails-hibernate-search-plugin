package grails.plugins.hibernate.search;

import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang.StringUtils.isNotBlank;
import static grails.plugins.hibernate.search.HibernateSearchGrailsPlugin.INDEXED_ENTITIES_GRAILS_APP_CONFIG_KEY;

import java.io.File;
import java.lang.annotation.ElementType;
import java.util.HashMap;
import java.util.Map;

import org.grails.core.util.ClassPropertyFetcher;
import org.grails.orm.hibernate.cfg.HibernateMappingContextConfiguration;
import org.hibernate.HibernateException;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.search.cfg.EntityDescriptor;
import org.hibernate.search.cfg.PropertyDescriptor;
import org.hibernate.search.cfg.SearchMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jndi.JndiTemplate;

import grails.core.GrailsApplication;
import grails.core.GrailsClass;
import grails.core.GrailsDomainClass;
import grails.core.GrailsDomainClassProperty;
import grails.orm.bootstrap.HibernateDatastoreSpringInitializer;
import groovy.lang.Closure;

/**
 * This bean inherits GORM session factory bean in order to initialize Hibernate
 * Search right before sessionFactory instantiation
 * 
 * @author lgrignon
 */
public class HibernateSearchConfigurationInterceptor {

	private final static Logger log = LoggerFactory.getLogger(HibernateSearchConfigurationInterceptor.class);

	private static final String DIRECTORY_PROVIDER = "hibernate.search.default.directory_provider";
	private static final String INDEX_BASE = "hibernate.search.default.indexBase";
	private static final String INDEX_BASE_JNDI_NAME = "hibernate.search.default.indexBaseJndiName";

	public static final String DEFAULT_DATA_SOURCE_NAME = HibernateDatastoreSpringInitializer.DEFAULT_DATA_SOURCE_NAME;

	private GrailsApplication grailsApplication;

	private Map<String, Map<String, PropertyDescriptor>> indexedPropertiesByName;

	public HibernateSearchConfigurationInterceptor(GrailsApplication grailsApplication) {
		this.grailsApplication = grailsApplication;
		this.indexedPropertiesByName = new HashMap<>();
	}

	public void configureHibernateSearch(Configuration hibernateConfiguration) throws HibernateException {

		log.debug("************** HibernateSearchConfigurationInterceptor.configureHibernateSearch() in Hibernate Search extension: $hibernateConfiguration **************");

		try {

			if (isBlank(hibernateConfiguration.getProperty(DIRECTORY_PROVIDER))) {
				hibernateConfiguration.setProperty(DIRECTORY_PROVIDER, "filesystem");
			}

			String jndiName = hibernateConfiguration.getProperty(INDEX_BASE_JNDI_NAME);
			if (isNotBlank(jndiName)) {
				hibernateConfiguration.setProperty(INDEX_BASE, (String) new JndiTemplate().lookup(jndiName));
			}

			if (isBlank(hibernateConfiguration.getProperty(INDEX_BASE))) {
				StringBuilder indexPathBuilder = new StringBuilder();
				indexPathBuilder.append(System.getProperty("user.home"));
				indexPathBuilder.append(File.separator);
				indexPathBuilder.append(".grails");
				indexPathBuilder.append(File.separator);
				indexPathBuilder.append(grailsApplication.metadata.getGrailsVersion());
				indexPathBuilder.append(File.separator);
				indexPathBuilder.append("projects");
				indexPathBuilder.append(File.separator);
				indexPathBuilder.append(grailsApplication.metadata.getApplicationName());
				indexPathBuilder.append(File.separator);
				indexPathBuilder.append("lucene-index");
				indexPathBuilder.append(File.separator);
				indexPathBuilder.append(grails.util.Environment.getCurrent().name());

				hibernateConfiguration.setProperty(INDEX_BASE, indexPathBuilder.toString());
			}

			SearchMapping searchMapping = new SearchMapping();

			// global config
			Object hibernateSearchConfig = grailsApplication.getConfig().get("grails.plugins.hibernatesearch");

			if (hibernateSearchConfig != null && hibernateSearchConfig instanceof Closure) {

				SearchMappingGlobalConfig searchMappingGlobalConfig = new SearchMappingGlobalConfig(searchMapping);

				Closure hibernateSearchConfigClosure = (Closure) hibernateSearchConfig;
				hibernateSearchConfigClosure.setDelegate(searchMappingGlobalConfig);
				hibernateSearchConfigClosure.setResolveStrategy(Closure.DELEGATE_FIRST);
				hibernateSearchConfigClosure.call();

			}

			// entities config
			GrailsClass[] domainClasses = grailsApplication.getArtefacts("Domain");
			for (GrailsClass domainClassUntyped : domainClasses) {
				GrailsDomainClass domainClass = (GrailsDomainClass) domainClassUntyped;

				Closure searchClosure = ClassPropertyFetcher.forClass(domainClass.getClazz())
						.getStaticPropertyValue("search", Closure.class);

				if (searchClosure != null) {
					SearchMappingEntityConfig searchMappingEntityConfig = new SearchMappingEntityConfig(searchMapping,
							domainClass.getClazz());

					searchClosure.setDelegate(searchMappingEntityConfig);
					searchClosure.setResolveStrategy(Closure.DELEGATE_FIRST);
					searchClosure.call();

					Map<String, PropertyDescriptor> indexedProperties = new HashMap<>();
					for (String indexedPropertyName : searchMappingEntityConfig.getIndexedPropertyNames()) {
						PropertyDescriptor indexedPropertyDescriptor = searchMapping
								.getEntityDescriptor(domainClass.getClazz())
								.getPropertyDescriptor(indexedPropertyName, ElementType.FIELD);
						if (indexedPropertyDescriptor != null) {
							indexedProperties.put(indexedPropertyDescriptor.getName(), indexedPropertyDescriptor);
						}
					}

					if (indexedProperties.size() > 0) {
						indexedPropertiesByName.put(domainClass.getName(), indexedProperties);
					}
				}
			}

			log.debug("registering " + indexedPropertiesByName + " with key " + INDEXED_ENTITIES_GRAILS_APP_CONFIG_KEY);
			grailsApplication.getConfig().put(INDEXED_ENTITIES_GRAILS_APP_CONFIG_KEY, indexedPropertiesByName);
			log.debug("registering " + searchMapping + " with key " + org.hibernate.search.cfg.Environment.MODEL_MAPPING);
			hibernateConfiguration.getProperties().put(org.hibernate.search.cfg.Environment.MODEL_MAPPING, searchMapping);

		} catch (Exception e) {
			log.error("Error while indexing entities", e);
		}
	}
}
