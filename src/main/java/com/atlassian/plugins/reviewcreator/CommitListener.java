package com.atlassian.plugins.reviewcreator;

import com.atlassian.event.EventListener;
import com.atlassian.event.Event;
import com.atlassian.crucible.spi.services.*;
import com.atlassian.crucible.spi.data.*;
import com.atlassian.fisheye.event.CommitEvent;
import com.atlassian.fisheye.spi.services.RevisionDataService;
import com.atlassian.fisheye.spi.data.ChangesetDataFE;

import java.util.*;

import org.apache.log4j.Logger;
import org.apache.commons.lang.StringUtils;

/**
 * <p>
 * Event listener that subscribes to commit events and creates a review for
 * each commit.
 * </p>
 * <p>
 * Auto review creation can be enabled/disabled by an administrator on a
 * per-project basis. Enabled projects must be bound to a FishEye repository
 * and must have a default Moderator configured in the admin section.
 * </p>
 * <p>
 * When auto review creation is enabled for a Crucible project, this
 * {@link com.atlassian.event.EventListener} will intercept all commits for
 * the project's repository and create a review for it. The review's author
 * role is set to the committer of the changeset and the review's moderator is
 * set to the project's default moderator.
 * </p>
 * <p>
 * When the project has default reviewers configured, these will be added to
 * the review.
 * </p>
 *
 * @author  Erik van Zijst
 */
public class CommitListener implements EventListener {

    private final Logger logger = Logger.getLogger(getClass().getName());

    private final RevisionDataService revisionService;  // provided by FishEye
    private final ReviewService reviewService;          // provided by Crucible
    private final ProjectService projectService;        // provided by Crucible
    private final UserService userService;              // provided by Crucible
    private final ImpersonationService impersonator;    // provided by Crucible
    private final ConfigurationManager config;          // provided by our plugin

    public CommitListener(ConfigurationManager config,
            ReviewService reviewService,
            ProjectService projectService,
            RevisionDataService revisionService,
            UserService userService,
            ImpersonationService impersonator) {

        this.reviewService = reviewService;
        this.revisionService = revisionService;
        this.projectService = projectService;
        this.userService = userService;
        this.impersonator = impersonator;
        this.config = config;
    }

    public Class[] getHandledEventClasses() {
        return new Class[] {CommitEvent.class};
    }

    public void handleEvent(Event event) {

        final CommitEvent commit = (CommitEvent) event;

        if (isPluginEnabled()) {
            try {
                // switch to admin user so we can access all projects:
                impersonator.doAsUser(null, config.loadRunAsUser(),
                    new Operation<Void, ServerException>() {
                        public Void perform() throws ServerException {

                            ChangesetDataFE cs = revisionService.getChangeset(
                                    commit.getRepositoryName(), commit.getChangeSetId());
                            ProjectData project = getEnabledProjectForRepository(
                                    commit.getRepositoryName());

                            if (project != null) {
                                String moderator = project.getDefaultModerator();
                                if (moderator != null) {
                                    // create the review:
                                    createReview(commit.getRepositoryName(), cs, project, moderator);

                                } else {
                                    logger.error(String.format("Unable to auto-create review for changeset %s. " +
                                            "No default moderator configured for project %s.",
                                            commit.getChangeSetId(), project.getKey()));
                                }
                            } else {
                                logger.error(String.format("Unable to auto-create review for changeset %s. " +
                                        "No projects found that bind to repository %s.",
                                        commit.getChangeSetId(), commit.getRepositoryName()));
                            }
                            return null;
                        }
                    });
            } catch (Exception e) {
                logger.error(String.format("Unable to auto-create " +
                        "review for changeset %s: %s.",
                        commit.getChangeSetId(), e.getMessage()), e);
            }
        }
    }

