package org.codehaus.groovy.grails.plugins.hibernate.search.bridge

import org.hibernate.search.bridge.TwoWayStringBridge
import org.codehaus.groovy.grails.commons.ApplicationHolder
import org.hibernate.search.bridge.ParameterizedBridge

/** 
* This class is the commen base for nameBridge and Idbridge
* It magicly gets the required domain class (with the magick findBy* methods)
*/
abstract class AbstractGrailsDomainBridge implements TwoWayStringBridge, ParameterizedBridge{

	public static final String STR_TO_OBJ_TYPE = 'classname'
	private Class grailsDomain

	@Override
	void setParameterValues(Map<String,String> parameters) {
		grailsDomain = ApplicationHolder.application.getClassForName(
			parameters.get(STR_TO_OBJ_TYPE)
		)
	}
	protected Class getGrailsDomain(){
		return grailsDomain
	}
}
