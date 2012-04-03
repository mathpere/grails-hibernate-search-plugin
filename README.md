# Grails Hibernate Search Plugin

This plugin aims to integrate Hibernate Search features to Grails in very few steps.

## Quick start


### Installation

```
  grails install-plugin hibernate-search
```

### Configuration

By default, the plugin stores your indexes in this directory:

```
 ~/.grails/${grails.version}/projects/${yourProjectName}/lucene-index/development/
```

You can override this configuration in your Datasource.groovy

```groovy
 hibernate {
 
    // default Grails configuration:
    cache.use_second_level_cache = true
    cache.use_query_cache = true
    cache.provider_class = 'net.sf.ehcache.hibernate.EhCacheProvider'

    // hibernate search configuration:
    search.default.directory_provider = 'filesystem'
    search.default.indexBase = '/path/to/your/indexes'

}
```

You can also define the path to your indexes with JNDI configuration as following:

```groovy
 hibernate {
 
    // default Grails configuration:
    cache.use_second_level_cache = true
    cache.use_query_cache = true
    cache.provider_class = 'net.sf.ehcache.hibernate.EhCacheProvider'

    // hibernate search configuration:
    search.default.directory_provider = 'filesystem'
    search.default.indexBaseJndiName = 'java:comp/env/luceneIndexBase'

}
```

### Mark your domain classes as indexable

Add a static search closure as following:

```groovy
class MyDomainClass {

    String author
    String body
    Date publishedDate
    String summary
    String title
    Status status
    Double price
    Integer someInteger

    enum Status {
        DISABLED, PENDING, ENABLED
    }

    static hasMany = [categories: Category]

    static search = {
        // fields
        author index: 'tokenized'
        body index: 'tokenized'
        publishedDate date: 'day'
        summary index: 'tokenized'
        title index: 'tokenized'
        status index: 'un_tokenized'
        categories indexEmbedded: true
        price numeric: 2
        someInteger index: 'un_tokenized', bridge: ['class': PaddedIntegerBridge, params: ['padding': 10]]

        // support for classBridge
        classBridge = ['class': MyClassBridge, params: [myParam: "4"]]
    }

}
```

This static property indicates which fields should be indexed and describes how the field has to be indexed.

Also, the plugin lets you to mark your domain classes as indexable with the Hibernate Search annotations.

```groovy
@Indexed
@ClassBridge(
     impl = MyClassBridge,
     params = @Parameter( name="myParam", value="4" ) )
class MyDomainClass {

    // when using annotations, id is required to define DocumentId
    @DocumentId
    Long id

    @Field(index=Index.TOKENIZED)
    String author
    
    @Field(index=Index.TOKENIZED)
    String body
    
    @Field
    @DateBridge(resolution=Resolution.DAY)
    Date publishedDate
    
    @Field(index=Index.TOKENIZED)
    String summary
    
    @Field(index=Index.TOKENIZED)
    String title
    
    @Field(index=Index.UN_TOKENIZED)
    Status status
    
    @Field 
    @NumericField( precisionStep = 2)
    Double price
    
    @Field(index=Index.UN_TOKENIZED)
    @FieldBridge(impl = PaddedIntegerBridge.class, params = @Parameter(name="padding", value="10"))
    Integer someInteger

    enum Status {
        DISABLED, PENDING, ENABLED
    }

    @IndexedEmbedded
    Set categories

    static hasMany = [categories: Category]

}
```


### Search

The plugin provides you dynamic method to search for indexed entities. 

#### Retrieving the results

All indexed domain classes provides .search() method which lets you to list the results.
The plugin provides a search DSL for simplifying the way you can search. Here is what it looks like with the search DSL:

```groovy
class SomeController {

   def myAction = { MyCommand command ->

      def page = [max: Math.min(params.max ? params.int('max') : 10, 50), offset: params.offset ? params.int('offset') : 0]

      def myDomainClasses = MyDomainClass.search().list {

         if ( command.dateTo ) {
            below "publishedDate", command.dateTo
         }

         if ( command.dateFrom ) {
            above "publishedDate", command.dateFrom
         }

         mustNot {
            keyword "status", Status.DISABLED
         }

         if ( command.keyword ) {
            should {
               command.keyword.tokenize().each { keyword ->

                  def wild = keyword.toLowerCase() + '*'

                  wildcard "author", wild
                  wildcard "body", wild
                  wildcard "summary", wild
                  wildcard "title", wild
                  wildcard "categories.name", wild
               }
            }
         }

         sort "publishedDate", "asc"

         maxResults page.max

         offset page.offset
      }

      [myDomainClasses: myDomainClasses]
   }
}
```

#### Counting the results

You can also retrieve the number of results by using 'count' method:

```groovy
def myDomainClasses = MyDomainClass.search().count {
 ...
}
```

## Bug tracker

Please report any issue on GitHub: 

https://github.com/mathpere/grails-hibernate-search-plugin/issues


## Authors

**Mathieu Perez**

+ http://twitter.com/mathieuperez

**Julie Ingignoli**

+ http://twitter.com/ZeJulie


## License

Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0