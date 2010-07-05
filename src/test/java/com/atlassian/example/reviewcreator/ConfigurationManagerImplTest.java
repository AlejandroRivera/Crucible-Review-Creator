package com.atlassian.example.reviewcreator;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.Before;
import com.atlassian.sal.api.pluginsettings.PluginSettings;

import java.util.*;

public class ConfigurationManagerImplTest {

    private SettingsMock store;

    @Before
    public void setup() {
        store = new SettingsMock();
    }

    @Test
    public void testConfigurationManager() {

        final String user = "foo";
        final List<String> expected = Arrays.asList("CR", "RC");
        final ConfigurationManagerImpl config = new ConfigurationManagerImpl(store);
        assertTrue(config.loadEnabledProjects().isEmpty());
        assertNull(config.loadRunAsUser());

        config.storeRunAsUser(user);
        assertEquals(user, config.loadRunAsUser());

        config.storeEnabledProjects(expected);
        assertEquals(expected.size(), config.loadEnabledProjects().size());
        List<String> actual = new ArrayList<String>(config.loadEnabledProjects());
        actual.removeAll(expected);
        assertTrue(actual.isEmpty());
    }

    private static class SettingsMock implements PluginSettings {

        private final Map<String, Object> store = new HashMap<String, Object>();

        public Object get(String s) {
            return store.get(s);
        }

        public Object put(String s, Object o) {
            return store.put(s, o);
        }

        public Object remove(String s) {
            return store.remove(s);
        }
    }
}
