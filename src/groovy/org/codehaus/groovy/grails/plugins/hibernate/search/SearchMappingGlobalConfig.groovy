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

    private def searchMapping

    public SearchMappingGlobalConfig( SearchMapping searchMapping ) {
        this.searchMapping = searchMapping
    }

    def analyzer( Map args, Closure filters = null ) {
        searchMapping = searchMapping.analyzerDef( args.name, args.tokenizer )

        if ( filters ) {
            filters.delegate = this
            filters.resolveStrategy = Closure.DELEGATE_FIRST
            filters.call()
        }
    }

    def filter( Class filterImpl ) {
        searchMapping = searchMapping.filter( filterImpl )
    }

    def filter( Map filterParams ) {
        searchMapping = searchMapping.filter( filterParams.factory )

        filterParams.params?.each { k, v ->
            searchMapping.param( k.toString(), v.toString() )
        }
    }

    def fullTextFilter( Map fullTextFilterParams ) {
        searchMapping = searchMapping.fullTextFilterDef( fullTextFilterParams.name, fullTextFilterParams.impl )

        if ( fullTextFilterParams.cache ) {
            searchMapping = searchMapping.cache( FilterCacheModeType."${fullTextFilterParams.cache.toUpperCase()}" )
        }
    }

    Object invokeMethod( String name, Object args ) {
        // makes it possible to ignore not concerned config
    }
}