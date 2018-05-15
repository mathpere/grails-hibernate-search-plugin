/* Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.plugins.hibernate.search.config

import grails.core.GrailsClass
import org.hibernate.search.annotations.*
import org.hibernate.search.cfg.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.lang.annotation.ElementType
import java.lang.reflect.Field

@SuppressWarnings('GroovyUnusedDeclaration')
class SearchMappingEntityConfig {

    public static final String INDEX_CONFIG_NAME = 'search'

    private final static Logger log = LoggerFactory.getLogger(SearchMappingEntityConfig)

    private static final String IDENTITY = 'id'

    private Object analyzer
    private final GrailsClass domainClass
    private final EntityMapping entityMapping
    private final List<String> indexedPropertyNames

    SearchMappingEntityConfig(SearchMapping searchMapping, GrailsClass domainClass) {
        this.indexedPropertyNames = []
        this.domainClass = domainClass
        this.entityMapping = searchMapping.entity(domainClass.getClazz())

        // Only index non-abstract classes
        if (!domainClass.isAbstract()) {
            entityMapping.indexed()
        }

        // Add id property
        entityMapping.property(IDENTITY, ElementType.FIELD).documentId().name('id')
    }

    List<String> getIndexedPropertyNames() {
        return indexedPropertyNames
    }

    void setAnalyzer(Object analyzer) {
        this.analyzer = analyzer
    }

    void setClassBridge(Map classBridge) {
        ClassBridgeMapping bridge = entityMapping.classBridge(classBridge['class'] as Class)

        classBridge.params?.each {k, v ->
            bridge = bridge.param(k.toString(), v.toString())
        }
    }

    Object invokeMethod(String name, argsAsList) {

        Map args = argsAsList[0] as Map ?: [:]

        Field backingField = findField(name)

        if (!backingField) {
            log.warn 'Indexed property not found! name={} entity={}', name, domainClass
            return
        }

        String propertyName = backingField.getName()
        String fieldName = args.name ?: name

        log.debug 'Property {}.{} found', domainClass.clazz.canonicalName, fieldName
        indexedPropertyNames.add(name)

        PropertyMapping propertyMapping = entityMapping.property(propertyName, ElementType.FIELD)

        if (args.indexEmbedded) {

            log.debug '> Adding indexEmbedded property'

            IndexEmbeddedMapping indexEmbeddedMapping = propertyMapping.indexEmbedded()

            if (args.indexEmbedded instanceof Map) {

                Integer depth = args.indexEmbedded.depth as Integer
                if (depth) {
                    indexEmbeddedMapping.depth(depth)
                }

                Boolean includeEmbeddedObjectId = args.indexEmbedded.includeEmbeddedObjectId?.toBoolean()
                if (includeEmbeddedObjectId) {
                    indexEmbeddedMapping.includeEmbeddedObjectId(includeEmbeddedObjectId)
                }
            }
        }
        else if (args.containedIn) {
            log.debug '> Adding containedIn property'
            propertyMapping.containedIn()
        }
        else {
            log.debug '> Adding indexed property'
            configureFieldMapping(propertyMapping.field(), fieldName, args)
        }
    }

    private Field findField(String name) {
        Field backingField = null

        // try to find the field in the parent class hierarchy (starting from domain class itself)
        Class clazz = domainClass.clazz
        while (clazz) {
            try {
                backingField = clazz.getDeclaredField(name)
                break
            } catch (NoSuchFieldException ignored) {
                // and in groovy's traits
                backingField = clazz.getDeclaredFields().find {field -> field.getName().endsWith('__' + name)}
                if (backingField) {
                    break
                }
                clazz = clazz.getSuperclass()
            }
        }

        backingField
    }

    private void configureFieldMapping(FieldMapping fieldMapping, String fieldName, Map<String, Object> args) {

        fieldMapping.name(fieldName)

        if (args.containsKey('analyze')) {
            fieldMapping.analyze(args.analyze ? Analyze.YES : Analyze.NO)
        }

        if (analyzer) {
            if (analyzer instanceof Class)
                fieldMapping.analyzer(analyzer as Class)
            else fieldMapping.analyzer(analyzer as String)
        }

        // Shouldnt have both analyzer and normalizer as they are the same thing,
        // just one is not tokenised
        if (args.analyzer) {
            if (args.analyzer instanceof Class)
                fieldMapping.analyzer(args.analyzer as Class)
            else fieldMapping.analyzer(args.analyzer as String)
        }

        else if (args.normalizer) {
            if (args.normalizer instanceof Class)
                fieldMapping.normalizer(args.normalizer as Class)
            else fieldMapping.normalizer(args.normalizer as String)
        }

        if (args.index) {
            fieldMapping.index(Index.valueOf((args.index as String).toUpperCase()))
        }

        if (args.store) {
            fieldMapping.store(Store.valueOf((args.store as String).toUpperCase()))
        }

        if (args.termVector) {
            fieldMapping.termVector(TermVector.valueOf((args.termVector as String).toUpperCase()))
        }

        if (args.norms) {
            fieldMapping.norms(Norms.valueOf((args.norms as String).toUpperCase()))
        }

        if (args.numeric) {
            fieldMapping.numericField().precisionStep(args.numeric as Integer)
        }

        if (args.boost) {
            fieldMapping.boost(args.boost as Float)
        }

        if (args.date) {
            fieldMapping.dateBridge(Resolution.valueOf((args.date as String).toUpperCase()))
        }

        if (args.bridge) {
            FieldBridgeMapping fieldBridgeMapping = fieldMapping.bridge(args.bridge['class'] as Class)

            args.bridge['params']?.each {k, v ->
                fieldBridgeMapping = fieldBridgeMapping.param(k.toString(), v.toString())
            }
        }

        if (args.sortable) {
            log.debug('> Adding sortable field')
            FieldMapping sortingField = fieldMapping

            // If args are a map then we want to create a new fieldmapping and augment it
            // Otherwise we just create a sortable field from the original
            if (args.sortable instanceof Map) {
                Map sortableArgs = args.sortable as Map

                sortingField = fieldMapping.field().name(sortableArgs.name ?: fieldName)

                /*
                 * Normalizer needs to be applied to sorting field rather than an analyzer,
                 * Warning messages will be given if its missing
                 *
                 * Fields used for sorting can be analyzed, but must not be tokenized, so you should rather use normalizers on those
                 * fields.
                 */
                if (sortableArgs.normalizer) {
                    if (sortableArgs.normalizer instanceof Class)
                        sortingField.normalizer(sortableArgs.normalizer as Class)
                    else sortingField.normalizer(sortableArgs.normalizer as String)
                }
            }

            sortingField.sortableField()
        }
    }
}
