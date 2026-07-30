class AvaliadorErro extends Error {
    constructor({ titulo, mensagem, linha, coluna, origem, dica, contexto }) {
        let msg = `❌ ${titulo}`;
        if (origem) msg += ` em ${origem}`;
        if (linha) msg += ` (linha ${linha}` + (coluna ? `, coluna ${coluna}` : '') + `)`;
        msg += `:\n   ${mensagem}`;
        if (contexto) msg += '\n\n' + contexto;
        if (dica) msg += `\n\n💡 ${dica}`;
        super(msg);
        this.name = 'AvaliadorErro';
        this._mambaFormatado = true;
        this.linha = linha;
        this.coluna = coluna;
    }
}

class AvaliadorHelpers {
    static carregarFonte(filePath) {
        try {
            const fs = require('fs');
            if (typeof filePath === 'string' && fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
                return fs.readFileSync(filePath, 'utf-8');
            }
        } catch (e) {}
        return null;
    }

    static dicaPara(msg) {
        if (!msg) return null;
        let m;
        if (msg.includes('Fim inesperado do código')) return 'Verifica se abriu e fechou todos os blocos com "fim".';
        if (msg.includes('Statement inválido')) return 'Verifica a sintaxe logo acima. Talvez falte um "fim" de um bloco anterior ou um ":" depois da condição.';
        if (msg.includes('Token inesperado')) return 'Verifica parênteses, vírgulas ou dois-pontos na linha acima.';
        if (msg.startsWith('Esperava')) return 'Falta um símbolo obrigatório nessa posição. Verifica a sintaxe do comando anterior.';
        if ((m = msg.match(/Variável não definida: (\w+)/))) return `Você esqueceu de declarar com 'variavel ${m[1]} = ...' antes de usar?`;
        if ((m = msg.match(/Função não definida: (\w+)/))) return `A função "${m[1]}" precisa ser declarada com 'funcao ${m[1]}(...)' antes de ser chamada.`;
        if ((m = msg.match(/Método não encontrado: (\w+)/))) return `Esse método não existe para esse tipo de valor. Métodos comuns: "tamanho()", "paraTexto()", "aparar()", "maiuscula()", "minuscula()", "incluir()" etc.`;
        if (msg.includes('O valor do tipo')) return 'Esse valor não tem métodos. Talvez seja "nulo"? Atribui um valor antes de tentar acessar.';
        if (msg.includes('Índice') && msg.includes('fora dos limites')) return 'O array não tem elemento nesse índice. Verifica o tamanho com ".tamanho()" e usa um índice válido.';
        if (msg.includes('Índice deve ser um número')) return 'O índice do array precisa ser número (não pode ser texto nem booleano).';
        if (msg.includes('Tentativa de acessar')) return 'Tens a certeza que esse valor é um array ou objeto? Atribui um array/objeto antes de acessar índice ou propriedade.';
        if (msg.includes('em valor nulo') || msg.includes('em valor null')) return 'O valor é nulo. Verifica se a variável contém o valor esperado antes de acessar propriedade.';
        if (msg.includes('Tipos') || msg.includes('permitida apenas entre')) return 'Estás a misturar tipos diferentes (número + texto). Converte com ".paraNumero()" ou ".paraTexto()" antes de operar.';
        if (msg.includes('Não é possível somar')) return 'Estás a somar valores de tipos diferentes. Concatena textos com "+", soma números com "+". Para juntar números como texto usa ".paraTexto()".';
        if (msg.includes('Nó inválido') || msg.includes('Expressão incompleta')) return 'A expressão parece estar incompleta. Verifica parênteses, vírgulas ou operadores.';
        if (msg.includes('não é um número válido')) return 'A string não representa um número válido. Verifica o conteúdo (deve ser só dígitos, opcionalmente com ponto decimal).';
        if (msg.includes('Erro HTTP')) return 'Verifica a URL, conexão de rede, ou se o servidor remoto está acessível.';
        if (msg.includes('Division') || msg.includes('divisão por zero')) return 'Não podes dividir por zero. Garante que o divisor é diferente de zero.';
        return null;
    }

    static gerarSnippet(sourceText, linha, coluna) {
        if (!sourceText || !linha) return null;
        const lines = sourceText.split('\n');
        const target = lines[linha - 1];
        if (target === undefined) return null;
        const numWidth = String(lines.length).length;
        const numPad = (n) => String(n).padStart(numWidth, ' ');
        const antes = linha > 1 ? `   ${numPad(linha - 1)} | ${lines[linha - 2]}\n` : '';
        const principal = `   ${numPad(linha)} | ${target}\n`;
        const linhaCol = ' '.repeat(Math.max(0, (coluna || 0)));
        const seta = `     | ${linhaCol}^`;
        return `${antes}${principal}${seta}`;
    }

    static origemCurta(filePath) {
        if (!filePath) return null;
        const parts = String(filePath).split(/[\\/]/);
        return parts[parts.length - 1] || null;
    }
}

// Cadeia de escopos com parent chain — permite closures reais e
// isola cada call frame (resolve race condition em HTTP paralelo).
class Environment {
    constructor(parent = null, isGlobal = false) {
        this.parent = parent;
        // Object.create(null) evita colisão com propriedades herdadas
        // como "constructor", "toString", "__proto__".
        this.vars = Object.create(null);
        this.isGlobal = isGlobal;
        // Flags de fluxo de controle pertencem a CADA call frame,
        // não ao Evaluator. Isso elimina race conditions entre requests.
        this.hasReturned = false;
        this.hasBreaked = false;
        this.hasContinued = false;
        this.returnValue = undefined;
    }

