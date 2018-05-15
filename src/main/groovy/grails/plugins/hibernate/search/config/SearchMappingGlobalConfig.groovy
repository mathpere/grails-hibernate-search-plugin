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
package grails.plugins.hibernate.search.config

import org.hibernate.search.annotations.FilterCacheModeType
import org.hibernate.search.cfg.FullTextFilterDefMapping
import org.hibernate.search.cfg.SearchMapping

@SuppressWarnings('GroovyUnusedDeclaration')
class SearchMappingGlobalConfig {

    private SearchMapping searchMapping
    private currentDef

    SearchMappingGlobalConfig(SearchMapping searchMapping) {
        this.searchMapping = searchMapping
    }

    void analyzer(Map args, @DelegatesTo(SearchMappingGlobalConfig) Closure filters = null) {
        currentDef = searchMapping.analyzerDef(args.name, args.tokenizer)

        if (filters) {
            filters.delegate = currentDef
            filters.resolveStrategy = Closure.DELEGATE_FIRST
            filters.call()
        }
    }

    void normalizer(Map args, @DelegatesTo(SearchMappingGlobalConfig) Closure filters = null) {
        currentDef = searchMapping.normalizerDef(args.name)

        if (filters) {
            filters.delegate = currentDef
            filters.resolveStrategy = Closure.DELEGATE_FIRST
            filters.call()
        }
    }

    void filter(Class filterImpl) {
        currentDef.filter(filterImpl)
    }

    void filter(Map filterParams) {
        currentDef.filter(filterParams.factory)

        filterParams.params?.each {k, v ->
            currentDef.param(k.toString(), v.toString())
        }
    }

    void fullTextFilter(Map<String, Object> fullTextFilterParams) {
        FullTextFilterDefMapping filterDef = searchMapping.fullTextFilterDef(fullTextFilterParams.name as String, fullTextFilterParams.impl as Class)

        if (fullTextFilterParams.cache) {
            filterDef.cache(FilterCacheModeType.valueOf((fullTextFilterParams.cache as String).toUpperCase()))
        }
    }

    Object invokeMethod(String name, Object args) {
        // makes it possible to ignore not concerned config
    }
}