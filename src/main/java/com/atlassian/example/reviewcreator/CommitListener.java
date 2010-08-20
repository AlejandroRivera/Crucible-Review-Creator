package com.atlassian.example.reviewcreator;

import com.atlassian.crucible.spi.data.*;
import com.atlassian.crucible.spi.PermId;
import com.atlassian.crucible.spi.services.*;
import com.atlassian.event.Event;
import com.atlassian.event.EventListener;
import com.atlassian.fisheye.event.CommitEvent;
import com.atlassian.fisheye.spi.data.ChangesetDataFE;
import com.atlassian.fisheye.spi.services.RevisionDataService;
import com.atlassian.sal.api.user.UserManager;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import java.util.*;

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

    private final RevisionDataService revisionService;          // provided by FishEye
    private final ReviewService reviewService;                  // provided by Crucible
    private final ProjectService projectService;                // provided by Crucible
    private final UserService userService;                      // provided by Crucible
    private final UserManager userManager;                      // provided by SAL
    private final ImpersonationService impersonator;            // provided by Crucible
    private final ConfigurationManager config;                  // provided by our plugin

    private static final ThreadLocal<Map<String, UserData>> committerToCrucibleUser =
            new ThreadLocal();

    public CommitListener(ConfigurationManager config,
            ReviewService reviewService,
            ProjectService projectService,
            RevisionDataService revisionService,
            UserService userService,
            UserManager userManager,
            ImpersonationService impersonator) {

        this.reviewService = reviewService;
        this.revisionService = revisionService;
        this.projectService = projectService;
        this.userService = userService;
        this.userManager = userManager;
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
                // switch to admin user so we can access all projects and API services:
                impersonator.doAsUser(null, config.loadRunAsUser(),
                    new Operation<Void, ServerException>() {
                        public Void perform() throws ServerException {

                            final ChangesetDataFE cs = revisionService.getChangeset(
                                    commit.getRepositoryName(), commit.getChangeSetId());
                            final ProjectData project = getEnabledProjectForRepository(
                                    commit.getRepositoryName());

                            if (project != null) {
                                committerToCrucibleUser.set(loadCommitterMappings(project.getDefaultRepositoryName()));
                                if (project.getDefaultModerator() != null) {
                                    if (isUnderScrutiny(cs.getAuthor())) {
                                        if (!config.loadIterative() || !appendToReview(commit.getRepositoryName(), cs, project)) {
                                            // create a new review:
                                            createReview(commit.getRepositoryName(), cs, project);
                                        }
                                    } else {
                                        logger.info(String.format("Not creating a review for changeset %s.",
                                                commit.getChangeSetId()));
                                    }
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

    /**
     * Determines whether or not the user that made the commit is exempt from
     * automatic reviews, or whether the user is on the list of always having
     * its commits automatically reviewed.
     *
     * @param committer the username that made the commit (the system will use
     * the committer mapping information to find the associated Crucible user)
     * @return
     */
    protected boolean isUnderScrutiny(String committer) {

        final UserData crucibleUser = committerToCrucibleUser.get().get(committer);
        final boolean userInList = crucibleUser != null &&
                config.loadCrucibleUserNames().contains(crucibleUser.getUserName());
        final boolean userInGroups = crucibleUser != null &&
                Iterables.any(config.loadCrucibleGroups(), new Predicate<String>() {
                    public boolean apply(String group) {
                        return userManager.isUserInGroup(crucibleUser.getUserName(), group);
                    }
                });

        switch (config.loadCreateMode()) {
            case ALWAYS:
                return !(userInList || userInGroups);
            case NEVER:
                return userInList || userInGroups;
            default:
                throw new AssertionError("Unsupported create mode");
        }
    }

    /**
     * Attempts to add the change set to an existing open review by scanning
     * the commit message for review IDs in the current project. When multiple
     * IDs are found, the first non-closed review is used.
     *
     * @param repoKey
     * @param cs
     * @param project
     * @return  {@code true} if the change set was successfully added to an
     * existing review, {@code false} otherwise.
     */
    private boolean appendToReview(final String repoKey, final ChangesetDataFE cs, final ProjectData project) {

        final ReviewData review = getFirstOpenReview(Utils.extractReviewIds(cs.getComment(), project.getKey()));

        if (review != null) {

            // impersonate the review's moderator (or creator if there is no moderator set):
            return impersonator.doAsUser(null,
                    Utils.defaultIfNull(review.getModerator(), review.getCreator()).getUserName(),
                    new Operation<Boolean, RuntimeException>() {

                public Boolean perform() throws RuntimeException {

                    try {
                        reviewService.addChangesetsToReview(review.getPermaId(), repoKey, Collections.singletonList(new ChangesetData(cs.getCsid())));
                        addComment(review, String.format(
                                "The Automatic Review Creator Plugin added changeset {cs:id=%s|rep=%s} to this review.",
                                cs.getCsid(), repoKey));
                        return true;
                    } catch (Exception e) {
                        logger.warn(String.format("Error appending changeset %s to review %s: %s",
                                cs.getCsid(), review.getPermaId().getId(), e.getMessage()), e);
                    }
                    return false;
                }
            });
        }
        return false;
    }

    /**
     * Note that this check is broken in Crucible older than 2.2. In 2.1, the
     * review state gets stale and won't always show the current state.
     * See: http://jira.atlassian.com/browse/CRUC-2912
     *
     * @param reviewIds
     * @return
     */
    private ReviewData getFirstOpenReview(Iterable<String> reviewIds) {

        final Collection<ReviewData.State> acceptableStates = ImmutableSet.of(
                ReviewData.State.Draft,
                ReviewData.State.Approval,
                ReviewData.State.Review);

        for (String reviewId : reviewIds) {
            try {
                final ReviewData review = reviewService.getReview(new PermId<ReviewData>(reviewId), false);
                if (acceptableStates.contains(review.getState())) {
                    return review;
                }
            } catch (NotFoundException nfe) {
                /* Exceptions for flow control is bad practice, but the API
                 * has no exists() method.
                 */
            }
        }
        return null;
    }

    private void createReview(final String repoKey, final ChangesetDataFE cs,
                              final ProjectData project)
            throws ServerException {

        final ReviewData template = buildReviewTemplate(cs, project);

        // switch to user moderator:
        impersonator.doAsUser(null, project.getDefaultModerator(), new Operation<Void, ServerException>() {
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
                addComment(review, "This review was created by the Automatic Review Creator Plugin.");

                logger.info(String.format("Auto-created review %s for " +
                        "commit %s:%s with moderator %s.",
                        review.getPermaId(), repoKey,
                        cs.getCsid(), review.getModerator().getUserName()));
                return null;
            }
        });
    }

    /**
     * Must be called within the context of a user.
     *
     * @param review
     * @param message
     */
    private void addComment(final ReviewData review, final String message) {

        final GeneralCommentData comment = new GeneralCommentData();
        comment.setCreateDate(new Date());
        comment.setDraft(false);
        comment.setDeleted(false);
        comment.setMessage(message);

        try {
            reviewService.addGeneralComment(review.getPermaId(), comment);
        } catch (Exception e) {
            logger.error(String.format("Unable to add a general comment to review %s: %s",
                    review.getPermaId().getId(), e.getMessage()), e);
        }
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

        final UserData creator = committerToCrucibleUser.get().get(cs.getAuthor()) == null ?
                userService.getUser(project.getDefaultModerator()) :
                committerToCrucibleUser.get().get(cs.getAuthor());
        final Date dueDate = project.getDefaultDuration() == null ? null :
                DateHelper.addWorkingDays(new Date(), project.getDefaultDuration());

        return new ReviewDataBuilder()
                .setProjectKey(project.getKey())
                .setName(Utils.firstNonEmptyLine(cs.getComment()))
                .setDescription(StringUtils.defaultIfEmpty(project.getDefaultObjectives(), cs.getComment()))
                .setAuthor(creator)
                .setModerator(userService.getUser(project.getDefaultModerator()))
                .setCreator(creator)
                .setAllowReviewersToJoin(project.isAllowReviewersToJoin())
                .setDueDate(dueDate)
                .build();
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
