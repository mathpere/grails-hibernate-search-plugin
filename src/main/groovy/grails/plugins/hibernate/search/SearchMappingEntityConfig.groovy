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
import org.hibernate.search.cfg.EntityMapping
import org.hibernate.search.cfg.SearchMapping
import org.slf4j.Logger
import org.slf4j.LoggerFactory;

import java.lang.annotation.ElementType

import grails.plugins.*;

class SearchMappingEntityConfig {

	private final static Logger log = LoggerFactory.getLogger(this);
	
    private static final String IDENTITY = 'id'

    def analyzer

    def searchMapping
    private final clazz

    private final EntityMapping entityMapping

    public SearchMappingEntityConfig( SearchMapping searchMapping, Class clazz ) {
        this.clazz = clazz
        this.entityMapping = searchMapping.entity( clazz )
        this.searchMapping = entityMapping.indexed().property( IDENTITY, ElementType.FIELD ).documentId()
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

            searchMapping = searchMapping.property( name, ElementType.FIELD ).indexEmbedded()

            if ( args.indexEmbedded instanceof Map ) {
                def depth = args.indexEmbedded["depth"]
                if ( depth ) {
                    searchMapping = searchMapping.depth( depth )
                }
				
				def includeEmbeddedObjectId = args.indexEmbedded["includeEmbeddedObjectId"]
				if ( includeEmbeddedObjectId ) {
					searchMapping = searchMapping.includeEmbeddedObjectId(includeEmbeddedObjectId)
				}
            }
        } else if ( args.containedIn ) {

            searchMapping = searchMapping.property( name, ElementType.FIELD ).containedIn()

        } else {

			log.debug "adding indexed field: " + name
		
            searchMapping = searchMapping.property( name, ElementType.FIELD ).field().name( args.name ?: name )

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
    }
}
