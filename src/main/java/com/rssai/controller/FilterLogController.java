package com.rssai.controller;

import com.rssai.mapper.FilterLogMapper;
import com.rssai.mapper.RssSourceMapper;
import com.rssai.mapper.UserMapper;
import com.rssai.model.FilterLog;
import com.rssai.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Controller
public class FilterLogController {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private FilterLogMapper filterLogMapper;
    @Autowired
    private RssSourceMapper rssSourceMapper;

    @Value("${email.enable:false}")
    private boolean emailEnabled;

    private static final int DEFAULT_PAGE_SIZE = 20;

    @GetMapping("/filter-logs")
public String filterLogs(Authentication auth, Model model,
                            @RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "20") int pageSize,
                            @RequestParam(required = false) String filtered,
                            @RequestParam(required = false) String source) {
        // Guard clause: validate page parameter - Law of Early Exit
        if (page < 1) {
            throw new IllegalArgumentException("page must be >= 1");
        }

        User user = userMapper.findByUsername(auth.getName());
        // Guard clause: validate user exists - Law of Fail Fast
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found");
        }
        model.addAttribute("user", user);

        List<FilterLog> logs;
        int totalLogs;

        if (filtered != null && !filtered.isEmpty()) {
            Boolean isFiltered = Boolean.parseBoolean(filtered);
            if (source != null && !source.isEmpty()) {
                logs = filterLogMapper.findByUserIdAndFilteredAndSource(user.getId(), isFiltered, source, page, pageSize);
                totalLogs = filterLogMapper.countByUserIdAndFilteredAndSource(user.getId(), isFiltered, source);
            } else {
                logs = filterLogMapper.findByUserIdAndFilteredWithPagination(user.getId(), isFiltered, page, pageSize);
                totalLogs = filterLogMapper.countByUserIdAndFiltered(user.getId(), isFiltered);
            }
        } else {
            if (source != null && !source.isEmpty()) {
                logs = filterLogMapper.findByUserIdAndSource(user.getId(), source, page, pageSize);
                totalLogs = filterLogMapper.countByUserIdAndSource(user.getId(), source);
            } else {
                logs = filterLogMapper.findByUserIdWithPagination(user.getId(), page, pageSize);
                totalLogs = filterLogMapper.countByUserId(user.getId());
            }
        }

        int totalPages = (int) Math.ceil((double) totalLogs / pageSize);

        Set<String> sources = filterLogMapper.findDistinctSourcesByUserId(user.getId());
        sources = new TreeSet<>(sources);

        model.addAttribute("logs", logs);
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("totalLogs", totalLogs);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("filtered", filtered);
        model.addAttribute("currentSource", source);
        model.addAttribute("sources", sources);
        model.addAttribute("emailEnabled", emailEnabled);

        return "filter-logs";
    }

    /**
     * REST API endpoint for infinite scroll pagination.
     * Returns filter logs in JSON format.
     */
    @GetMapping("/api/filter-logs")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getFilterLogsApi(
            Authentication auth,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String filtered,
            @RequestParam(required = false) String source) {

        // Guard clause: validate page parameter - Law of Early Exit
        if (page < 1) {
            throw new IllegalArgumentException("page must be >= 1");
        }

        User user = userMapper.findByUsername(auth.getName());
        // Guard clause: validate user exists - Law of Fail Fast
        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        List<FilterLog> logs = fetchLogsForUser(user.getId(), filtered, source, page, pageSize);
        int totalLogs = countLogsForUser(user.getId(), filtered, source);
        int totalPages = calculateTotalPages(totalLogs, pageSize);
        boolean hasMore = page < totalPages;

        Map<String, Object> response = buildPaginatedResponse(logs, page, pageSize, totalPages, totalLogs, hasMore);
        return ResponseEntity.ok(response);
    }

    /**
     * Fetches logs based on filter criteria - Law of Atomic Predictability.
     * Pure function: same inputs always produce same outputs.
     * Refactored to use unified query strategy - eliminates duplicate condition logic.
     */
    private List<FilterLog> fetchLogsForUser(Long userId, String filtered, String source, int page, int pageSize) {
        Boolean isFiltered = hasFilter(filtered) ? Boolean.parseBoolean(filtered) : null;
        QueryStrategy strategy = determineQueryStrategy(filtered, source);

        switch (strategy) {
            case FILTERED_AND_SOURCE:
                return filterLogMapper.findByUserIdAndFilteredAndSource(userId, isFiltered, source, page, pageSize);
            case FILTERED_ONLY:
                return filterLogMapper.findByUserIdAndFilteredWithPagination(userId, isFiltered, page, pageSize);
            case SOURCE_ONLY:
                return filterLogMapper.findByUserIdAndSource(userId, source, page, pageSize);
            case NONE:
            default:
                return filterLogMapper.findByUserIdWithPagination(userId, page, pageSize);
        }
    }

    /**
     * Counts logs based on filter criteria - Law of Atomic Predictability.
     * Refactored to use unified query strategy - eliminates duplicate condition logic.
     */
    private int countLogsForUser(Long userId, String filtered, String source) {
        Boolean isFiltered = hasFilter(filtered) ? Boolean.parseBoolean(filtered) : null;
        QueryStrategy strategy = determineQueryStrategy(filtered, source);

        switch (strategy) {
            case FILTERED_AND_SOURCE:
                return filterLogMapper.countByUserIdAndFilteredAndSource(userId, isFiltered, source);
            case FILTERED_ONLY:
                return filterLogMapper.countByUserIdAndFiltered(userId, isFiltered);
            case SOURCE_ONLY:
                return filterLogMapper.countByUserIdAndSource(userId, source);
            case NONE:
            default:
                return filterLogMapper.countByUserId(userId);
        }
    }

    /**
     * Guard clause for empty/null filter values - Law of Early Exit.
     */
    private boolean hasFilter(String value) {
        return value != null && !value.isEmpty();
    }

    /**
     * Query strategy selector - consolidates filter combination logic.
     * Returns an enum indicating which query pattern to use.
     */
    private enum QueryStrategy {
        FILTERED_AND_SOURCE,
        FILTERED_ONLY,
        SOURCE_ONLY,
        NONE
    }

    /**
     * Determines query strategy based on filter parameters - Law of Atomic Predictability.
     */
    private QueryStrategy determineQueryStrategy(String filtered, String source) {
        boolean hasFiltered = hasFilter(filtered);
        boolean hasSource = hasFilter(source);

        if (hasFiltered && hasSource) {
            return QueryStrategy.FILTERED_AND_SOURCE;
        } else if (hasFiltered) {
            return QueryStrategy.FILTERED_ONLY;
        } else if (hasSource) {
            return QueryStrategy.SOURCE_ONLY;
        } else {
            return QueryStrategy.NONE;
        }
    }

    /**
     * Calculates total pages - Law of Atomic Predictability.
     * Fail Fast: throws on invalid page size.
     */
    private int calculateTotalPages(int totalLogs, int pageSize) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("Page size must be positive, got: " + pageSize);
        }
        return (int) Math.ceil((double) totalLogs / pageSize);
    }

    /**
     * Builds standardized paginated response - Law of Intentional Naming.
     */
    private Map<String, Object> buildPaginatedResponse(
            List<FilterLog> logs, int page, int pageSize, int totalPages, int totalLogs, boolean hasMore) {

        Map<String, Object> response = new HashMap<>();
        response.put("data", logs);
        response.put("page", page);
        response.put("pageSize", pageSize);
        response.put("totalPages", totalPages);
        response.put("totalLogs", totalLogs);
        response.put("hasMore", hasMore);

        return response;
    }
}
