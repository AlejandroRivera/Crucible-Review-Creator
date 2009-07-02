package com.atlassian.crucible.reviewcreator;

import com.atlassian.event.EventListener;
import com.atlassian.event.Event;
import com.atlassian.crucible.spi.PluginIdAware;
import com.atlassian.crucible.spi.PluginId;
import com.atlassian.crucible.spi.FisheyePluginUtilities;
import com.atlassian.crucible.spi.data.ChangesetData;
import com.atlassian.crucible.spi.data.ReviewData;
import com.atlassian.crucible.spi.data.UserData;
import com.atlassian.crucible.spi.services.ReviewService;
import com.atlassian.crucible.spi.services.ImpersonationService;
import com.atlassian.crucible.spi.services.UserService;
import com.atlassian.crucible.spi.services.Operation;
import com.atlassian.fisheye.event.CommitEvent;
import com.atlassian.fisheye.spi.services.RevisionDataService;
import com.atlassian.fisheye.spi.data.ChangesetDataFE;

import java.util.Collections;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

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
        FisheyePluginUtilities fisheyePluginUtilities, ImpersonationService impersonationService) {

        this.userService = userService;
        this.reviewService = reviewService;
        this.revisionService = revisionService;
        this.fisheyePluginUtilities = fisheyePluginUtilities;
        this.impersonationService = impersonationService;

        logger.info("Review Creator Plugin started.");
    }

    public void handleEvent(Event event) {

        assert event instanceof CommitEvent;
        final CommitEvent ce = (CommitEvent) event;

        final ChangesetDataFE fisheyeChangeSet = revisionService.getChangeset(ce.getRepositoryName(), ce.getChangeSetId());
        if (fisheyeChangeSet != null) {

            final Operation<ReviewData, RuntimeException> task = new Operation<ReviewData, RuntimeException>() {
                public ReviewData perform() throws RuntimeException {
                    final ReviewData review = reviewService.createReviewFromChangeSets(prepareReview(fisheyeChangeSet), ce.getRepositoryName(),
                        Collections.singletonList(new ChangesetData(ce.getChangeSetId())));

                    // promote from Draft to Review:
                    return reviewService.changeState(review.getPermaId(), "action:approveReview");
                }
            };

            final ReviewData review = impersonationService.canImpersonate(pluginId, defaultCreator) ?
                impersonationService.doAsUser(pluginId, defaultCreator, task) :
                impersonationService.doAsDefaultUser(pluginId, task);


            logger.info(String.format("Automatic review created: %s for commit %s:%s : %s",
                review.getPermaId().getId(), ce.getRepositoryName(), ce.getChangeSetId(),
                fisheyeChangeSet.getComment()));

        } else {
            logger.warn(String.format("Failed to automatically create a review for commit: %s:%s",
                ce.getRepositoryName(), ce.getChangeSetId()));
        }
    }

    private ReviewData prepareReview(ChangesetDataFE fisheyeChangeSet) {

        final UserData author = userService.getUser(defaultAuthor);
        final UserData moderator = userService.getUser(defaultModerator);
        final UserData creator = userService.getUser(defaultCreator);

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
