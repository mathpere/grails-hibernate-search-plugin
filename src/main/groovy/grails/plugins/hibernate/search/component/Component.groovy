package grails.plugins.hibernate.search.component

import org.apache.lucene.search.Query
import org.hibernate.search.query.dsl.QueryBuilder
import org.hibernate.search.query.dsl.SimpleQueryStringMatchingContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @since 14/05/2018
 */
abstract class Component {

    protected QueryBuilder queryBuilder

    Component parent
    List<Component> children = []

    abstract Query createQuery()

    def leftShift(component) {
        assert component instanceof Component: "'component' should be an instance of Component"
        component.parent = this
        children << component
    }

    Logger getLog() {
        LoggerFactory.getLogger(getClass())
    }

    String toString(int indent) {
        [("-" * indent) + this.class.simpleName, children.collect {it.toString(indent + 1)}].flatten().findAll {it}.join("\n")
    }

    @Override
    String toString() {
        toString(0)
    }
}


class SimpleQueryStringComponent extends Component {

    public static final Float DEFAULT_BOOST = 1f

    String field
    Float fieldBoost
    List<String> fields
    Float fieldsBoost
    Map<String, Float> fieldsAndBoost
    String queryString
    Boolean withAndAsDefaultOperator = false

    @Override
    Query createQuery() {
        def context = queryBuilder.simpleQueryString()

        // Handle individual field boosting
        if (fieldsAndBoost) {

            // Get the first field and boost as the context is different for this one
            field = fieldsAndBoost.keySet().first()
            fieldBoost = fieldsAndBoost.remove(field)

            context = context.onField(field).boostedTo(fieldBoost)

            fieldsAndBoost.each {f, b ->
                context = (context as SimpleQueryStringMatchingContext).andField(f).boostedTo(b)
            }
        }
        else {

            context = context.onField(field).boostedTo(fieldBoost ?: DEFAULT_BOOST)

            if (fields) {
                context = context.andFields(fields.toArray() as String[]).boostedTo(fieldsBoost ?: DEFAULT_BOOST)
            }
        }

        if (withAndAsDefaultOperator) context = context.withAndAsDefaultOperator()

        log.debug('Adding SimpleQueryString for [{}]', queryString)
        context.matching(queryString).createQuery()
    }
}