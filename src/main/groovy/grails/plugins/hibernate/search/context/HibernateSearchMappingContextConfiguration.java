package grails.plugins.hibernate.search.context;

import grails.core.GrailsApplication;
import org.grails.datastore.gorm.jdbc.connections.DataSourceSettings;
import org.grails.datastore.mapping.core.connections.ConnectionSource;
import org.grails.orm.hibernate.HibernateEventListeners;
import org.grails.orm.hibernate.cfg.HibernateMappingContext;
import org.grails.orm.hibernate.cfg.HibernateMappingContextConfiguration;
import org.hibernate.*;
import org.hibernate.boot.model.TypeContributor;
import org.hibernate.boot.model.naming.ImplicitNamingStrategy;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.boot.model.relational.AuxiliaryDatabaseObject;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.spi.MetadataContributor;
import org.hibernate.cfg.AttributeConverterDefinition;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.annotations.NamedEntityGraphDefinition;
import org.hibernate.cfg.annotations.NamedProcedureCallDefinition;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.dialect.function.SQLFunction;
import org.hibernate.engine.spi.NamedQueryDefinition;
import org.hibernate.internal.util.xml.XmlDocument;
import org.hibernate.proxy.EntityNotFoundDelegate;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.tuple.entity.EntityTuplizerFactory;
import org.hibernate.type.BasicType;
import org.hibernate.type.SerializationException;
import org.hibernate.usertype.CompositeUserType;
import org.hibernate.usertype.UserType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.w3c.dom.Document;

import javax.persistence.AttributeConverter;
import javax.persistence.SharedCacheMode;
import javax.sql.DataSource;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;

@SuppressWarnings("all")
public class HibernateSearchMappingContextConfiguration extends HibernateMappingContextConfiguration {

    private final static Logger log = LoggerFactory.getLogger(HibernateSearchMappingContextConfiguration.class);

    static Class<? extends HibernateMappingContextConfiguration> delegateConfigClass;
    static GrailsApplication grailsApplication;

    private HibernateMappingContextConfiguration delegate;

    public HibernateSearchMappingContextConfiguration() {
        try {
            delegate = delegateConfigClass.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("cannot instantiate delegate configuration class", e);
        }

        log.info("Created delegate Hibernate Configuration on {}", delegateConfigClass);
    }

    public Properties getProperties() {
        return delegate.getProperties();
    }

    public Configuration setProperties(Properties properties) {
        return delegate.setProperties(properties);
    }

    public String getProperty(String propertyName) {
        return delegate.getProperty(propertyName);
    }

    public Configuration setProperty(String propertyName, String value) {
        return delegate.setProperty(propertyName, value);
    }

    public Configuration addProperties(Properties properties) {
        return delegate.addProperties(properties);
    }

    public void setImplicitNamingStrategy(ImplicitNamingStrategy implicitNamingStrategy) {
        delegate.setImplicitNamingStrategy(implicitNamingStrategy);
    }

    public void setPhysicalNamingStrategy(PhysicalNamingStrategy physicalNamingStrategy) {
        delegate.setPhysicalNamingStrategy(physicalNamingStrategy);
    }

    public Configuration configure() throws HibernateException {
        return delegate.configure();
    }

    public Configuration configure(String resource) throws HibernateException {
        return delegate.configure(resource);
    }

    public StandardServiceRegistryBuilder getStandardServiceRegistryBuilder() {
        return delegate.getStandardServiceRegistryBuilder();
    }

    public Configuration configure(URL url) throws HibernateException {
        return delegate.configure(url);
    }

    public Configuration configure(File configFile) throws HibernateException {
        return delegate.configure(configFile);
    }

    public Configuration configure(Document document) throws HibernateException {
        return delegate.configure(document);
    }

    public Configuration registerTypeContributor(TypeContributor typeContributor) {
        return delegate.registerTypeContributor(typeContributor);
    }

    public Configuration registerTypeOverride(BasicType type) {
        return delegate.registerTypeOverride(type);
    }

    public Configuration registerTypeOverride(UserType type, String[] keys) {
        return delegate.registerTypeOverride(type, keys);
    }

