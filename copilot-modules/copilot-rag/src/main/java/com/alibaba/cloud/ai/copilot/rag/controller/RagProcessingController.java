package com.alibaba.cloud.ai.copilot.rag.controller;

import com.alibaba.cloud.ai.copilot.rag.service.RagProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * RAG处理控制器
 * 提供文件上传和处理的API接口
 */
@Slf4j
@RestController
@RequestMapping("/api/rag/processing")
@RequiredArgsConstructor
public class RagProcessingController {

    private final RagProcessingService ragProcessingService;

    /**
     * 上传并处理单个文件
     */
    @PostMapping("/{kbKey}/upload-file")
    public ResponseEntity<RagProcessingService.ProcessingResult> uploadFile(
            @PathVariable String kbKey,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "system") String uploadedBy) {

        log.info("上传文件到知识库: {} - {}", kbKey, file.getOriginalFilename());

        RagProcessingService.ProcessingResult result = ragProcessingService.processFile(kbKey, file, uploadedBy);

        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 批量上传并处理文件
     */
    @PostMapping("/{kbKey}/upload-files")
    public ResponseEntity<RagProcessingService.BatchProcessingResult> uploadFiles(
            @PathVariable String kbKey,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(defaultValue = "system") String uploadedBy) {

        log.info("批量上传文件到知识库: {} - {} 个文件", kbKey, files.size());

        RagProcessingService.BatchProcessingResult result = ragProcessingService.processFiles(kbKey, files, uploadedBy);

        return ResponseEntity.ok(result);
    }

    /**
     * 添加文本内容
     */
    @PostMapping("/{kbKey}/add-text")
    public ResponseEntity<RagProcessingService.ProcessingResult> addTextContent(
            @PathVariable String kbKey,
            @RequestParam String content,
            @RequestParam(defaultValue = "文本内容") String title,
            @RequestParam(defaultValue = "system") String createdBy) {

        log.info("添加文本内容到知识库: {} - {}", kbKey, title);

        RagProcessingService.ProcessingResult result = ragProcessingService.processTextContent(kbKey, content, title, createdBy);

        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 异步上传并处理文件
     */
    @PostMapping("/{kbKey}/upload-file-async")
    public ResponseEntity<String> uploadFileAsync(
            @PathVariable String kbKey,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "system") String uploadedBy) {

        log.info("异步上传文件到知识库: {} - {}", kbKey, file.getOriginalFilename());

        String taskId = ragProcessingService.processFileAsync(kbKey, file, uploadedBy);

        return ResponseEntity.ok(taskId);
    }

    /**
     * 查询异步任务状态
     */
    @GetMapping("/task/{taskId}/status")
    public ResponseEntity<RagProcessingService.ProcessingTaskStatus> getTaskStatus(@PathVariable String taskId) {
        RagProcessingService.ProcessingTaskStatus status = ragProcessingService.getTaskStatus(taskId);
        return ResponseEntity.ok(status);
    }
}
