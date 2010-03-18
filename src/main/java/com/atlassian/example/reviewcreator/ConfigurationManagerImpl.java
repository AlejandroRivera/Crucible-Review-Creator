package com.atlassian.example.reviewcreator;

import com.atlassian.plugin.util.Assertions;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import org.apache.commons.lang.StringUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ConfigurationManagerImpl implements ConfigurationManager {

    private final String RUNAS_CFG      = "com.example.reviewcreator.runAs";
    private final String PROJECTS_CFG   = "com.example.reviewcreator.projects";
    private final String COMMITTER_CFG   = "com.example.reviewcreator.crucibleUsers";
    private final String CREATE_MODE_CFG   = "com.example.reviewcreator.createMode";
    private final PluginSettings store;

    public ConfigurationManagerImpl(PluginSettingsFactory settingsFactory) {
        this(settingsFactory.createGlobalSettings());
    }

    ConfigurationManagerImpl(PluginSettings store) {
        this.store = store;
    }

    public String loadRunAsUser() {
        final Object value = store.get(RUNAS_CFG);
        return value == null ? null : value.toString();
    }

    public void storeRunAsUser(String username) {
        store.put(RUNAS_CFG, username);
    }

    public CreateMode loadCreateMode() {
        final Object value = store.get(CREATE_MODE_CFG);
        try {
            return value == null ? CreateMode.ALWAYS : CreateMode.valueOf(value.toString());
        } catch(IllegalArgumentException e) {
            return CreateMode.ALWAYS;
        }
    }

    public void storeCreateMode(CreateMode mode) {
        store.put(CREATE_MODE_CFG, mode.name());
    }

    public List<String> loadEnabledProjects() {
        return loadStringList(PROJECTS_CFG);
    }

    public void storeEnabledProjects(List<String> projectKeys) {
        storeStringList(PROJECTS_CFG, projectKeys);
    }

    public Collection<String> loadCrucibleUserNames() {
        return loadStringList(COMMITTER_CFG);
    }

    public void storeCrucibleUserNames(Collection<String> usernames) {
        storeStringList(COMMITTER_CFG, usernames);
    }

    private void storeStringList(String key, Iterable<String> strings) {
        store.put(Assertions.notNull("PluginSettings key", key),
                StringUtils.join(strings.iterator(), ';'));
    }

    private List<String> loadStringList(String key) {
        final Object value = store.get(Assertions.notNull("PluginSettings key", key));
        return value == null ?
                Collections.<String>emptyList() :
                Arrays.asList(StringUtils.split(value.toString(), ';'));
    }
}
