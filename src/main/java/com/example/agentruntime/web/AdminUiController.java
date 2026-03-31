package com.example.agentruntime.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 管理台入口。
 */
@Controller
public class AdminUiController {

    @GetMapping({"/admin", "/ui"})
    public String admin() {
        return "forward:/admin/index.html";
    }
}
