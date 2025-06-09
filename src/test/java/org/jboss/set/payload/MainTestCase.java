package org.jboss.set.payload;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;

public class MainTestCase {

    @Test
    public void testInputParsing() {
        Collection<String> strings = Main.parseCommaSeparatedStringList(" ");
        Assert.assertTrue(strings.isEmpty());

        strings = Main.parseCommaSeparatedStringList("");
        Assert.assertTrue(strings.isEmpty());

        strings = Main.parseCommaSeparatedStringList("test");
        Assert.assertEquals(1, strings.size());
        Assert.assertEquals("test", strings.iterator().next());

        ArrayList<String> items = new ArrayList<>(Main.parseCommaSeparatedStringList("bla,blu"));
        Assert.assertEquals(2, items.size());
        Assert.assertTrue(items.contains("bla"));
        Assert.assertTrue(items.contains("blu"));
    }
}
