import 'axios'

declare module 'axios' {
  export interface AxiosRequestConfig<D = any> {
    /** 可选展示数据失败时不弹出全局错误提示，由调用页面自行降级。 */
    silent?: boolean
  }

  export interface InternalAxiosRequestConfig<D = any> {
    silent?: boolean
  }
}
