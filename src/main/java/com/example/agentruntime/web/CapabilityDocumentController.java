package com.example.agentruntime.web;

import com.example.agentruntime.document.CapabilityDocumentService;
import com.example.agentruntime.document.CapabilityDocumentSummary;
import com.example.agentruntime.document.CapabilityDocumentView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 能力文档接口。
 * 供前端与模型共用统一的“摘要搜索 + 详情读取”动态加载能力。
 */
@RestController
@RequestMapping("/api/capability-docs")
public class CapabilityDocumentController {

    private final CapabilityDocumentService capabilityDocumentService;

    public CapabilityDocumentController(CapabilityDocumentService capabilityDocumentService) {
        this.capabilityDocumentService = capabilityDocumentService;
    }

    @GetMapping("/search")
    public List<CapabilityDocumentSummary> search(@RequestParam(value = "q", required = false) String query) {
        return capabilityDocumentService.search(query);
    }

    @GetMapping("/read")
    public CapabilityDocumentView read(@RequestParam("docId") String docId,
                                       @RequestParam(value = "docType", required = false) String docType) {
        return capabilityDocumentService.read(docId, docType);
    }
}
