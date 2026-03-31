package com.example.agentruntime.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "agent.auth.enabled=false")
@AutoConfigureMockMvc(addFilters = false)
class AgentControllerValidationI18nTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnChineseValidationMessage() throws Exception {
        mockMvc.perform(post("/api/agent/execute")
                        .header("Accept-Language", "zh-CN")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("请求字段")));
    }

    @Test
    void shouldReturnEnglishValidationMessage() throws Exception {
        mockMvc.perform(post("/api/agent/execute")
                        .header("Accept-Language", "en-US")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Invalid request field")));
    }
}
