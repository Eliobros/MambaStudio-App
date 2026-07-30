#!/usr/bin/env node

const fs = require('fs');
const path = require('path');
const https = require('https');
const http = require('http');
const tar = require('tar');
const os = require('os');

// ======================== CONFIGURAÇÕES ========================
const REGISTRY_URL = process.env.MAMBAS_REGISTRY || 'https://habibo-mambascript-registry.mozhost.shop';

const MODULOS_DIR = path.join(process.cwd(), 'modulos_mambas');
const REGISTRO_PATH = path.join(MODULOS_DIR, '.registro.json');

// ======================== UTILITÁRIOS ========================

function garantirPasta() {
    if (!fs.existsSync(MODULOS_DIR)) {
        fs.mkdirSync(MODULOS_DIR, { recursive: true });
    }
}

function carregarRegistro() {
    if (!fs.existsSync(REGISTRO_PATH)) return {};
    try {
        return JSON.parse(fs.readFileSync(REGISTRO_PATH, 'utf-8'));
    } catch {
        return {};
    }
}

function salvarRegistro(registro) {
    fs.writeFileSync(REGISTRO_PATH, JSON.stringify(registro, null, 2));
}

function carregarMambaJsonProjeto() {
    const caminho = path.join(process.cwd(), 'mamba.json');
    if (!fs.existsSync(caminho)) return null;
    try {
        return JSON.parse(fs.readFileSync(caminho, 'utf-8'));
    } catch {
        console.warn('⚠️  mamba.json existe mas está inválido.');
        return null;
    }
}

function salvarMambaJsonProjeto(meta) {
    const caminho = path.join(process.cwd(), 'mamba.json');
    fs.writeFileSync(caminho, JSON.stringify(meta, null, 2));
    console.log('📝 mamba.json do projeto atualizado automaticamente.');
}

// ======================== DOWNLOAD ========================

function get(url, token = null) {
    return new Promise((resolve, reject) => {
        const client = url.startsWith('https') ? https : http;
        const headers = { 'User-Agent': 'MambaScript-Instalador' };
        if (token) headers['Authorization'] = `Bearer ${token}`;

        client.get(url, { headers }, (res) => {
            if (res.statusCode === 301 || res.statusCode === 302) {
                return get(res.headers.location, token).then(resolve).catch(reject);
            }
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                if (res.statusCode === 200) resolve(data);
                else reject(new Error(`HTTP ${res.statusCode}`));
            });
        }).on('error', reject);
    });
}

function download(url, destino) {
    return new Promise((resolve, reject) => {
        const client = url.startsWith('https') ? https : http;
        client.get(url, { headers: { 'User-Agent': 'MambaScript-Instalador' } }, (res) => {
            if (res.statusCode === 301 || res.statusCode === 302) {
                return download(res.headers.location, destino).then(resolve).catch(reject);
            }
            if (res.statusCode !== 200) return reject(new Error(`HTTP ${res.statusCode}`));

            const file = fs.createWriteStream(destino);
            res.pipe(file);
            file.on('finish', () => { file.close(); resolve(); });
            file.on('error', reject);
        }).on('error', reject);
    });
}

// ======================== COMANDOS ========================

function init() {
    const caminho = path.join(process.cwd(), 'mamba.json');
    
    if (fs.existsSync(caminho)) {
        console.log('⚠️  mamba.json já existe neste projeto.');
        return;
    }

    const template = {
        "nome": path.basename(process.cwd()),
        "versao": "1.0.0",
        "descricao": "Meu projeto em MambaScript",
        "autor": "seu-usuario",
        "principal": "index.ms",
        "palavrasChave": [],
        "licenca": "MIT",
        "dependencias": {},
        "mamba": {
            "minVersion": "1.5.0",
            "tipo": "module"
        }
    };

    fs.writeFileSync(caminho, JSON.stringify(template, null, 2));
    console.log('✅ mamba.json criado com sucesso!');
    console.log('💡 Edite o arquivo conforme sua necessidade.');
}

