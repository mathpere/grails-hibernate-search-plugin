package org.codehaus.groovy.grails.plugins.hibernate.search.components

import org.apache.lucene.search.Query
import org.hibernate.search.query.dsl.FieldCustomization
import org.hibernate.search.query.dsl.FuzzyContext

class FuzzyComponent extends Leaf {
	def matching
	def threshold

	Query createQuery( FieldCustomization fieldCustomization ) { fieldCustomization.matching( matching ).createQuery() }

	FieldCustomization createFieldCustomization( ) {
		FuzzyContext context = queryBuilder.keyword().fuzzy()
		if (threshold) { context.withThreshold( threshold ) }
		context.onField( field ) }
}
