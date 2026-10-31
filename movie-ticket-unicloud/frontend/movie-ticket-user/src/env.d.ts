/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_AMAP_KEY: string
  readonly VITE_AMAP_SECURITY_CODE: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module '@amap/amap-jsapi-loader' {
  interface AMapLoadOptions {
    key: string
    version?: string
    plugins?: string[]
    AMapUI?: { version?: string; plugins?: string[] }
    Loca?: { version?: string }
  }
  const AMapLoader: {
    load: (options: AMapLoadOptions) => Promise<any>
    reset: () => void
  }
  export default AMapLoader
}

interface Window {
  _AMapSecurityConfig?: { securityJsCode: string }
}