    define(name, value) {
        this.vars[name] = value;
    }

    get(name) {
        if (Object.prototype.hasOwnProperty.call(this.vars, name)) {
            return this.vars[name];
        }
        if (this.parent) return this.parent.get(name);
        throw new Error(`Variável não definida: ${name}`);
    }

    set(name, value) {
        if (Object.prototype.hasOwnProperty.call(this.vars, name)) {
            this.vars[name] = value;
            return;
        }
        if (this.parent) {
            this.parent.set(name, value);
            return;
        }
        throw new Error(`Variável não definida: ${name}`);
    }

    has(name) {
        if (Object.prototype.hasOwnProperty.call(this.vars, name)) return true;
        if (this.parent) return this.parent.has(name);
        return false;
    }
}

class Evaluator {
    constructor(filePath) {
        this.filePath = filePath || process.cwd();
        this.sourceText = AvaliadorHelpers.carregarFonte(this.filePath);
        // Cadeia de escopos: globalEnv -> ... -> callEnv -> ...
        this.globalEnv = new Environment(null, true);
        this.env = this.globalEnv;
        this.functions = {};
        
        this.builtinFunctions = {
            'hoje': this.createDateObject.bind(this),
            'ler': this.lerInput.bind(this),
            'json_ler': this.jsonLer.bind(this),
            
            'json_texto': this.jsonTexto.bind(this),
            'json_escrever': this.jsonEscrever.bind(this),
            'vermelho': (texto) => `\x1b[31m${texto}\x1b[0m`,
            'verde': (texto) => `\x1b[32m${texto}\x1b[0m`,
            'amarelo': (texto) => `\x1b[33m${texto}\x1b[0m`,
            'azul': (texto) => `\x1b[34m${texto}\x1b[0m`,
            'magenta': (texto) => `\x1b[35m${texto}\x1b[0m`,
            'ciano': (texto) => `\x1b[36m${texto}\x1b[0m`,
            'branco': (texto) => `\x1b[37m${texto}\x1b[0m`,
            'rosa': (texto) => `\x1b[95m${texto}\x1b[0m`,
            'laranja': (texto) => `\x1b[91m${texto}\x1b[0m`,
            'negrito': (texto) => `\x1b[1m${texto}\x1b[0m`,
            
            // Utilitários de log
            'alerta': (texto) => `\x1b[33m⚠ ALERTA: ${texto}\x1b[0m`,
            'erro': (texto) => `\x1b[31m✖ ERRO: ${texto}\x1b[0m`,
            'dica': (texto) => `\x1b[36m💡 DICA: ${texto}\x1b[0m`,
            'sucesso': (texto) => `\x1b[32m✔ SUCESSO: ${texto}\x1b[0m`,
        };
        this.builtinModules = {
            'fs': this.createFsModule(),
            'matematica': this.createMathModule(),
            'caminho': this.createPathModule(),
            'http': this.createHttpModule(this),
            'mysql': this.createMysqlModule(),
            'sistema': this.createSistemaModule(),
            'crypto': this.createCryptoModule(),
             'bcrypt': this.createBcryptModule(),
             'criptografia': this.createCriptografiaModule()
        };
    }

    async execute(ast) {
        for (const statement of ast.body) {
            await this.executeStatement(statement);
        }
    } 

    async chamarFuncaoMamba(func, argumentosPassados) {
        // Cada request HTTP entra aqui. Como agora cada chamada
        // cria o seu próprio Environment (com flags de fluxo isolados),
        // requests paralelos não corrompem mais um ao outro.
        const callEnv = func.closure
            ? new Environment(func.closure)
            : new Environment(this.globalEnv);
        for (let i = 0; i < func.params.length; i++) {
            callEnv.define(func.params[i], argumentosPassados[i]);
        }
        const prevEnv = this.env;
        let returnValue;
        try {
            this.env = callEnv;
            for (const stmt of func.body) {
                await this.executeStatement(stmt);
                if (this.env.hasReturned) break;
            }
            returnValue = this.env.returnValue;
        } finally {
            this.env = prevEnv;
        }
        return returnValue;
    }

    _formatarComContexto(node, error) {
        const msg = String((error && error.message) || error || '');
        const linha = (node && node.line) || (error && error.linha);
        const coluna = (node && node.column) || (error && error.coluna);
        return new AvaliadorErro({
            titulo: 'Erro MambaScript',
            mensagem: msg,
            linha,
            coluna,
            origem: AvaliadorHelpers.origemCurta(this.filePath),
            dica: AvaliadorHelpers.dicaPara(msg),
            contexto: AvaliadorHelpers.gerarSnippet(this.sourceText, linha, coluna),
        });
    }

    // Helper: chama uma função de usuário criando novo Environment
    // (parent = closure capturado na declaração) e restaurando o env anterior.
    async _callUserFunction(func, args) {
        const callEnv = new Environment(func.closure || this.globalEnv);
        const evaluatedArgs = [];
        for (const arg of args) evaluatedArgs.push(await this.evaluate(arg));
        for (let i = 0; i < func.params.length; i++) {
            callEnv.define(func.params[i], evaluatedArgs[i]);
        }
        const prevEnv = this.env;
        let returnValue;
        try {
            this.env = callEnv;
            for (const stmt of func.body) {
                await this.executeStatement(stmt);
                if (this.env.hasReturned) break;
            }
            returnValue = this.env.returnValue;
        } finally {
            this.env = prevEnv;
        }
        return returnValue;
    }

