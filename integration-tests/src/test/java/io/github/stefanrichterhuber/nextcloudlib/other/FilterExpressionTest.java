package io.github.stefanrichterhuber.nextcloudlib.other;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Predicate;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.stefanrichterhuber.nextcloudlib.profiles.AppPasswordTestProfile;
import io.github.stefanrichterhuber.nextcloudlib.runtime.events.FilterExpression;
import io.github.stefanrichterhuber.nextcloudlib.runtime.events.PHPMongoQueryParser.CompiledFilter;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

@QuarkusTest
@TestProfile(AppPasswordTestProfile.class)
public class FilterExpressionTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Filter with property expression
    @Inject
    @FilterExpression("{ \"user.uid\": \"${nextcloud.filter.admin-uid}\" }")
    CompiledFilter filter;

    // Filter with property expression and default value
    @Inject
    @FilterExpression("{ \"user.uid\": \"${nextcloud.filter.admin-no-id:notThatAdmin}\" }")
    CompiledFilter filter1;

    // Complex expression
    @Inject
    @FilterExpression("{ \"time\": { \"$lt\": 3000 } }")
    CompiledFilter filter2;

    // Multiple expressions
    @Inject
    @FilterExpression("{ \"time\": { \"$lt\": 3000 }, \"user.uid\": \"${nextcloud.filter.admin-uid}\"  }")
    CompiledFilter filter3;

    @Inject
    @ConfigProperty(name = "nextcloud.filter.admin-uid")
    String adminUid;

    public record TestObject(User user, String id, long time) {
        public record User(String name, String uid) {
        }
    }

    @Test
    public void testCompiledFilterInjection() throws JsonProcessingException {
        assertNotNull(filter);

        TestObject o1 = new TestObject(new TestObject.User("Admin", adminUid), "001", 1000);
        TestObject o2 = new TestObject(new TestObject.User("Admin", "notThatAdmin"), "002", 2000);

        Predicate<TestObject> p1 = filter.intoObjectPredicate();

        assertTrue(p1.test(o1));
        assertFalse(p1.test(o2));

        Predicate<String> p2 = filter.intoJSONStringPredicate();

        String o1Str = MAPPER.writeValueAsString(o1);
        String o2Str = MAPPER.writeValueAsString(o2);

        assertTrue(p2.test(o1Str));
        assertFalse(p2.test(o2Str));

        Predicate<TestObject> p3 = filter1.intoObjectPredicate();
        assertFalse(p3.test(o1));
        assertTrue(p3.test(o2));

        Predicate<TestObject> p4 = filter2.intoObjectPredicate();
        assertTrue(p4.test(o1));
        assertTrue(p4.test(o2));

        Predicate<TestObject> p5 = filter3.intoObjectPredicate();
        assertTrue(p5.test(o1));
        assertFalse(p5.test(o2));

    }

}