async function instalar(nomePacote) {
    if (!nomePacote) {
        console.error('❌ Uso: mambas instalar <pacote>[@versao]');
        process.exit(1);
    }

    const [nome, versaoEspecifica] = nomePacote.split('@');
    console.log(`📦 Instalando ${nome}${versaoEspecifica ? `@${versaoEspecifica}` : ''}...`);

    garantirPasta();

    // Buscar metadados
    let meta;
    try {
        const lista = JSON.parse(await get(`${REGISTRY_URL}/pacotes`));
        meta = lista.find(p => p.nome === nome);
        
        if (!meta) {
            console.error(`❌ Pacote "${nome}" não encontrado.`);
            console.error(`💡 Use "mambas procurar" para ver pacotes disponíveis.`);
            process.exit(1);
        }
    } catch (e) {
        console.error(`❌ Erro ao conectar ao registry: ${e.message}`);
        process.exit(1);
    }

    const versaoFinal = versaoEspecifica || meta.versao;

    // Download
    const tmpPath = path.join(os.tmpdir(), `${nome}.tgz`);
    try {
        const urlDownload = versaoEspecifica 
            ? `${REGISTRY_URL}/pacotes/${nome}/download?versao=${versaoFinal}`
            : `${REGISTRY_URL}/pacotes/${nome}/download`;
        
        await download(urlDownload, tmpPath);
    } catch (e) {
        console.error(`❌ Erro ao baixar ${nome}@${versaoFinal}: ${e.message}`);
        process.exit(1);
    }

    // Extrair
    const destino = path.join(MODULOS_DIR, nome);
    fs.mkdirSync(destino, { recursive: true });

    try {
        await tar.extract({ file: tmpPath, cwd: destino });
        fs.unlinkSync(tmpPath);
    } catch (e) {
        console.error(`❌ Erro ao extrair: ${e.message}`);
        process.exit(1);
    }

    // Atualizar registro local
    const registro = carregarRegistro();
    registro[nome] = {
        versao: versaoFinal,
        descricao: meta.descricao || '',
        instaladoEm: new Date().toISOString()
    };
    salvarRegistro(registro);

    // Atualizar mamba.json do projeto
    let projeto = carregarMambaJsonProjeto();
    if (projeto) {
        if (!projeto.dependencias) projeto.dependencias = {};
        projeto.dependencias[nome] = `^${versaoFinal}`;
        salvarMambaJsonProjeto(projeto);
    } else {
        console.log('ℹ️  Nenhum mamba.json encontrado no projeto (use "mambas init").');
    }

    console.log(`✅ ${nome}@${versaoFinal} instalado com sucesso!`);
    console.log(`💡 Use: importar ${nome} de "${nome}"`);
    process.exit(0);
}

async function remover(nomePacote) {
    if (!nomePacote) {
        console.error('❌ Uso: mambas remover <pacote>');
        process.exit(1);
    }

    const registro = carregarRegistro();

    if (!registro[nomePacote]) {
        console.error(`❌ Pacote "${nomePacote}" não está instalado.`);
        process.exit(1);
    }

    const pasta = path.join(MODULOS_DIR, nomePacote);
    if (fs.existsSync(pasta)) {
        fs.rmSync(pasta, { recursive: true, force: true });
    }

    delete registro[nomePacote];
    salvarRegistro(registro);

    console.log(`🗑️  Pacote "${nomePacote}" removido com sucesso!`);
}

function listar() {
    const registro = carregarRegistro();
    const pacotes = Object.keys(registro);

    if (pacotes.length === 0) {
        console.log('📭 Nenhum pacote instalado localmente.');
        console.log('💡 Use "mambas instalar <pacote>"');
        return;
    }

    console.log(`📦 Pacotes instalados (${pacotes.length}):\n`);
    for (const nome of pacotes) {
        const info = registro[nome];
        console.log(`  • ${nome} v${info.versao} — ${info.descricao}`);
    }
}

async function procurar(termo) {
    console.log('🔍 Buscando pacotes disponíveis...\n');

    try {
        const pacotes = JSON.parse(await get(`${REGISTRY_URL}/pacotes`));
        const registro = carregarRegistro();

        let resultado = pacotes;

        if (termo) {
            const t = termo.toLowerCase();
            resultado = pacotes.filter(p =>
                p.nome.toLowerCase().includes(t) ||
                (p.descricao && p.descricao.toLowerCase().includes(t))
            );
        }

        if (resultado.length === 0) {
            console.log(termo ? `📭 Nenhum pacote encontrado para "${termo}".` : '📭 Nenhum pacote disponível ainda.');
            return;
        }

        console.log(`📦 Pacotes encontrados (${resultado.length}):\n`);
        for (const p of resultado) {
            const instalado = registro[p.nome] ? ' ✅ (instalado)' : '';
            console.log(`  • ${p.nome} v${p.versao} — ${p.descricao || 'Sem descrição'}${instalado}`);
        }
    } catch (e) {
        console.error(`❌ Erro ao buscar pacotes: ${e.message}`);
    }
}

async function whoami() {
    const tokenPath = path.join(os.homedir(), '.mambas', 'token');
    
    if (!fs.existsSync(tokenPath)) {
        console.error('❌ Você não está autenticado.');
        console.error('💡 Use: mambas login <token>');
        process.exit(1);
    }

    const token = fs.readFileSync(tokenPath, 'utf-8').trim();

    try {
        const raw = await get(`${REGISTRY_URL}/me`, token);
        const data = JSON.parse(raw);
        console.log(`👤 Username : ${data.username}`);
        console.log(`📧 Email    : ${data.email}`);
        console.log(`📅 Membro desde: ${new Date(data.criadoEm).toLocaleDateString('pt-BR')}`);
    } catch (e) {
        console.error(`❌ Erro ao buscar informações: ${e.message}`);
    }
}

function ajuda() {
    console.log(`
🐍 MambaScript — Gestor de Pacotes

Uso:
  mambas init                    → Cria um mamba.json novo
  mambas instalar <pacote>[@versao]   → Instala um pacote
  mambas remover <pacote>        → Remove um pacote
  mambas listar                  → Lista pacotes instalados
  mambas procurar [termo]        → Busca pacotes no registry
  mambas whoami                  → Mostra usuário logado
  mambas ajuda                   → Mostra esta ajuda

Registry atual: ${REGISTRY_URL}
    `);
}

// ======================== EXECUÇÃO ========================


module.exports = { instalar, remover, listar, procurar, ajuda, whoami, init };
