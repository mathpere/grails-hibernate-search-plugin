/* Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.grails.plugins.hibernate.search

import org.codehaus.groovy.grails.commons.ClassPropertyFetcher
import org.codehaus.groovy.grails.orm.hibernate.ConfigurableLocalSessionFactoryBean
import org.hibernate.HibernateException
import org.hibernate.cfg.Configuration
import org.hibernate.search.Environment
import org.hibernate.search.cfg.SearchMapping
import org.springframework.jndi.JndiTemplate

class SearchMappingConfigurableLocalSessionFactoryBean extends ConfigurableLocalSessionFactoryBean {

    private static final String DIRECTORY_PROVIDER = "hibernate.search.default.directory_provider"
    private static final String INDEX_BASE = "hibernate.search.default.indexBase"
    private static final String INDEX_BASE_JNDI_NAME = "hibernate.search.default.indexBaseJndiName"

    @Override
    protected void postProcessConfiguration( Configuration config ) throws HibernateException {
        super.postProcessConfiguration( config )

        try {

            def properties = config.properties

            if ( !properties.containsKey( DIRECTORY_PROVIDER ) ) {
                properties.setProperty( DIRECTORY_PROVIDER, "filesystem" )
            }

            if ( properties.containsKey( INDEX_BASE_JNDI_NAME ) ) {
                def jndiName = properties.getProperty( INDEX_BASE_JNDI_NAME )
                properties.setProperty( INDEX_BASE, new JndiTemplate().lookup( jndiName ) )
            }

            if ( !properties.containsKey( INDEX_BASE ) ) {
                def sep = File.separator
                properties.setProperty( INDEX_BASE, "${System.properties['user.home']}${sep}.grails${sep}${grailsApplication.metadata["app.grails.version"]}${sep}projects${sep}${grailsApplication.metadata["app.name"]}${sep}lucene-index${sep}${grails.util.GrailsUtil.getEnvironment()}" )
            }

            def searchMapping = new SearchMapping()

            // global config
            def hibernateSearchConfig = grailsApplication.config.grails.plugins.hibernatesearch

            if ( hibernateSearchConfig && hibernateSearchConfig instanceof Closure ) {

                def searchMappingGlobalConfig = new SearchMappingGlobalConfig( searchMapping )

                hibernateSearchConfig.delegate = searchMappingGlobalConfig
                hibernateSearchConfig.resolveStrategy = Closure.DELEGATE_FIRST
                hibernateSearchConfig.call()

            }

            // entities config
            grailsApplication.domainClasses.each {

                def searchClosure = ClassPropertyFetcher.forClass( it.clazz ).getStaticPropertyValue( "search", Closure )

                if ( searchClosure ) {

                    def searchMappingEntityConfig = new SearchMappingEntityConfig( searchMapping, it.clazz )

                    searchClosure.delegate = searchMappingEntityConfig
                    searchClosure.resolveStrategy = Closure.DELEGATE_FIRST
                    searchClosure.call()
                }
            }

            properties.put( Environment.MODEL_MAPPING, searchMapping )

        } catch ( Exception e ) {
            logger.error( "Error while indexing entities", e )
        }
    }

}