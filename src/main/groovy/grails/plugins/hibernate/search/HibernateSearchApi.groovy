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
package grails.plugins.hibernate.search

import grails.core.GrailsClass
import grails.plugins.hibernate.search.component.*
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.search.Sort
import org.apache.lucene.search.SortField
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher
import org.hibernate.Criteria
import org.hibernate.Session
import org.hibernate.Transaction
import org.hibernate.search.FullTextQuery
import org.hibernate.search.FullTextSession
import org.hibernate.search.MassIndexer
import org.hibernate.search.Search
import org.hibernate.search.cfg.PropertyDescriptor
import org.hibernate.search.filter.FullTextFilter
import org.hibernate.search.query.dsl.QueryBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@SuppressWarnings('GroovyUnusedDeclaration')
class HibernateSearchApi {

    private final static Logger log = LoggerFactory.getLogger(HibernateSearchApi)

    private static final Map<Object, SortField.Type> SORT_TYPES = [(Integer)   : SortField.Type.INT,
                                                                   (Double)    : SortField.Type.DOUBLE,
                                                                   (Float)     : SortField.Type.FLOAT,
                                                                   (String)    : SortField.Type.STRING_VAL,
                                                                   (Long)      : SortField.Type.LONG,
                                                                   (BigDecimal): SortField.Type.DOUBLE,

                                                                   // see Emmanuel Bernard's comment
                                                                   // https://hibernate.onjira.com/browse/HSEARCH-97
                                                                   (Date)      : SortField.Type.STRING,
    ]

    private static final String ASC = 'asc'
    private static final String DESC = 'desc'

    private static final List MASS_INDEXER_METHODS = MassIndexer.methods.findAll {it.returnType == MassIndexer}*.name

    private final HibernateSearchConfig pluginConfig

    private final FullTextSession fullTextSession
    private final Class clazz
    private final instance
    private final staticContext

    private QueryBuilder queryBuilder
    private MassIndexer massIndexer

    private String sort
    private SortField.Type sortType
    private Boolean reverse = false
    private Integer maxResults = 0
    private Integer offset = 0
    private final Map<String, Map<String, Object>> filterDefinitions = [:]
    private final List projection = []
    private Criteria criteria

    private Component root
    private Component currentNode

    HibernateSearchApi(GrailsClass domainClass, instance, Session session, HibernateSearchConfig pluginConfig) {
        this.clazz = domainClass.clazz
        this.fullTextSession = Search.getFullTextSession(session)
        this.instance = instance
        this.staticContext = instance == null
        this.pluginConfig = pluginConfig
    }

    HibernateSearchApi(GrailsClass domainClass, Session session, HibernateSearchConfig pluginConfig) {
        this(domainClass, null, session, pluginConfig)
    }

    HibernateSearchApi(Class clazz) {
        this.clazz = clazz
        this.fullTextSession = null
        this.instance = null
        this.staticContext = true
        this.pluginConfig = null
    }

    /**
     *
     * @param searchDsl
     * @return the results for this search
     */
    List list(@DelegatesTo(HibernateSearchApi) Closure searchDsl = null) {

        initQueryBuilder()

        invokeClosureNode searchDsl

        FullTextQuery fullTextQuery = createFullTextQuery()

        if (maxResults > 0) {
            fullTextQuery.maxResults = maxResults
        }

        fullTextQuery.firstResult = offset

        if (sort) {
            fullTextQuery.sort = new Sort(new SortField(sort, sortType, reverse))
        }

        fullTextQuery.list()
    }

    /**
     *
     * @param searchDsl
     * @return the number of hits for this search.
     */
    int count(@DelegatesTo(HibernateSearchApi) Closure searchDsl = null) {

        initQueryBuilder()

        invokeClosureNode searchDsl

        createFullTextQuery().resultSize
    }

    /**
     * create an initial Lucene index for the data already present in your database
     *
     * @param massIndexerDsl
     */
    void createIndexAndWait(@DelegatesTo(HibernateSearchApi) Closure massIndexerDsl = null) {

        massIndexer = fullTextSession.createIndexer(clazz)

        invokeClosureNode massIndexerDsl

        massIndexer.startAndWait()
    }

    void must(Closure arg) {
        addComposite new MustComponent(queryBuilder: queryBuilder), arg
    }

    void should(Closure arg) {
        addComposite new ShouldComponent(queryBuilder: queryBuilder), arg
    }

    void mustNot(Closure arg) {
        addComposite new MustNotComponent(queryBuilder: queryBuilder), arg
    }

    void maxResults(int maxResults) {
        this.maxResults = maxResults
    }

    void offset(int offset) {
        this.offset = offset
    }

    void projection(String... projection) {
        this.projection.addAll(projection)
    }

    void criteria(Closure criteria) {
        this.criteria = fullTextSession.createCriteria(clazz)

        criteria.delegate = this.criteria
        criteria.resolveStrategy = Closure.DELEGATE_FIRST
        this.criteria = criteria.call() as Criteria

        log.debug 'setting criteria: {}', this.criteria
    }