    async executeStatement(node) {
        try {
            switch (node.type) {
            case 'Print':
                const printValue = await this.evaluate(node.value);
                if (printValue && printValue._type === 'DateObject') {
                    console.log(`Data: ${printValue.mostrarData()} às ${printValue.mostrarHora()}`);
                } else {
                    console.log(printValue);
                }
                break;
                
            case 'ImportNamed':
    await this.executeImportNamed(node);
    break;

            case 'VarDeclaration':
                const varValue = await this.evaluate(node.value);
                this.env.define(node.name, varValue);
                break;

            case 'Assignment': {
    const valor = await this.evaluate(node.value);

    if (node.name.type === 'IndexAccess') {
        const obj = await this.evaluate(node.name.object);
        const chave = await this.evaluate(node.name.index);
        obj[chave] = valor;
        return;
    }

    if (node.name.type === 'PropertyAccess') {
        const obj = await this.evaluate(node.name.object);
        obj[node.name.property] = valor;
        return;
    }

    if (node.name.type === 'Identifier') {
        try { this.env.set(node.name.name, valor); }
        catch (e) { this.env.define(node.name.name, valor); }
        return;
    }

    if (typeof node.name === 'string') {
        try { this.env.set(node.name, valor); }
        catch (e) { this.env.define(node.name, valor); }
        return;
    }

    throw new Error(`Assignment inválido`);
}
break
                
                case 'Break':
    this.env.hasBreaked = true;
    return;

case 'Continue':
    this.env.hasContinued = true;
    return;
    
    case 'ForEach': {
    const lista = await this.evaluate(node.iterable);

    // Um nome (compatibilidade)
    if (node.varNames.length === 1) {
        for (const item of lista) {
            this.env.define(node.varNames[0], item);

            for (const stmt of node.body) {
                await this.executeStatement(stmt);

                if (this.env.hasContinued) break;
                if (this.env.hasBreaked || this.env.hasReturned) break;
            }

            if (this.env.hasContinued) {
                this.env.hasContinued = false;
                continue;
            }

            if (this.env.hasBreaked) {
                this.env.hasBreaked = false;
                break;
            }

            if (this.env.hasReturned) return;
        }
    }

    // Dois nomes
    else if (node.varNames.length === 2) {

        for (const [chave, valor] of Object.entries(lista)) {

            this.env.define(node.varNames[0], chave);
            this.env.define(node.varNames[1], valor);

            for (const stmt of node.body) {
                await this.executeStatement(stmt);

                if (this.env.hasContinued) break;
                if (this.env.hasBreaked || this.env.hasReturned) break;
            }

            if (this.env.hasContinued) {
                this.env.hasContinued = false;
                continue;
            }

            if (this.env.hasBreaked) {
                this.env.hasBreaked = false;
                break;
            }

            if (this.env.hasReturned) return;
        }
    }

    break;
}

case 'Switch':
    const switchValue = await this.evaluate(node.value);
    let matched = false;

    for (const caso of node.cases) {
        const caseValue = await this.evaluate(caso.value);
        if (switchValue === caseValue) matched = true;

        if (matched) {
            for (const stmt of caso.body) {
                await this.executeStatement(stmt);
                if (this.env.hasBreaked) { this.env.hasBreaked = false; return; }
                if (this.env.hasReturned) return;
            }
        }
    }

    if (!matched && node.defaultBody) {
        for (const stmt of node.defaultBody) {
            await this.executeStatement(stmt);
            if (this.env.hasBreaked) { this.env.hasBreaked = false; return; }
            if (this.env.hasReturned) return;
        }
    }
    break;

            case 'If':
                if (await this.evaluate(node.condition)) {
                    for (const stmt of node.body) {
                        await this.executeStatement(stmt);
                        if (this.env.hasReturned) return;
                    }
                } else if (node.elseBody) {
                    for (const stmt of node.elseBody) {
                        await this.executeStatement(stmt);
                        if (this.env.hasReturned) return;
                    }
                }
                break;

            case 'While':
    while (await this.evaluate(node.condition)) {
        for (const stmt of node.body) {
            await this.executeStatement(stmt);
            if (this.env.hasContinued) break; // sai do for interno, relança o while
            if (this.env.hasBreaked || this.env.hasReturned) break;
        }
        if (this.env.hasContinued) { this.env.hasContinued = false; continue; }
        if (this.env.hasBreaked) { this.env.hasBreaked = false; break; }
        if (this.env.hasReturned) return;
    }
    break;

            case 'FunctionDeclaration':
                // closure: snapshot do escopo onde a função foi DECLARADA
                // (permite que futuras chamadas vejam as variáveis capturadas)
                this.functions[node.name] = {
                    _type: 'MambaFunction',
                    params: node.params,
                    body: node.body,
                    closure: this.env
                };
                break;

            case 'Return':
                this.env.returnValue = await this.evaluate(node.value);
                this.env.hasReturned = true;
                break;

            case 'For':
    const startVal = await this.evaluate(node.start);
    const endVal = await this.evaluate(node.end);
    for (let i = startVal; i <= endVal; i++) {
        this.env.define(node.varName, i);
        for (const stmt of node.body) {
            await this.executeStatement(stmt);
            if (this.env.hasContinued) break;
            if (this.env.hasBreaked || this.env.hasReturned) break;
        }
        if (this.env.hasContinued) { this.env.hasContinued = false; continue; }
        if (this.env.hasBreaked) { this.env.hasBreaked = false; break; }
        if (this.env.hasReturned) return;
    }
    break;
            case 'Import':
                await this.executeImport(node);
                break;

            case 'ExpressionStatement':
                await this.evaluate(node.expression);
                break;

            case 'TryCatch':
                try {
                    for (const stmt of node.body) {
                        await this.executeStatement(stmt);
                        if (this.env.hasReturned) return;
                    }
                } catch (e) {
                    this.env.define(node.errorVar, e.message);
                    for (const stmt of node.catchBody) {
                        await this.executeStatement(stmt);
                        if (this.env.hasReturned) return;
                    }
                }
                break;

            default:
                throw new Error(`Statement desconhecido: ${node.type}`);
        }
        } catch (e) {
            if (e && e._mambaFormatado) throw e;
            throw this._formatarComContexto(node, e);
        }
    }

