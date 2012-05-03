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

import org.hibernate.search.annotations.FilterCacheModeType
import org.hibernate.search.cfg.SearchMapping

class SearchMappingGlobalConfig {

    def grailsApplication

    private static final String ANALYZER = 'analyzer'
    private static final String FILTER = 'filter'
    private static final String FULL_TEXT_FILTER = 'fullTextFilter'

    private def currentMapping

    void processGlobalConfig( SearchMapping searchMapping ) {

        this.currentMapping = searchMapping

        def hibernateSearchConfig = grailsApplication.config.grails.plugins.hibernatesearch

        if ( !hibernateSearchConfig || !hibernateSearchConfig instanceof Closure ) {
            return
        }

        hibernateSearchConfig.delegate = this
        hibernateSearchConfig.resolveStrategy = Closure.DELEGATE_FIRST
        hibernateSearchConfig.call()
    }

    def invokeMethod( String name, obj ) {

        def args = obj[0]

        switch ( name ) {

            case FULL_TEXT_FILTER:

                currentMapping = currentMapping.fullTextFilterDef( args.name, args.impl )

                if ( args.cache ) {
                    currentMapping = currentMapping.cache( FilterCacheModeType."${args.cache.toUpperCase()}" )
                }

                break

            case ANALYZER:

                currentMapping = currentMapping.analyzerDef( args.name, args.tokenizer )

                if ( obj.size() > 1 ) {

                    def filters = obj[1]
                    if ( filters && filters instanceof Closure ) {

                        filters.delegate = this
                        filters.resolveStrategy = Closure.DELEGATE_FIRST
                        filters.call()
                    }
                }

                break

            case FILTER:

                if ( args instanceof Class ) {

                    currentMapping = currentMapping.filter( args )

                } else if ( args instanceof Map ) {

                    currentMapping = currentMapping.filter( args.factory )

                    args.params?.each { k, v ->
                        currentMapping.param( k.toString(), v.toString() )
                    }
                }

                break
        }
    }

}