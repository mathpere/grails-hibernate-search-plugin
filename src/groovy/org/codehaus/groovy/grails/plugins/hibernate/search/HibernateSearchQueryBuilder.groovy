/* Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.grails.plugins.hibernate.search

import org.apache.lucene.search.Filter
import org.apache.lucene.search.Sort
import org.apache.lucene.search.SortField
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher
import org.hibernate.Session
import org.hibernate.search.FullTextQuery
import org.hibernate.search.FullTextSession
import org.hibernate.search.MassIndexer
import org.hibernate.search.Search
import org.hibernate.search.query.dsl.QueryBuilder
import org.codehaus.groovy.grails.plugins.hibernate.search.components.*

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

	private static final String ASC = 'asc'
	private static final String DESC = 'desc'

	private static final List MASS_INDEXER_METHODS = MassIndexer.methods.findAll { it.returnType == MassIndexer }*.name

	private final FullTextSession fullTextSession
	private final clazz
	private final instance
	private final staticContext

	private QueryBuilder queryBuilder
	private MassIndexer massIndexer

	private def sort
	private def sortType
	private def reverse = false
	private def maxResults = 0
	private def offset = 0
	private def filterDefinitions = [:]
	private def projection = []

	private def root
	private def currentNode

	Filter filter

	HibernateSearchQueryBuilder( clazz, instance, Session session ) {
		this.clazz = clazz
		this.fullTextSession = Search.getFullTextSession( session )
		this.instance = instance
		this.staticContext = instance == null
	}

	HibernateSearchQueryBuilder( clazz, Session session ) {
		this( clazz, null, session )
	}

	private FullTextQuery createFullTextQuery( ) {
		def query = fullTextSession.createFullTextQuery(
			root.createQuery(),
			clazz
		)

		filterDefinitions?.each { filterName, filterParams ->

			def filter = query.enableFullTextFilter( filterName )

			filterParams?.each { k, v ->
				filter.setParameter( k, v )
			}
		}

		if ( filter ) {
			query.filter = filter
		}

		if ( projection ) {
			query.setProjection projection as String[]
		}

		query
	}

	private initQueryBuilder( ) {
		queryBuilder = fullTextSession.searchFactory.buildQueryBuilder().forEntity( clazz ).get()
		root = new MustComponent( queryBuilder: queryBuilder )
		currentNode = root
	}

	Object invokeMethod( String name, Object args ) {
		if ( name in MASS_INDEXER_METHODS ) {
			massIndexer = massIndexer.invokeMethod name, args
		} else {
			throw new MissingMethodException( name, getClass(), args )
		}
	}

	def invokeClosureNode( Closure callable ) {
		if ( !callable )
			return

		callable.delegate = this
		callable.resolveStrategy = Closure.DELEGATE_FIRST
		callable.call()
	}
	/**
	 *
	 * @param searchDsl
	 * @return the results for this search
	 */
	def list( searchDsl = null ) {

		initQueryBuilder()

		invokeClosureNode searchDsl

		FullTextQuery fullTextQuery = createFullTextQuery()

		if ( maxResults > 0 ) {
			fullTextQuery.maxResults = maxResults
		}

		fullTextQuery.firstResult = offset

		if ( sort ) {
			fullTextQuery.sort = new Sort( new SortField( sort, sortType, reverse ) )
		}

		fullTextQuery.list()
	}

	/**
	 *
	 * @param searchDsl
	 * @return the number of hits for this search.
	 */
	def count( Closure searchDsl = null ) {

		initQueryBuilder()

		invokeClosureNode searchDsl

		createFullTextQuery().resultSize
	}

	/**
	 * create an initial Lucene index for the data already present in your database
	 *
	 * @param massIndexerDsl
	 */
	def createIndexAndWait( Closure massIndexerDsl = null ) {

		massIndexer = fullTextSession.createIndexer( clazz )

		invokeClosureNode massIndexerDsl

		massIndexer.startAndWait()
	}

	private addComposite( Composite composite, Closure arg ) {

		currentNode << composite
		currentNode = composite

		invokeClosureNode arg

		currentNode = currentNode?.parent ?: root
	}

	private addLeaf( Leaf leaf ) {
		currentNode << leaf
	}

	def must( Closure arg ) {
		addComposite new MustComponent( queryBuilder: queryBuilder ), arg
	}

	def should( Closure arg ) {
		addComposite new ShouldComponent( queryBuilder: queryBuilder ), arg
	}

	def mustNot( Closure arg ) {
		addComposite new MustNotComponent( queryBuilder: queryBuilder ), arg
	}

	def maxResults( int maxResults ) {
		this.maxResults = maxResults
	}

	def offset( int offset ) {
		this.offset = offset
	}

	def projection( String... projection ) {
		this.projection.addAll( projection )
	}

	def sort( String field, String order = ASC, type = null ) {

		this.sort = field
		this.reverse = order == DESC

		if ( type ) {

			switch ( type.class ) {
				case Class:
					this.sortType = SORT_TYPES[type]
					break

				case String:
					this.sortType = SortField."${type.toUpperCase()}"
					break

				case int:
				case Integer:
					this.sortType = type
					break
			}

		} else {
			this.sortType = SORT_TYPES[ClassPropertyFetcher.forClass( clazz ).getPropertyType( sort )] ?: SortField.STRING
		}
	}

	/**
	 * Execute code within programmatic hibernate transaction
	 *
	 * @param callable a closure which takes an Hibernate Transaction as single argument.
	 * @return the result of callable
	 */
	def withTransaction( Closure callable ) {

		def transaction = fullTextSession.beginTransaction()

		try {

			def result = callable.call( transaction )

			if ( transaction.isActive() ) {
				transaction.commit()
			}

			result

		} catch ( ex ) {
			transaction.rollback()
			throw ex
		}
	}

	/**
	 *
	 * @return the scoped analyzer for this entity
	 */
	def getAnalyzer( ) {
		fullTextSession.searchFactory.getAnalyzer( clazz )
	}

	/**
	 * Force the (re)indexing of a given <b>managed</b> object.
	 * Indexation is batched per transaction: if a transaction is active, the operation
	 * will not affect the index at least until commit.
	 */
	def index( ) {
		if ( !staticContext ) {
			fullTextSession.index( instance )
		} else {
			throw new MissingMethodException( "index", getClass(), [] as Object[] )
		}
	}

	/**
	 * Remove the entity from the index.
	 */
	def purge( ) {
		if ( !staticContext ) {
			fullTextSession.purge( clazz, instance.id )
		} else {
			throw new MissingMethodException( "purge", getClass(), [] as Object[] )
		}
	}

	def purgeAll( ) {
		fullTextSession.purgeAll( clazz )
	}

	def filter( String filterName ) {
		filterDefinitions.put filterName, null
	}

	def filter( Map filterParams ) {
		filterDefinitions.put filterParams.name, filterParams.params
	}

	def below( field, below, Map optionalParams = [:] ) {
		addLeaf new BelowComponent( [queryBuilder: queryBuilder, field: field, below: below] + optionalParams )
	}

	def above( field, above, Map optionalParams = [:] ) {
		addLeaf new AboveComponent( [queryBuilder: queryBuilder, field: field, above: above] + optionalParams )
	}

	def between( field, from, to, Map optionalParams = [:] ) {
		addLeaf new BetweenComponent( [queryBuilder: queryBuilder, field: field, from: from, to: to] + optionalParams )
	}

	def keyword( field, matching, Map optionalParams = [:] ) {
		addLeaf new KeywordComponent( [queryBuilder: queryBuilder, field: field, matching: matching] + optionalParams )
	}

	def fuzzy( field, matching, Map optionalParams = [:] ) {
		addLeaf new FuzzyComponent( [queryBuilder: queryBuilder, field: field, matching: matching] + optionalParams )
	}

	def wildcard( field, matching, Map optionalParams = [:] ) {
		addLeaf new WildcardComponent( [queryBuilder: queryBuilder, field: field, matching: matching] + optionalParams )
	}

	def phrase( field, sentence, Map optionalParams = [:] ) {
		addLeaf new PhraseComponent( [queryBuilder: queryBuilder, field: field, sentence: sentence] + optionalParams )
	}

}
