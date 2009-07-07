package com.atlassian.crucible.reviewcreator;

import com.atlassian.crucible.spi.FisheyePluginUtilities;
import com.atlassian.crucible.spi.PluginId;
import com.atlassian.crucible.spi.PluginIdAware;
import com.atlassian.crucible.spi.data.ChangesetData;
import com.atlassian.crucible.spi.data.ReviewData;
import com.atlassian.crucible.spi.data.UserData;
import com.atlassian.crucible.spi.services.ImpersonationService;
import com.atlassian.crucible.spi.services.Operation;
import com.atlassian.crucible.spi.services.ReviewService;
import com.atlassian.crucible.spi.services.UserService;
import com.atlassian.event.Event;
import com.atlassian.event.EventListener;
import com.atlassian.fisheye.event.CommitEvent;
import com.atlassian.fisheye.spi.data.ChangesetDataFE;
import com.atlassian.fisheye.spi.services.CommitterMappingService;
import com.atlassian.fisheye.spi.services.RevisionDataService;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;

/**
 * Subscribes to FishEye commit events and automatically creates a review for
 * each commit.
 *
 * TODO:
 * - add suggested reviewers
 * - make users configurable
 * - watch only configurable repo paths
 * - allow per-user configuration
 *
 * @author  Erik van Zijst
 */
public class CommitEventListener implements EventListener, PluginIdAware {

    private final Logger logger = Logger.getLogger("com.atlassian.crucible.reviewcreator");
    private final UserService userService;
    private final ReviewService reviewService;
    private final RevisionDataService revisionService;
    private final CommitterMappingService mappingService;
    private final FisheyePluginUtilities fisheyePluginUtilities;
    private final ImpersonationService impersonationService;
    private PluginId pluginId;

    // TODO: make these configurable
    private final String projectKey = "TEST";
    private final boolean allowJoin = true;
    private final String defaultCreator = "evzijst";
    private final String defaultModerator = "evzijst";
    private final String defaultAuthor = "evzijst";

    @Autowired
    public CommitEventListener(ReviewService reviewService,
        RevisionDataService revisionService, UserService userService,
        FisheyePluginUtilities fisheyePluginUtilities, ImpersonationService impersonationService,
        CommitterMappingService mappingService) {

        this.userService = userService;
        this.reviewService = reviewService;
        this.revisionService = revisionService;
        this.mappingService = mappingService;
        this.fisheyePluginUtilities = fisheyePluginUtilities;
        this.impersonationService = impersonationService;

        logger.info("Review Creator Plugin started.");
    }

    public void handleEvent(Event event) {

        assert event instanceof CommitEvent;
        final CommitEvent ce = (CommitEvent) event;

        final ChangesetDataFE fisheyeChangeSet = revisionService.getChangeset(ce.getRepositoryName(), ce.getChangeSetId());
        if (fisheyeChangeSet != null) {

            final ReviewData reviewTemplate = prepareReview(ce.getRepositoryName(), fisheyeChangeSet);

            if (impersonationService.canImpersonate(pluginId, reviewTemplate.getCreator().getUserName())) {
                ReviewData review = impersonationService.doAsUser(pluginId, reviewTemplate.getCreator().getUserName(), new Operation<ReviewData, RuntimeException>() {
                    public ReviewData perform() throws RuntimeException {
                        final ReviewData review = reviewService.createReviewFromChangeSets(reviewTemplate, ce.getRepositoryName(),
                            Collections.singletonList(new ChangesetData(ce.getChangeSetId())));

                        // promote from Draft to Review:
                        return reviewService.changeState(review.getPermaId(), "action:approveReview");
                    }
                });

                logger.info(String.format("Automatic review created: %s for commit %s:%s : %s",
                    review.getPermaId().getId(), ce.getRepositoryName(), ce.getChangeSetId(),
                    fisheyeChangeSet.getComment()));

            } else {
                logger.error(String.format("Failed to automatically create a review for commit: %s:%s : " +
                        "Crucible won't let me impersonate user %s as review creator.",
                    ce.getRepositoryName(), ce.getChangeSetId(), reviewTemplate.getCreator().getUserName()));
            }

        } else {
            logger.error(String.format("Failed to automatically create a review for commit: %s:%s",
                ce.getRepositoryName(), ce.getChangeSetId()));
        }
    }

    private ReviewData prepareReview(String repository, ChangesetDataFE fisheyeChangeSet) {

        UserData author, moderator, creator;

        UserData committer = mappingService.getUserForCommitter(repository, fisheyeChangeSet.getAuthor());
        if (committer == null) {
            author = userService.getUser(defaultAuthor);
            moderator = userService.getUser(defaultModerator);
            creator = userService.getUser(defaultCreator);
        } else {
            logger.info("");
            author = moderator = creator = committer;
        }

        return new ReviewData(
            projectKey,
            fisheyeChangeSet.getComment(),
            fisheyeChangeSet.getComment() + "\n\nReview created automatically by review-creator-plugin.",
            author,
            moderator,
            creator,
            null,   // permaID
            null,   // summary
            null,   // state
            allowJoin,
            null,   // parent
            null,   // createDate
            null,   // closeDate
            null,   // dueDate -- TODO!
            0       // metricsVersion
            );
    }

    public Class[] getHandledEventClasses() {
        return new Class[] {CommitEvent.class};
    }

    public void setPluginId(PluginId pluginId) {
        this.pluginId = pluginId;
    }
}
