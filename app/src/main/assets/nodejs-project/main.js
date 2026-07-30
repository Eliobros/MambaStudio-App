/**
 * MambaStudio Bridge - Node.js side
 *
 * Starts an HTTP server on a random localhost port.
 * Writes the port number to a file so the Android app can discover it.
 *
 * Handles:
 *   POST /executar   - Execute a .ms file
 *   POST /comando    - Run mambas CLI commands (instalar, listar, remover)
 *   POST /verificar  - Check if Node.js bridge is ready
 *   GET  /sair       - Graceful shutdown
 *
 * NOTE: All dependencies are BUILT-IN Node.js modules.
 * No npm packages needed! (zlib instead of 'tar')
 */

const http = require('http');
const https = require('https');
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const os = require('os');

// ======================== SETUP ========================

const filesDir = process.argv[1];
const PORT_FILE = path.join(filesDir, 'node_port.txt');
const REGISTRY_URL = 'https://habibo-mambascript-registry.mozhost.shop';

// ======================== MAMBA ENGINE ========================

const Lexer = require('./Lexer/lexer');
const Parser = require('./Parser/parser');
const Evaluator = require('./Evaluator/evaluate');
const { remover, listar, procurar } = require('./Instalador/instalador');

// ======================== TAR EXTRACTION (built-in, no npm!) ========================

/**
 * Extrai um ficheiro .tgz usando apenas módulos nativos do Node.js (zlib).
 * Tar format: 512-byte headers + data blocks, ended by two zero blocks.
 *
 * Suporta:
 * - Nomes curtos (<100 chars) via campo name
 * - Nomes longos (100-255 chars) via campo prefix (GNU tar, offset 345)
 * - PAX extended headers (tipo 'x') para compatibilidade com npm package 'tar'
 * - Proteção contra path traversal
 */
function extractTgz(tgzPath, outputDir) {
    const compressed = fs.readFileSync(tgzPath);
    const tarData = zlib.gunzipSync(compressed);
    const resolvedOutput = path.resolve(outputDir);

    let offset = 0;
    let paxPath = null; // Guarda caminho real de PAX headers

    while (offset + 512 <= tarData.length) {
        const header = tarData.subarray(offset, offset + 512);

        // End of archive: two zero blocks
        if (header.every(b => b === 0)) break;

        const name = parseTarString(header, 0, 100);
        const sizeStr = parseTarString(header, 124, 12);
        const typeFlag = header[156];

        if (!name || sizeStr === '') break;

        const size = parseInt(sizeStr, 8);
        if (isNaN(size)) break;

        offset += 512;

        // ====== PAX extended header (tipo 'x') ======
        // Guarda o caminho real do próximo ficheiro
        if (typeFlag === 120) { // 'x' in ASCII
            const paxData = tarData.subarray(offset, offset + size).toString('utf-8');
            for (const line of paxData.split('\n')) {
                const match = line.match(/^\d+ path=(.+)$/);
                if (match) paxPath = match[1];
            }
            offset += Math.ceil(size / 512) * 512;
            continue;
        }

        // ====== Regular file (tipo '0' ou null/0) ======
        if (typeFlag === 48 || typeFlag === 0) {
            // Determinar caminho real (prioridade: PAX > prefix+name > name)
            let filePath;
            if (paxPath) {
                filePath = path.join(resolvedOutput, paxPath);
                paxPath = null; // Reset após usar
            } else {
                const prefix = parseTarString(header, 345, 155);
                const fullName = prefix ? prefix + '/' + name : name;
                filePath = path.join(resolvedOutput, fullName);
            }

            // SECURITY: Proteção contra path traversal
            const resolvedFilePath = path.resolve(filePath);
            if (!resolvedFilePath.startsWith(resolvedOutput + path.sep)) {
                throw new Error('Path traversal detectado no pacote: ' + name);
            }

            // Criar diretório e escrever ficheiro
            const fileDir = path.dirname(resolvedFilePath);
            fs.mkdirSync(fileDir, { recursive: true });

            const fileData = tarData.subarray(offset, offset + size);
            fs.writeFileSync(resolvedFilePath, fileData);
        } else {
            // PAX 'x' já foi tratado acima. Outros tipos (link, dir, etc.) ignoramos.
            paxPath = null;
        }

        // Avançar para o próximo bloco (padding 512)
        offset += Math.ceil(size / 512) * 512;
    }
}

function parseTarString(buffer, start, length) {
    const end = start + length;
    let str = '';
    for (let i = start; i < end && buffer[i] !== 0; i++) {
        str += String.fromCharCode(buffer[i]);
    }
    return str;
}

