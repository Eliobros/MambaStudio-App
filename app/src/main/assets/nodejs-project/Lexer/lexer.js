class Token {
    constructor(type, value, line, column) {
        this.type = type;
        this.value = value;
        this.line = line;
        this.column = column;
    }
}

class Lexer {
    constructor(code) {
        this.code = code;
        this.pos = 0;
        this.line = 1;
        this.column = 0;
        this.currentChar = this.code[this.pos] || null;
    }

    advance() {
        if (this.currentChar === '\n') {
            this.line++;
            this.column = 0;
        } else {
            this.column++;
        }

        this.pos++;
        this.currentChar = this.pos < this.code.length ? this.code[this.pos] : null;
    }

    skipWhitespace() {
        while (this.currentChar && /\s/.test(this.currentChar)) {
            this.advance();
        }
    }

    skipComment() {
        if (this.currentChar === '#') {
            while (this.currentChar && this.currentChar !== '\n') {
                this.advance();
            }
            this.advance();
        }
    }

    readNumber() {
        let num = '';
        let dotCount = 0;
        const startCol = this.column;

        while (this.currentChar && /[0-9.]/.test(this.currentChar)) {
            if (this.currentChar === '.') {
                dotCount++;
                if (dotCount > 1) break;
            }
            num += this.currentChar;
            this.advance();
        }

        return new Token('NUMBER', parseFloat(num), this.line, startCol);
    }

    readString(quoteType) {
        let str = '';
        const startCol = this.column;
        const startLine = this.line;
        this.advance(); // Pula a aspas de abertura

        while (this.currentChar && this.currentChar !== quoteType) {
            // Suporte a escape de aspas
            if (this.currentChar === '\\' && this.peek() === quoteType) {
                this.advance(); // Pula a barra
                str += this.currentChar;
                this.advance();
            } else {
                str += this.currentChar;
                this.advance();
            }
        }

        if (this.currentChar !== quoteType) {
            throw new Error(`String não finalizada (linha ${startLine}, coluna ${startCol})`);
        }

        this.advance(); // Pula a aspas de fechamento
        return new Token('STRING', str, startLine, startCol);
    }

    peek() {
        const peekPos = this.pos + 1;
        return peekPos < this.code.length ? this.code[peekPos] : null;
    }

    readIdentifier() {
        let id = '';
        const startCol = this.column;

        while (this.currentChar && /[a-zA-Z_áàãâéêíóôõúç0-9]/i.test(this.currentChar)) {
            id += this.currentChar;
            this.advance();
        }

        const keywords = {
            'escreva': 'PRINT',
            'variavel': 'VAR',
            'se': 'IF',
            'senao': 'ELSE',
            'enquanto': 'WHILE',
            'para': 'FOR',
            'de': 'DE',
            'ate': 'ATE',
            'funcao': 'FUNCTION',
            'retorna': 'RETURN',
            'fim': 'END',
            'cada': 'CADA',
            'em': 'EM',
            'importar': 'IMPORT',
            'e': 'AND',
            'ou': 'OR',
            'nao': 'NOT',
            'verdadeiro': 'TRUE',
            'falso': 'FALSE',
            'nulo': 'NULL',
            'maior': 'KW_GT',
            'menor': 'KW_LT',
            'igual': 'KW_EQ',
            'maiorIgual': 'KW_GTE',
            'menorIgual': 'KW_LTE',
            'mais': 'KW_PLUS',
            'menos': 'KW_MINUS',
            'vezes': 'KW_MULT',
            'dividido': 'KW_DIV',
            'tente': 'TENTE', 
            'capturar': 'CAPTURAR',
             'parar': 'BREAK',
             'aguardar': 'AWAIT',
             'continuar': 'CONTINUE',
              'escolher': 'SWITCH',
              'caso': 'CASE',
              'padrao': 'DEFAULT'
        };

        const type = keywords[id] || 'IDENTIFIER';
        return new Token(type, id, this.line, startCol);
    }