    public Configuration registerTypeOverride(CompositeUserType type, String[] keys) {
        return delegate.registerTypeOverride(type, keys);
    }

    public Configuration addFile(String xmlFile) throws MappingException {
        return delegate.addFile(xmlFile);
    }

    public Configuration addFile(File xmlFile) throws MappingException {
        return delegate.addFile(xmlFile);
    }

    public void add(XmlDocument metadataXml) {
        delegate.add(metadataXml);
    }

    public Configuration addCacheableFile(File xmlFile) throws MappingException {
        return delegate.addCacheableFile(xmlFile);
    }

    public Configuration addCacheableFileStrictly(File xmlFile) throws SerializationException, FileNotFoundException {
        return delegate.addCacheableFileStrictly(xmlFile);
    }

    public Configuration addCacheableFile(String xmlFile) throws MappingException {
        return delegate.addCacheableFile(xmlFile);
    }

    public Configuration addXML(String xml) throws MappingException {
        return delegate.addXML(xml);
    }

    public Configuration addURL(URL url) throws MappingException {
        return delegate.addURL(url);
    }

    public Configuration addDocument(Document doc) throws MappingException {
        return delegate.addDocument(doc);
    }

    public Configuration addInputStream(InputStream xmlInputStream) throws MappingException {
        return delegate.addInputStream(xmlInputStream);
    }

    public Configuration addResource(String resourceName, ClassLoader classLoader) throws MappingException {
        return delegate.addResource(resourceName, classLoader);
    }

    public Configuration addResource(String resourceName) throws MappingException {
        return delegate.addResource(resourceName);
    }

    public Configuration addClass(Class persistentClass) throws MappingException {
        return delegate.addClass(persistentClass);
    }

    public Configuration addPackage(String packageName) throws MappingException {
        return delegate.addPackage(packageName);
    }

    public Configuration addJar(File jar) throws MappingException {
        return delegate.addJar(jar);
    }

    public Configuration addDirectory(File dir) throws MappingException {
        return delegate.addDirectory(dir);
    }

    public Interceptor getInterceptor() {
        return delegate.getInterceptor();
    }

    public Configuration setInterceptor(Interceptor interceptor) {
        return delegate.setInterceptor(interceptor);
    }

    public EntityTuplizerFactory getEntityTuplizerFactory() {
        return delegate.getEntityTuplizerFactory();
    }

    public EntityNotFoundDelegate getEntityNotFoundDelegate() {
        return delegate.getEntityNotFoundDelegate();
    }

    public void setEntityNotFoundDelegate(EntityNotFoundDelegate entityNotFoundDelegate) {
        delegate.setEntityNotFoundDelegate(entityNotFoundDelegate);
    }

    public SessionFactoryObserver getSessionFactoryObserver() {
        return delegate.getSessionFactoryObserver();
    }

    public void setSessionFactoryObserver(SessionFactoryObserver sessionFactoryObserver) {
        delegate.setSessionFactoryObserver(sessionFactoryObserver);
    }

    public CurrentTenantIdentifierResolver getCurrentTenantIdentifierResolver() {
        return delegate.getCurrentTenantIdentifierResolver();
    }

    public void setCurrentTenantIdentifierResolver(CurrentTenantIdentifierResolver currentTenantIdentifierResolver) {
        delegate.setCurrentTenantIdentifierResolver(currentTenantIdentifierResolver);
    }

    public SessionFactory buildSessionFactory(ServiceRegistry serviceRegistry) throws HibernateException {
        HibernateSearchConfigurationInterceptor interceptor = new HibernateSearchConfigurationInterceptor(grailsApplication);
        interceptor.configureHibernateSearch(delegate);
        try {
            return delegate.buildSessionFactory(serviceRegistry);
        } catch (Exception ex) {
            log.error("blah 2", ex);
        }
        return null;
    }

    public Map<String, SQLFunction> getSqlFunctions() {
        return delegate.getSqlFunctions();
    }

    public void addSqlFunction(String functionName, SQLFunction function) {
        delegate.addSqlFunction(functionName, function);
    }

