package com.parallax.application.lab;

import com.parallax.engine.model.RuleConfig;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Strategy Lab rule-version lifecycle endpoints (SPEC §15): list, live, create, edit, draft,
 * delete, propose, approve, reject, rollback and compare. Authorization is declared in SecurityConfig.
 */
@RestController
public class LabVersionController {

    private final RuleVersionService service;

    public LabVersionController(RuleVersionService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/lab/versions")
    public LabViews.VersionsResponse list() {
        return service.list();
    }

    @GetMapping("/api/v1/lab/versions/live")
    public LabViews.LiveView live() {
        return service.live();
    }

    @GetMapping("/api/v1/lab/versions/compare")
    public LabViews.CompareView compare(@RequestParam String a, @RequestParam String b) {
        return service.compare(a, b);
    }

    @PostMapping("/api/v1/lab/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public LabViews.VersionView create(@RequestBody(required = false) CreateVersionRequest request) {
        return service.create(request);
    }

    @PutMapping("/api/v1/lab/versions/{v}/config")
    public LabViews.VersionView updateConfig(@PathVariable String v, @RequestBody RuleConfig config) {
        return service.updateConfig(v, config);
    }

    @PostMapping("/api/v1/lab/versions/{v}/draft")
    public LabViews.VersionView backToDraft(@PathVariable String v) {
        return service.backToDraft(v);
    }

    @DeleteMapping("/api/v1/lab/versions/{v}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String v) {
        service.delete(v);
    }

    @PostMapping("/api/v1/lab/versions/{v}/propose")
    public LabViews.VersionView propose(@PathVariable String v) {
        return service.propose(v);
    }

    @PostMapping("/api/v1/lab/versions/{v}/approve")
    public LabViews.VersionView approve(@PathVariable String v) {
        return service.approve(v);
    }

    @PostMapping("/api/v1/lab/versions/{v}/reject")
    public LabViews.VersionView reject(@PathVariable String v, @RequestBody(required = false) RejectRequest request) {
        return service.reject(v, request);
    }

    @PostMapping("/api/v1/lab/rollback")
    public LabViews.RollbackView rollback() {
        return service.rollback();
    }
}
