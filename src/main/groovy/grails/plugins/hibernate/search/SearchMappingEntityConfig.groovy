/* Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.plugins.hibernate.search

import org.hibernate.search.annotations.Analyze;
import org.hibernate.search.annotations.Index
import org.hibernate.search.annotations.Norms;
import org.hibernate.search.annotations.Resolution
import org.hibernate.search.annotations.Store
import org.hibernate.search.annotations.TermVector
import org.hibernate.search.cfg.DocumentIdMapping;
import org.hibernate.search.cfg.EntityDescriptor
import org.hibernate.search.cfg.EntityMapping
import org.hibernate.search.cfg.FieldMapping;
import org.hibernate.search.cfg.IndexEmbeddedMapping
import org.hibernate.search.cfg.PropertyMapping;
import org.hibernate.search.cfg.SearchMapping
import org.slf4j.Logger
import org.slf4j.LoggerFactory;

import java.lang.annotation.ElementType
import java.lang.reflect.Field;

import grails.plugins.*;

class SearchMappingEntityConfig {

	private final static Logger log = LoggerFactory.getLogger(this);
	
    private static final String IDENTITY = 'id'

    def analyzer

    private final DocumentIdMapping mapping
    private final Class<?> domainClass
	
	private final EntityMapping entityMapping
    private final SearchMapping searchMapping
	private final List<String> indexedPropertyNames;

    public SearchMappingEntityConfig( SearchMapping searchMapping, Class<?> domainClass ) {
        this.domainClass = domainClass
		
		this.searchMapping = searchMapping;
        this.entityMapping = searchMapping.entity( domainClass )
        this.mapping = entityMapping.indexed().property( IDENTITY, ElementType.FIELD ).documentId()
		
		this.indexedPropertyNames = new LinkedList<>();
		
    }

    def setClassBridge( Map classBridge ) {
        def bridge = entityMapping.classBridge( classBridge['class'] )

        classBridge.params?.each {k, v ->
            bridge = bridge.param( k.toString(), v.toString() )
        }
    }

    def invokeMethod( String name, argsAsList ) {

        def args = argsAsList[0] ?: [:]
		
        if ( args.indexEmbedded ) {

			log.debug "adding indexEmbedded property: " + name
			
            IndexEmbeddedMapping propertyMapping = mapping.property( name, ElementType.FIELD ).indexEmbedded()

            if ( args.indexEmbedded instanceof Map ) {
                def depth = args.indexEmbedded["depth"]
                if ( depth ) {
                    propertyMapping.depth( depth )
                }
				
				def includeEmbeddedObjectId = args.indexEmbedded["includeEmbeddedObjectId"]
				if ( includeEmbeddedObjectId ) {
					propertyMapping.includeEmbeddedObjectId(includeEmbeddedObjectId)
				}
            }
			
			indexedPropertyNames.add(name);
			
        } else if ( args.containedIn ) {

			log.debug "adding containedIn property: " + name
		
            mapping.property( name, ElementType.FIELD ).containedIn()
	
			indexedPropertyNames.add(name);
					
        } else {
		
			log.debug "adding indexed property: " + name

			Field backingField = null;
			
			// try to find the field in the parent class hierarchy (starting from domain class itself)
			Class currentDomainClass = domainClass;
			while (currentDomainClass != null) {
				try {
					backingField = currentDomainClass.getDeclaredField(name);
					break;
				} catch (NoSuchFieldException e) {
					// and in groovy's traits
					backingField = currentDomainClass.getDeclaredFields().find { field -> field.getName().endsWith('__' + name) };
					if (backingField != null) {
						break;
					}
					
					currentDomainClass = currentDomainClass.getSuperclass(); 
				}
			}
			
			if (backingField == null) {
				log.warn "indexed property not found! name=" + name + " entity=" + domainClass
				return;
			}
			
			log.debug "> property " + backingField.getDeclaringClass() + ".$name found";
			
			EntityMapping targetEntityMapping = mapping.entity( currentDomainClass );

			String fieldName = backingField.getName();
			FieldMapping fieldMapping = targetEntityMapping.property( fieldName, ElementType.FIELD ).field().name( args.name ?: name )
            registerIndexedProperty(fieldMapping, args)
			
			indexedPropertyNames.add(fieldName);
			
        }
    }
	
	private void registerIndexedProperty(FieldMapping fieldMapping, args) {
		
		def searchMapping = fieldMapping;
		
		if ( args.containsKey('analyze') ) {
			searchMapping = searchMapping.analyze( args.analyze ? Analyze.YES : Analyze.NO )
		}
		
		if ( analyzer ) {
			searchMapping = searchMapping.analyzer( analyzer )
		}

		if ( args.analyzer ) {
			searchMapping = searchMapping.analyzer( args.analyzer )
		}

		if ( args.index ) {
			searchMapping = searchMapping.index( Index."${args.index.toUpperCase()}" )
		}

		if ( args.store ) {
			searchMapping = searchMapping.store( Store."${args.store.toUpperCase()}" )
		}

		if ( args.termVector ) {
			searchMapping = searchMapping.termVector( TermVector."${args.termVector.toUpperCase()}" )
		}

		if ( args.norms ) {
			searchMapping = searchMapping.norms( Norms."${args.norms.toUpperCase()}" )
		}

		if ( args.numeric ) {
			searchMapping = searchMapping.numericField().precisionStep( args.numeric )
		}

		if ( args.date ) {
			searchMapping = searchMapping.dateBridge( Resolution."${args.date.toUpperCase()}" )
		}

		if ( args.boost ) {
			searchMapping = searchMapping.boost( args.boost )
		}

		if ( args.bridge ) {

			searchMapping = searchMapping.bridge( args.bridge["class"] )

			def params = args.bridge["params"]

			params?.each {k, v ->
				searchMapping = searchMapping.param( k.toString(), v.toString() )
			}
		}
	}
	
	EntityDescriptor getEntityDescriptor() {
		return searchMapping.getEntity( domainClass )
	}
	
	List<String> getIndexedPropertyNames() {
		return indexedPropertyNames;
	}
}
