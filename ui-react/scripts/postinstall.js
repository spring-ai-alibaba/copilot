#!/usr/bin/env node

const { execSync } = require('child_process');
const os = require('os');

const isWindows = os.platform() === 'win32';
const skipRebuild = process.env.SKIP_ELECTRON_REBUILD === 'true';

function runCommand(command, description, allowFailure = false) {
  try {
    console.log(`\n${description}...`);
    // 在 Windows 上需要 shell，Linux/macOS 使用默认值
    const execOptions = isWindows 
      ? { stdio: 'inherit', shell: true }
      : { stdio: 'inherit' };
    execSync(command, execOptions);
    console.log(`✓ ${description} 完成`);
    return true;
  } catch (error) {
    if (allowFailure) {
      console.warn(`\n⚠ ${description} 失败，但将继续安装`);
      if (isWindows) {
        console.warn(`  原因: 缺少 Visual Studio Build Tools`);
        console.warn(`  解决方案:`);
        console.warn(`    1. 安装 Visual Studio Build Tools:`);
        console.warn(`       https://visualstudio.microsoft.com/zh-hans/downloads/`);
        console.warn(`       选择 "Desktop development with C++" 工作负载`);
        console.warn(`    2. 或者安装完成后手动运行: pnpm rebuild`);
        console.warn(`    3. 或者设置环境变量 SKIP_ELECTRON_REBUILD=true 跳过重建`);
      }
      return false;
    } else {
      console.error(`\n✗ ${description} 失败`);
      // 在 Linux/macOS 上，提供更详细的错误信息
      if (!isWindows && description.includes('electron-rebuild')) {
        console.error(`  在 Linux/macOS 上，请确保已安装构建工具:`);
        console.error(`  - Ubuntu/Debian: sudo apt-get install build-essential`);
        console.error(`  - CentOS/RHEL: sudo yum groupinstall "Development Tools"`);
        console.error(`  - macOS: xcode-select --install`);
      }
      throw error;
    }
  }
}

async function main() {
  try {
    // 检查是否跳过 rebuild
    if (skipRebuild) {
      console.log('\n⚠ 跳过 electron-rebuild (SKIP_ELECTRON_REBUILD=true)');
    } else {
      // 重建 node-pty 模块以匹配 Electron 版本
      // Windows: 如果失败（通常因为缺少 Visual Studio Build Tools），允许继续安装
      // Linux/macOS: 如果失败，会报错并停止安装（因为通常有构建工具，失败说明是真实问题）
      const rebuildSuccess = runCommand(
        'electron-rebuild -f -w node-pty --arch=x64',
        '重建 node-pty 模块',
        isWindows // 仅在 Windows 上允许失败
      );

      // 如果 rebuild 失败，给出提示（仅在 Windows 上）
      if (!rebuildSuccess && isWindows) {
        console.log('\n提示:');
        console.log('  - node-pty 模块可能需要编译后才能使用');
        console.log('  - 如果遇到运行时错误，请安装 Visual Studio Build Tools 后运行: pnpm rebuild');
      }
    }

    // 继续执行其他步骤
    runCommand('vite build', '构建 Vite 项目', false);
    runCommand('electron-builder install-app-deps', '安装 Electron 应用依赖', false);

    console.log('\n✓ 安装完成！');
  } catch (error) {
    console.error('\n✗ 安装过程中出现错误:', error.message);
    process.exit(1);
  }
}

main();

