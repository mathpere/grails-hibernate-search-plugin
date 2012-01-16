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

import java.lang.annotation.ElementType
import org.codehaus.groovy.grails.commons.ClassPropertyFetcher
import org.codehaus.groovy.grails.orm.hibernate.ConfigurableLocalSessionFactoryBean
import org.hibernate.HibernateException
import org.hibernate.cfg.Configuration
import org.hibernate.search.Environment
import org.hibernate.search.annotations.Index
import org.hibernate.search.annotations.Resolution
import org.hibernate.search.annotations.Store
import org.hibernate.search.cfg.SearchMapping
import org.springframework.jndi.JndiTemplate

class SearchMappingConfigurableLocalSessionFactoryBean extends ConfigurableLocalSessionFactoryBean {

    private static final String DIRECTORY_PROVIDER = "hibernate.search.default.directory_provider"
    private static final String INDEX_BASE = "hibernate.search.default.indexBase"
    private static final String INDEX_BASE_JNDI_NAME = "hibernate.search.default.indexBaseJndiName"

    def currentMapping

    @Override
    protected void postProcessConfiguration(Configuration config) throws HibernateException {
        super.postProcessConfiguration(config)

        try {

            def properties = config.getProperties()

            if ( !properties.containsKey(DIRECTORY_PROVIDER) ) {
                properties.setProperty(DIRECTORY_PROVIDER, "filesystem")
            }

            if ( properties.containsKey(INDEX_BASE_JNDI_NAME) ) {
                def jndiName = properties.getProperty(INDEX_BASE_JNDI_NAME)
                properties.setProperty(INDEX_BASE, new JndiTemplate().lookup(jndiName))
            }

            if ( !properties.containsKey(INDEX_BASE) ) {
                def sep = File.separator
                properties.setProperty(INDEX_BASE, "${System.properties['user.home']}${sep}.grails${sep}${grailsApplication.metadata["app.grails.version"]}${sep}projects${sep}${grailsApplication.metadata["app.name"]}${sep}lucene-index${sep}${grails.util.GrailsUtil.getEnvironment()}")
            }

            SearchMapping searchMapping = new org.hibernate.search.cfg.SearchMapping()

            grailsApplication.domainClasses.each {

                ClassPropertyFetcher cpf = ClassPropertyFetcher.forClass(it.clazz);
                def searchCallable = cpf.getStaticPropertyValue("search", Closure)

                if ( searchCallable ) {

                    currentMapping = searchMapping.entity(it.clazz).indexed().property("id", ElementType.FIELD).documentId()

                    searchCallable.delegate = this
                    searchCallable.resolveStrategy = Closure.DELEGATE_FIRST
                    searchCallable.call()
                }
            }

            properties.put(Environment.MODEL_MAPPING, searchMapping)

        } catch (Exception e) {
            logger.error("Error while indexing entities", e)
        }
    }

    public Object invokeMethod(String name, Object obj) {

        obj = obj.class.isArray() ? obj : [obj]

        def args = obj[0] ?: [:]

        if ( args.indexEmbedded ) {

            currentMapping = currentMapping.property(name, ElementType.FIELD).indexEmbedded()

        } else if ( args.containedIn ) {

            currentMapping = currentMapping.property(name, ElementType.FIELD).containedIn()

        } else {
            currentMapping = currentMapping.property(name, ElementType.FIELD).field()

            if ( args.index ) {
                currentMapping = currentMapping.index(Index."${args.index.toUpperCase()}")
            }

            if ( args.store ) {
                currentMapping = currentMapping.store(Store."${args.store.toUpperCase()}")
            }

            if ( args.numeric ) {
                currentMapping = currentMapping.numericField().precisionStep(args.numeric)
            }

            if ( args.date ) {
                currentMapping = currentMapping.dateBridge(Resolution."${args.date.toUpperCase()}")
            }

            if ( args.bridge ) {

                currentMapping = currentMapping.bridge(args.bridge["class"])

                def params = args.bridge["params"]

                if ( params ) {
                    params.each {k, v ->
                        currentMapping = currentMapping.param(k.toString(), v.toString())
                    }
                }
            }
        }
    }
}