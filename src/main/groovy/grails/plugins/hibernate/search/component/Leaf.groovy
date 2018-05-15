package grails.plugins.hibernate.search.component

import org.apache.lucene.search.Query
import org.hibernate.search.query.dsl.*

abstract class Leaf<K extends FieldCustomization<K>> extends Composite {

    String field
    Boolean ignoreAnalyzer = false
    Boolean ignoreFieldBridge = false
    float boostedTo

    abstract Query createQuery(K fieldCustomization)

    abstract K createFieldCustomization()

    @Override
    def leftShift(component) {
        throw new UnsupportedOperationException("${this.class.name} is a leaf")
    }

    @Override
    Query createQuery() {
        K fieldCustomization = createFieldCustomization()

        if (ignoreAnalyzer) {fieldCustomization = fieldCustomization.ignoreAnalyzer()}

        if (ignoreFieldBridge) {fieldCustomization = fieldCustomization.ignoreFieldBridge()}

        if (boostedTo) {fieldCustomization = fieldCustomization.boostedTo(boostedTo)}

        createQuery(fieldCustomization)
    }
}

class BelowComponent extends Leaf<RangeMatchingContext> {
    def below

    Query createQuery(RangeMatchingContext fieldCustomization) {
        fieldCustomization.below(below).createQuery()
    }

    RangeMatchingContext createFieldCustomization() {
        queryBuilder.range().onField(field)
    }
}

class AboveComponent extends Leaf<RangeMatchingContext> {
    def above

    Query createQuery(RangeMatchingContext fieldCustomization) {
        fieldCustomization.above(above).createQuery()
    }

    RangeMatchingContext createFieldCustomization() {
        queryBuilder.range().onField(field)
    }
}

class KeywordComponent extends Leaf<TermMatchingContext> {
    def matching

    Query createQuery(TermMatchingContext fieldCustomization) {
        fieldCustomization.matching(matching).createQuery()
    }

    TermMatchingContext createFieldCustomization() {
        queryBuilder.keyword().onField(field)
    }
}

class BetweenComponent extends Leaf<RangeMatchingContext> {
    def from
    def to

    Query createQuery(RangeMatchingContext fieldCustomization) {
        fieldCustomization.from(from).to(to).createQuery()
    }

    RangeMatchingContext createFieldCustomization() {
        queryBuilder.range().onField(field)
    }
}

class FuzzyComponent extends Leaf<TermMatchingContext> {
    def matching
    float threshold
    int prefixLength
    int maxDistance

    Query createQuery(TermMatchingContext fieldCustomization) {
        fieldCustomization.matching(matching).createQuery()
    }

    TermMatchingContext createFieldCustomization() {
        FuzzyContext context = queryBuilder.keyword().fuzzy()
        if (threshold) {context.withThreshold(threshold)}
        if (maxDistance) {context.withEditDistanceUpTo(maxDistance)}
        if (prefixLength) {context.withPrefixLength(prefixLength)}
        context.onField(field)
    }
}

class WildcardComponent extends Leaf<TermMatchingContext> {
    def matching

    Query createQuery(TermMatchingContext fieldCustomization) {
        fieldCustomization.matching(matching).createQuery()
    }

    TermMatchingContext createFieldCustomization() {
        queryBuilder.keyword().wildcard().onField(field)
    }
}

class PhraseComponent extends Leaf<PhraseMatchingContext> {
    String sentence

    Query createQuery(PhraseMatchingContext fieldCustomization) {
        fieldCustomization.sentence(sentence).createQuery()
    }

    PhraseMatchingContext createFieldCustomization() {
        queryBuilder.phrase().onField(field)
    }
}