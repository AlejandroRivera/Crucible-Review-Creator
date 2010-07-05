package com.atlassian.example.reviewcreator;

import com.atlassian.crucible.spi.PermId;
import com.atlassian.crucible.spi.data.ReviewData;
import com.atlassian.crucible.spi.data.UserData;

import java.util.Date;

/**
 *
 * @since v1.4.2
 */
public class ReviewDataBuilder
{
    private final ReviewData reviewData = new ReviewData();

    public ReviewData build() {
        return new ReviewData(reviewData);
    }

    public ReviewDataBuilder setAllowReviewersToJoin(boolean allowReviewersToJoin)
    {
        reviewData.setAllowReviewersToJoin(allowReviewersToJoin);
        return this;
    }

    public ReviewDataBuilder setAuthor(UserData author)
    {
        reviewData.setAuthor(author);
        return this;
    }

    public ReviewDataBuilder setCloseDate(Date closeDate)
    {
        reviewData.setCloseDate(closeDate);
        return this;
    }

    public ReviewDataBuilder setCreateDate(Date createDate)
    {
        reviewData.setCreateDate(createDate);
        return this;
    }

    public ReviewDataBuilder setCreator(UserData creator)
    {
        reviewData.setCreator(creator);
        return this;
    }

    public ReviewDataBuilder setDescription(String description)
    {
        reviewData.setDescription(description);
        return this;
    }

    public ReviewDataBuilder setDueDate(Date dueDate)
    {
        reviewData.setDueDate(dueDate);
        return this;
    }

    public ReviewDataBuilder setJiraIssueKey(String jiraIssueKey)
    {
        reviewData.setJiraIssueKey(jiraIssueKey);
        return this;
    }

    public ReviewDataBuilder setMetricsVersion(int metricsVersion)
    {
        reviewData.setMetricsVersion(metricsVersion);
        return this;
    }

    public ReviewDataBuilder setModerator(UserData moderator)
    {
        reviewData.setModerator(moderator);
        return this;
    }

    public ReviewDataBuilder setName(String name)
    {
        reviewData.setName(name);
        return this;
    }

    public ReviewDataBuilder setParentReview(PermId<ReviewData> parentReview)
    {
        reviewData.setParentReview(parentReview);
        return this;
    }

    public ReviewDataBuilder setPermaId(String permaId)
    {
        reviewData.setPermaIdAsString(permaId);
        return this;
    }

    public ReviewDataBuilder setProjectKey(String projectKey)
    {
        reviewData.setProjectKey(projectKey);
        return this;
    }

    public ReviewDataBuilder setState(ReviewData.State state)
    {
        reviewData.setState(state);
        return this;
    }

    public ReviewDataBuilder setSummary(String summary)
    {
        reviewData.setSummary(summary);
        return this;
    }
}