    tokenize() {
        const tokens = [];

        while (this.currentChar !== null) {
            if (/\s/.test(this.currentChar)) {
                this.skipWhitespace();
                continue;
            }

            if (this.currentChar === '#') {
                this.skipComment();
                continue;
            }

            if (/[0-9]/.test(this.currentChar)) {
                tokens.push(this.readNumber());
                continue;
            }

            // Suporte a aspas duplas e simples
            if (this.currentChar === '"' || this.currentChar === "'") {
                tokens.push(this.readString(this.currentChar));
                continue;
            }

            const line = this.line;
            const col = this.column;

            if (this.currentChar === '=') {
                this.advance();
                if (this.currentChar === '=') {
                    this.advance();
                    tokens.push(new Token('EQ', '==', line, col));
                } else {
                    tokens.push(new Token('ASSIGN', '=', line, col));
                }
                continue;
            }
            
            if (this.currentChar === '!') {
    this.advance();
    if (this.currentChar === '=') {
        this.advance();
        tokens.push(new Token('NEQ', '!=', line, col));
    } else {
        throw new Error(`Caractere inválido '!' na linha ${line}, coluna ${col}`);
    }
    continue;
}

            if (this.currentChar === '>') {
                this.advance();
                if (this.currentChar === '=') {
                    this.advance();
                    tokens.push(new Token('GTE', '>=', line, col));
                } else {
                    tokens.push(new Token('GT', '>', line, col));
                }
                continue;
            }

            if (this.currentChar === '<') {
                this.advance();
                if (this.currentChar === '=') {
                    this.advance();
                    tokens.push(new Token('LTE', '<=', line, col));
                } else {
                    tokens.push(new Token('LT', '<', line, col));
                }
                continue;
            }
            
            if (this.currentChar === '+') {
    this.advance();
    if (this.currentChar === '=') {
        this.advance();
        tokens.push(new Token('PLUS_ASSIGN', '+=', line, col));
    } else {
        tokens.push(new Token('PLUS', '+', line, col));
    }
    continue;
}

if (this.currentChar === '-') {
    this.advance();
    if (this.currentChar === '=') {
        this.advance();
        tokens.push(new Token('MINUS_ASSIGN', '-=', line, col));
    } else {
        tokens.push(new Token('MINUS', '-', line, col));
    }
    continue;
}

if (this.currentChar === '*') {
    this.advance();
    if (this.currentChar === '=') {
        this.advance();
        tokens.push(new Token('MULT_ASSIGN', '*=', line, col));
    } else {
        tokens.push(new Token('MULT', '*', line, col));
    }
    continue;
}

if (this.currentChar === '/') {
    this.advance();
    if (this.currentChar === '=') {
        this.advance();
        tokens.push(new Token('DIV_ASSIGN', '/=', line, col));
    } else {
        tokens.push(new Token('DIV', '/', line, col));
    }
    continue;
}

            const symbols = {
                '(': 'LPAREN',
                ')': 'RPAREN',
                '[': 'LBRACKET',
                ']': 'RBRACKET',
                '{': 'LBRACE',      // ✅ NOVO
                '}': 'RBRACE',      // ✅ NOVO
                ':': 'COLON',
                ',': 'COMMA',
                '.': 'DOT'
            };

            if (symbols[this.currentChar]) {
                const char = this.currentChar;
                this.advance();
                tokens.push(new Token(symbols[char], char, line, col));
                continue;
            }

            if (/[a-zA-Z_áàãâéêíóôõúç]/i.test(this.currentChar)) {
                tokens.push(this.readIdentifier());
                continue;
            }

            throw new Error(
                `Caractere inválido '${this.currentChar}' na linha ${this.line}, coluna ${this.column}`
            );
        }

        tokens.push(new Token('EOF', null, this.line, this.column));
        return tokens;
    }
}

module.exports = Lexer;
