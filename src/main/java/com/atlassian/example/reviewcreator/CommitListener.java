package com.atlassian.example.reviewcreator;

import com.atlassian.crucible.spi.data.*;
import com.atlassian.crucible.spi.services.*;
import com.atlassian.event.Event;
import com.atlassian.event.EventListener;
import com.atlassian.fisheye.event.CommitEvent;
import com.atlassian.fisheye.spi.data.ChangesetDataFE;
import com.atlassian.fisheye.spi.services.RevisionDataService;
import com.atlassian.sal.api.user.UserManager;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private final Logger logger = LoggerFactory.getLogger(CommitListener.class);

    private final RevisionDataService revisionService;          // provided by FishEye
    private final ReviewService reviewService;                  // provided by Crucible
    private final ProjectService projectService;                // provided by Crucible
    private final UserService userService;                      // provided by Crucible
    private final UserManager userManager;                      // provided by SAL
    private final ImpersonationService impersonator;            // provided by Crucible
    private final ConfigurationManager config;                  // provided by our plugin
    private final SearchService searchService;                  // provided by our plugin

    private static final ThreadLocal<Map<String, UserData>> committerToCrucibleUser = new ThreadLocal<Map<String,UserData>>();

    public CommitListener(ConfigurationManager config,
            ReviewService reviewService,
            ProjectService projectService,
            RevisionDataService revisionService,
            UserService userService,
            UserManager userManager,
            ImpersonationService impersonator,
            SearchService searchService) {

        this.reviewService = reviewService;
        this.revisionService = revisionService;
        this.projectService = projectService;
        this.userService = userService;
        this.userManager = userManager;
        this.impersonator = impersonator;
        this.config = config;
        this.searchService = searchService;
    }

    public Class[] getHandledEventClasses() {
        return new Class[] {CommitEvent.class};
    }

    public void handleEvent(Event event) {

        final CommitEvent commit = (CommitEvent) event;

        if (!isPluginEnabled()) {
            return;
        }

        Operation<Void, ServerException> operation = new Operation<Void, ServerException>() {
            public Void perform() throws ServerException {
                final ChangesetDataFE cs = revisionService.getChangeset(commit.getRepositoryName(), commit.getChangeSetId());
                final ProjectData project = getEnabledProjectForRepository(commit.getRepositoryName());
                
                
                if (project == null) {
                    logger.error(String.format("Unable to auto-create review for changeset %s. No projects found that bind to repository %s.",
                            commit.getChangeSetId(), commit.getRepositoryName()));
                    return null;
                }
                final String branchForProject = getBranchForProject(commit.getRepositoryName());
                if(branchForProject != "" && branchForProject.equals(cs.getBranch()) == false)
                {
                    logger.info(String.format("Commit skiped because of branch is %s but branch filter is %s",
                    		cs.getBranch(), branchForProject));
                	return null;
                }

                committerToCrucibleUser.set(loadCommitterMappings(project.getDefaultRepositoryName()));
                if (project.getDefaultModerator() == null) {
                    logger.error(String.format("Unable to auto-create review for changeset %s. No default moderator configured for project %s.",
                            commit.getChangeSetId(), project.getKey()));
                    return null;
                }

                if (!isUnderScrutiny(cs.getAuthor())) {
                    logger.info(String.format("Not creating a review for changeset %s because author is not under review",
                            commit.getChangeSetId()));
                    return null;
                }

                if (!config.loadIterative() || !appendToReview(commit.getRepositoryName(), cs, project)) {
                    // create a new review:
                    createReview(commit.getRepositoryName(), cs, project);
                }
                return null;
            }
        };

        try {
            // switch to admin user so we can access all projects and API services:
            impersonator.doAsUser(null, config.loadRunAsUser(), operation);
        } catch (Exception e) {
            logger.error(String.format("Unable to auto-create review for changeset %s: %s.",
                    commit.getChangeSetId(), e.getMessage()), e);
        }
    }

    /**
     * Determines whether or not the user that made the commit is exempt from
     * automatic reviews, or whether the user is on the list of always having
     * its commits automatically reviewed.
     *
     * @param committer the username that made the commit (the system will use
     * the committer mapping information to find the associated Crucible user)
     */
    protected boolean isUnderScrutiny(String committer) {

        final UserData crucibleUser = committerToCrucibleUser.get().get(committer);
        if (crucibleUser == null) {
            logger.warn("Couldn't determine if the committer is under scrutiny: " + committer);
            return true;
        }

        final boolean userInList = crucibleUser != null && config.loadCrucibleUserNames().contains(crucibleUser.getUserName());
        final boolean userInGroups = crucibleUser != null && Iterables.any(config.loadCrucibleGroups(), new Predicate<String>() {
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
     * @return  {@code true} if the change set was successfully added to an
     * existing review, {@code false} otherwise.
     */
    private boolean appendToReview(final String repoKey, final ChangesetDataFE cs, final ProjectData project) {

        Set<String> branches = cs.getBranches();
        if (branches.isEmpty() || branches.contains("master")  || branches.contains("master_raptor2")) {
            logger.info("Not appending to review because commit branches are empty or `master` is found");
            return false;
        }

        String jiraKey = createJiraKey(cs);
        List<ReviewData> reviewDatas;
        try {
            reviewDatas = searchService.searchForReviewsByJiraKey(jiraKey);
        } catch (Exception e) {
            logger.warn("Couldn't perform search for existing reviews by JIRA Key: " + jiraKey, e);
            return false;
        }

        Predicate<ReviewData> predicate = new Predicate<ReviewData>() {
            public boolean apply(ReviewData input) {
                return input.getState() == ReviewData.State.Draft
                        || input.getState() == ReviewData.State.Approval
                        || input.getState() == ReviewData.State.Review;
            }
        };

        final ReviewData review;
        try {
            review = Iterables.find(reviewDatas, predicate);
        }
        catch (NoSuchElementException e){
            return false;
        }

        Operation<Boolean, RuntimeException> operation = new Operation<Boolean, RuntimeException>() {
            public Boolean perform() throws RuntimeException {
                try {
                    reviewService.addChangesetsToReview(review.getPermaId(), repoKey,
                            Collections.singletonList(new ChangesetData(cs.getCsid())));
                    addComment(review, cs.getComment());
                    return true;
                } catch (Exception e) {
                    logger.warn(String.format("Error appending changeset %s to review %s: %s",
                            cs.getCsid(), review.getPermaId().getId(), e.getMessage()), e);
                    return false;
                }
            }
        };
        String username = getCommitterUser(cs, project.getDefaultModerator()).getUserName();
        try {
            return impersonator.doAsUser(null, username, operation);
        } catch (Exception e){
            logger.warn(String.format("Couldn't append changeset %s to existing review %s",
                    cs.getCsid(), review.getPermaId().getId()), e);
            return false;
        }
    }

    private void createReview(final String repoKey, final ChangesetDataFE cs, final ProjectData project) {

        final ReviewData template = buildReviewTemplate(cs, project);
        if (template == null) return;

        if (cs.getBranches().isEmpty() || cs.getBranches().contains("master") || cs.getBranches().contains("master_raptor2")){
            logger.info("Skipping review creation since it's not a feature branch.");
            return;
        }

        Operation<Void, ServerException> operation = new Operation<Void, ServerException>() {
            public Void perform() throws ServerException {

                // create a new review:
                final ReviewData review = reviewService.createReviewFromChangeSets(
                        template,
                        repoKey,
                        Collections.singletonList(new ChangesetData(cs.getCsid())));

                // add the project's default reviewers:
                addReviewers(review, project);
                addComment(review, cs.getComment());

                // start the review, so everyone is notified:
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    logger.warn(e.getLocalizedMessage(), e);
                }
                reviewService.changeState(review.getPermaId(), ReviewService.Action.Approve);

                logger.info(String.format("Auto-created review %s for " +
                                "commit %s:%s with moderator %s.",
                        review.getPermaId(), repoKey,
                        cs.getCsid(), review.getModerator().getUserName()));
                return null;
            }
        };
        // switch to user moderator:
        String userName = getCommitterUser(cs, project.getDefaultModerator()).getUserName();
        try {
            impersonator.doAsUser(null, userName, operation);
        } catch (ServerException e) {
            logger.error("Couldn't create review: " + e.getLocalizedMessage(), e);
        }
    }


    private UserData getCommitterUser(ChangesetDataFE cs, String moderatorUsername) {
        String author = cs.getAuthor();
        UserData userData = committerToCrucibleUser.get().get(author);
        if (userData != null){
            return userData;
        }
        else {
            try {
                logger.warn("Couldn't find user info for: " + author + " in " + committerToCrucibleUser.get());
                return userService.getUser(moderatorUsername);
            } catch (ServerException e) {
                logger.error("Couldn't retrieve moderator from UserService: " + moderatorUsername);
                return null;
            }
        }
    }

    private String createJiraKey(ChangesetDataFE cs) {
        String jiraKey = cs.getBranches().iterator().next();
        jiraKey = jiraKey.replaceAll("\\W", "");
        jiraKey = jiraKey + "-1";
        return jiraKey;
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
        final List<String> reviewers = Lists.newArrayList(project.getDefaultReviewerUsers());

        if (reviewers != null && !reviewers.isEmpty()) {
            if (review.getAuthor() != null && reviewers.contains(review.getAuthor().getUserName()))
                reviewers.remove(review.getAuthor().getUserName());
            if (review.getModerator() != null && reviewers.contains(review.getModerator().getUserName()))
                reviewers.remove(review.getModerator().getUserName());

            String[] reviewersArray = reviewers.toArray(new String[reviewers.size()]);
            try {
                reviewService.addReviewers(review.getPermaId(), reviewersArray);
            }
            catch (Exception e) {
                logger.warn("Couldn't add default reviewers: " + e.getLocalizedMessage(), e);
            }
        }
    }

    /**
     * <p>
     * This method must be invoked with admin permissions.
     * </p>
     */
    private ReviewData buildReviewTemplate(ChangesetDataFE cs, ProjectData project){

        final UserData creator = getCommitterUser(cs, project.getDefaultModerator());
        final Date dueDate = project.getDefaultDuration() == null ? null :
                DateHelper.addWorkingDays(new Date(), project.getDefaultDuration());
        try {
            ReviewData.Builder builder = new ReviewData.Builder();
            builder.setProjectKey(project.getKey())
                    .setName(cs.getBranches().iterator().next())
                    .setDescription(StringUtils.defaultIfEmpty(project.getDefaultObjectives(), ""))
                    .setAuthor(creator)
                    .setModerator(userService.getUser(project.getDefaultModerator()))
                    .setCreator(userService.getUser(config.loadRunAsUser()))
                    .setState(ReviewData.State.Draft)
                    .setAllowReviewersToJoin(project.isAllowReviewersToJoin())
                    .setJiraIssueKey(createJiraKey(cs))
                    .setDueDate(dueDate);

            try {
                return builder.build();
            } catch (Exception e){
                logger.warn("Couldn't build template for new review", e.getStackTrace());
                return null;
            }
        }
        catch (ServerException e){
            logger.error("Couldn't retrieve moderator from UserService");
            return null;
        }
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
    
    /**
     * Returns a brunch for repository
     * This method must be invoked with admin permissions.
     * </p>
     *
     * @param   repoKey
     * @return  branchNAme
     */
    private String getBranchForProject(String repoKey) {

        final List<ProjectData> projects = projectService.getAllProjects();
        final Map<String, String> branches = config.loadBranchFilters();
        for (ProjectData project : projects) {
            if (repoKey.equals(project.getDefaultRepositoryName()) &&
            		branches.containsKey(project.getKey())) {
                return branches.get(project.getKey());
            }
        }
        return "";
    }
    
    
    private boolean isPluginEnabled() {
        return !StringUtils.isEmpty(config.loadRunAsUser());
    }
}
