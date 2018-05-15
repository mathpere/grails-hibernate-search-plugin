package grails.plugins.hibernate.search.component;

import org.apache.lucene.search.Query
import org.slf4j.Logger
import org.slf4j.LoggerFactory;

/**
 * @since 14/05/2018
 */
abstract class Component {
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
