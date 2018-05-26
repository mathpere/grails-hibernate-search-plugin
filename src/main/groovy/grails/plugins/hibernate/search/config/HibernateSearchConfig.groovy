package grails.plugins.hibernate.search.config

import org.hibernate.Session
import org.hibernate.search.FullTextSession
import org.hibernate.search.MassIndexer
import org.hibernate.search.Search
import org.hibernate.search.cfg.PropertyDescriptor
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@SuppressWarnings('GroovyUnusedDeclaration')
class HibernateSearchConfig {

    private static final List MASS_INDEXER_METHODS = MassIndexer.methods.findAll {it.returnType == MassIndexer}*.name

    private final static Logger log = LoggerFactory.getLogger(HibernateSearchConfig)

    private MassIndexer massIndexer
    private final FullTextSession fullTextSession
    boolean throwExceptionOnEmptyQuery

    Map<String, Map<String, PropertyDescriptor>> indexedPropertiesByEntity

    HibernateSearchConfig(Session session, Map<String, Map<String, PropertyDescriptor>> indexedPropertiesByEntity) {
        this.fullTextSession = Search.getFullTextSession(session)
        this.indexedPropertiesByEntity = indexedPropertiesByEntity
		
		log.trace "build HibernateSearchConfig indexedPropertiesByEntity=${indexedPropertiesByEntity}"
    }

    /**
     *
     * Rebuild the indexes of all indexed entity types with custom config
     *
     */
    def rebuildIndexOnStart(Closure massIndexerDsl) {
        log.debug 'Start rebuilding indexes of all indexed entity types...'
        massIndexer = fullTextSession.createIndexer()
        invokeClosureNode massIndexerDsl
        massIndexer.startAndWait()
    }

    /**
     *
     * Rebuild the indexes of all indexed entity types with default options:
     * - CacheMode.IGNORE
     * - purgeAllOnStart = true
     * - optimizeAfterPurge = true
     * - optimizeOnFinish = true
     *
     */
    def rebuildIndexOnStart(boolean rebuild) {
        if (!rebuild) return

        log.debug 'Start rebuilding indexes of all indexed entity types...'
        massIndexer = fullTextSession.createIndexer().startAndWait()
    }

    /**
     * Throws exception if Hibernate Search raises an EmptyQueryException, (could occur if analyzer has stop words) default false
     */
    def throwOnEmptyQuery(boolean throwException) {
        log.debug 'throwExceptionOnEmptyQuery = {}', throwException
        throwExceptionOnEmptyQuery = throwException
    }

    Object invokeMethod(String name, Object args) {
        if (name in MASS_INDEXER_METHODS) {
            massIndexer = massIndexer.invokeMethod(name, args) as MassIndexer
        }
        // makes it possible to ignore not concerned config
    }

    void invokeClosureNode(Closure callable) {
        if (!callable) return

        callable.delegate = this
        callable.resolveStrategy = Closure.DELEGATE_FIRST
        callable.call()
    }
}
