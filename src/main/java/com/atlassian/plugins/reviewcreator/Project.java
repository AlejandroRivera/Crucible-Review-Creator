package com.atlassian.plugins.reviewcreator;

public class Project {

    private final String key;
    private final String name;
    private final boolean enabled;

    Project(String key, String name, boolean enabled) {
        this.key = key;
        this.name = name;
        this.enabled = enabled;
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Project project = (Project) o;

        if (key != null ? !key.equals(project.key) : project.key != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = key != null ? key.hashCode() : 0;
        result = 31 * result + (enabled ? 1 : 0);
        return result;
    }
}
