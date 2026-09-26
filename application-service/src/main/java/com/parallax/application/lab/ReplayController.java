package com.parallax.application.lab;

import com.parallax.application.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Strategy Lab replay endpoints (SPEC §15): start a replay and read its report, flips and detail. */
@RestController
public class ReplayController {

    private final ReplayService replayService;
    private final ReplayQueryService queryService;
    private final CurrentUser currentUser;

    public ReplayController(ReplayService replayService, ReplayQueryService queryService, CurrentUser currentUser) {
        this.replayService = replayService;
        this.queryService = queryService;
        this.currentUser = currentUser;
    }

    /** STRATEGIST starts a replay; ASSISTANT only resolves an existing one. Both return 202 {jobId}. */
    @PostMapping("/api/v1/lab/versions/{v}/replays")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> replay(@PathVariable String v,
                                      @RequestBody(required = false) ReplayRequest request) {
        String jobId = "ASSISTANT".equals(currentUser.role())
                ? replayService.existingReplayJob(v)
                : replayService.startReplay(v, request, currentUser.username());
        return Map.of("jobId", jobId);
    }

    @GetMapping("/api/v1/lab/replays/{jobId}")
    public ReplayViews.JobView job(@PathVariable String jobId) {
        return queryService.job(jobId);
    }

    @GetMapping("/api/v1/lab/replays/{jobId}/flips")
    public ReplayViews.FlipPage flips(@PathVariable String jobId,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "50") int size) {
        return queryService.flips(jobId, page, size);
    }

    @GetMapping("/api/v1/lab/replays/{jobId}/flips/{seq}")
    public ReplayViews.FlipDetail flipDetail(@PathVariable String jobId, @PathVariable long seq) {
        return queryService.flipDetail(jobId, seq);
    }
}
