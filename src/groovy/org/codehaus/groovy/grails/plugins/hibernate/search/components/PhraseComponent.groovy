package org.codehaus.groovy.grails.plugins.hibernate.search.components

import org.hibernate.search.query.dsl.FieldCustomization
import org.apache.lucene.search.Query

class PhraseComponent extends Leaf {
	def sentence

	Query createQuery( FieldCustomization fieldCustomization ) { fieldCustomization.sentence( sentence ).createQuery() }

	FieldCustomization createFieldCustomization( ) { queryBuilder.phrase().onField( field ) }
}


