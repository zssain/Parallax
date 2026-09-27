package com.parallax.account.collections;

import com.parallax.account.collections.CollectionsViews.CollectionItem;
import com.parallax.account.collections.CollectionsViews.CollectionsSummary;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

/** The collections summary, work queue and actions (SPEC §15). */
@RestController
@RequestMapping("/api/v1/collections")
public class CollectionsController {

    private final CollectionsService collectionsService;

    public CollectionsController(CollectionsService collectionsService) {
        this.collectionsService = collectionsService;
    }

    @GetMapping("/summary")
    public CollectionsSummary summary() {
        return collectionsService.summary();
    }

    @GetMapping
    public List<CollectionItem> queue(@RequestParam(required = false) String bucket) {
        return collectionsService.queue(bucket);
    }

    @PostMapping("/{accountId}/actions")
    @ResponseStatus(HttpStatus.CREATED)
    public void recordAction(@PathVariable String accountId, @Valid @RequestBody CollectionActionRequest body,
                             Principal principal) {
        collectionsService.recordAction(accountId, body.type(), body.note(), principal.getName());
    }
}