    async evaluate(node) {
        if (!node) throw this._formatarComContexto(null, new Error('Nó inválido'));

        try {
            switch (node.type) {
            case 'Number':
                return node.value;

            case 'String':
                return node.value;

            case 'Boolean':
                return node.value;

            case 'Null':
                return null;
                
                case 'Await':
    return await this.evaluate(node.expression);


            case 'Identifier':
                // 1) Procura no env (variáveis e funções capturadas em escopo léxico)
                if (this.env.has(node.name)) {
                    return this.env.get(node.name);
                }
                // 2) Fallback: funções declaradas globalmente
                if (Object.prototype.hasOwnProperty.call(this.functions, node.name)) {
                    return this.functions[node.name];
                }
                throw new Error(`Variável não definida: ${node.name}`);

            case 'ArrayLiteral':
                const elements = [];
                for (const el of node.elements) {
                    elements.push(await this.evaluate(el));
                }
                return elements;

            case 'ObjectLiteral':
                const objectLiteral = {};
                for (const [key, valueNode] of Object.entries(node.properties)) {
                    objectLiteral[key] = await this.evaluate(valueNode);
                }
                return objectLiteral;

            case 'FunctionLiteral':
                return {
                    _type: 'MambaFunction',
                    params: node.params,
                    body: node.body,
                    closure: this.env
                };

            case 'ArrayAccess': {
    const array = await this.evaluate(node.array);
    const index = await this.evaluate(node.index);

    if (Array.isArray(array)) {
        if (typeof index !== 'number') throw new Error('Índice deve ser um número');
        if (index < 0 || index >= array.length) throw new Error(`Índice ${index} fora dos limites`);
        return array[index];
    }

    if (typeof array === 'object' && array !== null) {
        return array[index];
    }

    throw new Error('Tentativa de acessar índice em não-array ou objeto');
}

            case 'PropertyAccess': {
    const object = await this.evaluate(node.object);

    if (object === null || object === undefined) {
        throw new Error(`Tentativa de acessar propriedade "${node.property}" em valor nulo`);
    }

    // Atalho para .tamanho em arrays e strings
    if (node.property === 'tamanho') {
        if (Array.isArray(object) || typeof object === 'string') {
            return object.length;
        }
    }

    if (typeof object === 'object' || typeof object === 'function') {
        return object[node.property];
    }

    return object[node.property];
}

            case 'BinaryOp':
                const left = await this.evaluate(node.left);
                const right = await this.evaluate(node.right);
                if (node.operator === 'PLUS') {
                    if (typeof left === 'number' && typeof right === 'number') return left + right;
                    if (typeof left === 'string' || typeof right === 'string') return String(left) + String(right);
                    throw new Error(`Erro de Tipo: Não é possível somar ${typeof left} com ${typeof right}.`);
                }
                if (typeof left !== 'number' || typeof right !== 'number') {
                    throw new Error(`Operação ${node.operator} permitida apenas entre números.`);
                }
                switch (node.operator) {
                    case 'MINUS': return left - right;
                    case 'MULT': return left * right;
                    case 'DIV': return left / right;
                    default: throw new Error(`Operador desconhecido: ${node.operator}`);
                }

            case 'Comparison':
    const leftComp = await this.evaluate(node.left);
    const rightComp = await this.evaluate(node.right);
    switch (node.operator) {
        case 'GT': case 'GREATER': return leftComp > rightComp;
        case 'LT': case 'LESS': return leftComp < rightComp;
        case 'EQ': case 'EQUALS_COMP': return leftComp === rightComp;
        case 'NEQ': return leftComp !== rightComp;
        case 'GTE': case 'GREATER_EQUAL': return leftComp >= rightComp;
        case 'LTE': case 'LESS_EQUAL': return leftComp <= rightComp;
        default: throw new Error(`Comparação desconhecida: ${node.operator}`);
    }
                
              

            case 'FunctionCall':
                if (node.name in this.builtinFunctions) {
                    const args = [];
                    for (const arg of node.args) args.push(await this.evaluate(arg));
                    return await this.builtinFunctions[node.name](...args);
                }
                if (node.name in this.functions) {
                    return await this._callUserFunction(this.functions[node.name], node.args);
                }
                // Variável pode conter uma função anônima (FunctionLiteral) — closure
                try {
                    const val = this.env.get(node.name);
                    if (val && val._type === 'MambaFunction') {
                        return await this._callUserFunction(val, node.args);
                    }
                } catch (e) { /* não é uma função aninhada */ }
                throw new Error(`Função não definida: ${node.name}`);

            case 'LogicalOp':
                if (node.operator === 'AND') return (await this.evaluate(node.left)) && (await this.evaluate(node.right));
                if (node.operator === 'OR') return (await this.evaluate(node.left)) || (await this.evaluate(node.right));
                throw new Error(`Operador lógico desconhecido: ${node.operator}`);

            case 'UnaryOp':
                if (node.operator === 'NOT') return !(await this.evaluate(node.operand));
                throw new Error(`Operador unário desconhecido: ${node.operator}`);

            case 'MethodCall':
                const obj = await this.evaluate(node.object);
                return await this.callMethod(obj, node.method, node.args);

            default:
                throw new Error(`Tipo de nó desconhecido: ${node.type}`);
        }
        } catch (e) {
            if (e && e._mambaFormatado) throw e;
            throw this._formatarComContexto(node, e);
        }
    }

