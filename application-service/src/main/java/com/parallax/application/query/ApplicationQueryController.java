package com.parallax.application.query;

import com.parallax.application.security.CurrentUser;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read endpoints for applications and decisions (SPEC §15). */
@RestController
public class ApplicationQueryController {

    private final DecisionQueryService queryService;
    private final CurrentUser currentUser;

    public ApplicationQueryController(DecisionQueryService queryService, CurrentUser currentUser) {
        this.queryService = queryService;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/v1/applications")
    public ApplicationViews.ListResponse list(
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "LIVE") String source,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queryService.list(currentUser.role(), outcome, q, source, page, size);
    }

    @GetMapping("/api/v1/applications/{id}")
    public ApplicationViews.Detail detail(@PathVariable String id) {
        return queryService.detail(currentUser.role(), id);
    }

    @GetMapping(value = "/api/v1/applications/{id}/adverse-action-notice", produces = MediaType.TEXT_PLAIN_VALUE)
    public String adverseActionNotice(@PathVariable String id) {
        return queryService.adverseActionNotice(currentUser.role(), id);
    }

    @GetMapping("/api/v1/decisions/{seq}/reproduce")
    public ReproduceView reproduce(@PathVariable long seq) {
        return queryService.reproduce(seq);
    }
}
