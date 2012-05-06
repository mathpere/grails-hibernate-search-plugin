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

    def analyzer( Map args, Closure filters = null ) {
        currentMapping = currentMapping.analyzerDef( args.name, args.tokenizer )

        if ( filters ) {
            filters.delegate = this
            filters.resolveStrategy = Closure.DELEGATE_FIRST
            filters.call()
        }
    }

    def filter( Class filterImpl ) {
        currentMapping = currentMapping.filter( filterImpl )
    }

    def filter( Map filterParams ) {
        currentMapping = currentMapping.filter( filterParams.factory )

        filterParams.params?.each { k, v ->
            currentMapping.param( k.toString(), v.toString() )
        }
    }

    def fullTextFilter( Map fullTextFilterParams ) {
        currentMapping = currentMapping.fullTextFilterDef( fullTextFilterParams.name, fullTextFilterParams.impl )

        if ( fullTextFilterParams.cache ) {
            currentMapping = currentMapping.cache( FilterCacheModeType."${fullTextFilterParams.cache.toUpperCase()}" )
        }
    }
}