    async callMethod(obj, methodName, args) {
        // STRING METHODS
        if (typeof obj === 'string') {
    if (methodName === 'paraNumero') {
        const n = Number(obj);
        if (isNaN(n)) throw new Error(`❌ "${obj}" não é um número válido.`);
        return n;
    }
    if (methodName === 'tamanho') return obj.length;
    if (methodName === 'maiuscula') return obj.toUpperCase();
    if (methodName === 'minuscula') return obj.toLowerCase();
    
    // NOVOS
    if (methodName === 'dividir') {
    const sep = args[0] ? await this.evaluate(args[0]) : '';
    
    const unescape = (s) => s
        .replace(/\\n/g, '\n')
        .replace(/\\r/g, '\r')
        .replace(/\\t/g, '\t');
    
    return obj.split(unescape(sep));
}
    if (methodName === 'aparar') return obj.trim();
    if (methodName === 'incluir') {
        const sub = await this.evaluate(args[0]);
        return obj.includes(sub);
    }
    if (methodName === 'começa_com') {
        const sub = await this.evaluate(args[0]);
        return obj.startsWith(sub);
    }
    if (methodName === 'termina_com') {
        const sub = await this.evaluate(args[0]);
        return obj.endsWith(sub);
    }
    if (methodName === 'substituir') {
    const de = await this.evaluate(args[0]);
    const para = await this.evaluate(args[1]);
    
    // Interpreta escape sequences
    const unescape = (s) => s
        .replace(/\\n/g, '\n')
        .replace(/\\r/g, '\r')
        .replace(/\\t/g, '\t');
    
    return obj.replace(unescape(de), unescape(para));
}
    if (methodName === 'fatiar') {
        const inicio = await this.evaluate(args[0]);
        const fim = args[1] ? await this.evaluate(args[1]) : undefined;
        return obj.slice(inicio, fim);
    }
    
    if (methodName === 'indice_de') {
    const sub = await this.evaluate(args[0]);
    return obj.indexOf(sub);
}
if (methodName === 'substring') {
    const inicio = await this.evaluate(args[0]);
    const fim = args[1] ? await this.evaluate(args[1]) : undefined;
    return obj.substring(inicio, fim);
}
}

        // NUMBER METHODS
        if (typeof obj === 'number') {
            if (methodName === 'paraTexto') return String(obj);
        }

        // ARRAY METHODS
        if (Array.isArray(obj)) {
            if (methodName === 'tamanho') return obj.length;
            if (methodName === 'adicionar') { obj.push(await this.evaluate(args[0])); return undefined; }
            if (methodName === 'remover') {
                const index = await this.evaluate(args[0]);
                if (typeof index !== 'number') throw new Error('Índice deve ser um número');
                if (index < 0 || index >= obj.length) throw new Error(`Índice ${index} fora dos limites`);
                obj.splice(index, 1);
                return undefined;
            }
            if (methodName === 'pegar') {
                const index = await this.evaluate(args[0]);
                if (typeof index !== 'number') throw new Error('Índice deve ser um número');
                if (index < 0 || index >= obj.length) throw new Error(`Índice ${index} fora dos limites`);
                return obj[index];
            }
            if (methodName === 'contem') { return obj.includes(await this.evaluate(args[0])); }
            if (methodName === 'juntar') {
                const separator = args[0] ? await this.evaluate(args[0]) : ',';
                return obj.join(separator);
            }
        }

        if (!obj || typeof obj !== 'object') {
            throw new Error(`O valor do tipo ${typeof obj} não possui o método "${methodName}"`);
        }
        if (!(methodName in obj)) {
            throw new Error(`Método não encontrado: ${methodName}`);
        }

        const method = obj[methodName];
        const evaluatedArgs = [];
        for (const arg of args) evaluatedArgs.push(await this.evaluate(arg));
        return await method.call(obj, ...evaluatedArgs);
    }
    
    async executeImportNamed(node) {
    const { names, source } = node;
    const tempName = `__temp_${source}__`;
    await this.executeImport({ name: tempName, source });
    const modulo = this.env.get(tempName);
    this.env.define(tempName, undefined);
    for (const nome of names) {
        if (!(nome in modulo)) {
            throw new Error(`❌ "${nome}" não existe no módulo "${source}"`);
        }
        this.env.define(nome, modulo[nome]);
    }
}

