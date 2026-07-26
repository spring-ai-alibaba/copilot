declare module 'electron' {
  export interface ProxyConfig {
    mode?: 'direct' | 'auto_detect' | 'pac_script' | 'fixed_servers' | 'system'
    pacScript?: string
    proxyRules?: string
    proxyBypassRules?: string
  }

  export interface Session {
    setProxy(config: ProxyConfig): Promise<void>
    resolveProxy(url: string): Promise<string>
  }

  export const session: {
    readonly defaultSession: Session
    fromPartition(partition: string, options?: { cache?: boolean }): Session
  }
}
