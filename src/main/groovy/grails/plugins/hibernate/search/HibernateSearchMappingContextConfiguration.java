package grails.plugins.hibernate.search;

import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang.StringUtils.isNotBlank;

import java.io.File;

import org.grails.core.util.ClassPropertyFetcher;
import org.grails.orm.hibernate.cfg.HibernateMappingContextConfiguration;
import org.hibernate.HibernateException;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.search.cfg.SearchMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jndi.JndiTemplate;

import grails.core.GrailsApplication;
import grails.core.GrailsClass;
import grails.core.GrailsDomainClass;
import grails.orm.bootstrap.HibernateDatastoreSpringInitializer;
import groovy.lang.Closure;

/**
 * This bean inherits GORM session factory bean in order to initialize Hibernate
 * Search right before sessionFactory instantiation
 * 
 * @author lgrignon
 */
public class HibernateSearchMappingContextConfiguration extends HibernateMappingContextConfiguration {

	private final static Logger log = LoggerFactory.getLogger(HibernateSearchMappingContextConfiguration.class);

	private static final String DIRECTORY_PROVIDER = "hibernate.search.default.directory_provider";
	private static final String INDEX_BASE = "hibernate.search.default.indexBase";
	private static final String INDEX_BASE_JNDI_NAME = "hibernate.search.default.indexBaseJndiName";

	public static final String DEFAULT_DATA_SOURCE_NAME = HibernateDatastoreSpringInitializer.DEFAULT_DATA_SOURCE_NAME;

	public static GrailsApplication grailsApplication;

	@SuppressWarnings("rawtypes")
	@Override
	public SessionFactory buildSessionFactory() throws HibernateException {
		Configuration configuration = this;

		log.info("configuration extension for hibernate search: " + configuration);

		try {

			if (isBlank(configuration.getProperty(DIRECTORY_PROVIDER))) {
				configuration.setProperty(DIRECTORY_PROVIDER, "filesystem");
			}

			String jndiName = configuration.getProperty(INDEX_BASE_JNDI_NAME);
			if (isNotBlank(jndiName)) {
				configuration.setProperty(INDEX_BASE, (String) new JndiTemplate().lookup(jndiName));
			}

			if (isBlank(configuration.getProperty(INDEX_BASE))) {
				StringBuilder indexPathBuilder = new StringBuilder();
				indexPathBuilder.append(System.getProperty("user.home"));
				indexPathBuilder.append(File.separator);
				indexPathBuilder.append(".grails");
				indexPathBuilder.append(File.separator);
				indexPathBuilder.append(getProperty("info.app.grailsVersion"));
				indexPathBuilder.append(File.separator);
				indexPathBuilder.append("projects");
				indexPathBuilder.append(File.separator);
				indexPathBuilder.append(getProperty("info.app.name"));
				indexPathBuilder.append(File.separator);
				indexPathBuilder.append("lucene-index");
				indexPathBuilder.append(File.separator);
				indexPathBuilder.append(grails.util.Environment.getCurrent().name());

				configuration.setProperty(INDEX_BASE, indexPathBuilder.toString());
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
							domainClass);

					searchClosure.setDelegate(searchMappingEntityConfig);
					searchClosure.setResolveStrategy(Closure.DELEGATE_FIRST);
					searchClosure.call();
				}
			}

			configuration.getProperties().put(org.hibernate.search.cfg.Environment.MODEL_MAPPING, searchMapping);

		} catch (Exception e) {
			log.error("Error while indexing entities", e);
		}

		try {
			return super.buildSessionFactory();
		} catch (Exception e) {
			log.error("cannot build session factory", e);
			throw e;
		}
	}

}
