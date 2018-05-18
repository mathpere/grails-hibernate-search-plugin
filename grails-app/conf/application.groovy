import grails.plugins.hibernate.search.context.HibernateSearchConfigurationInterceptor

dataSources {

	dataSource {
		configClass = HibernateSearchConfigurationInterceptor
	}
}