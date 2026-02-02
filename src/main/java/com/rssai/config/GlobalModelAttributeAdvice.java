package com.rssai.config;

import com.rssai.mapper.UserMapper;
import com.rssai.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalModelAttributeAdvice {

    @Autowired
    private UserMapper userMapper;

    @ModelAttribute
    public void addAttributes(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            User user = userMapper.findByUsername(auth.getName());
            if (user != null) {
                model.addAttribute("currentUser", user);
                model.addAttribute("isCurrentUserAdmin", user.getIsAdmin() != null && user.getIsAdmin());
            }
        }
    }
}
