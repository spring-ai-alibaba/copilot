package com.alibaba.cloud.ai.copilot.memory.shortterm;

import com.alibaba.cloud.ai.copilot.memory.cache.MemoryCacheService;
import com.alibaba.cloud.ai.copilot.memory.compression.CompressionService;
import com.alibaba.cloud.ai.copilot.memory.compression.CompressedSummary;
import com.alibaba.cloud.ai.copilot.memory.compression.MessageBoundaryDetector;
import com.alibaba.cloud.ai.copilot.memory.config.MemoryProperties;
import com.alibaba.cloud.ai.copilot.memory.domain.Message;
import com.alibaba.cloud.ai.copilot.memory.token.TokenCounterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CompressibleChatMemory 缓存一致性集成测试
 * 测试 Redis 缓存与数据库的一致性保证
 */
@ExtendWith(MockitoExtension.class)
class CompressibleChatMemoryCacheIntegrationTest {

    @Mock
    private MemoryProperties memoryProperties;

    @Mock
    private TokenCounterService tokenCounterService;

    @Mock
    private MessageBoundaryDetector boundaryDetector;

    @Mock
    private CompressionService compressionService;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private MemoryCacheService cacheService;

    @Mock
    private RLock rLock;

    private CompressibleChatMemory chatMemory;

    @BeforeEach
    void setUp() {
        chatMemory = new CompressibleChatMemory(
                memoryProperties,
                tokenCounterService,
                boundaryDetector,
                compressionService,
                chatMessageRepository,
                cacheService
        );

        // 默认配置
        MemoryProperties.ShortTermMemory shortTerm = new MemoryProperties.ShortTermMemory();
        MemoryProperties.Compression compression = new MemoryProperties.Compression();
        compression.setEnabled(false); // 默认关闭压缩，避免干扰测试
        shortTerm.setCompression(compression);
        when(memoryProperties.getShortTerm()).thenReturn(shortTerm);
    }

    @Test
    void testAdd_CacheUpdateAfterDatabaseSave() {
        // Arrange
        String conversationId = "test-conv-1";
        Message message = createTestMessage("msg-1", "user", "Hello");
        List<Message> existingMessages = new ArrayList<>();
        
        when(cacheService.getMessages(conversationId)).thenReturn(null);
        when(chatMessageRepository.load(conversationId)).thenReturn(existingMessages);

        // Act
        chatMemory.add(conversationId, message);

        // Assert
        // 1. 验证先保存到数据库
        verify(chatMessageRepository).save(conversationId, message);
        
        // 2. 验证后更新缓存
        verify(cacheService).setMessages(eq(conversationId), anyList());
        
        // 3. 验证调用顺序
        var inOrder = inOrder(chatMessageRepository, cacheService);
        inOrder.verify(chatMessageRepository).save(conversationId, message);
        inOrder.verify(cacheService).setMessages(eq(conversationId), anyList());
    }

    @Test
    void testAdd_CacheInvalidatedOnException() {
        // Arrange
        String conversationId = "test-conv-2";
        Message message = createTestMessage("msg-2", "user", "Hello");
        
        when(cacheService.getMessages(conversationId)).thenReturn(null);
        when(chatMessageRepository.load(conversationId)).thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> chatMemory.add(conversationId, message));
        