    async executeImport(node) {
        const { name, source } = node;

        if (this.builtinModules[source]) {
            this.env.define(name, this.builtinModules[source]);
            return;
        }

        const fs = require('fs');
        const path = require('path');
        const Lexer = require('../Lexer/lexer');
        const Parser = require('../Parser/parser');

        const baseDir = typeof this.filePath === 'string' && fs.existsSync(this.filePath) && fs.statSync(this.filePath).isFile()
            ? path.dirname(this.filePath)
            : this.filePath;

        let resolvedPath = null;
        const nomeNormalizado = source.endsWith('.ms') ? source : source + '.ms';
        const candidates = [
            path.resolve(baseDir, nomeNormalizado),
            path.resolve(baseDir, 'modulos_mambas', nomeNormalizado),
            path.resolve(baseDir, source, 'index.ms'),
            path.resolve(baseDir, 'modulos_mambas', source, 'index.ms'),
        ];

        for (const candidate of candidates) {
            if (fs.existsSync(candidate)) {
                resolvedPath = candidate;
                break;
            }
        }

        if (!resolvedPath) {
            throw new Error(`❌ Módulo não encontrado: "${source}"\n   Procurado em:\n   - ${candidates.join('\n   - ')}`);
        }

        const code = fs.readFileSync(resolvedPath, 'utf-8');
        const lexer = new Lexer(code);
        const tokens = lexer.tokenize();
        const parser = new Parser(tokens);
        const ast = parser.parse();

        const moduleEvaluator = new Evaluator(resolvedPath);
        await moduleEvaluator.execute(ast);

        const moduleExports = {};
        for (const [key] of Object.entries(moduleEvaluator.functions)) {
            const funcRef = moduleEvaluator.functions[key];
            moduleExports[key] = async (...args) => {
                const callEnv = new Environment(funcRef.closure || moduleEvaluator.globalEnv);
                for (let i = 0; i < funcRef.params.length; i++) {
                    callEnv.define(funcRef.params[i], args[i]);
                }
                const prevEnv = moduleEvaluator.env;
                let returnValue;
                try {
                    moduleEvaluator.env = callEnv;
                    for (const stmt of funcRef.body) {
                        await moduleEvaluator.executeStatement(stmt);
                        if (moduleEvaluator.env.hasReturned) break;
                    }
                    returnValue = moduleEvaluator.env.returnValue;
                } finally {
                    moduleEvaluator.env = prevEnv;
                }
                return returnValue;
            };
        }
        for (const [key, value] of Object.entries(moduleEvaluator.globalEnv.vars)) {
            moduleExports[key] = value;
        }

        this.env.define(name, moduleExports);
    }
    
    

    createFsModule() {
        const fs = require('fs');
        return {
            ler: (arquivo) => { try { return fs.readFileSync(arquivo, 'utf-8'); } catch (e) { throw new Error(`❌ Erro ao ler arquivo: ${e.message}`); } },
            escrever: (arquivo, conteudo) => { try { fs.writeFileSync(arquivo, conteudo, 'utf-8'); } catch (e) { throw new Error(`❌ Erro ao escrever arquivo: ${e.message}`); } },
            existe: (arquivo) => fs.existsSync(arquivo),
            apagar: (arquivo) => { try { fs.unlinkSync(arquivo); } catch (e) { throw new Error(`❌ Erro ao apagar arquivo: ${e.message}`); } }
        };
    }

    createMathModule() {
        return {
            PI: Math.PI,
            raiz: (n) => Math.sqrt(n),
            potencia: (base, exp) => Math.pow(base, exp),
            absoluto: (n) => Math.abs(n),
            arredondar: (n) => Math.round(n),
            teto: (n) => Math.ceil(n),
            chao: (n) => Math.floor(n),
            aleatorio: (min, max) => {
    if (min !== undefined && max !== undefined) {
        return Math.floor(Math.random() * (max - min + 1)) + min;
    }
    return Math.random();
}
,
            seno: (n) => Math.sin(n),
            cosseno: (n) => Math.cos(n)
        };
    }

    createPathModule() {
        const path = require('path');
        return {
            juntar: (...partes) => path.join(...partes),
            diretorio: (caminho) => path.dirname(caminho),
            arquivo: (caminho) => path.basename(caminho),
            extensao: (caminho) => path.extname(caminho),
            absoluto: (caminho) => path.resolve(caminho)
        };
    }

    createMysqlModule() {
        let mysql2;
        try {
            mysql2 = require('mysql2/promise');
        } catch (e) {
            return new Proxy({}, {
                get: () => () => { throw new Error(`❌ Módulo "mysql" requer mysql2. Execute: npm install mysql2`); }
            });
        }

        let conexao = null;

        return {
            conectar: async (host, usuario, senha, base, porta = 3306) => {
                try {
                    conexao = await mysql2.createConnection({ host, user: usuario, password: senha, database: base,
                    port: porta});
                    return { ok: true, mensagem: "Conexão estabelecida!" };
                } catch (e) {
                    throw new Error(`❌ Erro ao conectar ao MySQL: ${e.message}`);
                }
            },

            consultar: async (sql, parametros) => {
                if (!conexao) throw new Error(`❌ Chame bd.conectar() antes de consultar`);
                try {
                    const [linhas] = await conexao.execute(sql, parametros || []);
                    return linhas;
                } catch (e) {
                    throw new Error(`❌ Erro na consulta: ${e.message}`);
                }
            },

            executar: async (sql, parametros) => {
                if (!conexao) throw new Error(`❌ Chame bd.executar() antes de executar`);
                try {
                    const [resultado] = await conexao.execute(sql, parametros || []);
                    return {
                        afetadas: resultado.affectedRows,
                        inseridoId: resultado.insertId,
                        ok: resultado.affectedRows > 0
                    };
                } catch (e) {
                    throw new Error(`❌ Erro ao executar: ${e.message}`);
                }
            },

            fechar: async () => {
                if (conexao) { await conexao.end(); conexao = null; }
            }
        };
    }

