import React, {forwardRef, useCallback, useImperativeHandle, useRef, useState} from 'react';
import {Button, Form, Input, message, Modal, Select, Tooltip, Tree, Upload} from 'antd';
import {
    ApiOutlined,
    DeleteOutlined,
    DownloadOutlined,
    EditOutlined,
    FolderAddOutlined,
    FolderOutlined,
    PlusOutlined,
    UploadOutlined
} from '@ant-design/icons';
import type {TreeProps} from 'antd/es/tree';
import {DataNode} from 'antd/es/tree';
import {ApiItem, FolderItem} from '../types';
import {v4 as uuidv4} from 'uuid';
import {useTranslation} from 'react-i18next';

interface ApiListProps {
  apiList: (ApiItem | FolderItem)[];
  onSelect: (api: ApiItem) => void;
  onImport: (apiList: (ApiItem | FolderItem)[]) => void;
}

export interface ApiListRef {
  handleEdit: (item: ApiItem | FolderItem) => void;
}

const ApiList = forwardRef<ApiListRef, ApiListProps>((props, ref) => {
  const { t } = useTranslation();
  const [editModalVisible, setEditModalVisible] = useState(false);
  const [editingItem, setEditingItem] = useState<ApiItem | FolderItem | null>(null);
  const [form] = Form.useForm();
  const [formType, setFormType] = useState<'api' | 'folder'>('api');
  const [width, setWidth] = useState(300);
  const isResizing = useRef(false);
  const startX = useRef(0);
  const startWidth = useRef(0);

  const handleEdit = (item: ApiItem | FolderItem) => {
    setEditingItem(item);
    setFormType(item.type);
    form.setFieldsValue(item);
    setEditModalVisible(true);
  };

  useImperativeHandle(ref, () => ({
    handleEdit
  }));

  const handleDelete = (item: ApiItem | FolderItem, e: React.MouseEvent) => {
    e.stopPropagation();

    Modal.confirm({
      title: t(item.type === 'api' ? 'weapi.delete_api_title' : 'weapi.delete_folder_title'),
      content: t(item.type === 'folder' ? 'weapi.delete_folder_confirm' : 'weapi.delete_api_confirm', { name: item.name }),
      okText: t('common.delete'),
      okType: 'danger',
      cancelText: t('common.cancel'),
      onOk: () => {
        const removeItem = (list: (ApiItem | FolderItem)[]): (ApiItem | FolderItem)[] => {
          return list.filter(listItem => {
            if (listItem.id === item.id) return false;
            if (listItem.type === 'folder') {
              listItem.children = removeItem(listItem.children);
            }
            return true;
          });
        };

        const newList = removeItem(props.apiList);
        props.onImport(newList);
      },
    });
  };

  const getMethodColor = (method: string): string => {
    const colors: Record<string, string> = {
      GET: '#61affe',    // 蓝色
      POST: '#49cc90',   // 绿色
      PUT: '#fca130',    // 橙色
      DELETE: '#f93e3e', // 红色
      PATCH: '#50e3c2',  // 青色
    };
    return colors[method] || '#999';
  };

  const truncateString = (str: string, maxLength: number = 30): string => {
    if (str.length <= maxLength) return str;
    return str.slice(0, maxLength) + '...';
  };

  const convertToTreeData = (list: (ApiItem | FolderItem)[]): DataNode[] => {
    return list.map(item => ({
      key: item.id,
      title: (
        <div style={{ 
          display: 'flex', 
          alignItems: 'center', 
          justifyContent: 'space-between', 
          width: '100%',
          padding: '4px 0'
        }}>
          <div style={{ 
            display: 'flex', 
            alignItems: 'center', 
            gap: '8px',
            flex: 1,
            overflow: 'hidden'
          }}>
            {item.type === 'folder' ? (
              <FolderOutlined style={{ fontSize: '16px', color: '#666' }} />
            ) : (
              <ApiOutlined style={{ fontSize: '16px', color: '#666' }} />
            )}
            <Tooltip title={item.name}>
              <span style={{ 
                flex: 1,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap'
              }}>
                {truncateString(item.name)}
              </span>
            </Tooltip>
            {item.type === 'api' && (
              <span style={{ 
                fontSize: '11px',
                padding: '1px 6px',
                backgroundColor: getMethodColor(item.method),
                color: '#fff',
                borderRadius: '3px',
                fontWeight: '600',
                minWidth: '44px',
                textAlign: 'center',
                letterSpacing: '0.5px',
                flexShrink: 0
              }}>
                {item.method}
              </span>
            )}
          </div>
          <div 
            className="node-actions" 
            style={{ 
              opacity: 0,
              transition: 'opacity 0.2s',
              display: 'flex',
              gap: '4px',
              marginLeft: '8px',
              flexShrink: 0
            }}
          >
            <Button
              type="text"
              size="small"
              icon={<EditOutlined style={{ fontSize: '14px' }} />}
              onClick={(e) => {
                e.stopPropagation();
                handleEdit(item);
              }}
            />
            <Button
              type="text"
              size="small"
              danger
              icon={<DeleteOutlined style={{ fontSize: '14px' }} />}
              onClick={(e) => handleDelete(item, e)}
            />
          </div>
        </div>
      ),
      children: item.type === 'folder' ? convertToTreeData(item.children) : undefined,
      isLeaf: item.type === 'api'
    }));
  };

  const handleSelect = (selectedKeys: React.Key[], info: any) => {
    const findItem = (list: (ApiItem | FolderItem)[], id: string): ApiItem | FolderItem | null => {
      for (const item of list) {
        if (item.id === id) return item;
        if (item.type === 'folder') {
          const found = findItem(item.children, id);
          if (found) return found;
        }
      }
      return null;
    };

    const selectedItem = findItem(props.apiList, selectedKeys[0] as string);
    if (selectedItem?.type === 'api') {
      props.onSelect(selectedItem);
    }
  };

  const handleAddApi = () => {
    const newApi: ApiItem = {
      id: uuidv4(),
      type: 'api',
      name: t('weapi.new_api'),
      method: 'GET',
      url: '',
      headers: [],
      query: [],
      cookies: [],
      pathParams: [],
      bodyType: 'none',
      body: {}
    };
    setEditingItem(null);
    setFormType('api');
    form.setFieldsValue({
      ...newApi,
      type: 'api'
    });
    setEditModalVisible(true);
  };

  const handleAddFolder = () => {
    const newFolder: FolderItem = {
      id: uuidv4(),
      type: 'folder',
      name: t('weapi.new_folder'),
      children: []
    };
    setEditingItem(null);
    setFormType('folder');
    form.setFieldsValue({
      ...newFolder,
      type: 'folder'
    });
    setEditModalVisible(true);
  };

  const handleModalOk = async () => {
    try {
      const values = await form.validateFields();
      let updatedItem;
      
      if (!editingItem) {
        const type = form.getFieldValue('type');
        if (type === 'api') {
          updatedItem = {
            id: uuidv4(),
            type: 'api',
            name: values.name,
            method: values.method || 'GET',
            url: values.url || '',
            headers: [],
            query: [],
            cookies: [],
            pathParams: [],
            bodyType: 'none',
            body: {}
          } as ApiItem;
        } else {
          updatedItem = {
            id: uuidv4(),
            type: 'folder',
            name: values.name,
            children: []
          } as FolderItem;
        }
      } else {
        updatedItem = { ...editingItem, ...values };
      }
      
      const updateList = (list: (ApiItem | FolderItem)[]): (ApiItem | FolderItem)[] => {
        if (!editingItem) {
          return [...list, updatedItem];
        }
        
        return list.map(item => {
          if (item.id === editingItem.id) {
            return updatedItem;
          }
          if (item.type === 'folder') {
            return { ...item, children: updateList(item.children) };
          }
          return item;
        });
      };

      const newList = updateList(props.apiList);
      props.onImport(newList);

      if (updatedItem.type === 'api') {
        props.onSelect(updatedItem as ApiItem);
      }

      setEditModalVisible(false);
      setEditingItem(null);
      setFormType('api');
      form.resetFields();
    } catch (error) {
      console.error('Validation failed:', error);
    }
  };

  const handleExport = () => {
    const dataStr = JSON.stringify(props.apiList, null, 2);
    const blob = new Blob([dataStr], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'api-collection.json';
    a.click();
    URL.revokeObjectURL(url);
  };

  const handleImport = (file: File) => {
    const reader = new FileReader();
    reader.onload = (e) => {
      try {
        const content = JSON.parse(e.target?.result as string);
        props.onImport(content);
        message.success('API collection imported successfully');
      } catch (error) {
        message.error('Failed to import API collection');
      }
    };
    reader.readAsText(file);
    return false;
  };

  const onDrop: TreeProps['onDrop'] = (info) => {
    const dropKey = info.node.key as string;
    const dragKey = info.dragNode.key as string;
    const dropPos = info.node.pos.split('-');
    const dropPosition = info.dropPosition - Number(dropPos[dropPos.length - 1]);

    const findAndRemoveItem = (items: (ApiItem | FolderItem)[], key: string): [ApiItem | FolderItem | null, (ApiItem | FolderItem)[]] => {
      let removedItem = null;
      const newItems = items.filter(item => {
        if (item.id === key) {
          removedItem = item;
          return false;
        }
        if (item.type === 'folder') {
          const [found, newChildren] = findAndRemoveItem(item.children, key);
          if (found) {
            removedItem = found;
            item.children = newChildren;
          }
        }
        return true;
      });
      return [removedItem, newItems];
    };

    const loop = (
      data: (ApiItem | FolderItem)[],
      key: string,
      callback: (item: ApiItem | FolderItem, index: number, arr: (ApiItem | FolderItem)[]) => void
    ) => {
      for (let i = 0; i < data.length; i++) {
        if (data[i].id === key) {
          callback(data[i], i, data);
          return;
        }
        if (data[i].type === 'folder') {
          const folderItem = data[i] as FolderItem;
          loop(folderItem.children, key, callback);
        }
      }
    };

    const [dragItem, dataWithoutDragItem] = findAndRemoveItem([...props.apiList], dragKey);
    if (!dragItem) return;

    if (dropPosition === 0) {
      loop(dataWithoutDragItem, dropKey, (item) => {
        if (item.type === 'folder') {
          item.children = item.children || [];
          item.children.unshift(dragItem);
        }
      });
    } else {
      let ar: (ApiItem | FolderItem)[] = [];
      let i: number;
      loop(dataWithoutDragItem, dropKey, (_item, index, arr) => {
        ar = arr;
        i = index;
      });
      if (dropPosition === -1) {
        ar.splice(i, 0, dragItem);
      } else {
        ar.splice(i + 1, 0, dragItem);
      }
    }

    props.onImport(dataWithoutDragItem);
  };

  const handleMouseDown = useCallback((e: React.MouseEvent) => {
    isResizing.current = true;
    startX.current = e.clientX;
    startWidth.current = width;
    document.addEventListener('mousemove', handleMouseMove);
    document.addEventListener('mouseup', handleMouseUp);
    document.body.style.userSelect = 'none';
  }, [width]);

  const handleMouseMove = useCallback((e: MouseEvent) => {
    if (!isResizing.current) return;
    const diff = e.clientX - startX.current;
    const newWidth = Math.max(250, Math.min(800, startWidth.current + diff));
    setWidth(newWidth);
  }, []);

  const handleMouseUp = useCallback(() => {
    isResizing.current = false;
    document.removeEventListener('mousemove', handleMouseMove);
    document.removeEventListener('mouseup', handleMouseUp);
    document.body.style.userSelect = '';
  }, []);

  return (
    <div 
      className="h-full flex flex-col border-r border-gray-200 dark:bg-[#131315] relative" 
      style={{ width: `${width}px` }}
    >
      <div className="p-4 border-b border-gray-200 dark:border-[#1a1a1c]">
        <div className="flex gap-2 flex-wrap">
          <Button icon={<PlusOutlined />} onClick={handleAddApi}>
            {t('weapi.add_api')}
          </Button>
          <Button icon={<FolderAddOutlined />} onClick={handleAddFolder}>
            {t('weapi.add_folder')}
          </Button>
          <Tooltip title={t('weapi.export')}>
            <Button icon={<DownloadOutlined />} onClick={handleExport} />
          </Tooltip>
          <Upload
            showUploadList={false}
            beforeUpload={handleImport}
            accept=".json"
          >
            <Tooltip title={t('weapi.import')}>
              <Button icon={<UploadOutlined />} />
            </Tooltip>
          </Upload>
        </div>
      </div>
      
      <div className="flex-1 overflow-auto p-4 text-sm">
        <Tree
          className="draggable-tree [&_.ant-tree-node-content-wrapper]:w-full [&_.ant-tree-node-content-wrapper:hover_.node-actions]:opacity-100 [&_.node-actions_.ant-btn]:p-1 [&_.node-actions_.ant-btn:hover]:bg-gray-100/50"
          draggable
          blockNode
          treeData={convertToTreeData(props.apiList)}
          onSelect={handleSelect}
          onDrop={onDrop}
          defaultExpandAll
        />
      </div>

      <Modal
        title={editingItem ? t('weapi.edit_item') : t('weapi.add_item')}
        open={editModalVisible}
        onOk={handleModalOk}
        onCancel={() => {
          setEditModalVisible(false);
          setFormType('api');
          form.resetFields();
        }}
        okText={t('common.confirm')}
        cancelText={t('common.cancel')}
      >
        <Form
          form={form}
          layout="vertical"
          onValuesChange={(changedValues) => {
            if (changedValues.type) {
              setFormType(changedValues.type);
            }
          }}
        >
          <Form.Item
            name="type"
            label={t('weapi.item_type')}
          >
            <Select disabled={!!editingItem}>
              <Select.Option value="api">{t('weapi.type_api')}</Select.Option>
              <Select.Option value="folder">{t('weapi.type_folder')}</Select.Option>
            </Select>
          </Form.Item>

          <Form.Item
            name="name"
            label={t('weapi.item_name')}
            rules={[{ required: true, message: t('weapi.name_required') }]}
          >
            <Input />
          </Form.Item>
          
          {formType === 'api' && (
            <>
              <Form.Item
                name="method"
                label={t('weapi.method')}
              >
                <Select>
                  <Select.Option value="GET">GET</Select.Option>
                  <Select.Option value="POST">POST</Select.Option>
                  <Select.Option value="PUT">PUT</Select.Option>
                  <Select.Option value="DELETE">DELETE</Select.Option>
                  <Select.Option value="PATCH">PATCH</Select.Option>
                  <Select.Option value="HEAD">HEAD</Select.Option>
                  <Select.Option value="OPTIONS">OPTIONS</Select.Option>
                </Select>
              </Form.Item>
              
              <Form.Item
                name="url"
                label={t('weapi.url')}
                rules={[{ required: true, message: t('weapi.url_required') }]}
              >
                <Input />
              </Form.Item>
            </>
          )}
        </Form>
      </Modal>

      <div
        className="absolute top-0 -right-1 w-2.5 h-full cursor-col-resize z-10 dark:bg-[#1a1a1c] hover:bg-gray-100/50 active:bg-gray-200/50 dark:hover:bg-gray-800/50 dark:active:bg-gray-700/50"
        onMouseDown={handleMouseDown}
      />
    </div>
  );
});

ApiList.displayName = 'ApiList';

export default ApiList; 