// ======================== HTTP HELPERS ========================

function httpGet(url, depth = 0) {
    if (depth > 10) throw new Error('Demasiados redirecionamentos');

    return new Promise((resolve, reject) => {
        const client = url.startsWith('https') ? https : http;
        client.get(url, { headers: { 'User-Agent': 'MambaStudio' } }, (res) => {
            if (res.statusCode === 301 || res.statusCode === 302) {
                resolve(httpGet(res.headers.location, depth + 1));
                return;
            }
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                if (res.statusCode === 200) resolve(data);
                else reject(new Error('HTTP ' + res.statusCode));
            });
        }).on('error', reject);
    });
}

function downloadFile(url, destPath, depth = 0) {
    if (depth > 10) throw new Error('Demasiados redirecionamentos');

    return new Promise((resolve, reject) => {
        const client = url.startsWith('https') ? https : http;
        client.get(url, { headers: { 'User-Agent': 'MambaStudio' } }, (res) => {
            if (res.statusCode === 301 || res.statusCode === 302) {
                resolve(downloadFile(res.headers.location, destPath, depth + 1));
                return;
            }
            if (res.statusCode !== 200) {
                reject(new Error('HTTP ' + res.statusCode));
                return;
            }
            const file = fs.createWriteStream(destPath);
            res.pipe(file);
            file.on('finish', () => { file.close(); resolve(); });
            file.on('error', reject);
        }).on('error', reject);
    });
}

// ======================== PACKAGE INSTALL (no npm deps!) ========================

function getModulosDir(workingDir) {
    return path.join(workingDir, 'modulos_mambas');
}

function getRegistroPath(modulosDir) {
    return path.join(modulosDir, '.registro.json');
}

function carregarRegistro(modulosDir) {
    const rPath = getRegistroPath(modulosDir);
    if (fs.existsSync(rPath)) {
        try { return JSON.parse(fs.readFileSync(rPath, 'utf-8')); } catch {}
    }
    return {};
}

function salvarRegistro(modulosDir, registro) {
    fs.writeFileSync(getRegistroPath(modulosDir), JSON.stringify(registro, null, 2));
}

async function instalarPacote(nomePacote, workingDir) {
    const [nome, versaoEspecifica] = nomePacote.split('@');

    // 1. Buscar metadados no registry
    const lista = JSON.parse(await httpGet(REGISTRY_URL + '/pacotes'));
    const meta = lista.find(p => p.nome === nome);
    if (!meta) throw new Error('Pacote "' + nome + '" não encontrado no registry.');

    const versaoFinal = versaoEspecifica || meta.versao;

    // 2. Download do .tgz
    const tmpPath = path.join(os.tmpdir(), nome + '.tgz');
    const urlDownload = versaoEspecifica
        ? REGISTRY_URL + '/pacotes/' + nome + '/download?versao=' + versaoFinal
        : REGISTRY_URL + '/pacotes/' + nome + '/download';

    console.log('📥 A descarregar ' + nome + '@' + versaoFinal + '...');
    await downloadFile(urlDownload, tmpPath);

    // 3. Extrair usando zlib nativo do Node.js (sem npm 'tar'!)
    const modulosDir = getModulosDir(workingDir);
    fs.mkdirSync(modulosDir, { recursive: true });
    const destino = path.join(modulosDir, nome);
    if (fs.existsSync(destino)) {
        fs.rmSync(destino, { recursive: true, force: true });
    }
    fs.mkdirSync(destino, { recursive: true });

    console.log('📦 A extrair ' + nome + '@' + versaoFinal + '...');
    extractTgz(tmpPath, destino);

    // Cleanup temp
    try { fs.unlinkSync(tmpPath); } catch {}

    // 4. Atualizar registro local
    const registro = carregarRegistro(modulosDir);
    registro[nome] = {
        versao: versaoFinal,
        descricao: meta.descricao || '',
        instaladoEm: new Date().toISOString()
    };
    salvarRegistro(modulosDir, registro);

    return nome + '@' + versaoFinal + ' instalado com sucesso!';
}

// ======================== EXECUTAR CÓDIGO ========================