        // 验证缓存被删除
        verify(cacheService).deleteMessages(conversationId);
    }

    @Test
    void testGet_CacheHit() {
        // Arrange
        String conversationId = "test-conv-3";
        List<Message> cachedMessages = createTestMessages(3);
        
        when(cacheService.getMessages(conversationId)).thenReturn(cachedMessages);

        // Act
        List<Message> result = chatMemory.get(conversationId);

        // Assert
        assertNotNull(result);
        assertEquals(3, result.size());
        
        // 验证刷新了缓存过期时间
        verify(cacheService).refreshExpiration(conversationId);
        
        // 验证没有访问数据库
        verify(chatMessageRepository, never()).load(conversationId);
    }

    @Test
    void testGet_CacheMiss_LoadFromDatabase() {
        // Arrange
        String conversationId = "test-conv-4";
        List<Message> dbMessages = createTestMessages(5);
        
        when(cacheService.getMessages(conversationId)).thenReturn(null);
        when(chatMessageRepository.load(conversationId)).thenReturn(dbMessages);

        // Act
        List<Message> result = chatMemory.get(conversationId);

        // Assert
        assertNotNull(result);
        assertEquals(5, result.size());
        
        // 验证从数据库加载
        verify(chatMessageRepository).load(conversationId);
        
        // 验证回填缓存
        verify(cacheService).setMessages(conversationId, dbMessages);
    }

    @Test
    void testCompressHistory_WithDistributedLock() {
        // Arrange
        String conversationId = "test-conv-5";
        List<Message> messages = createTestMessages(10);
        CompressedSummary summary = createTestSummary();
        
        // 启用压缩
        MemoryProperties.ShortTermMemory shortTerm = new MemoryProperties.ShortTermMemory();
        MemoryProperties.Compression compression = new MemoryProperties.Compression();
        compression.setEnabled(true);
        compression.setPreserveThreshold(0.3);
        shortTerm.setCompression(compression);
        when(memoryProperties.getShortTerm()).thenReturn(shortTerm);
        
        when(cacheService.tryLock(conversationId, 5, 60)).thenReturn(rLock);
        when(cacheService.getMessages(conversationId)).thenReturn(messages);
        when(boundaryDetector.findCompressionBoundary(anyList(), anyDouble())).thenReturn(7);
        when(compressionService.compressMessages(anyList(), anyString())).thenReturn(summary);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        // Act
        chatMemory.compressHistory(conversationId);

        // Assert
        // 1. 验证获取了分布式锁
        verify(cacheService).tryLock(conversationId, 5, 60);
        
        // 2. 验证先更新数据库
        verify(chatMessageRepository).replace(eq(conversationId), anyList());
        
        // 3. 验证后删除缓存
        verify(cacheService).deleteMessages(conversationId);
        
        // 4. 验证更新了压缩时间
        verify(cacheService).setLastCompressionTime(eq(conversationId), anyLong());
        
        // 5. 验证释放了锁
        verify(cacheService).unlock(rLock);
    }

    @Test
    void testCompressHistory_LockAcquisitionFailed() {
        // Arrange
        String conversationId = "test-conv-6";
        
        when(cacheService.tryLock(conversationId, 5, 60)).thenReturn(null);

        // Act
        chatMemory.compressHistory(conversationId);

        // Assert
        // 验证没有执行压缩操作
        verify(chatMessageRepository, never()).replace(anyString(), anyList());
        verify(cacheService, never()).deleteMessages(conversationId);
    }

    @Test
    void testCompressHistory_CacheInvalidatedOnException() {
        // Arrange
        String conversationId = "test-conv-7";
        List<Message> messages = createTestMessages(10);
        
        // 启用压缩
        MemoryProperties.ShortTermMemory shortTerm = new MemoryProperties.ShortTermMemory();
        MemoryProperties.Compression compression = new MemoryProperties.Compression();
        compression.setEnabled(true);
        compression.setPreserveThreshold(0.3);
        shortTerm.setCompression(compression);
        when(memoryProperties.getShortTerm()).thenReturn(shortTerm);
        
        when(cacheService.tryLock(conversationId, 5, 60)).thenReturn(rLock);
        when(cacheService.getMessages(conversationId)).thenReturn(messages);
        when(boundaryDetector.findCompressionBoundary(anyList(), anyDouble())).thenReturn(7);
        when(compressionService.compressMessages(anyList(), anyString()))
                .thenThrow(new RuntimeException("Compression failed"));
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        // Act
        chatMemory.compressHistory(conversationId);

        // Assert
        // 验证缓存被删除（保证一致性）
        verify(cacheService).deleteMessages(conversationId);
        
        // 验证锁被释放
        verify(cacheService).unlock(rLock);
    }

    @Test
    void testClear_DatabaseFirstThenCache() {
        // Arrange
        String conversationId = "test-conv-8";

        // Act
        chatMemory.clear(conversationId);

        // Assert
        // 验证调用顺序：先删数据库，后删缓存
        var inOrder = inOrder(chatMessageRepository, cacheService);
        inOrder.verify(chatMessageRepository).delete(conversationId);
        inOrder.verify(cacheService).clearAll(conversationId);
    }

    @Test
    void testClear_CacheNotClearedOnDatabaseError() {
        // Arrange
        String conversationId = "test-conv-9";
        doThrow(new RuntimeException("Database error"))
                .when(chatMessageRepository).delete(conversationId);

        // Act & Assert
        assertThrows(RuntimeException.class, () -> chatMemory.clear(conversationId));
        
        // 验证缓存没有被删除（保证一致性）
        verify(cacheService, never()).clearAll(conversationId);
    }

    @Test
    void testCanCompress_ChecksCompressionInterval() {
        // Arrange
        String conversationId = "test-conv-10";
        long lastTime = System.currentTimeMillis() - 30000; // 30秒前
        
        MemoryProperties.ShortTermMemory shortTerm = new MemoryProperties.ShortTermMemory();
        MemoryProperties.Compression compression = new MemoryProperties.Compression();
        compression.setEnabled(true);
        compression.setMinIntervalSeconds(60); // 最小间隔60秒
        shortTerm.setCompression(compression);
        when(memoryProperties.getShortTerm()).thenReturn(shortTerm);
        
        when(cacheService.getLastCompressionTime(conversationId)).thenReturn(lastTime);

        // Act
        List<Message> messages = createTestMessages(5);
        when(cacheService.getMessages(conversationId)).thenReturn(messages);
        chatMemory.get(conversationId); // 触发检查

        // Assert
        // 由于间隔不足，不应该执行压缩
        verify(chatMessageRepository, never()).replace(anyString(), anyList());
    }

    /**
     * 创建测试消息
     */
    private Message createTestMessage(String id, String role, String content) {
        Message message = new Message();
        message.setId(id);
        message.setRole(role);
        message.setContent(content);
        message.setTimestamp(LocalDateTime.now());
        return message;
    }

    /**
     * 创建测试消息列表
     */
    private List<Message> createTestMessages(int count) {
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            messages.add(createTestMessage("msg-" + i, i % 2 == 0 ? "user" : "assistant", "Content " + i));
        }
        return messages;
    }

    /**
     * 创建测试摘要
     */
    private CompressedSummary createTestSummary() {
        return CompressedSummary.builder()
                .mainTopics(List.of("Topic 1", "Topic 2"))
                .keyDecisions(List.of("Decision 1"))
                .build();
    }
}

