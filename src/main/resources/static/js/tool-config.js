/**
 * 动态工具配置管理
 */

// 工具配置管理器
class ToolConfigManager {
    constructor() {
        this.systemTools = [];
        this.mcpTools = [];
        this.mcpServers = [];
        this.init();
    }

    async init() {
        await this.loadAllTools();
        await this.loadMcpServers();
    }

    // 加载所有工具（系统工具 + MCP工具）
    async loadAllTools() {
        try {
            const response = await fetch('/api/tools/all');
            if (response.ok) {
                const allTools = await response.json();
                
                // 分类工具
                this.systemTools = allTools.filter(tool => tool.type === 'SYSTEM');
                this.mcpTools = allTools.filter(tool => tool.type === 'MCP');
                
                console.log('加载工具完成:', {
                    systemTools: this.systemTools.length,
                    mcpTools: this.mcpTools.length
                });
                
                this.renderSystemTools();
                this.renderMcpTools();
            }
        } catch (error) {
            console.error('加载工具列表失败:', error);
            showStatus('加载工具列表失败', 'error');
        }
    }

    // 加载MCP服务器列表
    async loadMcpServers() {
        try {
            const response = await fetch('/api/mcp/servers');
            if (response.ok) {
                this.mcpServers = await response.json();
                console.log('加载MCP服务器完成:', this.mcpServers.length);
            }
        } catch (error) {
            console.error('加载MCP服务器列表失败:', error);
        }
    }

    // 渲染系统工具
    renderSystemTools() {
        const container = document.getElementById('systemToolsList');
        if (!container) return;

        if (this.systemTools.length === 0) {
            container.innerHTML = '<div class="no-tools">暂无系统工具</div>';
            return;
        }

        container.innerHTML = this.systemTools.map(tool => `
            <div class="tool-item system-tool ${tool.enabled ? 'active' : ''}" data-tool-name="${tool.name}">
                <div class="tool-info">
                    <div class="tool-name">
                        <span class="tool-icon">🔧</span>
                        <span class="tool-title">${tool.displayName || tool.name}</span>
                        <span class="tool-badge system">系统内置</span>
                    </div>
                    <div class="tool-description">${tool.description || '无描述'}</div>
                    <div class="tool-source">来源: ${tool.source}</div>
                    <div class="tool-parameters">
                        参数: ${tool.parameters && tool.parameters.length > 0 
                            ? tool.parameters.map(p => `${p.name}(${p.type})`).join(', ')
                            : '无参数'}
                    </div>
                </div>
                <div class="tool-actions">
                    <button class="btn-test" onclick="testSystemTool('${tool.name}')">测试</button>
                    <button class="btn-info" onclick="showToolInfo('${tool.name}', 'system')">详情</button>
                </div>
            </div>
        `).join('');
    }

    // 渲染MCP工具
    renderMcpTools() {
        const container = document.getElementById('mcpToolsList');
        if (!container) return;

        if (this.mcpTools.length === 0) {
            container.innerHTML = '<div class="no-tools">暂无MCP工具<br><small>请先添加MCP服务器</small></div>';
            return;
        }

        container.innerHTML = this.mcpTools.map(tool => `
            <div class="tool-item mcp-tool ${tool.enabled ? 'active' : ''}" data-tool-name="${tool.name}">
                <div class="tool-info">
                    <div class="tool-name">
                        <span class="tool-icon">🌐</span>
                        <span class="tool-title">${tool.displayName || tool.name}</span>
                        <span class="tool-badge mcp">MCP</span>
                    </div>
                    <div class="tool-description">${tool.description || '无描述'}</div>
                    <div class="tool-source">服务器: ${tool.source}</div>
                </div>
                <div class="tool-actions">
                    <button class="btn-test" onclick="testMcpTool('${tool.name}')">测试</button>
                    <button class="btn-info" onclick="showToolInfo('${tool.name}', 'mcp')">详情</button>
                    <button class="btn-delete" onclick="removeMcpTool('${tool.name}')">删除</button>
                </div>
            </div>
        `).join('');
    }

