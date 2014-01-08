package org.codehaus.groovy.grails.plugins.hibernate.search.components

import org.apache.lucene.search.Query
import org.hibernate.search.query.dsl.FieldCustomization

class KeywordComponent extends Leaf {
	def matching

	Query createQuery( FieldCustomization fieldCustomization ) { fieldCustomization.matching( matching ).createQuery() }

	FieldCustomization createFieldCustomization( ) { queryBuilder.keyword().onField( field ) }
}
