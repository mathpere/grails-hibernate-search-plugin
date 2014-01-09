package org.codehaus.groovy.grails.plugins.hibernate.search.components

import org.apache.lucene.search.Query

class MustComponent extends Composite {
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
