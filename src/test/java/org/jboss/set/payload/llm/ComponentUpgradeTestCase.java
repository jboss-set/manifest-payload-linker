package org.jboss.set.payload.llm;

import org.junit.Assert;
import org.junit.Test;

public class ComponentUpgradeTestCase {

    @Test
    public void validityTest() {
        Assert.assertFalse(new ComponentUpgrade("null", "1.2.3").isValid());
        Assert.assertFalse(new ComponentUpgrade(null, "1.2.3").isValid());
        Assert.assertFalse(new ComponentUpgrade("", "1.2.3").isValid());
        Assert.assertFalse(new ComponentUpgrade("component", null).isValid());
        Assert.assertFalse(new ComponentUpgrade("component", "null").isValid());
        Assert.assertFalse(new ComponentUpgrade("component", "").isValid());
        Assert.assertFalse(new ComponentUpgrade("component", "c5ecee13bf0e53cc21ad59ac73d150e475f5b625").isValid());
        Assert.assertFalse(new ComponentUpgrade("component", "123").isValid());

        Assert.assertTrue(new ComponentUpgrade("component", "1.2").isValid());
        Assert.assertTrue(new ComponentUpgrade("component", "1.2.3").isValid());
        Assert.assertTrue(new ComponentUpgrade("component", "1.2.3.Final").isValid());
        Assert.assertTrue(new ComponentUpgrade("component", "1.2.3.Final-redhat-00001").isValid());
    }
}
