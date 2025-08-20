import {useEffect} from 'react';
import {MCPServer} from '@/types/mcp';
import useMCPServers from './useMCPServers';
import useEnabledMCPsStore from '@/stores/enabledMCPsSlice';

const useMCPTools = () => {
    const {activedMcpServers} = useMCPServers();

    const {
        enabledMCPs,
        toggleMCP: toggleStoreMCP,
        enableAllMCPs: enableAllStoreMCPs,
        disableAllMCPs
    } = useEnabledMCPsStore();

    useEffect(() => {
        useEnabledMCPsStore.setState(state => ({
            enabledMCPs: state.enabledMCPs.filter(enabled =>
                activedMcpServers.some(actived => actived.name === enabled.name)
            )
        }));
    }, [activedMcpServers]);

    const toggleEnableMCP = (server: MCPServer) => {
        toggleStoreMCP(server);
    };

    const enableAllMCPs = () => {
        enableAllStoreMCPs(activedMcpServers);
    };

    const toggleAllMCPs = () => {
        if (enabledMCPs.length > 0) {
            disableAllMCPs();
        } else {
            enableAllMCPs();
        }
    };

    return {
        enabledMCPs,
        toggleEnableMCP,
        enableAllMCPs,
        disableAllMCPs,
        toggleAllMCPs,
        hasEnabledMCPs: enabledMCPs.length > 0
    };
};

export default useMCPTools; 