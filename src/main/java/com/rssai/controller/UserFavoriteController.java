package com.rssai.controller;

import com.rssai.mapper.UserMapper;
import com.rssai.model.User;
import com.rssai.model.UserFavorite;
import com.rssai.service.UserFavoriteService;
import com.rssai.util.HtmlUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class UserFavoriteController {
    private final UserFavoriteService userFavoriteService;
    private final UserMapper userMapper;

    public UserFavoriteController(UserFavoriteService userFavoriteService, UserMapper userMapper) {
        this.userFavoriteService = userFavoriteService;
        this.userMapper = userMapper;
    }

    /**
     * 收藏文章页面
     */
    @GetMapping("/favorites")
    public String favoritesPage(Authentication auth, Model model,
                                @RequestParam(defaultValue = "1") int page,
                                @RequestParam(defaultValue = "20") int pageSize) {
        User user = userMapper.findByUsername(auth.getName());
        model.addAttribute("user", user);

        int totalItems = userFavoriteService.getUserFavoriteCount(user.getId());
        int totalPages = (int) Math.ceil((double) totalItems / pageSize);

        List<UserFavorite> favorites = userFavoriteService.getUserFavorites(user.getId(), page, pageSize);
        for (UserFavorite favorite : favorites) {
            if (favorite.getRssItem() != null) {
                String cleanDesc = HtmlUtils.stripHtml(favorite.getRssItem().getDescription());
                favorite.getRssItem().setDescription(HtmlUtils.truncate(cleanDesc, 200));
                // 提取第一张图片
                String imageUrl = HtmlUtils.extractFirstImage(favorite.getRssItem().getContent());
                if (imageUrl == null) {
                    imageUrl = HtmlUtils.extractFirstImage(favorite.getRssItem().getDescription());
                }
                favorite.getRssItem().setImageUrl(imageUrl);
            }
        }

        model.addAttribute("favorites", favorites);
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("totalItems", totalItems);
        model.addAttribute("totalPages", totalPages);
        return "favorites";
    }

    /**
     * 加载更多收藏（AJAX）
     */
    @GetMapping("/favorites/items")
    @ResponseBody
    public Map<String, Object> loadMoreFavorites(Authentication auth,
                                                 @RequestParam int page,
                                                 @RequestParam(defaultValue = "20") int pageSize) {
        User user = userMapper.findByUsername(auth.getName());
        int totalItems = userFavoriteService.getUserFavoriteCount(user.getId());
        int totalPages = (int) Math.ceil((double) totalItems / pageSize);

        List<UserFavorite> favorites = userFavoriteService.getUserFavorites(user.getId(), page, pageSize);
        for (UserFavorite favorite : favorites) {
            if (favorite.getRssItem() != null) {
                String cleanDesc = HtmlUtils.stripHtml(favorite.getRssItem().getDescription());
                favorite.getRssItem().setDescription(HtmlUtils.truncate(cleanDesc, 200));
                // 提取第一张图片
                String imageUrl = HtmlUtils.extractFirstImage(favorite.getRssItem().getContent());
                if (imageUrl == null) {
                    imageUrl = HtmlUtils.extractFirstImage(favorite.getRssItem().getDescription());
                }
                favorite.getRssItem().setImageUrl(imageUrl);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("favorites", favorites);
        result.put("hasMore", page < totalPages);
        result.put("currentPage", page);
        result.put("totalPages", totalPages);
        return result;
    }

    /**
     * 切换收藏状态
     */
    @PostMapping("/favorites/toggle")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleFavorite(Authentication auth, @RequestParam Long rssItemId) {
        User user = userMapper.findByUsername(auth.getName());
        boolean isFavorite = userFavoriteService.toggleFavorite(user.getId(), rssItemId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("isFavorite", isFavorite);
        return ResponseEntity.ok(result);
    }

    /**
     * 检查是否已收藏
     */
    @GetMapping("/favorites/check")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkFavorite(Authentication auth, @RequestParam Long rssItemId) {
        User user = userMapper.findByUsername(auth.getName());
        boolean isFavorite = userFavoriteService.isFavorite(user.getId(), rssItemId);

        Map<String, Object> result = new HashMap<>();
        result.put("isFavorite", isFavorite);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取当前用户的所有收藏ID列表
     */
    @GetMapping("/favorites/ids")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getFavoriteIds(Authentication auth) {
        User user = userMapper.findByUsername(auth.getName());
        List<Long> favoriteIds = userFavoriteService.getUserFavoriteItemIds(user.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("favoriteIds", favoriteIds);
        return ResponseEntity.ok(result);
    }
}
