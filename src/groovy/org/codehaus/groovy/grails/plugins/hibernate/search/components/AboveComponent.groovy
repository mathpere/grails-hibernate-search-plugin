package org.codehaus.groovy.grails.plugins.hibernate.search.components

import org.apache.lucene.search.Query
import org.hibernate.search.query.dsl.FieldCustomization

class AboveComponent extends Leaf {
	def above

	Query createQuery( FieldCustomization fieldCustomization ) { fieldCustomization.above( above ).createQuery() }

	FieldCustomization createFieldCustomization( ) { queryBuilder.range().onField( field ) }
}
