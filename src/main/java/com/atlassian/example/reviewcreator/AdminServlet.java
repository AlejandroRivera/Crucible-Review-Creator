package com.atlassian.example.reviewcreator;

import com.atlassian.crucible.spi.data.ProjectData;
import com.atlassian.crucible.spi.services.ImpersonationService;
import com.atlassian.crucible.spi.services.NotFoundException;
import com.atlassian.crucible.spi.services.Operation;
import com.atlassian.crucible.spi.services.ProjectService;
import com.atlassian.crucible.spi.services.ServerException;
import com.atlassian.crucible.spi.services.UserService;
import com.atlassian.fisheye.plugin.web.helpers.VelocityHelper;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class AdminServlet extends HttpServlet {

    private final ProjectService projectService;
    private final ImpersonationService impersonator;
    private final UserService userService;
    private final VelocityHelper velocity;
    private final ConfigurationManager config;

    public AdminServlet(
            ConfigurationManager config,
            ProjectService projectService,
            ImpersonationService impersonator,
            UserService userService,
            VelocityHelper velocity) {
        
        this.projectService = projectService;
        this.impersonator = impersonator;
        this.userService = userService;
        this.velocity = velocity;
        this.config = config;
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        final Map<String, Object> params = new HashMap<String, Object>();

        final String username = config.loadRunAsUser();
        if (!StringUtils.isEmpty(username)) {
            params.put("username", username);

            impersonator.doAsUser(null, username, new Operation<Void, RuntimeException>() {
                public Void perform() throws RuntimeException {
                    params.put("projects", loadProjects());
                    return null;
                }
            });

            params.put("contextPath", request.getContextPath());
            params.put("createMode", config.loadCreateMode().name());
            params.put("committerNames", config.loadCrucibleUserNames());
            params.put("groupNames", config.loadCrucibleGroups());
            params.put("iterative", config.loadIterative());
            params.put("stringUtils", new StringUtils());
        }

        response.setContentType("text/html");
        velocity.renderVelocityTemplate("templates/admin.vm", params, response.getWriter());
    }

    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException {

        final String username = req.getParameter("username");
        config.storeRunAsUser(username);

        final List<String> enabled = req.getParameterValues("enabled") == null ?
                Collections.<String>emptyList() : Arrays.asList(req.getParameterValues("enabled"));

        impersonator.doAsUser(null, username, new Operation<Void, RuntimeException>() {
            public Void perform() throws RuntimeException {
                final Set<Project> projects = new HashSet<Project>();
                // TODO: use a google collections transformer
                for (Project p : loadProjects()) {
                    projects.add(new Project(p.getId(), p.getKey(), p.getName(), p.getModerator(), enabled.contains(p.getKey())));
                }
                storeProjects(projects);

                config.storeCreateMode(CreateMode.valueOf(
                        Utils.defaultIfNull(req.getParameter("createMode"), CreateMode.ALWAYS.name())));

                final String[] committerNames = StringUtils.split(req.getParameter("committerNames"), ",    \n\r");
                config.storeCrucibleUserNames(committerNames == null ? Collections.<String>emptyList() :
                        getValidatedUsernames(Lists.newArrayList(committerNames)));

                final String[] groupNames = StringUtils.split(req.getParameter("groupNames"), ",    \n\r");
                config.storeCrucibleGroups(groupNames == null ? Collections.<String>emptyList() :
                        Lists.newArrayList(groupNames));

                config.storeIterative(req.getParameter("iterative") != null);
                return null;
            }
        });

        resp.sendRedirect("./reviewcreatoradmin");
    }

    /**
     * @param crucibleUsernames
     * @return  the (sub)set of usernames that exist in the system. The names
     * present in the specified list that are not present in the returned list
     * represent invalid usernames.
     */
    private Collection<String> getValidatedUsernames(Collection<String> crucibleUsernames) {

        return Collections2.filter(crucibleUsernames, new Predicate<String>() {
            public boolean apply(String username) {
                try {
                    userService.getUser(username);
                    return true;
                } catch (NotFoundException nfe) {
                    // Not very good practice to use exceptions for flow
                    // control, but it's the only way to detect the existence
                    // of a Crucible user.
                    return false;
                } catch (ServerException se) {
                    throw new RuntimeException(String.format(
                            "Error validating Crucible user \"%s\": %s", username, se.getMessage()), se);
                }
            }
        });
    }

    /**
     * Returns a set of all projects.
     * Note: this method must be run as a valid Crucible user.
     *
     * @return
     */
    private Set<Project> loadProjects() {

        final List<String> enabledKeys = config.loadEnabledProjects();

        final Set<Project> projects = new TreeSet<Project>(new Comparator<Project>() {
            public int compare(Project p1, Project p2) {
                return p1.getKey().compareTo(p2.getKey());
            }
        });
        for (ProjectData p : projectService.getAllProjects()) {
            projects.add(new Project(p.getId(), p.getKey(), p.getName(), p.getDefaultModerator(), enabledKeys.contains(p.getKey())));
        }
        return projects;
    }

    /**
     * Stores the projects for which auto review creation is enabled.
     *
     * @param projects
     */
    private void storeProjects(Set<Project> projects) {

        final List<String> enabled = new ArrayList<String>();
        for (Project p : projects) {
            if (p.isEnabled()) {
                enabled.add(p.getKey());
            }
        }
        config.storeEnabledProjects(enabled);
    }
}
