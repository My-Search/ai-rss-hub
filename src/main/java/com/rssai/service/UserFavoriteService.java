package com.rssai.service;

import com.rssai.mapper.UserFavoriteMapper;
import com.rssai.model.UserFavorite;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserFavoriteService {
    private final UserFavoriteMapper userFavoriteMapper;

    public UserFavoriteService(UserFavoriteMapper userFavoriteMapper) {
        this.userFavoriteMapper = userFavoriteMapper;
    }

    /**
     * 添加收藏
     */
    public boolean addFavorite(Long userId, Long rssItemId) {
        return userFavoriteMapper.insert(userId, rssItemId) > 0;
    }

    /**
     * 取消收藏
     */
    public boolean removeFavorite(Long userId, Long rssItemId) {
        return userFavoriteMapper.delete(userId, rssItemId) > 0;
    }

    /**
     * 检查是否已收藏
     */
    public boolean isFavorite(Long userId, Long rssItemId) {
        return userFavoriteMapper.exists(userId, rssItemId);
    }

    /**
     * 切换收藏状态
     * @return 切换后的收藏状态：true表示已收藏，false表示未收藏
     */
    public boolean toggleFavorite(Long userId, Long rssItemId) {
        if (isFavorite(userId, rssItemId)) {
            removeFavorite(userId, rssItemId);
            return false; // 取消收藏后返回false
        } else {
            addFavorite(userId, rssItemId);
            return true; // 添加收藏后返回true
        }
    }

    /**
     * 获取用户的收藏列表
     */
    public List<UserFavorite> getUserFavorites(Long userId, int page, int pageSize) {
        return userFavoriteMapper.findByUserIdWithPagination(userId, page, pageSize);
    }

    /**
     * 获取用户收藏总数
     */
    public int getUserFavoriteCount(Long userId) {
        return userFavoriteMapper.countByUserId(userId);
    }

    /**
     * 获取用户收藏的所有文章ID
     */
    public List<Long> getUserFavoriteItemIds(Long userId) {
        return userFavoriteMapper.findRssItemIdsByUserId(userId);
    }
}
