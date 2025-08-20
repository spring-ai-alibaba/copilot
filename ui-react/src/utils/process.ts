import {spawn} from 'child_process'
import fs from 'fs'
import os from 'os'
import path from 'path'

export function runInstallScript(scriptPath: string): Promise<void> {

    return new Promise<void>((resolve, reject) => {
        const installScriptPath = path.join('./', 'scripts', scriptPath)

        const env = {
            ...process.env,
            ELECTRON_RUN_AS_NODE: '1',
            all_proxy: process.env.all_proxy || process.env.ALL_PROXY || undefined,
            grpc_proxy: process.env.grpc_proxy || process.env.GRPC_PROXY || undefined,
            http_proxy: process.env.http_proxy || process.env.HTTP_PROXY || undefined,
            https_proxy: process.env.https_proxy || process.env.HTTPS_PROXY || undefined
        }

        const nodeProcess = spawn(process.execPath, [installScriptPath], {env})

        nodeProcess.stdout.on('data', (data) => {
            console.info(`Script output: ${data}`)
        })

        nodeProcess.stderr.on('data', (data) => {
            console.error(`Script error: ${data}`)
        })

        nodeProcess.on('close', (code) => {
            if (code === 0) {
                resolve()
            } else {
                reject(new Error(`Process exited with code ${code}`))
            }
        })
    })
}

export async function getBinaryPath(name: string): Promise<string> {
    let cmd = process.platform === 'win32' ? `${name}.exe` : name
    const binariesDir = path.join(os.homedir(), '.we0', 'bin')
    const binariesDirExists = await fs.existsSync(binariesDir)
    cmd = binariesDirExists ? path.join(binariesDir, cmd) : name
    return cmd
}

export async function isBinaryExists(name: string): Promise<boolean> {
    const cmd = await getBinaryPath(name)
    return await fs.existsSync(cmd)
}