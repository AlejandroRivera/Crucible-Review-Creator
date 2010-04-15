package com.atlassian.example.reviewcreator;

import java.util.Collection;
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

    /**
     * @since   v1.2
     */
    Collection<String> loadCrucibleUserNames();

    /**
     * @since   v1.2
     */
    void storeCrucibleUserNames(Collection<String> usernames);

    /**
     * @since   v1.3
     */
    Collection<String> loadCrucibleGroups();

    /**
     * @since   v1.3
     */
    void storeCrucibleGroups(Collection<String> groupnames);

    CreateMode loadCreateMode();

    void storeCreateMode(CreateMode mode);

    /**
     * @since   v1.4.1
     */
    boolean loadIterative();

    /**
     * @since   v1.4.1
     */
    void storeIterative(boolean iterative);
}
