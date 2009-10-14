package com.example.reviewcreator;

import com.atlassian.sal.api.pluginsettings.PluginSettings;

import java.util.List;
import java.util.Collections;
import java.util.Arrays;

import org.apache.commons.lang.StringUtils;

/**
 * Created by IntelliJ IDEA.
 * User: administrator
 * Date: Oct 14, 2009
 * Time: 4:23:33 PM
 * To change this template use File | Settings | File Templates.
 */
public class ConfigurationManager {

    private final String RUNAS_CFG = "com.example.reviewcreator.runAs";
    private final String PROJECTS_CFG = "com.example.reviewcreator.projects";
    private final PluginSettings store;

    public ConfigurationManager(PluginSettings store) {
        this.store = store;
    }

    public String loadRunAsUser() {
        return store.get(RUNAS_CFG).toString();
    }

    public void storeRunAsUser(String username) {
        store.put(RUNAS_CFG, username);
    }

    public List<String> loadEnabledProjects() {

        return store.get(PROJECTS_CFG) == null ?
                Collections.<String>emptyList() :
                Arrays.asList(StringUtils.split(store.get(PROJECTS_CFG).toString(), ';'));
    }

    public void storeEnabledProjects(List<String> projectKeys) {
        store.put(PROJECTS_CFG, StringUtils.join(projectKeys.iterator(), ';'));
    }
}
