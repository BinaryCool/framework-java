package com.dodosupermarket.im.controller;

import com.dodosupermarket.im.service.ImAccountService;
import com.dodosupermarket.im.service.ImImageService;
import com.dodosupermarket.im.service.ImMessageContent;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;
import pers.binaryhunter.framework.bean.vo.R;

import java.util.Map;
import javax.servlet.http.HttpServletRequest;

/**
 * Allows the page to select a transport before any send, without falling back after failure.
 */
@RestController
@RequestMapping("/api/v1/im")
public class ImCapabilityController {
    private final ObjectProvider<ImAccountService> service;
    private final ObjectProvider<ImImageService> images;
    private final ObjectProvider<ImMessageContent> content;

    public ImCapabilityController(ObjectProvider<ImAccountService> service, ObjectProvider<ImImageService> images, ObjectProvider<ImMessageContent> content) {
        this.service = service;
        this.images = images;
        this.content = content;
    }

    @GetMapping("/capabilities")
    public R<Map<String, Object>> capabilities(HttpServletRequest request) {
        ImAccountService active = service.getIfAvailable();
        if (active == null) return R.toResponse(Map.of("enabled", false));
        // No account data is returned; identity is still required by all conversation operations.
        ImImageService storage = images.getIfAvailable();
        ImMessageContent messages = content.getIfAvailable();
        return R.toResponse(Map.of("enabled", true, "text", true, "sendLedger", true, "images", storage != null && storage.enabled(), "quotes", messages != null, "cards", messages != null && messages.cardsEnabled()));
    }
}
