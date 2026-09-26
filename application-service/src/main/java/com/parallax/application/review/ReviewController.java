package com.parallax.application.review;

import com.parallax.application.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Review queue, overrides and override statistics for the Review-queue screen (SPEC §15). */
@RestController
public class ReviewController {

    private final ReviewService reviewService;
    private final CurrentUser currentUser;

    public ReviewController(ReviewService reviewService, CurrentUser currentUser) {
        this.reviewService = reviewService;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/v1/reviews/queue")
    public List<ReviewViews.QueueItem> queue() {
        return reviewService.queue(currentUser.role());
    }

    @PostMapping("/api/v1/reviews/{id}")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewViews.RecordResult record(@PathVariable String id, @RequestBody ReviewRequest request) {
        return reviewService.record(id, request);
    }

    @GetMapping("/api/v1/reviews/override-stats")
    public ReviewViews.OverrideStats overrideStats() {
        return reviewService.overrideStats();
    }
}