    // 从JSON添加MCP服务器
    async addMcpFromJson(jsonConfig) {
        try {
            const config = JSON.parse(jsonConfig);
            
            if (!config.mcpServers) {
                throw new Error('JSON配置中缺少 mcpServers 节点');
            }
            
            // 遍历mcpServers配置
            for (const [serverName, serverConfig] of Object.entries(config.mcpServers)) {
                await this.addMcpServer(serverName, serverConfig);
            }
            
            showStatus('MCP服务器配置添加成功', 'success');
            await this.loadAllTools();
            await this.loadMcpServers();
            
        } catch (error) {
            console.error('添加MCP服务器失败:', error);
            showStatus('添加MCP服务器失败: ' + error.message, 'error');
        }
    }

    // 添加单个MCP服务器
    async addMcpServer(serverName, serverConfig) {
        const response = await fetch('/api/mcp/servers', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                name: serverName,
                config: serverConfig
            })
        });

        if (!response.ok) {
            const errorData = await response.json();
            throw new Error(errorData.error || '添加MCP服务器失败');
        }
        
        const result = await response.json();
        console.log('MCP服务器添加成功:', result);
    }

    // 测试工具
    async testTool(toolName, parameters) {
        try {
            const response = await fetch(`/api/tools/${toolName}/test`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(parameters)
            });

            const result = await response.json();
            
            if (response.ok) {
                showStatus('工具测试成功', 'success');
                this.showTestResult(result);
            } else {
                showStatus('工具测试失败: ' + (result.error || result.message), 'error');
            }
        } catch (error) {
            console.error('测试工具失败:', error);
            showStatus('测试工具失败: ' + error.message, 'error');
        }
    }

    // 显示测试结果
    showTestResult(result) {
        // 创建测试结果模态框
        const resultModal = document.createElement('div');
        resultModal.className = 'test-result-modal';
        resultModal.innerHTML = `
            <div class="test-result-content">
                <div class="test-result-header">
                    <h3>🧪 工具测试结果</h3>
                    <button onclick="this.parentElement.parentElement.parentElement.remove()">&times;</button>
                </div>
                <div class="test-result-body">
                    <pre>${JSON.stringify(result, null, 2)}</pre>
                </div>
            </div>
        `;
        
        document.body.appendChild(resultModal);
        
        // 3秒后自动移除
        setTimeout(() => {
            if (resultModal.parentElement) {
                resultModal.remove();
            }
        }, 5000);
    }

    // 获取工具统计信息
    async getToolStats() {
        try {
            const response = await fetch('/api/tools/stats');
            if (response.ok) {
                return await response.json();
            }
        } catch (error) {
            console.error('获取工具统计失败:', error);
        }
        return null;
    }
}

// 全局工具配置管理器实例
let toolConfigManager;

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', function() {
    // 延迟初始化，确保其他脚本已加载
    setTimeout(() => {
        toolConfigManager = new ToolConfigManager();
    }, 100);
});

// 打开工具配置模态框
function openToolConfig() {
    document.getElementById('toolConfigModal').style.display = 'block';
    if (toolConfigManager) {
        toolConfigManager.loadAllTools();
        toolConfigManager.loadMcpServers();
    }
}

// 关闭工具配置模态框
function closeToolConfig() {
    document.getElementById('toolConfigModal').style.display = 'none';
}

// 查看活跃工具
function viewActiveTools() {
    openToolConfig();
    // 切换到系统工具标签页
    switchTab('system-tools');
}

// 测试工具入口
function testTool() {
    openToolConfig();
    // 切换到系统工具标签页并滚动到列表
    switchTab('system-tools');
    setTimeout(() => {
        const toolsList = document.getElementById('systemToolsList');
        if (toolsList) {
            toolsList.scrollIntoView({ behavior: 'smooth' });
        }
    }, 100);
}

// 标签页切换
function switchTab(tabName) {
    // 隐藏所有标签页内容
    document.querySelectorAll('.tab-content').forEach(tab => {
        tab.classList.remove('active');
    });
    
    // 移除所有按钮的active状态
    document.querySelectorAll('.tab-btn').forEach(btn => {
        btn.classList.remove('active');
    });
    
    // 显示选中的标签页
    const targetTab = document.getElementById(tabName);
    const targetBtn = event ? event.target : document.querySelector(`[onclick="switchTab('${tabName}')"]`);
    
    if (targetTab) {
        targetTab.classList.add('active');
    }
    if (targetBtn) {
        targetBtn.classList.add('active');
    }
}