async function executarScript(scriptPath, workingDir) {
    const originalLog = console.log;
    let output = '';

    console.log = (...args) => {
        const line = args.map(a => typeof a === 'object' ? JSON.stringify(a, null, 2) : String(a)).join(' ');
        output += line + '\n';
    };

    try {
        const code = fs.readFileSync(scriptPath, 'utf-8');
        const lexer = new Lexer(code);
        const tokens = lexer.tokenize();
        const parser = new Parser(tokens);
        const ast = parser.parse();
        const evaluator = new Evaluator(scriptPath);
        await evaluator.execute(ast);

        return { success: true, output: output.trimEnd() };
    } catch (error) {
        return { success: false, error: error.message || 'Erro desconhecido', output: output.trimEnd() };
    } finally {
        console.log = originalLog;
    }
}

/**
 * Executa um comando da CLI mambas (instalar, listar, remover)
 * Usa APENAS módulos nativos do Node.js.
 */
async function executarComando(args, workingDir) {
    const originalLog = console.log;
    let output = '';

    console.log = (...args) => {
        output += args.map(a => String(a)).join(' ') + '\n';
    };
    console.error = (...args) => {
        output += args.map(a => String(a)).join(' ') + '\n';
    };

    try {
        const comando = args[0];
        const wd = workingDir || filesDir;

        if (comando === 'instalar' && args[1]) {
            const msg = await instalarPacote(args[1], wd);
            console.log(msg);
        } else if (comando === 'remover' && args[1]) {
            await remover(args[1]);
        } else if (comando === 'listar') {
            await listar();
        } else if (comando === 'procurar' && args[1]) {
            await procurar(args[1]);
        } else if (comando === 'procurar') {
            await procurar();
        } else if (comando === 'init') {
            const { init } = require('./Instalador/instalador');
            init();
        } else {
            throw new Error('Comando desconhecido: ' + comando);
        }

        return { success: true, output: output.trimEnd() };
    } catch (error) {
        return { success: false, error: error.message || 'Erro desconhecido', output: output.trimEnd() };
    } finally {
        console.log = originalLog;
    }
}

// ======================== HTTP SERVER ========================

const server = http.createServer(async (req, res) => {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
    res.setHeader('Content-Type', 'application/json; charset=utf-8');

    if (req.method === 'OPTIONS') {
        res.writeHead(204);
        res.end();
        return;
    }

    const sendJson = (status, data) => {
        res.writeHead(status);
        res.end(JSON.stringify(data));
    };

    try {
        if (req.method === 'POST' && req.url === '/verificar') {
            sendJson(200, { pronto: true, nodeVersion: process.version });
            return;
        }

        if (req.method === 'POST' && req.url === '/executar') {
            let body = '';
            req.on('data', chunk => body += chunk);
            req.on('end', async () => {
                try {
                    const { scriptPath, workingDir } = JSON.parse(body);
                    if (!scriptPath || !fs.existsSync(scriptPath)) {
                        sendJson(400, { success: false, error: 'Ficheiro não encontrado: ' + scriptPath });
                        return;
                    }
                    const result = await executarScript(scriptPath, workingDir || path.dirname(scriptPath));
                    sendJson(result.success ? 200 : 400, result);
                } catch (e) {
                    sendJson(400, { success: false, error: 'JSON inválido: ' + e.message });
                }
            });
            return;
        }

        if (req.method === 'POST' && req.url === '/comando') {
            let body = '';
            req.on('data', chunk => body += chunk);
            req.on('end', async () => {
                try {
                    const { args, workingDir } = JSON.parse(body);
                    if (!args || !Array.isArray(args) || args.length === 0) {
                        sendJson(400, { success: false, error: 'Argumentos inválidos' });
                        return;
                    }
                    const result = await executarComando(args, workingDir || filesDir);
                    sendJson(result.success ? 200 : 400, result);
                } catch (e) {
                    sendJson(400, { success: false, error: 'JSON inválido: ' + e.message });
                }
            });
            return;
        }

        if (req.method === 'GET' && req.url === '/sair') {
            sendJson(200, { mensagem: 'A encerrar...' });
            try { fs.unlinkSync(PORT_FILE); } catch(e) {}
            server.close(() => process.exit(0));
            return;
        }

        sendJson(404, { success: false, error: 'Rota não encontrada: ' + req.method + ' ' + req.url });

    } catch (e) {
        sendJson(500, { success: false, error: 'Erro interno: ' + e.message });
    }
});

// ======================== START ========================

server.listen(0, '127.0.0.1', () => {
    const port = server.address().port;
    fs.writeFileSync(PORT_FILE, String(port));
    console.log('[MambaStudio Bridge] Servidor HTTP pronto na porta ' + port);
});

process.on('SIGTERM', () => {
    try { fs.unlinkSync(PORT_FILE); } catch(e) {}
    server.close(() => process.exit(0));
});
