package grails.plugins.hibernate.search;

import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang.StringUtils.isNotBlank;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.sql.DataSource;

import org.grails.core.util.ClassPropertyFetcher;
import org.grails.orm.hibernate.HibernateEventListeners;
import org.grails.orm.hibernate.HibernateMappingContextSessionFactoryBean;
import org.grails.orm.hibernate.cfg.HibernateMappingContext;
import org.grails.orm.hibernate.cfg.Mapping;
import org.hibernate.Interceptor;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.search.cfg.SearchMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.jndi.JndiTemplate;

import grails.config.Config;
import grails.core.GrailsApplication;
import grails.core.GrailsDomainClass;
import grails.orm.bootstrap.HibernateDatastoreSpringInitializer;
import groovy.lang.Closure;

public class HibernateSearchCapableSessionFactoryBean extends HibernateMappingContextSessionFactoryBean {

	private final static Logger log = LoggerFactory.getLogger(HibernateSearchCapableSessionFactoryBean.class);

	private static final String DIRECTORY_PROVIDER = "hibernate.search.default.directory_provider";
	private static final String INDEX_BASE = "hibernate.search.default.indexBase";
	private static final String INDEX_BASE_JNDI_NAME = "hibernate.search.default.indexBaseJndiName";

	public static final String DEFAULT_DATA_SOURCE_NAME = HibernateDatastoreSpringInitializer.DEFAULT_DATA_SOURCE_NAME;

	private GrailsApplication grailsApplication;
	private List<GrailsDomainClass> domainClasses;

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public HibernateSearchCapableSessionFactoryBean(GrailsApplication grailsApplication,
			List<GrailsDomainClass> domainClasses, Interceptor entityInterceptor, Properties hibernateProperties,
			HibernateEventListeners hibernateEventListeners, HibernateMappingContext hibernateMappingContext,
			DataSource dataSource) {

		this.grailsApplication = grailsApplication;
		this.domainClasses = domainClasses;
		this.setEntityInterceptor(entityInterceptor);
		this.setHibernateProperties(hibernateProperties);
		this.setHibernateEventListeners(hibernateEventListeners);
		this.setHibernateMappingContext(hibernateMappingContext);

		this.dataSourceName = Mapping.DEFAULT_DATA_SOURCE;
		this.setDataSource(dataSource);

		this.setSessionFactoryBeanName(HibernateDatastoreSpringInitializer.SESSION_FACTORY_BEAN_NAME);

		Config appConfiguration = grailsApplication.getConfig();
		Map hibernateConfig = appConfiguration.getProperty("hibernate", Map.class, Collections.emptyMap());
		String dsConfigPrefix = appConfiguration.containsProperty("dataSources")
				? "dataSources." + DEFAULT_DATA_SOURCE_NAME : DEFAULT_DATA_SOURCE_NAME;

		List<Object> hibConfigLocations = new LinkedList<>();
		if (Thread.currentThread().getContextClassLoader().getResource("hibernate.cfg.xml") != null) {
			hibConfigLocations.add("classpath:" + "hibernate.cfg.xml");
		}

		if (hibernateConfig.containsKey("config") && ((Map) hibernateConfig.get("config")).containsKey("location")) {
			Object explicitLocations = ((Map) hibernateConfig.get("config")).get("location");
			if (explicitLocations instanceof Collection) {
				for (Object location : ((Collection) explicitLocations)) {
					hibConfigLocations.add(location.toString());
				}
			} else {
				hibConfigLocations.add(explicitLocations.toString());
			}
		}

		setConfigLocations(hibConfigLocations.toArray(new Resource[0]));

		try {
			String defaultConfigClass = "";
			if (hibernateProperties.containsKey("hibernate.config_class")) {
				defaultConfigClass = hibernateProperties.getProperty("hibernate.config_class").toString();
			}

			String hibConfigClass = appConfiguration.getProperty(dsConfigPrefix + ".configClass", defaultConfigClass);
			if (isNotBlank(hibConfigClass)) {
				setConfigClass((Class) Thread.currentThread().getContextClassLoader().loadClass(hibConfigClass));
			}
		} catch (Exception e) {
			e.printStackTrace();
			log.error("cannot set sessionFactory config class - please report or PR", e);
		}
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		log.info("Hibernate Search capable SessionFactoryBean built");

		super.afterPropertiesSet();
	}

	@SuppressWarnings("rawtypes")
	@Override
	protected SessionFactory doBuildSessionFactory() {
		Configuration configuration = getConfiguration();

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
				indexPathBuilder.append(grailsApplication.getMetadata().getProperty("info.app.grailsVersion"));
				indexPathBuilder.append(File.separator);
				indexPathBuilder.append("projects");
				indexPathBuilder.append(File.separator);
				indexPathBuilder.append(grailsApplication.getMetadata().getProperty("info.app.name"));
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
			for (GrailsDomainClass domainClass : domainClasses) {

				Closure searchClosure = ClassPropertyFetcher.forClass(domainClass.getClazz())
						.getStaticPropertyValue("search", Closure.class);

				if (searchClosure != null) {

					SearchMappingEntityConfig searchMappingEntityConfig = new SearchMappingEntityConfig(searchMapping,
							domainClass.getClazz());

					searchClosure.setDelegate(searchMappingEntityConfig);
					searchClosure.setResolveStrategy(Closure.DELEGATE_FIRST);
					searchClosure.call();
				}
			}

			configuration.getProperties().put(org.hibernate.search.cfg.Environment.MODEL_MAPPING, searchMapping);

		} catch (Exception e) {
			log.error("Error while indexing entities", e);
		}

		return super.doBuildSessionFactory();
	}

}