    createCriptografiaModule() {
    let bcrypt;
    try {
        bcrypt = require('bcrypt');
    } catch (e) {
        return new Proxy({}, {
            get: (target, prop) => {
                if (typeof prop === 'symbol' || prop === 'then' || prop === 'inspect') {
                    return undefined;
                }
                return () => {
                    throw new Error(`❌ Módulo "criptografia" requer bcrypt. Execute: npm install bcrypt`);
                };
            }
        });
    }

    const CUSTO_MIN = 4;
    const CUSTO_MAX = 15;
    const CUSTO_PADRAO = 10;

    function validarCusto(custo) {
        const n = Number(custo);
        if (!Number.isInteger(n) || n < CUSTO_MIN || n > CUSTO_MAX) {
            throw new Error(`❌ Custo inválido. Use um número inteiro entre ${CUSTO_MIN} e ${CUSTO_MAX}.`);
        }
        return n;
    }

    function validarSenha(senha) {
        if (senha === undefined || senha === null || senha === '') {
            throw new Error("Senha é obrigatória.");
        }
    }

    return {
        // --- Versões assíncronas (recomendadas, não bloqueiam o event loop) ---

        gerarHash: async (senha, custo = CUSTO_PADRAO) => {
            try {
                validarSenha(senha);
                const custoFinal = validarCusto(custo);
                return await bcrypt.hash(String(senha), custoFinal);
            } catch (e) {
                throw new Error(`❌ Erro ao gerar hash de criptografia: ${e.message}`);
            }
        },

        comparar: async (senha, hash) => {
            try {
                if (!senha || !hash) {
                    throw new Error("Senha e Hash são obrigatórios para a comparação.");
                }
                return await bcrypt.compare(String(senha), String(hash));
            } catch (e) {
                throw new Error(`❌ Erro ao comparar criptografia: ${e.message}`);
            }
        },

        // --- Versões síncronas (⚠️ bloqueiam o event loop, evite em rotas de alto tráfego) ---

        gerarHashSincrono: (senha, custo = CUSTO_PADRAO) => {
            try {
                validarSenha(senha);
                const custoFinal = validarCusto(custo);
                return bcrypt.hashSync(String(senha), custoFinal);
            } catch (e) {
                throw new Error(`❌ Erro ao gerar hash síncrono: ${e.message}`);
            }
        },

        compararSincrono: (senha, hash) => {
            try {
                if (!senha || !hash) {
                    throw new Error("Senha e Hash são obrigatórios para a comparação.");
                }
                return bcrypt.compareSync(String(senha), String(hash));
            } catch (e) {
                throw new Error(`❌ Erro ao comparar síncrono: ${e.message}`);
            }
        }
    };
}
    createHttpModule(evaluator) {
        const fetch = require('node-fetch');

        const validarUrl = (url) => {
            try {
                const urlObj = new URL(url);
                if (!['http:', 'https:'].includes(urlObj.protocol)) {
                    throw new Error(`Protocolo "${urlObj.protocol}" não permitido. Use http: ou https:.`);
                }
                return urlObj;
            } catch (e) {
                if (e.message.includes('Protocolo')) throw e;
                throw new Error(`URL inválida: "${url}" — ${e.message}`);
            }
        };

        const request = async (method, url, corpo, cabecalhos) => {
            validarUrl(url);

            const headers = {};
            if (cabecalhos && typeof cabecalhos === 'object') {
                for (const [k, v] of Object.entries(cabecalhos)) headers[k] = v;
            }

            const options = { method, headers };
            if (corpo !== undefined && corpo !== null) {
                options.body = typeof corpo === 'object' ? JSON.stringify(corpo) : String(corpo);
                if (!headers['Content-Type'] && !headers['content-type']) {
                    headers['Content-Type'] = 'application/json';
                }
            }

            try {
                const resposta = await fetch(url, options);
                const texto = await resposta.text();
                let dados;
                try { dados = JSON.parse(texto); } catch { dados = texto; }
                return { status: resposta.status, corpo: dados, texto, ok: resposta.ok };
            } catch (e) {
                throw new Error(`Erro HTTP ao ${method} ${url}: ${e.message}`);
            }
        };

        return {
            get: (url, cabecalhos) => request('GET', url, null, cabecalhos),
            post: (url, corpo, cabecalhos) => request('POST', url, corpo, cabecalhos),
            put: (url, corpo, cabecalhos) => request('PUT', url, corpo, cabecalhos),
            apagar: (url, cabecalhos) => request('DELETE', url, null, cabecalhos),

            criarServidor: () => {
                return {
                    callbackMamba: null,
                    aoReceber: function(funcaoUsuario) { this.callbackMamba = funcaoUsuario; },
                    escutar: function(porta) {
                        const httpNativo = require('http');
                        const servidorNode = httpNativo.createServer((req, res) => {
                            let corpoRequisicao = '';
                            req.on('data', chunk => { corpoRequisicao += chunk; });
                            req.on('end', async () => {
                                let corpoParseado = corpoRequisicao;
                                try { corpoParseado = JSON.parse(corpoRequisicao); } catch {}

                                const urlObj = new URL(req.url, `http://localhost:${porta}`);
                                const params = {};
                                urlObj.searchParams.forEach((val, chave) => { params[chave] = val; });

                                const requisicaoMamba = {
                                    url: urlObj.pathname,
                                    metodo: req.method,
                                    corpo: corpoParseado,
                                    params: params,
                                    cabecalhos: req.headers
                                };

                                const respostaMamba = {
                                    enviar: (status, conteudo) => {
                                        const tipo = typeof conteudo === 'object' ? 'application/json' : 'text/plain; charset=utf-8';
                                        const saida = typeof conteudo === 'object' ? JSON.stringify(conteudo) : String(conteudo);
                                        res.writeHead(status, { 'Content-Type': tipo });
                                        res.end(saida);
                                    },
                                    json: (status, conteudo) => {
                                        res.writeHead(status, { 'Content-Type': 'application/json' });
                                        res.end(JSON.stringify(conteudo));
                                    },
                                    cabecalho: (chave, valor) => { res.setHeader(chave, valor); },
                                    redirecionar: (url) => { res.writeHead(302, { 'Location': url }); res.end(); }
                                };

                                if (this.callbackMamba && this.callbackMamba._type === 'MambaFunction') {
                                    await evaluator.chamarFuncaoMamba(this.callbackMamba, [requisicaoMamba, respostaMamba]);
                                }
                            });
                        });
                        servidorNode.listen(porta);
                    }
                };
            }
        };
    }

