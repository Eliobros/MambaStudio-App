const fs = require('fs');
const path = require('path');
const tar = require('tar');
const https = require('https');
const http = require('http');
const FormData = require('form-data');

const REGISTRY_URL = process.env.MAMBAS_REGISTRY || 'https://habibo-mambascript-registry.mozhost.shop';
const CONFIG_FILE = 'mamba.json';
const IGNORE_LIST = ['node_modules', '.git', 'modulos_mambas', '.env', '*.log', 'dist', 'build'];

// ======================== FUNÇÕES AUXILIARES ========================

function lerConfig() {
    const configPath = path.join(process.cwd(), CONFIG_FILE);
    if (!fs.existsSync(configPath)) {
        console.error(`❌ ${CONFIG_FILE} não encontrado!`);
        console.error(`💡 Use "mambas init" para criar um.`);
        process.exit(1);
    }

    try {
        const config = JSON.parse(fs.readFileSync(configPath, 'utf-8'));
        if (!config.nome || !config.versao) {
            console.error('❌ mamba.json deve conter "nome" e "versao".');
            process.exit(1);
        }
        return config;
    } catch (e) {
        console.error(`❌ ${CONFIG_FILE} inválido.`);
        process.exit(1);
    }
}

function deveIgnorar(filePath) {
    return IGNORE_LIST.some(ign => {
        if (ign.includes('*')) return filePath.endsWith(ign.replace('*', ''));
        return filePath.includes(ign);
    });
}

// Comparação simples de SemVer (major.minor.patch)
function compararVersao(v1, v2) {
    const v1Parts = v1.split('.').map(Number);
    const v2Parts = v2.split('.').map(Number);
    
    for (let i = 0; i < 3; i++) {
        if (v1Parts[i] > v2Parts[i]) return 1;   // v1 é maior
        if (v1Parts[i] < v2Parts[i]) return -1;  // v1 é menor
    }
    return 0; // iguais
}

async function checarPacoteExistente(nome, token) {
    try {
        const url = `${REGISTRY_URL}/pacotes/${nome}`;
        const client = url.startsWith('https') ? https : http;

        return new Promise((resolve) => {
            client.get(url, {
                headers: { 'Authorization': `Bearer ${token}` }
            }, (res) => {
                let data = '';
                res.on('data', chunk => data += chunk);
                res.on('end', () => {
                    if (res.statusCode === 200) {
                        try {
                            const pacote = JSON.parse(data);
                            resolve({ 
                                existe: true, 
                                versaoAtual: pacote.versaoAtual || pacote.versao,
                                autor: pacote.autor 
                            });
                        } catch {
                            resolve({ existe: false });
                        }
                    } else {
                        resolve({ existe: false });
                    }
                });
            }).on('error', () => resolve({ existe: false }));
        });
    } catch {
        return { existe: false };
    }
}

async function criarTgz(config) {
    const tmpDir = path.join(require('os').tmpdir(), 'mambas-publish');
    fs.mkdirSync(tmpDir, { recursive: true });

    const tgzPath = path.join(tmpDir, `${config.nome}-${config.versao}.tgz`);
    const ficheiros = [];

    function coletar(dir, base = '') {
        for (const item of fs.readdirSync(dir)) {
            const fullPath = path.join(dir, item);
            const relPath = base ? `${base}/${item}` : item;
            if (deveIgnorar(relPath)) continue;
            if (fs.statSync(fullPath).isDirectory()) {
                coletar(fullPath, relPath);
            } else {
                ficheiros.push(relPath);
            }
        }
    }

    coletar(process.cwd());

    console.log(`📦 Empacotando ${ficheiros.length} arquivos...`);

    await tar.create({ gzip: true, file: tgzPath, cwd: process.cwd() }, ficheiros);
    return tgzPath;
}

function obterToken() {
    const tokenPath = path.join(require('os').homedir(), '.mambas', 'token');
    if (!fs.existsSync(tokenPath)) {
        console.error('❌ Não autenticado. Use: mambas login <token>');
        process.exit(1);
    }
    return fs.readFileSync(tokenPath, 'utf-8').trim();
}

// ======================== PUBLICAR ========================
async function publicar() {
    const config = lerConfig();
    const token = obterToken();

    console.log(`🚀 Verificando ${config.nome}@${config.versao}...`);

    // 1. Verificar se pacote existe e validar versão
    const checagem = await checarPacoteExistente(config.nome, token);

    if (checagem.existe) {
        console.log(`📦 Pacote já existe (versão atual: ${checagem.versaoAtual})`);

        const comparacao = compararVersao(config.versao, checagem.versaoAtual);

        if (comparacao <= 0) {
            console.error(`❌ Versão inválida!`);
            console.error(`   Atual: ${checagem.versaoAtual} → Nova: ${config.versao}`);
            console.error(`💡 Use uma versão maior (ex: ${checagem.versaoAtual} → 1.1.0 ou 2.0.0)`);
            process.exit(1);
        }

        console.log(`✅ Versão ${config.versao} é maior que a atual. Prosseguindo...`);
    } else {
        console.log(`🆕 Primeira publicação do pacote "${config.nome}"`);
    }

    // 2. Criar TGZ
    const tgzPath = await criarTgz(config);

    console.log(`📤 Enviando para o registry...`);

    // 3. Enviar
    const form = new FormData();
    form.append('nome', config.nome);
    form.append('versao', config.versao);
    form.append('descricao', config.descricao || '');

    form.append('arquivo', fs.createReadStream(tgzPath), {
        filename: `${config.nome}-${config.versao}.tgz`,
        contentType: 'application/gzip'
    });

    const url = new URL(`${REGISTRY_URL}/pacotes/publicar`);
    const client = url.protocol === 'https:' ? https : http;

    const options = {
        hostname: url.hostname,
        port: url.port || (url.protocol === 'https:' ? 443 : 80),
        path: url.pathname,
        method: 'POST',
        headers: {
            ...form.getHeaders(),
            'Authorization': `Bearer ${token}`
        }
    };

    const req = client.request(options, (res) => {
        let data = '';
        res.on('data', chunk => data += chunk);
        res.on('end', () => {
            try {
                const result = JSON.parse(data);
                if (res.statusCode === 200 && result.sucesso) {
                    console.log(`\n✅ Publicado com sucesso!`);
                    console.log(`📦 ${config.nome}@${config.versao}`);
                    console.log(`🌐 ${REGISTRY_URL}/pacotes/${config.nome}`);
                } else {
                    console.error(`\n❌ ${result.erro || 'Erro desconhecido'}`);
                }
            } catch {
                console.error(`\n❌ Resposta inválida do servidor.`);
            }

            if (fs.existsSync(tgzPath)) fs.unlinkSync(tgzPath);
        });
    });

    req.on('error', (e) => {
        console.error(`❌ Erro: ${e.message}`);
        if (fs.existsSync(tgzPath)) fs.unlinkSync(tgzPath);
    });

    form.pipe(req);
}

// ======================== EXPORT ========================
module.exports = { 
    publicar, 
    salvarToken: (token) => {
        const dir = path.join(require('os').homedir(), '.mambas');
        fs.mkdirSync(dir, { recursive: true });
        fs.writeFileSync(path.join(dir, 'token'), token.trim());
        console.log('✅ Token salvo!');
    }
};
