package com.example.reviewcreator;

import com.atlassian.fisheye.plugin.web.helpers.VelocityHelper;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.crucible.spi.services.ImpersonationService;
import com.atlassian.crucible.spi.services.Operation;
import com.atlassian.crucible.spi.services.ProjectService;
import com.atlassian.crucible.spi.data.ProjectData;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.*;

import org.apache.commons.lang.StringUtils;

/**
 * @author  Erik van Zijst
 */
public class AdminServlet extends HttpServlet {

    private final String RUNAS_CFG = "com.example.reviewcreator.runAs";
    private final String PROJECTS_CFG = "com.example.reviewcreator.projects";
    private final PluginSettingsFactory settingsFactory;
    private final ProjectService projectService;
    private final ImpersonationService impersonator;
    private final VelocityHelper velocity;

    public AdminServlet(PluginSettingsFactory settingsFactory, ProjectService projectService, ImpersonationService impersonator,  VelocityHelper velocity) {
        this.settingsFactory = settingsFactory;
        this.projectService = projectService;
        this.impersonator = impersonator;
        this.velocity = velocity;
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

        final PluginSettings settings = settingsFactory.createGlobalSettings();
        final Map<String, Object> params = new HashMap<String, Object>();

        final String username = (String) settings.get(RUNAS_CFG);
        if (!StringUtils.isEmpty(username)) {
            params.put("username", username);

            impersonator.doAsUser(null, username, new Operation<Void, RuntimeException>() {
                public Void perform() throws RuntimeException {
                    params.put("projects", loadProjects(settings));
                    return null;
                }
            });
        }

        response.setContentType("text/html");
        velocity.renderVelocityTemplate("admin.vm", params, response.getWriter());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        final PluginSettings settings = settingsFactory.createGlobalSettings();
        final String username = req.getParameter("username");

        settings.put(RUNAS_CFG, username);
        final List<String> enabled = req.getParameterValues("enabled") == null ?
                Collections.<String>emptyList() : Arrays.asList(req.getParameterValues("enabled"));

        impersonator.doAsUser(null, username, new Operation<Void, RuntimeException>() {
            public Void perform() throws RuntimeException {
                final Set<Project> projects = new HashSet<Project>();
                for (Project p : loadProjects(settings)) {
                    projects.add(new Project(p.getKey(), p.getName(), enabled.contains(p.getKey())));
                }
                storeProjects(settings, projects);
                return null;
            }
        });
        resp.sendRedirect("./reviewcreatoradmin");
    }

    /**
     * Returns a list of all projects.
     * Note: this method must be run as a valid Crucible user.
     *
     * @return
     */
    private Set<Project> loadProjects(PluginSettings settings) {

        final List<String> enabledKeys = settings.get(PROJECTS_CFG) == null ?
                Collections.<String>emptyList() :
                Arrays.asList(StringUtils.split(settings.get(PROJECTS_CFG).toString(), ';'));

        final Set<Project> projects = new TreeSet<Project>(new Comparator<Project>() {
            public int compare(Project p1, Project p2) {
                return p1.getKey().compareTo(p2.getKey());
            }
        });
        for (ProjectData p : projectService.getAllProjects()) {
            projects.add(new Project(p.getKey(), p.getName(), enabledKeys.contains(p.getKey())));
        }
        return projects;
    }

    /**
     * Stores the projects for which auto review creation is enabled.
     *
     * @param projects
     */
    private void storeProjects(PluginSettings settings, Set<Project> projects) {

        final List<String> enabled = new ArrayList<String>();
        for (Project p : projects) {
            if (p.isEnabled()) {
                enabled.add(p.getKey());
            }
        }
        settings.put(PROJECTS_CFG, StringUtils.join(enabled.iterator(), ';'));
    }

}