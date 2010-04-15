package com.atlassian.example.reviewcreator;

import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import static org.junit.Assert.*;

public class UtilsTest {

    @Test
    public void testGetFirstNonEmptyLine() throws Exception {

        assertNull(Utils.firstNonEmptyLine(null));
        assertEquals("", Utils.firstNonEmptyLine(""));
        assertEquals("", Utils.firstNonEmptyLine("\r\n\n\r"));
        assertEquals(" line2 ", Utils.firstNonEmptyLine(" \n line2 "));
        assertEquals("line1", Utils.firstNonEmptyLine("\nline1\nline2"));
        assertEquals("line3", Utils.firstNonEmptyLine(" \n \nline3"));
    }

    @Test
    public void testExtractReviewIds() {

        assertTrue(Utils.extractReviewIds(null, "Foo").isEmpty());
        assertTrue(Utils.extractReviewIds("", "Foo").isEmpty());
        assertTrue(Utils.extractReviewIds("Foo", "Foo").isEmpty());
        assertTrue(Utils.extractReviewIds("This is not a review id: Foo-", "Foo").isEmpty());
        assertTrue(Utils.extractReviewIds("This is a review in a different project: CR-FE-1", "CR").isEmpty());

        assertEquals(ImmutableSet.of("FOO-1"), Utils.extractReviewIds("FOO-1", "FOO"));
        assertEquals(ImmutableSet.of("FOO-1", "FOO-3456"), Utils.extractReviewIds("There's 2 reviews in here: FOO-1, FOOBAR-4, FOO-3456", "FOO"));
    }
}
