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
package org.codehaus.groovy.grails.plugins.hibernate.search

import grails.gorm.DetachedCriteria
import java.util.regex.Pattern
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.orm.hibernate.metaclass.AbstractStaticPersistentMethod
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.springframework.orm.hibernate3.HibernateCallback

class SearchPersistentMethod extends AbstractStaticPersistentMethod {

    private static final String METHOD_PATTERN = '^search$'

    public SearchPersistentMethod(SessionFactory sessionFactory, ClassLoader classLoader, GrailsApplication grailsApplication) {
        super(sessionFactory, classLoader, Pattern.compile(METHOD_PATTERN), grailsApplication);
    }

    @Override
    protected Object doInvokeInternal(Class clazz, String methodName, Closure additionalCriteria, Object[] arguments) {
        hibernateTemplate.execute({ Session session ->
            new HibernateSearchQueryBuilder(clazz, session)
        } as HibernateCallback)
    }

    @Override
    protected Object doInvokeInternal(Class clazz, String methodName, DetachedCriteria additionalCriteria, Object[] arguments) {
        return null
    }
}