import React, { useState } from 'react';
import { Input, Button, Space, Alert, Tabs } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { PathParam } from '../types';

interface PathParamsEditorProps {
  url: string;
  pathParams: PathParam[];
  onUpdateUrl: (url: string) => void;
  onUpdateParams: (params: PathParam[]) => void;
}

function PathParamsEditor({ url, pathParams, onUpdateUrl, onUpdateParams }: PathParamsEditorProps): JSX.Element {
  const [activeTab, setActiveTab] = useState<'edit' | 'preview'>('edit');

  // 解析 URL 中的参数占位符
  const parseUrlParams = (url: string): string[] => {
    const matches = url.match(/:[a-zA-Z_][a-zA-Z0-9_]*/g) || [];
    return matches.map(match => match.substring(1));
  };

  // 当 URL 改变时更新参数列表
  const handleUrlChange = (newUrl: string) => {
    onUpdateUrl(newUrl);
    const paramNames = parseUrlParams(newUrl);
    
    // 保留已存在的参数值，添加新的参数
    const updatedParams = paramNames.map(name => {
      const existingParam = pathParams.find(p => p.placeholder === `:${name}`);
      return existingParam || {
        key: name,
        value: '',
        placeholder: `:${name}`
      };
    });

    onUpdateParams(updatedParams);
  };

  // 获取实际的 URL（替换占位符）
  const getResolvedUrl = () => {
    let resolvedUrl = url;
    pathParams.forEach(param => {
      resolvedUrl = resolvedUrl.replace(param.placeholder, param.value || param.placeholder);
    });
    return resolvedUrl;
  };

  const renderUrlPreview = () => (
    <div style={{ marginBottom: '16px' }}>
      <Alert
        message="Current URL"
        description={
          <div style={{ 
            fontFamily: 'monospace', 
            wordBreak: 'break-all',
            whiteSpace: 'pre-wrap'
          }}>
            {getResolvedUrl()}
          </div>
        }
        type="info"
        showIcon
      />
    </div>
  );

  const renderEditTab = () => (
    <>
      <div>
        <div style={{ marginBottom: '8px' }}>URL Template</div>
        <Input.TextArea
          value={url}
          onChange={(e) => handleUrlChange(e.target.value)}
          placeholder="Enter URL with :param placeholders (e.g., /api/users/:id/posts/:postId)"
          autoSize={{ minRows: 2 }}
        />
      </div>

      {pathParams.length > 0 && (
        <div style={{ marginTop: '16px' }}>
          <div style={{ marginBottom: '8px' }}>Parameter Values</div>
          {pathParams.map((param, index) => (
            <Space key={index} style={{ display: 'flex', marginBottom: 8, width: '100%' }}>
              <Input
                value={param.key}
                disabled
                style={{ width: 150 }}
                addonBefore="Name"
              />
              <Input
                placeholder="Value"
                value={param.value}
                onChange={(e) => {
                  const newParams = [...pathParams];
                  newParams[index] = { ...param, value: e.target.value };
                  onUpdateParams(newParams);
                }}
                style={{ width: 200 }}
                addonBefore="Value"
              />
            </Space>
          ))}
        </div>
      )}
    </>
  );

  const renderPreviewTab = () => (
    <div>
      <div style={{ marginBottom: '16px' }}>
        <div style={{ marginBottom: '8px' }}>URL Template</div>
        <Alert
          message={
            <div style={{ 
              fontFamily: 'monospace', 
              wordBreak: 'break-all',
              whiteSpace: 'pre-wrap'
            }}>
              {url}
            </div>
          }
          type="info"
        />
      </div>

      {pathParams.length > 0 && (
        <div>
          <div style={{ marginBottom: '8px' }}>Parameters</div>
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ backgroundColor: '#fafafa' }}>
                <th style={{ padding: '8px', textAlign: 'left', border: '1px solid #f0f0f0' }}>Parameter</th>
                <th style={{ padding: '8px', textAlign: 'left', border: '1px solid #f0f0f0' }}>Value</th>
              </tr>
            </thead>
            <tbody>
              {pathParams.map((param, index) => (
                <tr key={index}>
                  <td style={{ padding: '8px', border: '1px solid #f0f0f0' }}>{param.placeholder}</td>
                  <td style={{ padding: '8px', border: '1px solid #f0f0f0' }}>{param.value || '(empty)'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div style={{ marginTop: '16px' }}>
        <div style={{ marginBottom: '8px' }}>Resolved URL</div>
        <Alert
          message={
            <div style={{ 
              fontFamily: 'monospace', 
              wordBreak: 'break-all',
              whiteSpace: 'pre-wrap'
            }}>
              {getResolvedUrl()}
            </div>
          }
          type="success"
        />
      </div>
    </div>
  );

  return (
    <div className="text-sm">
      {renderUrlPreview()}
      
      <Tabs
        activeKey={activeTab}
        onChange={(key) => setActiveTab(key as 'edit' | 'preview')}
        items={[
          {
            key: 'edit',
            label: 'Edit',
            children: renderEditTab()
          },
          {
            key: 'preview',
            label: 'Preview',
            children: renderPreviewTab()
          }
        ]}
      />
    </div>
  );
}

export default PathParamsEditor; 