package com.alibaba.cloud.ai.copilot.knowledge.service;

import com.alibaba.cloud.ai.copilot.knowledge.domain.entity.FileIndexState;
import com.alibaba.cloud.ai.copilot.knowledge.mapper.IndexStateMapper;
import com.alibaba.cloud.ai.copilot.knowledge.domain.vo.KnowledgeChunk;
import com.alibaba.cloud.ai.copilot.knowledge.splitter.DocumentSplitter;
import com.alibaba.cloud.ai.copilot.knowledge.splitter.SplitterFactory;
import com.alibaba.cloud.ai.copilot.knowledge.utils.FileScanner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 代码库索引编排器
 * 核心类，负责全量扫描、增量差异计算、调用 Splitter 和 VectorStore
 *
 * @author RobustH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodebaseIndexer {

    private final FileScanner fileScanner;
    private final IndexStateMapper indexStateRepository;
    private final SplitterFactory splitterFactory;
    private final KnowledgeVectorStoreService vectorStoreService;

    /**
     * 触发刷新索引
     *
     * @param userId        用户ID
     * @param workspacePath 工作区根目录
     */
    public void refresh(String userId, String workspacePath) {
        log.info("开始刷新索引: 用户={}, 路径={}", userId, workspacePath);
        Path root = Path.of(workspacePath);
        
        // 1. 扫描文件
        List<Path> files = fileScanner.scan(root);
        log.info("扫描到 {} 个文件", files.size());

        int added = 0;
        int updated = 0;
        int skipped = 0;
        int error = 0;

        // 2. 遍历处理 (Diff 逻辑)
        for (Path file : files) {
            String absolutePath = file.toAbsolutePath().toString();
            try {
                // 读取文件内容
                String content = Files.readString(file, StandardCharsets.UTF_8);
                String currentHash = DigestUtils.md5DigestAsHex(content.getBytes(StandardCharsets.UTF_8));

                // 检查状态
                // 检查状态: 根据 userId + filePath 查询
                FileIndexState queryState = new FileIndexState();
                queryState.setUserId(userId);
                queryState.setFilePath(absolutePath);
                
                QueryWrapper<FileIndexState> queryWrapper = new QueryWrapper<>(queryState);
                FileIndexState state = indexStateRepository.selectOne(queryWrapper);

                if (state == null) {
                    // 新增文件
                    indexFile(userId, absolutePath, content, currentHash, null); // null state -> insert
                    added++;
                } else {
                    FileIndexState currentState = state;
                    if (!currentHash.equals(currentState.getContentHash())) {
                        // 内容变更，重新索引
                        // 先清理旧数据
                        vectorStoreService.deleteKnowledgeByFilePath(userId, absolutePath);
                        indexFile(userId, absolutePath, content, currentHash, currentState); // state -> update
                        updated++;
                    } else {
                        // 无变更，跳过
                        skipped++;
                    }
                }

            } catch (IOException e) {
                log.error("读取文件失败: {}", absolutePath, e);
                error++;
            } catch (Exception e) {
                log.error("索引文件失败: {}", absolutePath, e);
                error++;
            }
        }
        
        // 3. 处理被删除的文件 (Database 中有但 filesystem 中没有的)
        try {
            // 获取数据库中所有的文件状态
            // 注意: 对于大型项目，应该分批处理或只查询路径。
            // 获取数据库中该用户的所有文件状态
            QueryWrapper<FileIndexState> allStatesQuery = new QueryWrapper<>();
            allStatesQuery.eq("user_id", userId);
            List<FileIndexState> allStates = indexStateRepository.selectList(allStatesQuery);
            
            // 构建扫描到的文件路径集合 (转为 String)
            java.util.Set<String> scannedPaths = files.stream()
                .map(p -> p.toAbsolutePath().toString())
                .collect(java.util.stream.Collectors.toSet());

            int deleted = 0;
            for (FileIndexState state : allStates) {
                if (!scannedPaths.contains(state.getFilePath())) {
                    // 文件已删除
                    log.info("发现已删除文件: {}", state.getFilePath());
                    
                    // 1. 清理向量库
                    vectorStoreService.deleteKnowledgeByFilePath(userId, state.getFilePath());
                    
                    // 2. 清理状态库
                    indexStateRepository.deleteById(state.getId());
                    
                    deleted++;
                }
            }
            log.info("索引刷新完成. 新增: {}, 更新: {}, 删除: {}, 跳过: {}, 错误: {}", added, updated, deleted, skipped, error);
            
        } catch (Exception e) {
            log.error("处理删除文件逻辑失败", e);
        }
    }

    /**
     * 索引单个文件
     */
    private void indexFile(String userId, String filePath, String content, String hash, FileIndexState existingState) {
        // 1. 获取合适的 Splitter
        DocumentSplitter splitter = splitterFactory.getSplitterByPath(filePath);
        
        // 2. 切割文档
        List<KnowledgeChunk> chunks = splitter.split(content, filePath);
        
        // 3. 补充 Hash 信息
        chunks.forEach(chunk -> chunk.setContentHash(hash));

        // 4. 存入向量库
        vectorStoreService.addKnowledgeBatch(userId, chunks);

        // 5. 更新状态库
        if (existingState == null) {
            FileIndexState newState = FileIndexState.builder()
                    .id(java.util.UUID.randomUUID().toString())
                    .userId(userId)
                    .filePath(filePath)
                    .contentHash(hash)
                    .lastModifiedAt(LocalDateTime.now())
                    .fileSize((long) content.length())
                    .build();
            indexStateRepository.insert(newState);
        } else {
            existingState.setContentHash(hash);
            existingState.setLastModifiedAt(LocalDateTime.now());
            existingState.setFileSize((long) content.length());
            indexStateRepository.updateById(existingState);
        }
        
        log.debug("已索引文件: {}", filePath);
    }
}
