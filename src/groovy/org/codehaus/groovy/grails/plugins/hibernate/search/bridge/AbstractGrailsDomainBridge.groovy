package org.codehaus.groovy.grails.plugins.hibernate.search.bridge

import org.hibernate.search.bridge.TwoWayStringBridge
import org.codehaus.groovy.grails.commons.ApplicationHolder
import org.hibernate.search.bridge.ParameterizedBridge

/** 
* This class is the commen base for nameBridge and Idbridge
* It magicly gets the required domain class (with the magick findBy* methods)
*/
abstract class AbstractGrailsDomainBridge<Domain> implements TwoWayStringBridge{
	protected Class getGrailsDomain(){
		return ApplicationHolder.application.getClassForName(
			Domain.class.getName()
		)
	}
}
