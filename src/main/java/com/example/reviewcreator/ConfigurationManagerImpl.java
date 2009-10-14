package com.example.reviewcreator;

import org.apache.commons.lang.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Collections;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;

public class ConfigurationManagerImpl implements ConfigurationManager {

    private final String RUNAS_CFG      = "com.example.reviewcreator.runAs";
    private final String PROJECTS_CFG   = "com.example.reviewcreator.projects";
    private final PluginSettings store;

    public ConfigurationManagerImpl(PluginSettingsFactory settingsFactory) {
        store = settingsFactory.createGlobalSettings();
    }

    public String loadRunAsUser() {
        final Object value = store.get(RUNAS_CFG);
        return value == null ? null : value.toString();
    }

    public void storeRunAsUser(String username) {
        store.put(RUNAS_CFG, username);
    }

    public List<String> loadEnabledProjects() {

        final Object value = store.get(PROJECTS_CFG);
        return value == null ?
                Collections.<String>emptyList() :
                Arrays.asList(StringUtils.split(value.toString(), ';'));
    }

    public void storeEnabledProjects(List<String> projectKeys) {
        store.put(PROJECTS_CFG, StringUtils.join(projectKeys.iterator(), ';'));
    }
}
