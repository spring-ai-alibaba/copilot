import React, { useState, useEffect } from 'react';
import { Card, Descriptions, Tabs, Typography } from 'antd';
import { ApiResponse, ResponseData } from '../types';

interface ResponseViewerProps {
  response: ApiResponse | null;
}

function ResponseViewer({ response }: ResponseViewerProps): JSX.Element {
  const [responseData, setResponseData] = useState<ResponseData | null>(null);

  useEffect(() => {
    if (!response) return;

    const contentType = response.headers['content-type'] || '';
    
    if (response.data instanceof Blob) {
      if (contentType.includes('image/')) {
        setResponseData({ type: 'image', data: URL.createObjectURL(response.data) });
      } else if (contentType.includes('application/pdf')) {
        setResponseData({ type: 'pdf', data: URL.createObjectURL(response.data) });
      } else {
        setResponseData({ type: 'binary', size: response.data.size });
      }
    } else if (typeof response.data === 'object') {
      setResponseData({ type: 'json', data: response.data });
    } else {
      setResponseData({ type: 'text', data: response.data });
    }
  }, [response]);

  if (!response) return <div></div>;

  const items = [
    {
      key: 'response',
      label: 'Response',
      children: responseData && (
        <pre style={{ maxHeight: '400px', overflow: 'auto' }}>
          {JSON.stringify(responseData.data, null, 2)}
        </pre>
      )
    },
    {
      key: 'headers',
      label: 'Headers',
      children: (
        <pre>
          {JSON.stringify(response.headers, null, 2)}
        </pre>
      )
    }
  ];

  return (
    <div style={{ marginTop: '20px' }} className="text-sm">
      <div style={{ 
        padding: '8px 16px',
        backgroundColor: response.status < 400 ? '#f6ffed' : '#fff2f0',
        borderRadius: '4px',
        marginBottom: '16px'
      }}>
        {response.status} {response.statusText}
      </div>
      <Tabs items={items} />
    </div>
  );
}

export default ResponseViewer; 