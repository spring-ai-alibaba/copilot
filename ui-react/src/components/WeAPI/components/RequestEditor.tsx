import React from 'react';
import { Tabs } from 'antd';
import HeadersEditor from './HeadersEditor';
import QueryEditor from './QueryEditor';
import CookiesEditor from './CookiesEditor';
import BodyEditor from './BodyEditor';
import { ApiItem } from '../types';
import PathParamsEditor from './PathParamsEditor';

interface RequestEditorProps {
  api: ApiItem;
  onUpdate: (api: ApiItem) => void;
}

function RequestEditor({ api, onUpdate }: RequestEditorProps): JSX.Element {
  const handleUpdate = (field: keyof ApiItem, value: any) => {
    onUpdate({ ...api, [field]: value });
  };

  const items = [
    {
      key: 'url',
      label: 'URL',
      children: (
        <PathParamsEditor
          url={api.url}
          pathParams={api.pathParams || []}
          onUpdateUrl={(url) => handleUpdate('url', url)}
          onUpdateParams={(params) => handleUpdate('pathParams', params)}
        />
      )
    },
    {
      key: 'headers',
      label: 'Headers',
      children: (
        <HeadersEditor 
          headers={api.headers || []} 
          onUpdate={(headers) => handleUpdate('headers', headers)} 
        />
      )
    },
    {
      key: 'query',
      label: 'Query Params',
      children: (
        <QueryEditor 
          query={api.query || []} 
          onUpdate={(query) => handleUpdate('query', query)} 
        />
      )
    },
    {
      key: 'cookies',
      label: 'Cookies',
      children: (
        <CookiesEditor 
          cookies={api.cookies || []} 
          onUpdate={(cookies) => handleUpdate('cookies', cookies)} 
        />
      )
    }
  ];

  if (api.method !== 'GET') {
    items.push({
      key: 'body',
      label: 'Body',
      children: (
        <BodyEditor 
          bodyType={api.bodyType}
          body={api.body}
          onUpdate={(bodyType, body) => {
            onUpdate({
              ...api,
              bodyType,
              body
            });
          }} 
        />
      )
    });
  }

  return (
    <div style={{ flex: 1 }} className="text-sm">
      <Tabs defaultActiveKey="url" items={items} />
    </div>
  );
}

export default RequestEditor; 