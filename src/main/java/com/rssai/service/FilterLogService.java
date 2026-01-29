package com.rssai.service;

import com.rssai.mapper.FilterLogMapper;
import com.rssai.model.FilterLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class FilterLogService {
    @Autowired
    private FilterLogMapper filterLogMapper;

    public void saveFilterLog(Long userId, Long rssItemId, String title, String link, 
                             Boolean aiFiltered, String aiReason, String aiRawResponse, String sourceName) {
        FilterLog log = new FilterLog();
        log.setUserId(userId);
        log.setRssItemId(rssItemId);
        log.setTitle(title);
        log.setLink(link);
        log.setAiFiltered(aiFiltered);
        log.setAiReason(aiReason);
        log.setAiRawResponse(aiRawResponse);
        log.setSourceName(sourceName);
        filterLogMapper.insert(log);
    }

    public List<FilterLog> getUserFilterLogs(Long userId) {
        return filterLogMapper.findByUserId(userId);
    }

    public List<FilterLog> getUserFilterLogsWithPagination(Long userId, int page, int pageSize) {
        return filterLogMapper.findByUserIdWithPagination(userId, page, pageSize);
    }

    public int getUserFilterLogCount(Long userId) {
        return filterLogMapper.countByUserId(userId);
    }

    public List<FilterLog> getUserFilterLogsByFiltered(Long userId, Boolean filtered) {
        return filterLogMapper.findByUserIdAndFiltered(userId, filtered);
    }

    public void deleteOldLogs(Long userId, int daysToKeep) {
        filterLogMapper.deleteOldLogs(userId, daysToKeep);
    }
}
