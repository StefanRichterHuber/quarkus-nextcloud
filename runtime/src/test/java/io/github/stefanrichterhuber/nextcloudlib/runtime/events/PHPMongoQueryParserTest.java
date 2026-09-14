package io.github.stefanrichterhuber.nextcloudlib.runtime.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.stefanrichterhuber.nextcloudlib.runtime.events.PHPMongoQueryParser.CompiledFilter;
import io.github.stefanrichterhuber.nextcloudlib.runtime.events.PHPMongoQueryParser.FilterSyntaxException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link PHPMongoQueryParser}'s PHPMongoQuery-style filter compiler:
 * document/field structure, operators, Mongo array semantics, dotted-path
 * resolution and error handling.
 */
public class PHPMongoQueryParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode json(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean matches(String filterJson, String documentJson) {
        return PHPMongoQueryParser.compile(filterJson).test(json(documentJson));
    }

    @Nested
    @DisplayName("empty / trivial filters")
    class EmptyFilters {

        @Test
        void emptyObjectMatchesEverything() {
            assertTrue(matches("{}", "{\"any\":\"thing\"}"));
        }

        @Test
        void jsonNullLiteralMatchesEverything() {
            assertTrue(matches("null", "{\"any\":\"thing\"}"));
        }

        @Test
        void compileOfNullJsonNodeMatchesEverything() {
            CompiledFilter f = PHPMongoQueryParser.compile((JsonNode) null);
            assertTrue(f.test(json("{}")));
        }
    }

    @Nested
    @DisplayName("field equality")
    class FieldEquality {

        @Test
        void nestedDottedPathEqualityMatches() {
            assertTrue(matches("{\"user.uid\": \"admin\"}", "{\"user\":{\"uid\":\"admin\"}}"));
        }

        @Test
        void nestedDottedPathEqualityMismatch() {
            assertFalse(matches("{\"user.uid\": \"admin\"}", "{\"user\":{\"uid\":\"someone-else\"}}"));
        }

        @Test
        void missingFieldNeverMatchesPlainEquality() {
            assertFalse(matches("{\"user.uid\": \"admin\"}", "{\"user\":{}}"));
        }

        @Test
        void numericEqualityIgnoresIntVsDecimalRepresentation() {
            assertTrue(matches("{\"count\": 1}", "{\"count\": 1.0}"));
        }

        @Test
        void deepObjectEquality() {
            assertTrue(matches("{\"meta\": {\"a\":1,\"b\":2}}", "{\"meta\": {\"a\":1,\"b\":2}}"));
        }

        @Test
        void multipleFieldsAreImplicitlyAnded() {
            assertTrue(matches("{\"a\":1,\"b\":2}", "{\"a\":1,\"b\":2,\"c\":3}"));
            assertFalse(matches("{\"a\":1,\"b\":2}", "{\"a\":1,\"b\":9}"));
        }
    }

    @Nested
    @DisplayName("Mongo array semantics")
    class ArraySemantics {

        @Test
        void scalarConditionMatchesArrayContainingIt() {
            assertTrue(matches("{\"tags\": \"a\"}", "{\"tags\": [\"a\",\"b\"]}"));
        }

        @Test
        void scalarConditionMismatchesArrayWithoutIt() {
            assertFalse(matches("{\"tags\": \"z\"}", "{\"tags\": [\"a\",\"b\"]}"));
        }

        @Test
        void explicitNumericSegmentIndexesIntoArray() {
            assertTrue(matches("{\"tags.1\": \"b\"}", "{\"tags\": [\"a\",\"b\",\"c\"]}"));
            assertFalse(matches("{\"tags.1\": \"z\"}", "{\"tags\": [\"a\",\"b\",\"c\"]}"));
        }

        @Test
        void outOfBoundsNumericSegmentFallsBackToImplicitTraversalOverScalars() {
            // "tags.5" -> 5 is out of bounds for a 3-element array, so resolve() falls
            // back to re-applying the "5" segment to each (scalar) element instead of
            // indexing. A scalar has no "5" property, so nothing resolves and the
            // filter never matches.
            assertFalse(matches("{\"tags.5\": \"b\"}", "{\"tags\": [\"a\",\"b\",\"c\"]}"));
        }

        @Test
        @DisplayName("dotted path into an array of objects resolves the element property")
        void dottedPathOverArrayOfObjectsDescendsIntoElements() {
            // "tags.name" over an array of objects looks up "name" on each element of
            // "tags", per Mongo dotted-path semantics.
            assertTrue(matches("{\"tags.name\": \"b\"}", "{\"tags\": [{\"name\":\"a\"},{\"name\":\"b\"}]}"));
            assertFalse(matches("{\"tags.name\": \"z\"}", "{\"tags\": [{\"name\":\"a\"},{\"name\":\"b\"}]}"));
        }
    }

    @Nested
    @DisplayName("comparison operators")
    class ComparisonOperators {

        @Test
        void eqAndAliasE() {
            assertTrue(matches("{\"a\": {\"$eq\": 1}}", "{\"a\":1}"));
            assertTrue(matches("{\"a\": {\"$e\": 1}}", "{\"a\":1}"));
            assertFalse(matches("{\"a\": {\"$eq\": 1}}", "{\"a\":2}"));
        }

        @Test
        void ne() {
            assertTrue(matches("{\"a\": {\"$ne\": 1}}", "{\"a\":2}"));
            assertFalse(matches("{\"a\": {\"$ne\": 1}}", "{\"a\":1}"));
        }

        @Test
        void neOnMissingFieldIsTrue() {
            // no values resolve at the path, so "not equal" trivially holds
            assertTrue(matches("{\"user.uid\": {\"$ne\": \"admin\"}}", "{\"user\":{}}"));
        }

        @Test
        void gtGteLtLte() {
            assertTrue(matches("{\"a\": {\"$gt\": 5}}", "{\"a\":6}"));
            assertFalse(matches("{\"a\": {\"$gt\": 5}}", "{\"a\":5}"));
            assertTrue(matches("{\"a\": {\"$gte\": 5}}", "{\"a\":5}"));
            assertTrue(matches("{\"a\": {\"$lt\": 5}}", "{\"a\":4}"));
            assertFalse(matches("{\"a\": {\"$lt\": 5}}", "{\"a\":5}"));
            assertTrue(matches("{\"a\": {\"$lte\": 5}}", "{\"a\":5}"));
        }

        @Test
        void comparisonAcrossIncompatibleTypesNeverMatches() {
            // compare() returns null for a string vs. a number -> comparison is false,
            // not an error
            assertFalse(matches("{\"a\": {\"$gt\": 5}}", "{\"a\":\"text\"}"));
        }

        @Test
        void stringComparisonUsesLexicographicOrder() {
            assertTrue(matches("{\"a\": {\"$gt\": \"apple\"}}", "{\"a\":\"banana\"}"));
        }
    }

    @Nested
    @DisplayName("$in / $nin / $all")
    class SetOperators {

        @Test
        void in() {
            assertTrue(matches("{\"role\": {\"$in\": [\"admin\",\"root\"]}}", "{\"role\":\"root\"}"));
            assertFalse(matches("{\"role\": {\"$in\": [\"admin\",\"root\"]}}", "{\"role\":\"guest\"}"));
        }

        @Test
        void nin() {
            assertTrue(matches("{\"role\": {\"$nin\": [\"admin\",\"root\"]}}", "{\"role\":\"guest\"}"));
            assertFalse(matches("{\"role\": {\"$nin\": [\"admin\",\"root\"]}}", "{\"role\":\"admin\"}"));
        }

        @Test
        void all() {
            assertTrue(matches("{\"tags\": {\"$all\": [\"a\",\"b\"]}}", "{\"tags\":[\"a\",\"b\",\"c\"]}"));
            assertFalse(matches("{\"tags\": {\"$all\": [\"a\",\"z\"]}}", "{\"tags\":[\"a\",\"b\",\"c\"]}"));
        }

        @Test
        void inRequiresArrayArgument() {
            FilterSyntaxException ex = assertThrows(FilterSyntaxException.class,
                    () -> PHPMongoQueryParser.compile("{\"a\": {\"$in\": 1}}"));
            assertTrue(ex.getMessage().contains("$in expects an array"));
        }
    }

    @Nested
    @DisplayName("$exists / $mod / $size")
    class MiscOperators {

        @Test
        void existsTrueOnPresentField() {
            assertTrue(matches("{\"user.uid\": {\"$exists\": true}}", "{\"user\":{\"uid\":\"x\"}}"));
            assertFalse(matches("{\"user.uid\": {\"$exists\": true}}", "{\"user\":{}}"));
        }

        @Test
        void existsFalseOnMissingField() {
            assertTrue(matches("{\"user.uid\": {\"$exists\": false}}", "{\"user\":{}}"));
            assertFalse(matches("{\"user.uid\": {\"$exists\": false}}", "{\"user\":{\"uid\":\"x\"}}"));
        }

        @Test
        void existsRequiresBooleanArgument() {
            assertThrows(FilterSyntaxException.class,
                    () -> PHPMongoQueryParser.compile("{\"a\": {\"$exists\": 1}}"));
        }

        @Test
        void mod() {
            assertTrue(matches("{\"count\": {\"$mod\": [2,0]}}", "{\"count\":4}"));
            assertFalse(matches("{\"count\": {\"$mod\": [2,0]}}", "{\"count\":3}"));
        }

        @Test
        void modRejectsWrongShape() {
            assertThrows(FilterSyntaxException.class,
                    () -> PHPMongoQueryParser.compile("{\"a\": {\"$mod\": [1]}}"));
        }

        @Test
        void modRejectsZeroDivisor() {
            FilterSyntaxException ex = assertThrows(FilterSyntaxException.class,
                    () -> PHPMongoQueryParser.compile("{\"a\": {\"$mod\": [0,0]}}"));
            assertTrue(ex.getMessage().contains("divisor must not be zero"));
        }

        @Test
        void size() {
            assertTrue(matches("{\"tags\": {\"$size\": 2}}", "{\"tags\":[\"a\",\"b\"]}"));
            assertFalse(matches("{\"tags\": {\"$size\": 2}}", "{\"tags\":[\"a\"]}"));
        }

        @Test
        void sizeRequiresNumberArgument() {
            assertThrows(FilterSyntaxException.class,
                    () -> PHPMongoQueryParser.compile("{\"a\": {\"$size\": \"x\"}}"));
        }
    }

    @Nested
    @DisplayName("regex matching")
    class RegexMatching {

        @Test
        void regexOperatorWithBarePattern() {
            assertTrue(matches("{\"name\": {\"$regex\": \"^adm\"}}", "{\"name\":\"admin\"}"));
            assertFalse(matches("{\"name\": {\"$regex\": \"^adm\"}}", "{\"name\":\"guest\"}"));
        }

        @Test
        void regexOperatorRequiresStringArgument() {
            assertThrows(FilterSyntaxException.class,
                    () -> PHPMongoQueryParser.compile("{\"a\": {\"$regex\": 1}}"));
        }

        @Test
        void regexOperatorRejectsInvalidPattern() {
            FilterSyntaxException ex = assertThrows(FilterSyntaxException.class,
                    () -> PHPMongoQueryParser.compile("{\"a\": {\"$regex\": \"[\"}}"));
            assertTrue(ex.getMessage().contains("invalid regular expression"));
        }

        @Test
        void slashDelimitedRegexLiteralWithCaseInsensitiveFlag() {
            assertTrue(matches("{\"name\": \"/^adm/i\"}", "{\"name\":\"Admin\"}"));
            assertFalse(matches("{\"name\": \"/^adm/i\"}", "{\"name\":\"guest\"}"));
        }

        @Test
        void valueThatLooksLikeARegexLiteralButIsntFallsBackToPlainEquality() {
            // "/home/files/" is not a valid regex body once the delimiters are
            // stripped in a way that would compile, so it is treated as a literal
            // string instead of a pattern.
            assertTrue(matches("{\"path\": \"/home/files/\"}", "{\"path\":\"/home/files/\"}"));
        }
    }

    @Nested
    @DisplayName("field-level $not")
    class FieldLevelNot {

        @Test
        void negatesInnerValueSpec() {
            assertTrue(matches("{\"user.uid\": {\"$not\": {\"$eq\": \"admin\"}}}", "{\"user\":{\"uid\":\"guest\"}}"));
            assertFalse(matches("{\"user.uid\": {\"$not\": {\"$eq\": \"admin\"}}}", "{\"user\":{\"uid\":\"admin\"}}"));
        }
    }

    @Nested
    @DisplayName("top-level logical operators")
    class TopLevelLogicalOperators {

        @Test
        void and() {
            assertTrue(matches("{\"$and\": [{\"a\":1},{\"b\":2}]}", "{\"a\":1,\"b\":2}"));
            assertFalse(matches("{\"$and\": [{\"a\":1},{\"b\":2}]}", "{\"a\":1,\"b\":9}"));
        }

        @Test
        void or() {
            assertTrue(matches("{\"$or\": [{\"a\":1},{\"b\":2}]}", "{\"a\":9,\"b\":2}"));
            assertFalse(matches("{\"$or\": [{\"a\":1},{\"b\":2}]}", "{\"a\":9,\"b\":9}"));
        }

        @Test
        void nor() {
            assertTrue(matches("{\"$nor\": [{\"a\":1},{\"b\":2}]}", "{\"a\":9,\"b\":9}"));
            assertFalse(matches("{\"$nor\": [{\"a\":1},{\"b\":2}]}", "{\"a\":1,\"b\":9}"));
        }

        @Test
        void not() {
            assertTrue(matches("{\"$not\": {\"a\":1}}", "{\"a\":9}"));
            assertFalse(matches("{\"$not\": {\"a\":1}}", "{\"a\":1}"));
        }

        @Test
        void andOrNorRequireNonEmptyArray() {
            assertThrows(FilterSyntaxException.class, () -> PHPMongoQueryParser.compile("{\"$and\": []}"));
            assertThrows(FilterSyntaxException.class, () -> PHPMongoQueryParser.compile("{\"$and\": {}}"));
        }

        @Test
        void unsupportedTopLevelOperatorRejected() {
            FilterSyntaxException ex = assertThrows(FilterSyntaxException.class,
                    () -> PHPMongoQueryParser.compile("{\"$xor\": []}"));
            assertTrue(ex.getMessage().contains("unsupported top-level operator"));
        }
    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @Test
        void invalidJsonIsRejected() {
            FilterSyntaxException ex = assertThrows(FilterSyntaxException.class,
                    () -> PHPMongoQueryParser.compile("not json"));
            assertTrue(ex.getMessage().startsWith("filter is not valid JSON:"));
        }

        @Test
        void nonObjectTopLevelIsRejected() {
            FilterSyntaxException ex = assertThrows(FilterSyntaxException.class,
                    () -> PHPMongoQueryParser.compile("[1,2,3]"));
            assertEquals("filter must be a JSON object", ex.getMessage());
        }

        @Test
        void unsupportedFieldOperatorRejected() {
            FilterSyntaxException ex = assertThrows(FilterSyntaxException.class,
                    () -> PHPMongoQueryParser.compile("{\"a\": {\"$bogus\": 1}}"));
            assertTrue(ex.getMessage().contains("unsupported operator: $bogus"));
        }

        @Test
        void mixingOperatorsAndPlainFieldsInSameSpecIsRejected() {
            FilterSyntaxException ex = assertThrows(FilterSyntaxException.class,
                    () -> PHPMongoQueryParser.compile("{\"a\": {\"$eq\": 1, \"b\": 2}}"));
            assertTrue(ex.getMessage().contains("cannot mix operators and plain fields"));
        }

        @Test
        void filterSyntaxExceptionIsAnIllegalArgumentException() {
            assertTrue(IllegalArgumentException.class.isAssignableFrom(FilterSyntaxException.class));
        }
    }

    @Nested
    @DisplayName("CompiledFilter.negate()")
    class Negation {

        @Test
        void flipsTheResult() {
            CompiledFilter f = PHPMongoQueryParser.compile("{\"a\":1}");
            CompiledFilter negated = f.negate();
            JsonNode doc = json("{\"a\":1}");
            assertTrue(f.test(doc));
            assertFalse(negated.test(doc));
        }
    }
}
