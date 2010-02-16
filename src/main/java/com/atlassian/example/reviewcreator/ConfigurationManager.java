package com.atlassian.example.reviewcreator;

import java.util.List;

/**
 * Manages plugin settings serialization and persistence.
 * This service is accessed by both the admin servlet and the event listener.
 */
public interface ConfigurationManager {

    String loadRunAsUser();

    void storeRunAsUser(String username);

    List<String> loadEnabledProjects();

    void storeEnabledProjects(List<String> projectKeys);
}
