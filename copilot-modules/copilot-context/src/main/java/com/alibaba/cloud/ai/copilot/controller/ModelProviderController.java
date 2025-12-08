package com.alibaba.cloud.ai.copilot.controller;

import com.alibaba.cloud.ai.copilot.core.domain.R;
import com.alibaba.cloud.ai.copilot.core.domain.model.LoginUser;
import com.alibaba.cloud.ai.copilot.dto.HealthCheckResult;
import com.alibaba.cloud.ai.copilot.dto.LlmServiceProvider;
import com.alibaba.cloud.ai.copilot.dto.ModelOptionVO;
import com.alibaba.cloud.ai.copilot.dto.OpenAiCompatibleRequest;
import com.alibaba.cloud.ai.copilot.entity.LlmFactoriesEntity;
import com.alibaba.cloud.ai.copilot.entity.ModelConfigEntity;
import com.alibaba.cloud.ai.copilot.mapper.LlmFactoriesMapper;
import com.alibaba.cloud.ai.copilot.satoken.utils.LoginHelper;
import com.alibaba.cloud.ai.copilot.service.ModelConfigService;
import com.alibaba.cloud.ai.copilot.provider.ProviderHealthCheckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 模型供应商管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/model-provider")
@RequiredArgsConstructor
public class ModelProviderController {
    
    private final LlmFactoriesMapper providerMapper;
    private final ProviderHealthCheckService healthCheckService;
    private final ModelConfigService modelConfigService;

    /**
     * 获取所有供应商（过滤掉当前用户已配置的供应商）
     */
    @GetMapping("/list")
    public List<LlmFactoriesEntity> getAllProviders() {
        LoginUser loginUser = LoginHelper.getLoginUser();
        // 获取当前用户已配置的供应商代码集合
        Set<String> configuredProviders = modelConfigService.getCurrentUserModels(loginUser.getUserId())
                .stream()
                .map(LlmServiceProvider::providerId)
                .collect(Collectors.toSet());

        // 过滤掉已配置的供应商
        return providerMapper.selectList(null).stream()
                .filter(provider -> !configuredProviders.contains(provider.getProviderCode()))
                .collect(Collectors.toList());
    }
    
    /**
     * 检测供应商健康状态
     */
    @PostMapping("/health")
    public HealthCheckResult checkProviderHealth(
            @RequestParam String providerCode,
            @RequestParam String apiKey) {
        return healthCheckService.checkHealth(providerCode, apiKey);
    }

    /**
     * 检测指定模型健康状态
     *
     * @param providerCode 供应商代码
     * @param modelName    模型名称
     * @return 健康检测结果
     */
    @PostMapping("/model-health")
    public HealthCheckResult checkModelHealth(
            @RequestParam String providerCode,
            @RequestParam String modelName) {
        return healthCheckService.checkModelHealth(providerCode, modelName);
    }

    /**
     * 判断当前用户在该供应商是否配置了apikey
     * @return
     */
    @GetMapping("/my_llms")
    public R<List<LlmServiceProvider>> myLlms() {
        LoginUser loginUser = LoginHelper.getLoginUser();
        return R.ok(modelConfigService.getCurrentUserModels(loginUser.getUserId()));
    }

    /**
     * 删除当前用户在指定供应商下的所有模型配置
     * @param providerCode 供应商代码
     * @return 删除结果
     */
    @DeleteMapping("/delete/{providerCode}")
    public R<Integer> deleteProviderModels(@PathVariable String providerCode) {
        LoginUser loginUser = LoginHelper.getLoginUser();
        int deletedCount = modelConfigService.deleteModelsByProvider(loginUser.getUserId(), providerCode);
        return R.ok(deletedCount);
    }

    /**
     * 编辑已配置模型
     * @param modelConfig 模型配置实体（需包含id）
     * @return 更新结果
     */
    @PutMapping("/model")
    public R<Boolean> updateModelConfig(@RequestBody ModelConfigEntity modelConfig) {
        if (modelConfig.getId() == null) {
            return R.fail("模型配置ID不能为空");
        }
        boolean success = modelConfigService.updateUserModelConfig(modelConfig);
        if (success) {
            return R.ok(true);
        } else {
            return R.fail("模型配置更新失败，请检查配置是否存在或权限是否正确");
        }
    }

    /**
     * 检测 OpenAI Compatible 供应商健康状态
     * 用户可以自定义 URL、API Key 和测试模型名称
     * @param request OpenAI Compatible 请求参数
     * @return 健康检测结果
     */
    @PostMapping("/openai-compatible/health")
    public R<HealthCheckResult> checkOpenAiCompatibleHealth(@Valid @RequestBody OpenAiCompatibleRequest request) {
        return R.ok(healthCheckService.checkOpenAiCompatibleHealth(
                request.getApiUrl(),
                request.getApiKey(),
                request.getTestModelName()
        ));
    }
}

