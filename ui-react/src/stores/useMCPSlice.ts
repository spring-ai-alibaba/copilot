import {create} from 'zustand'
import {persist} from 'zustand/middleware'
import {MCPServer} from '@/types/mcp'

type ElectronWindow = Window & {
    electron?: {
        ipcRenderer: {
            on: (channel: string, listener: (...args: any[]) => void) => void
        }
    }
}

const normalizeServers = (maybeServers: unknown): MCPServer[] => {
    if (Array.isArray(maybeServers)) {
        return maybeServers.filter(Boolean) as MCPServer[]
    }

    if (maybeServers && typeof maybeServers === 'object') {
        return Object.entries(maybeServers as Record<string, Partial<MCPServer>>).map(
            ([name, data]) =>
                ({
                    ...(data ?? {}),
                    name: data?.name ?? name,
                    isActive: data?.isActive ?? false
                } as MCPServer)
        )
    }

    return []
}

export interface initialState {
    servers: MCPServer[]
}

interface MCPState extends initialState {
    // Add/Update/Delete servers
    addServer: (server: MCPServer) => Promise<void>
    updateServer: (server: MCPServer) => Promise<void>
    deleteServer: (name: string) => Promise<void>
    setServerActive: (name: string, isActive: boolean) => Promise<void>

    // Set server list
    setServers: (servers: MCPServer[]) => void

    // Get active server list
    getActiveServers: () => MCPServer[]
    getAllServers: () => MCPServer[]
}

const useMCPStore = create<MCPState>()(
    persist(
        (set, get) => ({
            servers: [],

            // Set all servers
            setServers: (servers: MCPServer[]) => {
                set({servers: normalizeServers(servers)})
            },

            // Add server
            addServer: async (server: MCPServer) => {
                try {
                    set((state) => ({
                        servers: normalizeServers([...state.servers, server])
                    }))
                } catch (error) {
                    console.error('Failed to add MCP server:', error)
                    throw error
                }
            },

            // Update server
            updateServer: async (server: MCPServer) => {
                try {
                    set((state) => ({
                        servers: normalizeServers(state.servers.map((s) =>
                            s.name === server.name ? server : s
                        ))
                    }))
                } catch (error) {
                    console.error('Failed to update MCP server:', error)
                    throw error
                }
            },

            // Delete server
            deleteServer: async (name: string) => {
                try {
                    set((state) => ({
                        servers: normalizeServers(state.servers.filter((s) => s.name !== name))
                    }))
                } catch (error) {
                    console.error('Failed to delete MCP server:', error)
                    throw error
                }
            },

            // Set server active status
            setServerActive: async (name: string, isActive: boolean) => {
                try {
                    set((state) => ({
                        servers: normalizeServers(state.servers.map((s) =>
                            s.name === name ? {...s, isActive} : s
                        ))
                    }))
                } catch (error) {
                    console.error('Failed to set MCP server active status:', error)
                    throw error
                }
            },

            // Get active servers
            getActiveServers: () => {
                return get().servers.filter((server) => server.isActive)
            },

            // Get all servers
            getAllServers: () => {
                return get().servers
            }
        }),
        {
            name: 'mcp-storage',
            partialize: (state) => ({servers: state.servers}),
            version: 2,
            migrate: (persistedState?: MCPState) => {
                if (!persistedState) {
                    return {servers: []}
                }
                return {
                    ...persistedState,
                    servers: normalizeServers(persistedState.servers)
                }
            }
        }
    )
)

// Listen for server change events from main process
if (typeof window !== 'undefined') {
    const electronWindow = window as ElectronWindow
    if (electronWindow.electron) {
        electronWindow.electron.ipcRenderer.on('mcp:servers-changed', (servers: MCPServer[]) => {
            useMCPStore.getState().setServers(servers)
        })
    }
}

export default useMCPStore