    private void createReview(final String repoKey, final ChangesetDataFE cs,
                              final ProjectData project, String moderator)
            throws ServerException {

        final ReviewData template = buildReviewTemplate(cs, project);

        // switch to user moderator:
        impersonator.doAsUser(null, moderator, new Operation<Void, ServerException>() {
            public Void perform() throws ServerException {

                // create a new review:
                final ReviewData review = reviewService.createReviewFromChangeSets(
                        template,
                        repoKey,
                        Collections.singletonList(new ChangesetData(cs.getCsid())));

                // add the project's default reviewers:
                addReviewers(review, project);

                // start the review, so everyone is notified:
                reviewService.changeState(review.getPermaId(), "action:approveReview");

                logger.info(String.format("Auto-created review %s for " +
                        "commit %s:%s with moderator %s.",
                        review.getPermaId(), repoKey,
                        cs.getCsid(), review.getModerator().getUserName()));
                return null;
            }
        });
    }

    private void addReviewers(ReviewData review, ProjectData project) {
        final List<String> reviewers = project.getDefaultReviewerUsers();
        if (reviewers != null && !reviewers.isEmpty()) {
            reviewService.addReviewers(review.getPermaId(),
                    reviewers.toArray(new String[reviewers.size()]));
        }
    }

    /**
     * <p>
     * This method must be invoked with admin permissions.
     * </p>
     *
     * @param cs
     * @param project
     * @return
     * @throws ServerException
     */
    private ReviewData buildReviewTemplate(ChangesetDataFE cs, ProjectData project)
            throws ServerException {

        final Map<String, UserData> committerToUser =
                loadCommitterMappings(project.getDefaultRepositoryName());
        final UserData creator = committerToUser.get(cs.getAuthor()) == null ?
                userService.getUser(project.getDefaultModerator()) :
                committerToUser.get(cs.getAuthor());
        final Date dueDate = project.getDefaultDuration() == null ? null :
                DateHelper.addWorkingDays(new Date(), project.getDefaultDuration());

        return new ReviewData(
                project.getKey(),
                cs.getComment(),    // review name
                StringUtils.defaultIfEmpty(project.getDefaultObjectives(), cs.getComment()),
                creator,            // author
                userService.getUser(project.getDefaultModerator()),
                creator,            // creator
                null,   // review permaId
                null,   // review summary
                null,   // review state
                true,   // allow anyone to join
                null,   // parent review
                null,   // create data
                null,   // close date
                dueDate,
                0,      // defect metrics version; unused during creation
                null    // jira issue key
        );
    }

    /**
     * <p>
     * Given a FishEye repository key, returns the Crucible project that has
     * this repository configured as its default.
     * </p>
     * <p>
     * When no project is bound to the specified repository, or not enabled
     * for automatic review creation, <code>null</code> is returned.
     * </p>
     * <p>
     * This method must be invoked with admin permissions.
     * </p>
     *
     * TODO: What to do when there are multiple projects?
     *
     * @param repoKey   a FishEye repository key (e.g. "CR").
     * @return  the Crucible project that has the specified repository
     *  configured as its default repo and has been enabled for automatic
     *  review creation.
     */
    private ProjectData getEnabledProjectForRepository(String repoKey) {

        final List<ProjectData> projects = projectService.getAllProjects();
        final List<String> enabled = config.loadEnabledProjects();
        for (ProjectData project : projects) {
            if (repoKey.equals(project.getDefaultRepositoryName()) &&
                    enabled.contains(project.getKey())) {
                return project;
            }
        }
        return null;
    }

    /**
     * Returns a map containing all committer names that are mapped to Crucible
     * user accounts.
     * This is an expensive operation that will be redundant when the fecru SPI
     * gets a <code>CommitterMapperService</code>.
     * <p>
     * This method must be invoked with admin permissions.
     * </p>
     *
     * @param   repoKey
     * @return
     */
    private Map<String, UserData> loadCommitterMappings(final String repoKey)
            throws ServerException {

        final Map<String, UserData> committerToUser = new HashMap<String, UserData>();
        for (UserData ud : userService.getAllUsers()) {
            final UserProfileData profile = userService.getUserProfile(ud.getUserName());
            final List<String> committers = profile.getMappedCommitters().get(repoKey);
            if (committers != null) {
                for (String committer : committers) {
                    committerToUser.put(committer, ud);
                }
            }
        }
        return committerToUser;
    }
    
    private boolean isPluginEnabled() {
        return !StringUtils.isEmpty(config.loadRunAsUser());
    }
}
