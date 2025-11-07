export interface GeekResponse<T = any> {
  code: number | string;
  msg: string;
  data: T;
  total?: number;
  rows?: T[];
}

export interface GeekRequestConfig {
  url: string;
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  data?: any;
  params?: any;
  headers?: any;
}