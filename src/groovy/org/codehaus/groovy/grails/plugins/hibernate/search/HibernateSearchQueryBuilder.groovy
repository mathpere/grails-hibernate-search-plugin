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

import org.apache.lucene.search.Sort
import org.apache.lucene.search.SortField
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher
import org.hibernate.search.FullTextQuery
import org.hibernate.search.FullTextSession
import org.hibernate.search.Search
import org.hibernate.search.query.dsl.QueryBuilder
import org.apache.lucene.search.Query
import org.hibernate.search.query.dsl.QueryCustomization
import org.hibernate.search.query.dsl.FieldCustomization

class HibernateSearchQueryBuilder {

    private static final def SORT_TYPES = [( Integer ): SortField.INT,
            ( Double ): SortField.DOUBLE,
            ( Float ): SortField.FLOAT,
            ( String ): SortField.STRING_VAL,
            ( Long ): SortField.LONG,
            ( BigDecimal ): SortField.DOUBLE,

            // see Emmanuel Bernard's comment
            // https://hibernate.onjira.com/browse/HSEARCH-97
            ( Date ): SortField.STRING]

    private static interface Component {
        Query createQuery( )
    }

    private static abstract class Composite implements Component {

        QueryBuilder queryBuilder
        def parent
        def children = []

        def leftShift( component ) {
            assert component instanceof Component: "'component' should be an instance of Component"
            component.parent = this
            children << component
        }
    }

    private static abstract class Leaf extends Composite {
        def field

        def ignoreAnalyzer = false
        def ignoreFieldBridge = false

        def leftShift( component ) {
            throw new UnsupportedOperationException( "${this.class.name} is a leaf" )
        }

        abstract FieldCustomization createFieldCustomization( )

        FieldCustomization getFieldCustomization( ) {

            def field = createFieldCustomization()

            if ( ignoreAnalyzer ) { field = field.ignoreAnalyzer() }

            if ( ignoreFieldBridge ) { field = field.ignoreFieldBridge() }

            field
        }
    }

    private static class MustNotComponent extends Composite {
        Query createQuery( ) {
            if ( children ) {

                def query = queryBuilder.bool()

                children*.createQuery().each {
                    query = query.must( it ).not()
                }

                query.createQuery()

            } else {
                queryBuilder.all().createQuery()
            }
        }
    }
    private static class MustComponent extends Composite {
        Query createQuery( ) {
            if ( children ) {

                def query = queryBuilder.bool()

                children*.createQuery().each {
                    query = query.must( it )
                }

                query.createQuery()

            } else {
                queryBuilder.all().createQuery()
            }
        }
    }

    private static class ShouldComponent extends Composite {
        Query createQuery( ) {
            if ( children ) {

                def query = queryBuilder.bool()

                children*.createQuery().each {
                    query = query.should( it )
                }

                query.createQuery()

            } else {
                queryBuilder.all().createQuery()
            }
        }
    }

    private static class BelowComponent extends Leaf {
        def below

        Query createQuery( ) { fieldCustomization.below( below ).createQuery() }

        FieldCustomization createFieldCustomization( ) { queryBuilder.range().onField( field ) }
    }

    private static class AboveComponent extends Leaf {
        def above

        Query createQuery( ) { fieldCustomization.above( above ).createQuery() }

        FieldCustomization createFieldCustomization( ) { queryBuilder.range().onField( field ) }
    }

    private static class KeywordComponent extends Leaf {
        def matching

        Query createQuery( ) { fieldCustomization.matching( matching ).createQuery() }

        FieldCustomization createFieldCustomization( ) { queryBuilder.keyword().onField( field ) }
    }

    private static class BetweenComponent extends Leaf {
        def from
        def to

        Query createQuery( ) { fieldCustomization.from( from ).to( to ).createQuery() }

        FieldCustomization createFieldCustomization( ) { queryBuilder.range().onField( field ) }
    }

    private static class FuzzyComponent extends Leaf {
        def matching

        Query createQuery( ) { fieldCustomization.matching( matching ).createQuery() }

        FieldCustomization createFieldCustomization( ) { queryBuilder.keyword().fuzzy().onField( field ) }
    }

    private static class WildcardComponent extends Leaf {
        def matching

        Query createQuery( ) { fieldCustomization.matching( matching ).createQuery() }

        FieldCustomization createFieldCustomization( ) { queryBuilder.keyword().wildcard().onField( field ) }
    }

    private static class PhraseComponent extends Leaf {
        def sentence

        Query createQuery( ) { fieldCustomization.sentence( sentence ).createQuery() }

        FieldCustomization createFieldCustomization( ) { queryBuilder.phrase().onField( field ) }
    }

    private static final String LIST = 'list'
    private static final String COUNT = 'count'
    private static final String GET_ANALYZER = 'getAnalyzer'

    private static final String SHOULD = 'should'
    private static final String MUST = 'must'
    private static final String MUST_NOT = 'mustNot'
    private static final String BELOW = 'below'
    private static final String ABOVE = 'above'
    private static final String BETWEEN = 'between'
    private static final String KEYWORD = 'keyword'
    private static final String FUZZY = 'fuzzy'
    private static final String WILDCARD = 'wildcard'
    private static final String PHRASE = 'phrase'
    private static final String MAX_RESULTS = 'maxResults'
    private static final String OFFSET = 'offset'
    private static final String SORT = 'sort'
    private static final String DESC = 'desc'
    private static final String FILTER = 'filter'

