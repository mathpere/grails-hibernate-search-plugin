package grails.plugins.hibernate.search.component

import grails.plugins.hibernate.search.HibernateSearchGrailsPlugin
import org.apache.lucene.search.Query
import org.hibernate.search.exception.EmptyQueryException
import org.hibernate.search.query.dsl.BooleanJunction
import org.hibernate.search.query.dsl.QueryBuilder

abstract class Composite extends Component {

    protected QueryBuilder queryBuilder

    /**
     * @return true if composite contains at least one valid (not empty) query
     */
    protected boolean forEachQuery(Closure action) {

        boolean notEmpty = false
        if (children) {

            for (child in children) {
                try {

                    Query subQuery = child.createQuery()

                    action.delegate = subQuery
                    action.resolveStrategy = Closure.DELEGATE_FIRST
                    action.call(subQuery)
                    notEmpty = true

                } catch (EmptyQueryException e) {
                    if (HibernateSearchGrailsPlugin.pluginConfig.throwExceptionOnEmptyQuery) {
                        throw e
                    }
                    else {
                        log.warn 'empty Hibernate search query ignored! ' + child, e
                    }
                }
            }
        }
        notEmpty
    }
}

class MustNotComponent extends Composite {
    Query createQuery() {

        BooleanJunction query = queryBuilder.bool()
        boolean notEmpty = forEachQuery {subQuery ->
            query = query.must(subQuery).not()
        }

        notEmpty ? query.createQuery() : queryBuilder.all().createQuery()
    }
}

class MustComponent extends Composite {
    Query createQuery() {
        BooleanJunction query = queryBuilder.bool()
        boolean notEmpty = forEachQuery {subQuery ->
            query = query.must(subQuery)
        }

        notEmpty ? query.createQuery() : queryBuilder.all().createQuery()
    }
}

class ShouldComponent extends Composite {
    Query createQuery() {
        BooleanJunction query = queryBuilder.bool()
        boolean notEmpty = forEachQuery {subQuery ->
            query = query.should(subQuery)
        }

        notEmpty ? query.createQuery() : queryBuilder.all().createQuery()
    }
}