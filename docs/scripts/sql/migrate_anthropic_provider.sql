-- 为现有数据库增加 Anthropic Messages API Provider。
-- 可重复执行；不会修改 API Key、Base URL、启用状态或模型 ID。

INSERT INTO `model_llm_factories`
    (`name`, `provider_code`, `logo`, `tags`, `sort_order`, `status`)
SELECT
    'Anthropic / Claude', 'Anthropic', NULL, 'LLM,Image2Text,Chat', 35, 0
WHERE NOT EXISTS (
    SELECT 1
    FROM `model_llm_factories`
    WHERE `provider_code` = 'Anthropic'
);

UPDATE `model_llm_factories`
SET `name` = '自定义供应商（OpenAI Compatible）'
WHERE `provider_code` = 'OpenAiCompatible'
  AND `name` = '自定义供应商';

INSERT INTO `model_llm`
    (`fid`, `llm_name`, `model_type`, `max_tokens`, `tags`, `is_tools`, `status`)
SELECT
    'Anthropic', 'claude-sonnet-4-6', 'CHAT', 200000, 'LLM,CHAT,TOOLS,IMAGE2TEXT', 1, 1
WHERE NOT EXISTS (
    SELECT 1
    FROM `model_llm`
    WHERE `fid` = 'Anthropic'
      AND `llm_name` = 'claude-sonnet-4-6'
);

-- 迁移此前被错误归类为 OpenAI Compatible 的 Claude 配置。
UPDATE `model_config`
SET `provider` = 'Anthropic',
    `updated_time` = CURRENT_TIMESTAMP
WHERE `provider` = 'OpenAiCompatible'
  AND (`model_key` LIKE 'claude-%' OR `model_name` LIKE 'claude-%');
