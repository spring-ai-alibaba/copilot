export interface ApiItem {
  id: string;
  type: 'api';
  name: string;
  method: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH' | 'HEAD' | 'OPTIONS';
  url: string;
  pathParams: PathParam[];
  headers: KeyValuePair[];
  query: KeyValuePair[];
  cookies: KeyValuePair[];
  bodyType?: BodyType;
  body?: RequestBody;
}

export interface FolderItem {
  id: string;
  type: 'folder';
  name: string;
  children: (ApiItem | FolderItem)[];
}

export interface KeyValuePair {
  key: string;
  value: string;
}

export type BodyType = 'none' | 'formData' | 'urlencoded' | 'raw' | 'json' | 'binary';

export interface RequestBody {
  none?: string;
  formData?: FormDataItem[];
  urlencoded?: KeyValuePair[];
  raw?: string;
  json?: any;
  binary?: File | null;
}

export interface FormDataItem {
  key: string;
  type?: 'text' | 'file';
  fileName?: string;
  value: string | File;
}

export interface ApiResponse {
  status: number;
  statusText: string;
  headers: Record<string, string>;
  data: any;
}

export interface ResponseData {
  type: 'json' | 'text' | 'image' | 'pdf' | 'binary';
  data?: any;
  size?: number;
}

export type ApiCollection = {
  id: string;
  name: string;
  type: 'folder';
  children: (ApiItem | FolderItem)[];
}

export interface PathParam {
  key: string;
  value: string;
  placeholder: string;
} 