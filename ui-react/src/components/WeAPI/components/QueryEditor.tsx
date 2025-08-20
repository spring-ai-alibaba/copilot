import React from 'react';
import { Input, Button, Space } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { KeyValuePair } from '../types';

interface QueryEditorProps {
  query: KeyValuePair[];
  onUpdate: (query: KeyValuePair[]) => void;
}

function QueryEditor({ query, onUpdate }: QueryEditorProps): JSX.Element {
  const handleQueryChange = (index: number, key: string, value: string) => {
    const newQuery = [...query];
    newQuery[index] = { ...newQuery[index], [key]: value };
    onUpdate(newQuery);
  };

  return (
    <div className="text-sm">
      {query.map((param, index) => (
        <Space key={index} style={{ display: 'flex', marginBottom: 8 }}>
          <Input
            placeholder="Key"
            value={param.key}
            onChange={(e) => handleQueryChange(index, 'key', e.target.value)}
            style={{ width: 200 }}
          />
          <Input
            placeholder="Value"
            value={param.value}
            onChange={(e) => handleQueryChange(index, 'value', e.target.value)}
            style={{ width: 200 }}
          />
          <Button
            type="text"
            icon={<DeleteOutlined />}
            onClick={() => {
              const newQuery = query.filter((_, i) => i !== index);
              onUpdate(newQuery);
            }}
          />
        </Space>
      ))}
      <Button
        type="dashed"
        onClick={() => onUpdate([...query, { key: '', value: '' }])}
        icon={<PlusOutlined />}
      >
        Add Query Param
      </Button>
    </div>
  );
}

export default QueryEditor; 