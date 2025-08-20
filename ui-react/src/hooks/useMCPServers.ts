import {useMemo} from 'react';
import useMCPStore from '@/stores/useMCPSlice';

/**
 * Custom Hook for getting MCP server information
 * Extracts all servers and active servers from useMCPStore
 */
const useMCPServers = () => {
    // Get all servers from useMCPStore
    const allServers = useMCPStore(state => state.servers);

    // Use useMemo to filter active servers, avoiding unnecessary recalculations
    const activedMcpServers = useMemo(() => {
        return allServers.filter(server => server.isActive);
    }, [allServers]);

    return {
        mcpServers: allServers,
        activedMcpServers,
    };
};

export default useMCPServers; 