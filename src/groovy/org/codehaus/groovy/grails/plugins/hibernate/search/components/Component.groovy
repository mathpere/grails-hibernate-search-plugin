package org.codehaus.groovy.grails.plugins.hibernate.search.components

import org.apache.lucene.search.Query

interface Component {
	Query createQuery( )
}