    private final QueryBuilder queryBuilder
    private final FullTextSession fullTextSession
    private final clazz

    def sort
    def reverse = false
    def maxResults = 0
    def offset = 0
    def filterDefinitions = [:]

    def root
    def currentNode

    public HibernateSearchQueryBuilder( clazz, session ) {
        this.clazz = clazz
        this.fullTextSession = Search.getFullTextSession( session )
        this.queryBuilder = fullTextSession.searchFactory.buildQueryBuilder().forEntity( clazz ).get()
        this.root = new MustComponent( queryBuilder: queryBuilder )
    }

    private FullTextQuery createFullTextQuery( ) {
        def query = fullTextSession.createFullTextQuery( root.createQuery(), clazz )

        filterDefinitions?.each { filterName, filterParams ->

            def filter = query.enableFullTextFilter( filterName )

            filterParams?.each { k, v ->
                filter.setParameter( k, v )
            }
        }

        query
    }

    public Object invokeMethod( String name, Object obj ) {

        def args = obj.class.isArray() ? obj : [obj]

        if ( args.size() == 1 && args[0] instanceof Closure ) {

            def composite

            currentNode = ( currentNode?.parent ) ?: root

            switch ( name ) {
                case MUST:
                    composite = new MustComponent( queryBuilder: queryBuilder )
                    break

                case SHOULD:
                    composite = new ShouldComponent( queryBuilder: queryBuilder )
                    break

                case MUST_NOT:
                    composite = new MustNotComponent( queryBuilder: queryBuilder )
                    break
            }

            if ( composite ) {
                currentNode << composite
                currentNode = composite
            }

            invokeClosureNode( args[0] )

        } else {

            def leaf

            switch ( name ) {

                case BELOW:
                    def params = [queryBuilder: queryBuilder, field: args[0], below: args[1]]
                    if ( args.size() == 3 ) { params.putAll args[2] }
                    leaf = new BelowComponent( params )
                    break

                case ABOVE:
                    def params = [queryBuilder: queryBuilder, field: args[0], above: args[1]]
                    if ( args.size() == 3 ) { params.putAll args[2] }
                    leaf = new AboveComponent( params )
                    break

                case BETWEEN:
                    def params = [queryBuilder: queryBuilder, field: args[0], from: args[1], to: args[2]]
                    if ( args.size() == 4 ) { params.putAll args[3] }
                    leaf = new BetweenComponent( params )
                    break

                case ABOVE:
                    def params = [queryBuilder: queryBuilder, field: args[0], above: args[1]]
                    if ( args.size() == 3 ) { params.putAll args[2] }
                    leaf = new AboveComponent( params )
                    break

                case KEYWORD:
                    def params = [queryBuilder: queryBuilder, field: args[0], matching: args[1]]
                    if ( args.size() == 3 ) { params.putAll args[2] }
                    leaf = new KeywordComponent( params )
                    break

                case FUZZY:
                    def params = [queryBuilder: queryBuilder, field: args[0], matching: args[1]]
                    if ( args.size() == 3 ) { params.putAll args[2] }
                    leaf = new FuzzyComponent( params )
                    break

                case WILDCARD:
                    def params = [queryBuilder: queryBuilder, field: args[0], matching: args[1]]
                    if ( args.size() == 3 ) { params.putAll args[2] }
                    leaf = new WildcardComponent( params )
                    break

                case PHRASE:
                    def params = [queryBuilder: queryBuilder, field: args[0], sentence: args[1]]
                    if ( args.size() == 3 ) { params.putAll args[2] }
                    leaf = new PhraseComponent( params )
                    break

                case MAX_RESULTS:
                    maxResults = args[0]
                    break

                case OFFSET:
                    offset = args[0]
                    break

                case SORT:
                    sort = args[0]
                    reverse = args.size() == 2 && args[1] == DESC
                    break

                case FILTER:

                    if ( args[0] instanceof Map ) {
                        def filter = args[0]
                        filterDefinitions.put filter.name, filter.params
                    } else {
                        filterDefinitions.put args[0], null
                    }

                    break
            }

            if ( leaf ) {
                currentNode << leaf
            }
        }

        switch ( name ) {

            case LIST:

                FullTextQuery fullTextQuery = createFullTextQuery()

                if ( maxResults > 0 ) {
                    fullTextQuery.maxResults = maxResults
                }

                fullTextQuery.firstResult = offset

                if ( sort ) {

                    def sortType = SORT_TYPES[ClassPropertyFetcher.forClass( clazz ).getPropertyType( sort )]

                    if ( sortType ) {
                        fullTextQuery.sort = new Sort( new SortField( sort, sortType, reverse ) )
                    }
                }

                fullTextQuery.list()

                break

            case COUNT:

                FullTextQuery fullTextQuery = createFullTextQuery()
                fullTextQuery.resultSize

                break

            case GET_ANALYZER:

                fullTextSession.getSearchFactory().getAnalyzer( clazz )

                break
        }
    }

    def invokeClosureNode( callable ) {
        callable.delegate = this
        callable.resolveStrategy = Closure.DELEGATE_FIRST
        callable.call()
    }
}