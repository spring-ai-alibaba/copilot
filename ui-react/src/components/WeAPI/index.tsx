import React, { useState, useRef, useEffect } from "react";
import { useFileStore } from "../WeIde/stores/fileStore";
import { Layout, Button, message, ConfigProvider, theme } from "antd";
import ApiList from "./components/ApiList";
import RequestEditor from "./components/RequestEditor";
import ResponseViewer from "./components/ResponseViewer";
import { EditOutlined } from "@ant-design/icons";
import { ApiItem, ApiCollection, ApiResponse, FolderItem } from "./types";
import useThemeStore from "@/stores/themeSlice";
import { useTranslation } from "react-i18next";


const { Sider, Content } = Layout;

const getMethodColor = (method: string): string => {
  const colors: Record<string, string> = {
    GET: "#61affe",
    POST: "#49cc90",
    PUT: "#fca130",
    DELETE: "#f93e3e",
    PATCH: "#50e3c2",
    HEAD: "#9012fe",
    OPTIONS: "#0d5aa7",
  };
  return colors[method] || "#999";
};

export default function WeAPI(): React.ReactElement {
  const { t } = useTranslation();
  const [apiList, setApiList] = useState<ApiCollection>({
    id: "root",
    name: t("weapi.api_collection"),
    type: "folder",
    children: [],
  });
  const [selectedApi, setSelectedApi] = useState<ApiItem | null>(null);
  const [response, setResponse] = useState<ApiResponse | null>(null);
  const apiListRef = useRef<any>(null);
  const { files, updateContent } = useFileStore();
  const isUpdatingRef = useRef<boolean>(false);

  const { isDarkMode, toggleTheme, setTheme } = useThemeStore();

  useEffect(() => {
    if (isUpdatingRef.current) {
      isUpdatingRef.current = false;
      return;
    }

    const apiJsonStr = files["api.json"];
    try {
      if (apiJsonStr) {
        const parsedData = JSON.parse(apiJsonStr);
        setApiList(parsedData);
      }
    } catch (e) {
      console.warn("Failed to parse api.json:", e);
    }
  }, [files]);

  const saveToFile = async (newApiList: ApiCollection): Promise<boolean> => {
    try {
      isUpdatingRef.current = true;
      const apiJsonStr = JSON.stringify(newApiList, null, 2);
      const filePath = "api.json";
      updateContent(filePath, apiJsonStr);
      setApiList(newApiList);
      return true;
    } catch (e) {
      console.error("Failed to save api.json:", e);
      message.error(t("weapi.request_failed"));
      return false;
    }
  };

  const handleSendRequest = async () => {
    if (!selectedApi) return;

    try {
      const {
        method,
        url,
        headers = [],
        query = [],
        cookies = [],
        body,
        bodyType,
      } = selectedApi;

      // 构建URL和查询参数
      const queryString = query
        .filter((q) => q.key && q.value)
        .map(
          (q) => `${encodeURIComponent(q.key)}=${encodeURIComponent(q.value)}`
        )
        .join("&");
      const fullUrl = queryString ? `${url}?${queryString}` : url;

      // 构建headers
      const headerObj: Record<string, string> = headers.reduce(
        (acc, h) => {
          if (h.key && h.value) acc[h.key] = h.value;
          return acc;
        },
        {} as Record<string, string>
      );

      // 构建cookies
      const cookieStr = cookies
        .filter((c) => c.key && c.value)
        .map((c) => `${c.key}=${c.value}`)
        .join("; ");
      if (cookieStr) {
        headerObj["Cookie"] = cookieStr;
      }

      // 构建body
      let bodyData: any = null;
      if (bodyType === "json" && body?.json) {
        try {
          bodyData = JSON.stringify(body.json);
          headerObj["Content-Type"] = "application/json";
        } catch (e) {
          throw new Error("Invalid JSON body");
        }
      } else if (bodyType === "formData" && body?.formData) {
        const formData = new FormData();
        body.formData.forEach((item) => {
          if (item.type === "file" && item.value) {
            formData.append(item.key, item.value);
          } else if (item.key && item.value) {
            formData.append(item.key, item.value);
          }
        });
        bodyData = formData;
      }

      const fetchResponse = await fetch(fullUrl, {
        method,
        headers: headerObj,
        body: bodyData,
      });

      const contentType = fetchResponse.headers.get("content-type") || "";
      let responseData: any;

      if (contentType.includes("application/json")) {
        responseData = await fetchResponse.json();
      } else if (
        contentType.includes("image/") ||
        contentType.includes("application/pdf")
      ) {
        responseData = await fetchResponse.blob();
      } else {
        responseData = await fetchResponse.text();
      }

      const result: ApiResponse = {
        status: fetchResponse.status,
        statusText: fetchResponse.statusText,
        headers: Object.fromEntries(fetchResponse.headers.entries()),
        data: responseData,
      };

      setResponse(result);
    } catch (error) {
      message.error(
        `Request failed: ${error instanceof Error ? error.message : "Unknown error"}`
      );
    }
  };

  const handleApiSelect = (api: ApiItem) => {
    setSelectedApi(api);
  };

  const handleSave = async (updatedApi: ApiItem) => {
    if (!updatedApi) return;

    const updateApiInList = (
      list: (ApiItem | FolderItem)[]
    ): (ApiItem | FolderItem)[] => {
      return list.map((item) => {
        if (item.type === "folder" && "children" in item) {
          return {
            ...item,
            children: updateApiInList(item.children),
          };
        }
        if (item.id === updatedApi.id) {
          return { ...updatedApi, type: "api" };
        }
        return item;
      });
    };

    const newApiList = {
      ...apiList,
      children: updateApiInList(apiList.children),
    };

    const success = await saveToFile(newApiList);
    if (success) {
      setSelectedApi(updatedApi);
      message.success(t("weapi.api_saved"));
    }
  };

  const handleEdit = (api: ApiItem) => {
    if (apiListRef.current) {
      apiListRef.current.handleEdit(api);
    }
  };

  return (
    <div className="dark:border-[3px] border-[2px]  h-full dark:border-[#1a1a1a]">
      <ConfigProvider
        theme={{
          algorithm: isDarkMode ? theme.darkAlgorithm : theme.defaultAlgorithm,
        }}
      >
        <div className="flex h-screen rounded-lg">
          <ApiList
            ref={apiListRef}
            apiList={apiList.children}
            onSelect={handleApiSelect}
            onImport={async (newApiList) => {
              const success = await saveToFile({
                ...apiList,
                children: newApiList,
              });
              if (success) {
                message.success(t("weapi.import_success"));
              }
            }}
          />
          <div className="flex-1 p-6 overflow-auto">
            {selectedApi && (
              <>
                <div className="flex justify-between items-center mb-5 bg-gray-50 dark:bg-[#1a1a1c] p-4 rounded-lg">
                  <div className="flex items-center gap-4">
                    <div>
                      <span
                        className="px-2 py-1 rounded font-bold text-sm text-white"
                        style={{
                          backgroundColor: getMethodColor(selectedApi.method),
                        }}
                      >
                        {t(`weapi.method.${selectedApi.method}`)}
                      </span>
                    </div>
                    <div>
                      <h2 className="m-0">{selectedApi.name}</h2>
                      <p className="mt-1 mb-0 text-gray-500">
                        {selectedApi.url}
                      </p>
                    </div>
                  </div>
                  <div className="flex gap-2">
                    <Button
                      icon={<EditOutlined />}
                      onClick={() => handleEdit(selectedApi)}
                    >
                      {t("weapi.edit")}
                    </Button>
                    <Button
                      type="primary"
                      onClick={() => handleSave(selectedApi)}
                    >
                      {t("weapi.save_changes")}
                    </Button>
                  </div>
                </div>
                <RequestEditor api={selectedApi} onUpdate={setSelectedApi} />
                <div className="mt-5">
                  <Button type="primary" onClick={handleSendRequest}>
                    {t("weapi.send_request")}
                  </Button>
                  <ResponseViewer response={response} />
                </div>
              </>
            )}
          </div>
        </div>
      </ConfigProvider>
    </div>
  );
}