    createDateObject(timezone) {
        const now = new Date();
        const getLocalDate = () => {
            if (!timezone) return now;
            const parts = new Intl.DateTimeFormat('en-US', {
                timeZone: timezone, year: 'numeric', month: '2-digit', day: '2-digit',
                hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
            }).formatToParts(now);
            const get = (type) => parseInt(parts.find(p => p.type === type).value);
            return new Date(get('year'), get('month') - 1, get('day'), get('hour'), get('minute'), get('second'));
        };
        const localDate = getLocalDate();
        const nomesMeses = ["Janeiro","Fevereiro","Março","Abril","Maio","Junho","Julho","Agosto","Setembro","Outubro","Novembro","Dezembro"];
        const nomesSemana = ["Domingo","Segunda-feira","Terça-feira","Quarta-feira","Quinta-feira","Sexta-feira","Sábado"];
        return {
            _type: 'DateObject',
            mostrarHora: () => localDate.toLocaleTimeString('pt-BR'),
            mostrarData: () => localDate.toLocaleDateString('pt-BR'),
            ano: () => localDate.getFullYear(),
            dia: () => localDate.getDate(),
            horas: () => localDate.getHours(),
            minutos: () => localDate.getMinutes(),
            segundos: () => localDate.getSeconds(),
            mes: () => ({ numero: localDate.getMonth() + 1, nome: nomesMeses[localDate.getMonth()] }),
            semana: () => ({ numero: localDate.getDay(), nome: nomesSemana[localDate.getDay()] }),
            timestamp: () => now.getTime(),
            formatado: () => `${String(localDate.getDate()).padStart(2,'0')}/${String(localDate.getMonth()+1).padStart(2,'0')}/${localDate.getFullYear()}`,
            horaFormatada: () => `${String(localDate.getHours()).padStart(2,'0')}:${String(localDate.getMinutes()).padStart(2,'0')}:${String(localDate.getSeconds()).padStart(2,'0')}`
        };
    }

    lerInput() {
        const prompt = require('prompt-sync')({ sigint: true });
        return prompt('');
    }

    jsonLer(arquivo) {
        const fs = require('fs');
        try { return JSON.parse(fs.readFileSync(arquivo, 'utf-8')); }
        catch (e) { throw new Error(`❌ Erro ao ler JSON: ${e.message}`); }
    }

    jsonTexto(textoJson) {
        try { return JSON.parse(textoJson); }
        catch (e) { throw new Error(`❌ Erro ao parsear JSON: ${e.message}`); }
    }
    
    createSistemaModule() {
    const { execSync } = require('child_process');
    return {
        plataforma: () => process.platform,
        variavel: (nome) => process.env[nome] || null,
        executar: (cmd) => {
            try {
                return execSync(cmd, { encoding: 'utf-8' }).trim();
            } catch (e) {
                throw new Error(`❌ Erro ao executar comando: ${e.message}`);
            }
        },
        sair: (codigo) => process.exit(codigo || 0),
        args: () => process.argv.slice(2),
        pid: () => process.pid,
        memoria: () => process.memoryUsage()
    };
}

createCryptoModule() {
    const crypto = require('crypto');
    return {
        hash: (texto, algoritmo) => {
            return crypto.createHash(algoritmo || 'sha256').update(texto).digest('hex');
        },
        md5: (texto) => crypto.createHash('md5').update(texto).digest('hex'),
        sha256: (texto) => crypto.createHash('sha256').update(texto).digest('hex'),
        sha512: (texto) => crypto.createHash('sha512').update(texto).digest('hex'),
        aleatorio: (tamanho) => crypto.randomBytes(tamanho || 16).toString('hex')
    };
}

createBcryptModule() {
    let bcrypt;
    try {
        bcrypt = require('bcryptjs');
    } catch (e) {
        return new Proxy({}, {
            get: () => () => { throw new Error(`❌ Módulo "bcrypt" requer bcryptjs. Execute: npm install bcryptjs`); }
        });
    }
    return {
        hashSenha: (senha, rounds) => bcrypt.hashSync(senha, rounds || 10),
        verificar: (senha, hash) => bcrypt.compareSync(senha, hash),
        salt: (rounds) => bcrypt.genSaltSync(rounds || 10)
    };
}

    jsonEscrever(arquivo, dados) {
        const fs = require('fs');
        try { fs.writeFileSync(arquivo, JSON.stringify(dados, null, 2), 'utf-8'); }
        catch (e) { throw new Error(`❌ Erro ao escrever JSON: ${e.message}`); }
    }
}

module.exports = Evaluator;