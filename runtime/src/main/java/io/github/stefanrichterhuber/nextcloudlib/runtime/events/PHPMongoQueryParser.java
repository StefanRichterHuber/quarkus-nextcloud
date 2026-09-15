package io.github.stefanrichterhuber.nextcloudlib.runtime.events;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Interpreter for the filters used for 'webhook_listener' events
 * PHPMongoQuery
 * 
 * @see <a href=
 *      "https://docs.nextcloud.com/server/stable/admin_manual/webhook_listeners/index.html">Nextcloud
 *      Webhook Listeners</a>
 */
public final class PHPMongoQueryParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @FunctionalInterface
    public interface CompiledFilter extends Predicate<JsonNode> {
        boolean test(JsonNode document);

        /**
         * Returns a CompiledFilter that represents the logical negation of this
         * CompiledFilter.
         *
         * @return a CompiledFilter that represents the logical negation of this
         *         CompiledFilter
         */
        default CompiledFilter negate() {
            return (t) -> !test(t);
        }

        /**
         * Turns this CompiledFilter into a Predicate for JSON in String format
         * 
         * @return Predicate for strings containing json
         */
        default Predicate<String> intoJSONStringPredicate() {
            return json -> {
                try {
                    return test(MAPPER.readTree(json));
                } catch (JsonProcessingException e) {
                    throw new IllegalArgumentException("String not valid json", e);
                }
            };
        }

        /**
         * Turns this CompiledFilter into a Predicate for generic objects which must be
         * convertable to {@link JsonNode} using an {@link ObjectMapper}!
         * 
         * @param <T>
         * @return Predicate for objects convertable to {@link JsonNode}
         */
        default <T> Predicate<T> intoObjectPredicate() {
            return obj -> test(MAPPER.convertValue(obj, JsonNode.class));
        }

    }

    public static final class FilterSyntaxException extends IllegalArgumentException {
        public FilterSyntaxException(String message) {
            super(message);
        }

        public FilterSyntaxException(String message, Throwable e) {
            super(message, e);
        }
    }

    private PHPMongoQueryParser() {
        // Intentionally empty
    }

    public static CompiledFilter compile(String filterJson) {
        try {
            return compile(MAPPER.readTree(filterJson));
        } catch (JsonProcessingException e) {
            throw new FilterSyntaxException("filter is not valid JSON: " + e.getOriginalMessage(), e);
        }
    }

    public static CompiledFilter compile(JsonNode filter) {
        CompiledFilter p = compileDocument(filter);
        return p;
    }

    private static CompiledFilter compileDocument(JsonNode doc) {
        if (doc == null || doc.isNull()) {
            return root -> true; // no filter == match everything
        }
        if (!doc.isObject()) {
            throw new FilterSyntaxException("filter must be a JSON object");
        }

        final List<CompiledFilter> parts = new ArrayList<>();

        final Set<Entry<String, JsonNode>> properties = doc.properties();
        for (Entry<String, JsonNode> property : properties) {
            final String key = property.getKey();
            final JsonNode spec = property.getValue();
            parts.add(key.startsWith("$")
                    ? compileLogical(key, spec)
                    : compileField(key, spec));
        }
        return allOf(parts); // {} -> always true
    }

    private static CompiledFilter compileLogical(String op, JsonNode spec) {
        switch (op) {
            case "$and":
                return allOf(compileBranches(op, spec));
            case "$or":
                return anyOf(compileBranches(op, spec));
            case "$nor":
                return anyOf(compileBranches(op, spec)).negate();
            case "$not": {
                final CompiledFilter inner = compileDocument(spec);
                return inner.negate();
            }
            default:
                throw new FilterSyntaxException("unsupported top-level operator: " + op);
        }
    }

    private static List<CompiledFilter> compileBranches(String op, JsonNode spec) {
        if (!spec.isArray() || spec.isEmpty()) {
            throw new FilterSyntaxException(op + " expects a non-empty array of filter documents");
        }
        final List<CompiledFilter> branches = new ArrayList<>(spec.size());
        for (JsonNode branch : spec) {
            branches.add(compileDocument(branch));
        }
        return branches;
    }

    // ---------------------------------------------------------- field-level
    // compile

    private static CompiledFilter compileField(String path, JsonNode spec) {
        String[] segments = path.split("\\.");
        if (segments.length == 0) {
            throw new FilterSyntaxException("empty field path");
        }
        ValueTest test = compileValueSpec(spec);
        return root -> test.test(resolve(root, segments));
    }

    /**
     * A test over the values found at a path. The list is empty when the path
     * does not resolve — which is exactly what {@code $exists} needs to see.
     */
    @FunctionalInterface
    private interface ValueTest {
        boolean test(List<JsonNode> values);
    }

    private static ValueTest compileValueSpec(JsonNode spec) {
        if (isOperatorDocument(spec)) {
            final List<ValueTest> tests = new ArrayList<>();
            final Set<Entry<String, JsonNode>> properties = spec.properties();
            for (Entry<String, JsonNode> property : properties) {
                tests.add(compileOperator(property.getKey(), property.getValue()));
            }
            return values -> {
                for (ValueTest t : tests) {
                    if (!t.test(values)) {
                        return false;
                    }
                }
                return true;
            };
        }
        // Bare value: implicit equality (or regex, if it is a /.../ literal).
        return anyValue(equality(spec));
    }

    private static boolean isOperatorDocument(JsonNode spec) {
        if (!spec.isObject() || spec.isEmpty()) {
            return false;
        }
        boolean sawOperator = false;
        boolean sawPlain = false;
        Iterator<String> names = spec.fieldNames();
        while (names.hasNext()) {
            if (names.next().startsWith("$")) {
                sawOperator = true;
            } else {
                sawPlain = true;
            }
        }
        if (sawOperator && sawPlain) {
            throw new FilterSyntaxException(
                    "cannot mix operators and plain fields in the same value specification");
        }
        return sawOperator;
    }

    private static ValueTest compileOperator(String op, JsonNode arg) {
        switch (op) {
            case "$e": // PHPMongoQuery spelling
            case "$eq":
                return anyValue(equality(arg));
            case "$ne":
                return not(anyValue(equality(arg)));
            case "$gt":
                return anyValue(comparison(arg, c -> c > 0));
            case "$gte":
                return anyValue(comparison(arg, c -> c >= 0));
            case "$lt":
                return anyValue(comparison(arg, c -> c < 0));
            case "$lte":
                return anyValue(comparison(arg, c -> c <= 0));
            case "$in":
                return anyValue(anyOfPredicates(compileAlternatives(op, arg)));
            case "$nin":
                return not(anyValue(anyOfPredicates(compileAlternatives(op, arg))));
            case "$all":
                return all(compileAlternatives(op, arg));
            case "$exists":
                return exists(arg);
            case "$mod":
                return modulo(arg);
            case "$size":
                return size(arg);
            case "$regex":
                return anyValue(regexPredicate(requireRegex(arg)));
            case "$not": {
                ValueTest inner = compileValueSpec(arg);
                return not(inner);
            }
            default:
                throw new FilterSyntaxException("unsupported operator: " + op);
        }
    }

    // ------------------------------------------------------------------- operators

    private static List<CompiledFilter> compileAlternatives(String op, JsonNode arg) {
        if (!arg.isArray()) {
            throw new FilterSyntaxException(op + " expects an array");
        }
        List<CompiledFilter> out = new ArrayList<>(arg.size());
        for (JsonNode candidate : arg) {
            out.add(equality(candidate));
        }
        return out;
    }

    private static ValueTest exists(JsonNode arg) {
        if (!arg.isBoolean()) {
            throw new FilterSyntaxException("$exists expects a boolean");
        }
        boolean wanted = arg.booleanValue();
        return values -> !values.isEmpty() == wanted;
    }

    private static ValueTest modulo(JsonNode arg) {
        if (!arg.isArray() || arg.size() != 2 || !arg.get(0).isNumber() || !arg.get(1).isNumber()) {
            throw new FilterSyntaxException("$mod expects [divisor, remainder]");
        }
        long divisor = arg.get(0).longValue();
        long remainder = arg.get(1).longValue();
        if (divisor == 0) {
            throw new FilterSyntaxException("$mod divisor must not be zero");
        }
        return anyValue(v -> v.isNumber() && v.longValue() % divisor == remainder);
    }

    private static ValueTest size(JsonNode arg) {
        if (!arg.isNumber()) {
            throw new FilterSyntaxException("$size expects a number");
        }
        int wanted = arg.intValue();
        return values -> {
            for (JsonNode v : values) {
                if (v.isArray() && v.size() == wanted) {
                    return true;
                }
            }
            return false;
        };
    }

    /**
     * {@code $all}: one of the resolved values must contain every listed element.
     */
    private static ValueTest all(List<CompiledFilter> required) {
        return values -> {
            for (JsonNode value : values) {
                List<JsonNode> elements = new ArrayList<>();
                if (value.isArray()) {
                    value.forEach(elements::add);
                } else {
                    elements.add(value);
                }
                boolean complete = true;
                for (CompiledFilter req : required) {
                    boolean found = false;
                    for (JsonNode element : elements) {
                        if (req.test(element)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        complete = false;
                        break;
                    }
                }
                if (complete) {
                    return true;
                }
            }
            return false;
        };
    }

    // ------------------------------------------------------------ value predicates

    /**
     * Mongo array semantics: a scalar condition matches if the value itself
     * matches,
     * or if the value is an array containing a matching element.
     */
    private static ValueTest anyValue(CompiledFilter predicate) {
        return values -> {
            for (JsonNode value : values) {
                if (predicate.test(value)) {
                    return true;
                }
                if (value.isArray()) {
                    for (JsonNode element : value) {
                        if (predicate.test(element)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        };
    }

    private static ValueTest not(ValueTest test) {
        return values -> !test.test(values);
    }

    private static CompiledFilter anyOfPredicates(List<CompiledFilter> predicates) {
        return value -> {
            for (CompiledFilter p : predicates) {
                if (p.test(value)) {
                    return true;
                }
            }
            return false;
        };
    }

    /**
     * Equality, or regex matching when the expected value is a {@code /.../}
     * literal.
     */
    private static CompiledFilter equality(JsonNode expected) {
        Pattern pattern = asRegexLiteral(expected);
        if (pattern != null) {
            return regexPredicate(pattern);
        }
        return actual -> valuesEqual(actual, expected);
    }

    private static CompiledFilter regexPredicate(Pattern pattern) {
        return actual -> actual.isTextual() && pattern.matcher(actual.textValue()).find();
    }

    private static CompiledFilter comparison(JsonNode expected,
            java.util.function.IntPredicate accept) {
        return actual -> {
            Integer c = compare(actual, expected);
            return c != null && accept.test(c);
        };
    }

    private static boolean valuesEqual(JsonNode a, JsonNode b) {
        if (a.isNumber() && b.isNumber()) {
            return a.decimalValue().compareTo(b.decimalValue()) == 0; // 1 == 1.0
        }
        return a.equals(b); // deep equality
    }

    /**
     * {@code null} when the two nodes are not comparable (different BSON-ish
     * types).
     */
    private static Integer compare(JsonNode a, JsonNode b) {
        if (a.isNumber() && b.isNumber()) {
            return a.decimalValue().compareTo(b.decimalValue());
        }
        if (a.isTextual() && b.isTextual()) {
            return a.textValue().compareTo(b.textValue());
        }
        if (a.isBoolean() && b.isBoolean()) {
            return Boolean.compare(a.booleanValue(), b.booleanValue());
        }
        return null;
    }

    // ---------------------------------------------------------------- path
    // resolution

    private static List<JsonNode> resolve(JsonNode root, String[] segments) {
        List<JsonNode> out = new ArrayList<>(1);
        resolve(root, segments, 0, out);
        return out;
    }

    private static void resolve(JsonNode node, String[] segments, int index, List<JsonNode> out) {
        if (node == null || node.isMissingNode()) {
            return;
        }
        if (index == segments.length) {
            out.add(node);
            return;
        }
        String segment = segments[index];

        if (node.isArray()) {
            Integer position = asArrayIndex(segment);
            if (position != null && position < node.size()) {
                resolve(node.get(position), segments, index + 1, out);
            } else {
                // implicit traversal: "event.tags.name" over an array of objects.
                // The current segment was not consumed as an array index, so it must
                // still be applied to each element (do not advance index here).
                for (JsonNode element : node) {
                    resolve(element, segments, index, out);
                }
            }
            return;
        }
        if (node.isObject()) {
            JsonNode child = node.get(segment);
            if (child != null) {
                resolve(child, segments, index + 1, out);
            }
        }
    }

    private static Integer asArrayIndex(String segment) {
        if (segment.isEmpty() || segment.length() > 9) {
            return null;
        }
        for (int i = 0; i < segment.length(); i++) {
            if (!Character.isDigit(segment.charAt(i))) {
                return null;
            }
        }
        return Integer.valueOf(segment);
    }

    // ------------------------------------------------------------------ regex
    // literals

    private static Pattern requireRegex(JsonNode node) {
        Pattern p = asRegexLiteral(node);
        if (p == null) {
            if (!node.isTextual()) {
                throw new FilterSyntaxException("$regex expects a string");
            }
            return compilePattern(node.textValue(), 0); // bare pattern, no delimiters
        }
        return p;
    }

    /**
     * Recognises {@code "/pattern/flags"} and converts it to a {@link Pattern}.
     * Returns {@code null} when the value is not a regex literal.
     */
    private static Pattern asRegexLiteral(JsonNode node) {
        if (!node.isTextual()) {
            return null;
        }
        String raw = node.textValue();
        if (raw.length() < 2 || raw.charAt(0) != '/') {
            return null;
        }
        int end = raw.lastIndexOf('/');
        if (end <= 0) {
            return null;
        }
        String body = raw.substring(1, end);
        String flags = raw.substring(end + 1);

        int f = 0;
        for (int i = 0; i < flags.length(); i++) {
            switch (flags.charAt(i)) {
                case 'i':
                    f |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
                    break;
                case 'm':
                    f |= Pattern.MULTILINE;
                    break;
                case 's':
                    f |= Pattern.DOTALL;
                    break;
                case 'x':
                    f |= Pattern.COMMENTS;
                    break;
                case 'u':
                    f |= Pattern.UNICODE_CASE;
                    break;
                default:
                    return null; // unknown flag -> treat the whole thing as a literal
            }
        }
        try {
            return compilePattern(body, f);
        } catch (FilterSyntaxException e) {
            return null; // e.g. a plain path like "/home/files/" — not a regex
        }
    }

    private static Pattern compilePattern(String body, int flags) {
        try {
            // PCRE allows \/ to escape the delimiter; Java tolerates it, but normalising
            // keeps patterns portable across regex engines.
            return Pattern.compile(body.replace("\\/", "/"), flags);
        } catch (PatternSyntaxException e) {
            throw new FilterSyntaxException("invalid regular expression: " + body, e);
        }
    }

    // --------------------------------------------------------------------
    // combinators

    private static CompiledFilter allOf(List<CompiledFilter> parts) {
        if (parts.isEmpty()) {
            return root -> true;
        }
        if (parts.size() == 1) {
            return parts.get(0);
        }
        List<CompiledFilter> copy = List.copyOf(parts);
        return root -> {
            for (CompiledFilter p : copy) {
                if (!p.test(root)) {
                    return false;
                }
            }
            return true;
        };
    }

    private static CompiledFilter anyOf(List<CompiledFilter> parts) {
        List<CompiledFilter> copy = List.copyOf(parts);
        return root -> {
            for (CompiledFilter p : copy) {
                if (p.test(root)) {
                    return true;
                }
            }
            return false;
        };
    }

}
