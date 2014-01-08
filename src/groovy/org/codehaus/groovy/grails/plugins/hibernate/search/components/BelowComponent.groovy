package org.codehaus.groovy.grails.plugins.hibernate.search.components

import org.apache.lucene.search.Query
import org.hibernate.search.query.dsl.FieldCustomization

class BelowComponent extends Leaf {
	def below

	Query createQuery( FieldCustomization fieldCustomization ) { fieldCustomization.below( below ).createQuery() }

	FieldCustomization createFieldCustomization( ) { queryBuilder.range().onField( field ) }
}