    void sort(String field, String order = ASC, type = null) {

        this.sort = field
        this.reverse = order == DESC

        if (type) {

            switch (type.class) {
                case Class:
                    this.sortType = SORT_TYPES[type]
                    break

                case String:
                    this.sortType = SortField.Type."${type.toUpperCase()}"
                    break

                case int:
                case Integer:
                    this.sortType = type
                    break
            }

        } else {
            this.sortType = SORT_TYPES[ClassPropertyFetcher.forClass(clazz).getPropertyType(sort)] ?: SortField.Type.STRING
        }
    }

    /**
     * Execute code within programmatic hibernate transaction
     *
     * @param callable a closure which takes an Hibernate Transaction as single argument.
     * @return the result of callable
     */
    void withTransaction(Closure callable) {

        Transaction transaction = fullTextSession.beginTransaction()

        try {

            def result = callable.call(transaction)

            if (transaction.isActive()) {
                transaction.commit()
            }

            result

        } catch (ex) {
            transaction.rollback()
            throw ex
        }
    }

    /**
     *
     * @return the scoped analyzer for this entity
     */
    Analyzer getAnalyzer() {
        fullTextSession.searchFactory.getAnalyzer(clazz)
    }

    /**
     * Force the (re)indexing of a given <b>managed</b> object.
     * Indexation is batched per transaction: if a transaction is active, the operation
     * will not affect the index at least until commit.
     */
    void index() {
        if (!staticContext) {
            fullTextSession.index(instance)
        } else {
            throw new MissingMethodException('index', getClass(), [] as Object[])
        }
    }

    /**
     * Remove the entity from the index.
     */
    void purge() {
        if (!staticContext) {
            fullTextSession.purge(clazz, instance.id as Serializable)
        } else {
            throw new MissingMethodException('purge', getClass(), [] as Object[])
        }
    }

    void purgeAll() {
        fullTextSession.purgeAll(clazz)
    }

    void filter(String filterName) {
        filterDefinitions.put filterName, null
    }

    void filter(Map<String, Object> filterParams) {
        filterDefinitions.put filterParams.name as String, filterParams.params as Map<String, Object>
    }

    void filter(String filterName, Map<String, Object> filterParams) {
        filterDefinitions.put filterName, filterParams
    }

    void below(field, below, Map optionalParams = [:]) {
        addLeaf new BelowComponent([queryBuilder: queryBuilder, field: field, below: below] + optionalParams)
    }

    void above(field, above, Map optionalParams = [:]) {
        addLeaf new AboveComponent([queryBuilder: queryBuilder, field: field, above: above] + optionalParams)
    }

    void between(field, from, to, Map optionalParams = [:]) {
        addLeaf new BetweenComponent([queryBuilder: queryBuilder, field: field, from: from, to: to] + optionalParams)
    }

    void keyword(field, matching, Map optionalParams = [:]) {
        addLeaf new KeywordComponent([queryBuilder: queryBuilder, field: field, matching: matching] + optionalParams)
    }

    void fuzzy(field, matching, Map optionalParams = [:]) {
        addLeaf new FuzzyComponent([queryBuilder: queryBuilder, field: field, matching: matching] + optionalParams)
    }

    void wildcard(field, matching, Map optionalParams = [:]) {
        addLeaf new WildcardComponent([queryBuilder: queryBuilder, field: field, matching: matching] + optionalParams)
    }

    void phrase(field, sentence, Map optionalParams = [:]) {
        addLeaf new PhraseComponent([queryBuilder: queryBuilder, field: field, sentence: sentence] + optionalParams)
    }

    Object invokeMethod(String name, Object args) {
        if (name in MASS_INDEXER_METHODS) {
            massIndexer = massIndexer.invokeMethod(name, args) as MassIndexer
        } else {
            throw new MissingMethodException(name, getClass(), args)
        }
    }

    void invokeClosureNode(@DelegatesTo(HibernateSearchApi) Closure callable) {
        if (!callable)
            return

        callable.delegate = this
        callable.resolveStrategy = Closure.DELEGATE_FIRST
        callable.call()
    }

    Map<String, PropertyDescriptor> getIndexedProperties() {
        this.pluginConfig.getIndexedPropertiesByEntity()[clazz.getName()]
    }

    private FullTextQuery createFullTextQuery() {
        FullTextQuery query = fullTextSession.createFullTextQuery(root.createQuery(), clazz)

        filterDefinitions?.each {filterName, filterParams ->

            FullTextFilter filter = query.enableFullTextFilter(filterName)

            filterParams?.each {k, v ->
                filter.setParameter(k, v)
            }
        }

        if (criteria) {
            log.info 'add criteria query: {}', criteria
            query.setCriteriaQuery(criteria)
        }

        if (projection) {
            query.setProjection projection as String[]
        }

        query
    }

    private initQueryBuilder() {
        queryBuilder = fullTextSession.searchFactory.buildQueryBuilder().forEntity(clazz).get()
        root = new MustComponent(queryBuilder: queryBuilder)
        currentNode = root
    }

    private void addComposite(Composite composite, Closure arg) {

        currentNode << composite
        currentNode = composite

        invokeClosureNode arg

        currentNode = currentNode?.parent ?: root
    }

    private void addLeaf(Leaf leaf) {
        currentNode << leaf
    }
}