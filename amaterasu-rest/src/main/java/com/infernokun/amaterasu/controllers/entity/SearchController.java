package com.infernokun.amaterasu.controllers.entity;

import com.infernokun.amaterasu.controllers.BaseController;
import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.search.SearchResults;
import com.infernokun.amaterasu.services.entity.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The top bar search, scoped to what the caller's role may open. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/search")
public class SearchController extends BaseController {
    private final SearchService searchService;

    @GetMapping
    public ResponseEntity<ApiResponse<SearchResults>> search(@RequestParam(defaultValue = "") String q,
                                                             @RequestParam(defaultValue = "5") int limit) {
        return createSuccessResponse(searchService.search(q, limit), "Search results.");
    }
}
