import React, { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Button, Descriptions, Input, Modal, Select, Space, Switch, Table, Tabs, Tag, Typography, message } from "antd";
import type { ColumnsType } from "antd/es/table";
import useUserStore from "@/stores/userSlice";
import { useMemoryStore } from "@/stores/memorySlice";
import { deleteMemory, listMemoriesByNamespace, searchMemory } from "@/api/memory";
import type { MemoryItem, PreferenceItem, PreferenceCategory } from "@/types/memory";

const { Text } = Typography;

const CATEGORY_OPTIONS: Array<{ value: PreferenceCategory; label: string }> = [
  { value: "programming_language", label: "编程语言" },
  { value: "framework_preference", label: "框架偏好" },
  { value: "tool_preference", label: "工具偏好" },
  { value: "coding_style", label: "编码风格" },
  { value: "response_style", label: "回答风格" },
  { value: "language_preference", label: "语言偏好" },
  { value: "other", label: "其他" },
];

const NAMESPACE_OPTIONS = [
  { value: "user_profiles", label: "用户画像" },
  { value: "user_preferences", label: "用户偏好" },
];

export default function MemorySettings() {
  const { t } = useTranslation();
  const { user } = useUserStore();
  const rawUserId = (user as any)?.userId ?? user?.id;
  // 后端 userId 是 long，这里强制校验/规范化，避免写到错误 key 导致“看起来没保存”
  const userId = useMemo(() => {
    if (rawUserId == null) return null;
    const s = String(rawUserId).trim();
    return /^\d+$/.test(s) ? s : null;
  }, [rawUserId]);

  const {
    userProfile,
    userPreferences,
    enablePreferenceLearning,
    enablePreferencesInChat,
    enablePreferenceLearningInChat,
    loadUserProfile,
    loadUserPreferences,
    updateUserProfile,
    addPreference,
    removePreference,
    togglePreferenceEnabled,
    setEnablePreferenceLearning,
    setEnablePreferencesInChat,
    setEnablePreferenceLearningInChat,
    loading,
  } = useMemoryStore();

  const [profileDraft, setProfileDraft] = useState<{ name?: string; language?: string }>({});
  const [prefsModalOpen, setPrefsModalOpen] = useState(false);
  const [newPref, setNewPref] = useState<Partial<PreferenceItem>>({
    category: "programming_language",
    value: "",
    context: "",
    enabled: true,
  });

  const [namespace, setNamespace] = useState<string>("user_preferences");
  const [namespaceItems, setNamespaceItems] = useState<MemoryItem[]>([]);
  const [namespaceLoading, setNamespaceLoading] = useState(false);

  const [searchNamespace, setSearchNamespace] = useState<string>('["user_preferences"]');
  const [searchFilter, setSearchFilter] = useState<string>("{}");
  const [searchResults, setSearchResults] = useState<MemoryItem[]>([]);
  const [searchLoading, setSearchLoading] = useState(false);

  useEffect(() => {
    if (!userId) return;
    loadUserProfile(userId);
    loadUserPreferences(userId);
  }, [userId]);

  useEffect(() => {
    setProfileDraft({
      name: userProfile?.name || "",
      language: userProfile?.language || "zh",
    });
  }, [userProfile?.name, userProfile?.language]);

  const enabledPrefs = useMemo(
    () => userPreferences.filter((p) => p.enabled !== false),
    [userPreferences]
  );

  const prefColumns: ColumnsType<PreferenceItem> = [
    {
      title: t("memory.columns.enabled"),
      dataIndex: "enabled",
      width: 90,
      render: (_, record) => (
        <Switch
          checked={record.enabled !== false}
          onChange={async (checked) => {
            if (!userId) return;
            await togglePreferenceEnabled(userId, String(record.category), record.value, checked);
          }}
        />
      ),
    },
    {
      title: t("memory.columns.category"),
      dataIndex: "category",
      width: 180,
      render: (v) => <Tag color="purple">{String(v)}</Tag>,
    },
    {
      title: t("memory.columns.value"),
      dataIndex: "value",
      render: (v) => <Text>{String(v)}</Text>,
    },
    {
      title: t("memory.columns.confidence"),
      dataIndex: "confidence",
      width: 110,
      render: (v) => (typeof v === "number" ? v.toFixed(2) : "-"),
    },
    {
      title: t("memory.columns.source"),
      dataIndex: "source",
      width: 140,
      render: (v) => (v ? <Tag>{String(v)}</Tag> : "-"),
    },
    {
      title: t("memory.columns.actions"),
      key: "actions",
      width: 120,
      render: (_, record) => (
        <Button
          danger
          size="small"
          onClick={() => {
            if (!userId) return;
            Modal.confirm({
              title: t("memory.confirm.deletePreferenceTitle"),
              content: `${record.category}: ${record.value}`,
              onOk: async () => {
                await removePreference(userId, String(record.category), record.value);
                message.success(t("memory.messages.preferenceDeleted"));
              },
            });
          }}
        >
          {t("common.delete")}
        </Button>
      ),
    },
  ];

  const memoryColumns: ColumnsType<MemoryItem> = [
    { title: t("memory.columns.key"), dataIndex: "key", width: 220 },
    {
      title: t("memory.columns.namespace"),
      dataIndex: "namespace",
      width: 240,
      render: (v) => <Text type="secondary">{Array.isArray(v) ? v.join(" / ") : String(v)}</Text>,
    },
    {
      title: t("memory.columns.value"),
      dataIndex: "value",
      render: (v) => (
        <pre className="text-xs text-gray-600 dark:text-gray-300 bg-gray-50 dark:bg-[#111113] p-2 rounded overflow-auto max-h-48">
          {JSON.stringify(v, null, 2)}
        </pre>
      ),
    },
    {
      title: t("memory.columns.actions"),
      key: "actions",
      width: 120,
      render: (_, record) => (
        <Button
          danger
          size="small"
          onClick={() => {
            Modal.confirm({
              title: t("memory.confirm.deleteMemoryTitle"),
              content: `${record.namespace.join(" / ")} / ${record.key}`,
              onOk: async () => {
                await deleteMemory({ namespace: record.namespace, key: record.key });
                message.success(t("memory.messages.memoryDeleted"));
                await loadNamespace();
              },
            });
          }}
        >
          {t("common.delete")}
        </Button>
      ),
    },
  ];

  const loadNamespace = async () => {
    setNamespaceLoading(true);
    try {
      const items = await listMemoriesByNamespace([namespace]);
      setNamespaceItems(items);
    } catch (e: any) {
      message.error(e?.message || t("memory.messages.loadFailed"));
    } finally {
      setNamespaceLoading(false);
    }
  };

  useEffect(() => {
    loadNamespace();
  }, [namespace]);

  const runSearch = async () => {
    setSearchLoading(true);
    try {
      const ns = JSON.parse(searchNamespace);
      const filterObj = JSON.parse(searchFilter);
      const res = await searchMemory({
        namespace: ns,
        filter: filterObj && Object.keys(filterObj).length > 0 ? filterObj : undefined,
      });
      const items = (res.items || []).map((it) => ({ namespace: it.namespace, key: it.key, value: it.value }));
      setSearchResults(items);
    } catch (e: any) {
      message.error(e?.message || t("memory.messages.searchFailed"));
    } finally {
      setSearchLoading(false);
    }
  };

  if (!userId) {
    return (
      <div className="text-gray-500">
        {t("memory.messages.loginRequired")}
        {rawUserId != null && (
          <div className="mt-2 text-xs text-gray-400">
            userId 无效（应为数字）: {String(rawUserId)}
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <Tabs
        items={[
          {
            key: "profile",
            label: t("memory.tabs.profile"),
            children: (
              <div className="space-y-4">
                <Descriptions bordered size="small" column={1} title={t("memory.sections.profile")}>
                  <Descriptions.Item label={t("memory.fields.name")}>
                    <Input
                      value={profileDraft.name}
                      onChange={(e) => setProfileDraft((p) => ({ ...p, name: e.target.value }))}
                      placeholder={t("memory.placeholders.name")}
                    />
                  </Descriptions.Item>
                  <Descriptions.Item label={t("memory.fields.language")}>
                    <Select
                      value={profileDraft.language || "zh"}
                      onChange={(v) => setProfileDraft((p) => ({ ...p, language: v }))}
                      options={[
                        { value: "zh", label: "中文" },
                        { value: "en", label: "English" },
                      ]}
                      style={{ width: 180 }}
                    />
                  </Descriptions.Item>
                  <Descriptions.Item label={t("memory.fields.enablePreferenceLearning")}>
                    <Space>
                      <Switch
                        checked={enablePreferenceLearning}
                        onChange={async (checked) => {
                          await setEnablePreferenceLearning(userId, checked);
                          message.success(t("memory.messages.saved"));
                        }}
                      />
                      <Text type="secondary">{t("memory.hints.enablePreferenceLearning")}</Text>
                    </Space>
                  </Descriptions.Item>
                </Descriptions>

                <Descriptions bordered size="small" column={1} title={t("memory.sections.session")}>
                  <Descriptions.Item label={t("memory.fields.enablePreferencesInChat")}>
                    <Space>
                      <Switch checked={enablePreferencesInChat} onChange={setEnablePreferencesInChat} />
                      <Text type="secondary">{t("memory.hints.enablePreferencesInChat")}</Text>
                    </Space>
                  </Descriptions.Item>
                  <Descriptions.Item label={t("memory.fields.enablePreferenceLearningInChat")}>
                    <Space>
                      <Switch checked={enablePreferenceLearningInChat} onChange={setEnablePreferenceLearningInChat} />
                      <Text type="secondary">{t("memory.hints.enablePreferenceLearningInChat")}</Text>
                    </Space>
                  </Descriptions.Item>
                </Descriptions>

                <Space>
                  <Button
                    type="primary"
                    loading={loading}
                    onClick={async () => {
                      await updateUserProfile(userId, {
                        name: profileDraft.name,
                        language: profileDraft.language,
                      });
                      message.success(t("memory.messages.saved"));
                    }}
                  >
                    {t("common.confirm")}
                  </Button>
                  <Button
                    onClick={() =>
                      setProfileDraft({
                        name: userProfile?.name || "",
                        language: userProfile?.language || "zh",
                      })
                    }
                  >
                    {t("common.cancel")}
                  </Button>
                </Space>
              </div>
            ),
          },
          {
            key: "preferences",
            label: t("memory.tabs.preferences"),
            children: (
              <div className="space-y-3">
                <Space>
                  <Button type="primary" onClick={() => setPrefsModalOpen(true)}>
                    {t("memory.actions.addPreference")}
                  </Button>
                  <Button onClick={() => loadUserPreferences(userId)} loading={loading}>
                    {t("memory.actions.refresh")}
                  </Button>
                  <Text type="secondary">
                    {t("memory.hints.enabledPreferencesCount", { count: enabledPrefs.length })}
                  </Text>
                </Space>

                <Table
                  rowKey={(r) => `${r.category}:${r.value}`}
                  columns={prefColumns}
                  dataSource={userPreferences}
                  pagination={{ pageSize: 8 }}
                />

                <Modal
                  title={t("memory.actions.addPreference")}
                  open={prefsModalOpen}
                  onCancel={() => setPrefsModalOpen(false)}
                  onOk={async () => {
                    if (!newPref.category || !newPref.value?.trim()) {
                      message.warning(t("memory.messages.preferenceValueRequired"));
                      return;
                    }
                    try {
                      await addPreference(userId, {
                        category: String(newPref.category),
                        value: String(newPref.value).trim(),
                        context: String(newPref.context || "").trim() || undefined,
                        enabled: newPref.enabled ?? true,
                        source: "manual",
                        learnedAt: new Date().toISOString(),
                        usageCount: 0,
                        confidence: 1.0,
                      });
                      message.success(t("memory.messages.preferenceAdded"));
                      setPrefsModalOpen(false);
                      setNewPref({ category: "programming_language", value: "", context: "", enabled: true });
                    } catch (e: any) {
                      message.error(e?.message || t("memory.messages.saveFailed"));
                    }
                  }}
                >
                  <Space direction="vertical" style={{ width: "100%" }}>
                    <div>
                      <div className="mb-1 text-sm">{t("memory.fields.category")}</div>
                      <Select
                        value={(newPref.category as any) || "programming_language"}
                        onChange={(v) => setNewPref((p) => ({ ...p, category: v }))}
                        options={CATEGORY_OPTIONS}
                        style={{ width: "100%" }}
                      />
                    </div>
                    <div>
                      <div className="mb-1 text-sm">{t("memory.fields.value")}</div>
                      <Input
                        value={newPref.value as any}
                        onChange={(e) => setNewPref((p) => ({ ...p, value: e.target.value }))}
                        placeholder={t("memory.placeholders.preferenceValue")}
                      />
                    </div>
                    <div>
                      <div className="mb-1 text-sm">{t("memory.fields.context")}</div>
                      <Input.TextArea
                        value={newPref.context as any}
                        onChange={(e) => setNewPref((p) => ({ ...p, context: e.target.value }))}
                        placeholder={t("memory.placeholders.context")}
                        rows={3}
                      />
                    </div>
                    <Space>
                      <Switch
                        checked={newPref.enabled !== false}
                        onChange={(checked) => setNewPref((p) => ({ ...p, enabled: checked }))}
                      />
                      <Text type="secondary">{t("memory.fields.enabled")}</Text>
                    </Space>
                  </Space>
                </Modal>
              </div>
            ),
          },
          {
            key: "list",
            label: t("memory.tabs.list"),
            children: (
              <div className="space-y-3">
                <Space>
                  <Select
                    value={namespace}
                    onChange={setNamespace}
                    options={NAMESPACE_OPTIONS}
                    style={{ width: 200 }}
                  />
                  <Button onClick={loadNamespace} loading={namespaceLoading}>
                    {t("memory.actions.refresh")}
                  </Button>
                </Space>
                <Table
                  rowKey={(r) => `${r.namespace.join("/")}:${r.key}`}
                  columns={memoryColumns}
                  dataSource={namespaceItems}
                  loading={namespaceLoading}
                  pagination={{ pageSize: 6 }}
                />
              </div>
            ),
          },
          {
            key: "search",
            label: t("memory.tabs.search"),
            children: (
              <div className="space-y-3">
                <Space direction="vertical" style={{ width: "100%" }}>
                  <div>
                    <div className="mb-1 text-sm">{t("memory.fields.namespace")}</div>
                    <Input
                      value={searchNamespace}
                      onChange={(e) => setSearchNamespace(e.target.value)}
                      placeholder='["user_preferences"]'
                    />
                  </div>
                  <div>
                    <div className="mb-1 text-sm">{t("memory.fields.filter")}</div>
                    <Input.TextArea
                      value={searchFilter}
                      onChange={(e) => setSearchFilter(e.target.value)}
                      placeholder="{}"
                      rows={4}
                    />
                  </div>
                  <Button type="primary" onClick={runSearch} loading={searchLoading}>
                    {t("memory.actions.search")}
                  </Button>
                </Space>

                <Table
                  rowKey={(r) => `${r.namespace.join("/")}:${r.key}`}
                  columns={memoryColumns}
                  dataSource={searchResults}
                  loading={searchLoading}
                  pagination={{ pageSize: 6 }}
                />
              </div>
            ),
          },
        ]}
      />
    </div>
  );
}

