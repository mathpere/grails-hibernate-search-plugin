package org.codehaus.groovy.grails.plugins.hibernate.search.components

import org.apache.lucene.search.Query
import org.hibernate.search.query.dsl.FieldCustomization


class BetweenComponent extends Leaf {
	def from
	def to

	Query createQuery( FieldCustomization fieldCustomization ) { fieldCustomization.from( from ).to( to ).createQuery() }

	FieldCustomization createFieldCustomization( ) { queryBuilder.range().onField( field ) }
}
