package com.example.reviewcreator;

import com.atlassian.event.EventListener;
import com.atlassian.event.Event;
import com.atlassian.crucible.spi.services.*;
import com.atlassian.crucible.spi.data.ChangesetData;
import com.atlassian.crucible.spi.data.ProjectData;
import com.atlassian.crucible.spi.data.ReviewData;
import com.atlassian.crucible.spi.data.UserData;
import com.atlassian.fisheye.event.CommitEvent;
import com.atlassian.fisheye.spi.services.RevisionDataService;
import com.atlassian.fisheye.spi.data.ChangesetDataFE;

import java.util.Collections;
import java.util.Date;
import java.util.List;

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

    private final String admin = "admin";
    private final Logger logger = Logger.getLogger(getClass().getName());
    private final ReviewService reviews;
    private final RevisionDataService revisionService;
    private final ProjectService projectService;
    private final UserService userService;
    private final ImpersonationService impersonator;

    public CommitListener(
            ReviewService reviews,
            ProjectService projectService,
            RevisionDataService revisionService,
            UserService userService,
            ImpersonationService impersonator) {

        this.reviews = reviews;
        this.revisionService = revisionService;
        this.projectService = projectService;
        this.userService = userService;
        this.impersonator = impersonator;
    }

    public void handleEvent(Event event) {

        final CommitEvent commit = (CommitEvent) event;
        final ChangesetDataFE cs = revisionService.getChangeset(commit.getRepositoryName(), commit.getChangeSetId());

        try {
            final ProjectData project = getProjectForRepository(commit.getRepositoryName());
            if (project != null) {
                final String moderator = project.getDefaultModerator();
                if (moderator != null) {
                    impersonator.doAsUser(null, moderator, new Operation<Void, ServerException>() {
                        public Void perform() throws ServerException {

                            // create a new review:
                            final ReviewData review = reviews.createReviewFromChangeSets(
                                    buildReviewTemplate(cs, project),
                                    commit.getRepositoryName(),
                                    Collections.singletonList(new ChangesetData(cs.getCsid())));

                            // add the project's default reviewers:
                            final List<String> reviewers = project.getDefaultReviewerUsers();
                            if (reviewers != null && !reviewers.isEmpty()) {
                                reviews.addReviewers(review.getPermaId(),
                                        reviewers.toArray(new String[reviewers.size()]));
                            }

                            // start the review, so everyone is notified:
                            reviews.changeState(review.getPermaId(), "action:approveReview");

                            logger.info(String.format("Auto-created review %s for " +
                                    "commit %s:%s with moderator %s.",
                                    review.getPermaId(), commit.getRepositoryName(),
                                    cs.getCsid(), review.getModerator().getUserName()));
                            return null;
                        }
                    });

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
        } catch (Exception e) {
            logger.error(String.format("Unable to auto-create " +
                    "review for changeset %s: %s.",
                    commit.getChangeSetId(), e.getMessage(), e));
        }
    }

    private ReviewData buildReviewTemplate(ChangesetDataFE cs, ProjectData project) throws ServerException {

        final Date dueDate = project.getDefaultDuration() == null ? null :
                DateHelper.addWorkingDays(new Date(), project.getDefaultDuration());

        return new ReviewData(
                project.getKey(),
                cs.getComment(),    // review name
                StringUtils.defaultIfEmpty(project.getDefaultObjectives(), cs.getComment()),
                getUserByCommitter(cs.getAuthor()), // author
                userService.getUser(project.getDefaultModerator()),
                getUserByCommitter(cs.getAuthor()), // creator
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

    private UserData getUserByCommitter(String username) throws ServerException {
        // TODO: need to implement a CommitterMappingService in trunk
        return userService.getUser(username);
    }

    /**
     * <p>
     * Given a FishEye repository key, returns the Crucible project that has
     * this repository configured as its default.
     * </p>
     * <p>
     * When no project is bound this the specified repository,
     * <code>null</code> is returned.
     * </p>
     *
     * TODO: What to do when there are multiple projects?
     *
     * @param repoKey   a FishEye repository key (e.g. "CR").
     * @return  the Crucible project that has the specified repository
     *  configured as its default repo.
     */
    private ProjectData getProjectForRepository(String repoKey) {

        final List<ProjectData> projects =
                impersonator.doAsUser(null, admin, new Operation<List<ProjectData>, RuntimeException>() {
                    public List<ProjectData> perform() throws RuntimeException {
                        return projectService.getAllProjects();
                    }
                });
        for (ProjectData project : projects) {
            if (repoKey.equals(project.getDefaultRepositoryName())) {
                return project;
            }
        }
        return null;
    }

    public Class[] getHandledEventClasses() {
        return new Class[] {CommitEvent.class};
    }
}