    public void addAuxiliaryDatabaseObject(AuxiliaryDatabaseObject object) {
        delegate.addAuxiliaryDatabaseObject(object);
    }

    public void addAttributeConverter(Class<? extends AttributeConverter> attributeConverterClass, boolean autoApply) {
        delegate.addAttributeConverter(attributeConverterClass, autoApply);
    }

    public void addAttributeConverter(Class<? extends AttributeConverter> attributeConverterClass) {
        delegate.addAttributeConverter(attributeConverterClass);
    }

    public void addAttributeConverter(AttributeConverter attributeConverter) {
        delegate.addAttributeConverter(attributeConverter);
    }

    public void addAttributeConverter(AttributeConverter attributeConverter, boolean autoApply) {
        delegate.addAttributeConverter(attributeConverter, autoApply);
    }

    public void addAttributeConverter(AttributeConverterDefinition definition) {
        delegate.addAttributeConverter(definition);
    }

    public void setSharedCacheMode(SharedCacheMode sharedCacheMode) {
        delegate.setSharedCacheMode(sharedCacheMode);
    }

    public Map getNamedSQLQueries() {
        return delegate.getNamedSQLQueries();
    }

    public Map getSqlResultSetMappings() {
        return delegate.getSqlResultSetMappings();
    }

    public Collection<NamedEntityGraphDefinition> getNamedEntityGraphs() {
        return delegate.getNamedEntityGraphs();
    }

    public Map<String, NamedQueryDefinition> getNamedQueries() {
        return delegate.getNamedQueries();
    }

    public Map<String, NamedProcedureCallDefinition> getNamedProcedureCallMap() {
        return delegate.getNamedProcedureCallMap();
    }

    public void buildMappings() {
        delegate.buildMappings();
    }

    public Configuration mergeProperties(Properties properties) {
        return delegate.mergeProperties(properties);
    }

    public int hashCode() {
        return delegate.hashCode();
    }

    public boolean equals(Object obj) {
        return delegate.equals(obj);
    }

    public String toString() {
        return delegate.toString();
    }

    public void setHibernateMappingContext(HibernateMappingContext hibernateMappingContext) {
        delegate.setHibernateMappingContext(hibernateMappingContext);
    }

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        delegate.setApplicationContext(applicationContext);
    }

    public void setDataSourceConnectionSource(ConnectionSource<DataSource, DataSourceSettings> connectionSource) {
        delegate.setDataSourceConnectionSource(connectionSource);
    }

    public void addAnnotatedClasses(Class<?>... annotatedClasses) {
        delegate.addAnnotatedClasses(annotatedClasses);
    }

    public Configuration addAnnotatedClass(Class annotatedClass) {
        return delegate.addAnnotatedClass(annotatedClass);
    }

    public void addPackages(String... annotatedPackages) {
        delegate.addPackages(annotatedPackages);
    }

    public void scanPackages(String... packagesToScan) throws HibernateException {
        delegate.scanPackages(packagesToScan);
    }

    public void setSessionFactoryBeanName(String name) {
        delegate.setSessionFactoryBeanName(name);
    }

    public void setDataSourceName(String name) {
        delegate.setDataSourceName(name);
    }

    public SessionFactory buildSessionFactory() throws HibernateException {
        HibernateSearchConfigurationInterceptor interceptor = new HibernateSearchConfigurationInterceptor(grailsApplication);
        interceptor.configureHibernateSearch(delegate);

        try {
            return delegate.buildSessionFactory();
        } catch (Exception ex) {
            log.error("blah", ex);
        }
        return null;
    }

    public void setEventListeners(Map<String, Object> listeners) {
        delegate.setEventListeners(listeners);
    }

    public void setHibernateEventListeners(HibernateEventListeners listeners) {
        delegate.setHibernateEventListeners(listeners);
    }

    public ServiceRegistry getServiceRegistry() {
        return delegate.getServiceRegistry();
    }

    public void setMetadataContributor(MetadataContributor metadataContributor) {
        delegate.setMetadataContributor(metadataContributor);
    }
}