// 从JSON添加MCP服务器
function addMcpFromJson() {
    const jsonConfig = document.getElementById('mcpJsonConfig').value.trim();
    if (!jsonConfig) {
        showStatus('请输入JSON配置', 'error');
        return;
    }
    
    if (toolConfigManager) {
        toolConfigManager.addMcpFromJson(jsonConfig);
    }
}

// 测试系统工具
function testSystemTool(toolName) {
    if (!toolConfigManager) return;
    
    const tool = toolConfigManager.systemTools.find(t => t.name === toolName);
    if (!tool) {
        showStatus('工具不存在', 'error');
        return;
    }
    
    // 根据工具参数生成测试参数
    const testParams = {};
    if (tool.parameters && tool.parameters.length > 0) {
        tool.parameters.forEach(param => {
            testParams[param.name] = getDefaultValueForType(param.type);
        });
    }
    
    toolConfigManager.testTool(toolName, testParams);
}

// 测试MCP工具
function testMcpTool(toolName) {
    // 弹出参数输入对话框
    const params = prompt('请输入测试参数 (JSON格式):', '{}');
    if (params) {
        try {
            const parsedParams = JSON.parse(params);
            if (toolConfigManager) {
                toolConfigManager.testTool(toolName, parsedParams);
            }
        } catch (error) {
            showStatus('参数格式错误: ' + error.message, 'error');
        }
    }
}

// 显示工具详情
function showToolInfo(toolName, toolType) {
    if (!toolConfigManager) return;
    
    const tool = toolType === 'system' 
        ? toolConfigManager.systemTools.find(t => t.name === toolName)
        : toolConfigManager.mcpTools.find(t => t.name === toolName);
    
    if (tool) {
        const paramInfo = tool.parameters && tool.parameters.length > 0
            ? tool.parameters.map(p => `  - ${p.name} (${p.type}): ${p.description}`).join('\n')
            : '  无参数';
            
        alert(`工具详情:\n\n名称: ${tool.name}\n显示名: ${tool.displayName}\n描述: ${tool.description}\n类型: ${tool.type}\n来源: ${tool.source}\n状态: ${tool.enabled ? '启用' : '禁用'}\n\n参数:\n${paramInfo}`);
    }
}

// 获取类型的默认值
function getDefaultValueForType(type) {
    switch (type) {
        case 'string': return 'test';
        case 'number': return 123;
        case 'boolean': return true;
        case 'object': return {};
        case 'array': return [];
        default: return null;
    }
}

// 表单提交处理
function setupFormHandlers() {
    const mcpServerForm = document.getElementById('mcpServerForm');
    if (mcpServerForm) {
        mcpServerForm.addEventListener('submit', function(e) {
            e.preventDefault();

            const serverName = document.getElementById('mcpServerName').value.trim();
            const command = document.getElementById('mcpCommand').value.trim();
            const argsText = document.getElementById('mcpArgs').value.trim();
            const workDir = document.getElementById('mcpWorkDir').value.trim();

            if (!serverName || !command) {
                showStatus('请填写服务器名称和命令', 'error');
                return;
            }

            // 解析参数
            const args = argsText ? argsText.split('\n').map(arg => arg.trim()).filter(arg => arg) : [];

            const serverConfig = {
                command: command,
                args: args
            };

            if (workDir) {
                serverConfig.workingDirectory = workDir;
            }

            // 添加MCP服务器
            if (toolConfigManager) {
                toolConfigManager.addMcpServer(serverName, serverConfig)
                    .then(() => {
                        // 清空表单
                        mcpServerForm.reset();
                        showStatus('MCP服务器添加成功', 'success');
                        // 刷新工具列表
                        toolConfigManager.loadAllTools();
                        toolConfigManager.loadMcpServers();
                    })
                    .catch(error => {
                        showStatus('添加MCP服务器失败: ' + error.message, 'error');
                    });
            }
        });
    }
}

// 移除MCP工具
function removeMcpTool(toolName) {
    if (confirm(`确定要删除工具 "${toolName}" 吗？`)) {
        // TODO: 实现MCP工具删除逻辑
        showStatus('MCP工具删除功能待实现', 'info');
    }
}

// 点击模态框外部关闭
window.addEventListener('click', function(event) {
    const modal = document.getElementById('toolConfigModal');
    if (event.target === modal) {
        closeToolConfig();
    }
});

// 页面加载完成后设置表单处理器
window.addEventListener('load', function() {
    setupFormHandlers();
});
