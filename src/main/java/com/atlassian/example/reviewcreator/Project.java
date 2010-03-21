package com.atlassian.example.reviewcreator;

public class Project {

    private final int id;
    private final String key;
    private final String name;
    private final boolean enabled;
    private final String moderator;

    Project(int id, String key, String name, String moderator, boolean enabled) {
        this.id = id;
        this.key = key;
        this.name = name;
        this.moderator = moderator;
        this.enabled = enabled;
    }

    /**
     * @since   v1.3
     */
    public int getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    /**
     * @since   v1.3
     * @return  the username of this project's default moderator or
     * <code>null</code> if not set.
     */
    public String getModerator() {
        return moderator;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Project project = (Project) o;

        if (id != project.id) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return id;
    }
}
