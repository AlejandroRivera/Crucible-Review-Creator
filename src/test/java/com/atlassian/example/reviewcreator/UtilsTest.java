package com.atlassian.example.reviewcreator;

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
}
