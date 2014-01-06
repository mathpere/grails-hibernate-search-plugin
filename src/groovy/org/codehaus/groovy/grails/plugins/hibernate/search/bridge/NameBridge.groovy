package org.codehaus.groovy.grails.plugins.hibernate.search.bridge

/** 
	this class asumes the object it gets has a name, and its unique
	it also asumes it can search on its given parameter like a grails
	domain class to allow for the string to object conversion
*/
class NameBridge extends AbstractGrailsDomainBridge{
	
	@Override
	String objectToString(Object object) {
		// otherwise it should crash
		return object.name
	}

	@Override
	Object stringToObject(String stringValue) {
		// should crash if not has method
		return getGrailsDomain().findByName(stringValue)
	}
}
