package org.codehaus.groovy.grails.plugins.hibernate.search.bridge
import java.lang.reflect.ParameterizedType

class IdCollectionBridge<ObjectArray extends Collection> extends IdBridge{
	@Override
	String objectToString(Object object){
		Collection col = (Collection) object
		String result = ""
		col.each{
			result += super.objectToString(it)
		}
	}

	@Override 
	Object stringToObject(String stringValue){

		Collection col = createCollection()

		stringValue.split(",").each{
			col.add(super.stringToObject(it))	
		}
		return col
	}

	/** a java can't create new generic workaround */
	private static Collection createCollection(){
		return (
			(Class)(
				(ParameterizedType) this.getClass().getGenericSuperclass()

			).getActualTypeArguments()[0]

		).newInstance(); 
	}

}

