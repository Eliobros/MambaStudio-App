class Parser {
    constructor(tokens) {
        this.tokens = tokens;
        this.pos = 0;
        this.currentToken = this.tokens[this.pos];
    }

    advance() {
        this.pos++;
        this.currentToken = this.pos < this.tokens.length ? this.tokens[this.pos] : null;
    }

    // Helper: pega a linha e coluna do token atual sem perder a referência
    loc() {
        if (!this.currentToken) return { line: 0, column: 0 };
        return { line: this.currentToken.line, column: this.currentToken.column };
    }

    expect(type) {
        if (!this.currentToken) {
            throw new Error(`❌ Fim inesperado do código. Esperava ${this.translateToken(type)}.`);
        }
        if (this.currentToken.type !== type) {
            throw new Error(
                `❌ Erro na linha ${this.currentToken.line}, coluna ${this.currentToken.column}:\n` +
                `   Esperado ${this.translateToken(type)}, mas encontrado ${this.translateToken(this.currentToken.type)}\n` +
                `   ${this.getHint(type)}`
            );
        }
        const token = this.currentToken;
        this.advance();
        return token;
    }

    translateToken(type) {
        const translations = {
            'END': '"fim"',
            'COLON': '":"',
            'LPAREN': '"("',
            'RPAREN': '")"',
            'LBRACKET': '"["',
            'RBRACKET': '"]"',
            'LBRACE': '"{"',      // ✅ NOVO
            'RBRACE': '"}"',      // ✅ NOVO
            'COMMA': '","',       // ✅ NOVO
            'IF': '"se"',
            'ELSE': '"senao"',
            'WHILE': '"enquanto"',
            'FUNCTION': '"funcao"',
            'PRINT': '"escreva"',
            'VAR': '"variavel"',
            'RETURN': '"retorna"',
        };
        return translations[type] || type;
    }

    getHint(type) {
        const hints = {
            'END': '💡 Dica: Você esqueceu de fechar o bloco com "fim"?',
            'COLON': '💡 Dica: Você esqueceu de colocar ":" depois da condição?',
            'RPAREN': '💡 Dica: Você esqueceu de fechar o parêntese ")"?',
            'RBRACKET': '💡 Dica: Você esqueceu de fechar o colchete "]"?',
            'RBRACE': '💡 Dica: Você esqueceu de fechar a chave "}"?',    // ✅ NOVO
            'COMMA': '💡 Dica: Você esqueceu de separar os itens com vírgula?',  // ✅ NOVO
        };
        return hints[type] || '';
    }

    parse() {
        const statements = [];
        while (this.currentToken && this.currentToken.type !== 'EOF') {
            statements.push(this.statement());
        }
        return { type: 'Program', body: statements, line: 1, column: 1 };
    }

    statement() {
    switch (this.currentToken.type) {
        case 'PRINT':
            return this.printStatement();
        case 'VAR':
            return this.varStatement();
        case 'IF':
            return this.ifStatement();
        case 'WHILE':
            return this.whileStatement();
        case 'FOR':
            return this.forStatement();
        case 'FUNCTION':
            return this.functionDeclaration();
        case 'RETURN':
            return this.returnStatement();
        case 'IMPORT':
            return this.importStatement();
        case 'BREAK':
            return this.breakStatement();
        case 'CONTINUE':
            return this.continueStatement();
        case 'SWITCH':
            return this.switchStatement();
        case 'TENTE':
            return this.tryCatchStatement();

        case 'IDENTIFIER': {
            const loc = this.loc();
            const savedPos = this.pos;
            this.expect('IDENTIFIER');

            // resultado = valor | resultado += valor | -= | *= | /=
            if (this.currentToken && ['ASSIGN', 'PLUS_ASSIGN', 'MINUS_ASSIGN', 'MULT_ASSIGN', 'DIV_ASSIGN'].includes(this.currentToken.type)) {
                // Rebobina para que assignmentStatement consuma o IDENTIFIER de novo
                this.pos = savedPos;
                this.currentToken = this.tokens[this.pos];
                return this.assignmentStatement(loc);
            }

            // obj.prop = valor
            if (this.currentToken && this.currentToken.type === 'DOT') {
    this.advance();
    this.expect('IDENTIFIER');

    if (this.currentToken && ['ASSIGN', 'PLUS_ASSIGN', 'MINUS_ASSIGN', 'MULT_ASSIGN', 'DIV_ASSIGN'].includes(this.currentToken.type)) {
        this.pos = savedPos;
        this.currentToken = this.tokens[this.pos];
        return this.assignmentStatement(loc);
    }

    // 🔧 FIX: rebobina e deixa expression()/factor() parsear a chamada completa
    this.pos = savedPos;
    this.currentToken = this.tokens[this.pos];

    const exprDot = this.expression();
    exprDot.line = loc.line;
    exprDot.column = loc.column;
    return { type: 'ExpressionStatement', expression: exprDot, line: loc.line, column: loc.column };
}

            // resultado[chave] = valor
            if (this.currentToken && this.currentToken.type === 'LBRACKET') {
    this.advance();
    this.comparison(); // consome o índice (troquei expression() por comparison() por segurança, mas não é o foco)
    this.expect('RBRACKET');

    if (this.currentToken && ['ASSIGN', 'PLUS_ASSIGN', 'MINUS_ASSIGN', 'MULT_ASSIGN', 'DIV_ASSIGN'].includes(this.currentToken.type)) {
        this.pos = savedPos;
        this.currentToken = this.tokens[this.pos];
        return this.assignmentStatement(loc);
    }

    // 🔧 FIX: rebobina e deixa expression()/factor() parsear tudo desde o início
    this.pos = savedPos;
    this.currentToken = this.tokens[this.pos];

    const exprIdx = this.expression();
    exprIdx.line = loc.line;
    exprIdx.column = loc.column;
    return { type: 'ExpressionStatement', expression: exprIdx, line: loc.line, column: loc.column };
}

            // Chamada de função ou expressão standalone
            const exprStmt = this.expression();
            exprStmt.line = loc.line;
            exprStmt.column = loc.column;
            return { type: 'ExpressionStatement', expression: exprStmt, line: loc.line, column: loc.column };
        }

        default:
            throw new Error(
                `❌ Erro na linha ${this.currentToken.line}: ` +
                `Statement inválido: ${this.currentToken.type}`
            );
    }
}



    printStatement() {
        const loc = this.loc();
        this.advance();
        const value = this.comparison();
        return { type: 'Print', value, line: loc.line, column: loc.column };
    }

    varStatement() {
        const loc = this.loc();
        this.advance();
        const name = this.expect('IDENTIFIER').value;
        this.expect('ASSIGN');
        const value = this.comparison();
        return { type: 'VarDeclaration', name, value, line: loc.line, column: loc.column };
    }

    breakStatement() {
        const loc = this.loc();
        this.advance();
        return { type: 'Break', line: loc.line, column: loc.column };
    }

    continueStatement() {
        const loc = this.loc();
        this.advance();
        return { type: 'Continue', line: loc.line, column: loc.column };
    }

    switchStatement() {
        const loc = this.loc();
        this.advance();
        const value = this.comparison();
        this.expect('COLON');

        const cases = [];
        let defaultBody = null;

        while (
            this.currentToken &&
            this.currentToken.type !== 'END' &&
            this.currentToken.type !== 'EOF'
        ) {
            if (this.currentToken.type === 'CASE') {
                this.advance();
                const caseValue = this.comparison();
                this.expect('COLON');

                const body = [];
                while (
                    this.currentToken &&
                    !['CASE', 'DEFAULT', 'END', 'EOF'].includes(this.currentToken.type)
                ) {
                    body.push(this.statement());
                }

                cases.push({ value: caseValue, body });

            } else if (this.currentToken.type === 'DEFAULT') {
                this.advance();
                this.expect('COLON');

                defaultBody = [];
                while (
                    this.currentToken &&
                    !['CASE', 'END', 'EOF'].includes(this.currentToken.type)
                ) {
                    defaultBody.push(this.statement());
                }
            } else {
                break;
            }
        }

        this.expect('END');

        return { type: 'Switch', value, cases, defaultBody, line: loc.line, column: loc.column };
    }

    ifStatement() {
        const loc = this.loc();
        this.advance();
        const condition = this.comparison();
        this.expect('COLON');

        const body = [];
        while (
            this.currentToken &&
            !['ELSE', 'END', 'EOF'].includes(this.currentToken.type)
        ) {
            body.push(this.statement());
        }

        let elseBody = null;

        if (this.currentToken && this.currentToken.type === 'ELSE') {
            this.advance();
            this.expect('COLON');
            elseBody = [];

            while (
                this.currentToken &&
                this.currentToken.type !== 'END' &&
                this.currentToken.type !== 'EOF'
            ) {
                elseBody.push(this.statement());
            }
        }

        this.expect('END');

        return { type: 'If', condition, body, elseBody, line: loc.line, column: loc.column };
    }

    whileStatement() {
        const loc = this.loc();
        this.advance();
        const condition = this.comparison();
        this.expect('COLON');

        const body = [];
        while (
            this.currentToken &&
            this.currentToken.type !== 'END' &&
            this.currentToken.type !== 'EOF'
        ) {
            body.push(this.statement());
        }

        this.expect('END');

        return { type: 'While', condition, body, line: loc.line, column: loc.column };
    }

    functionDeclaration() {
        const loc = this.loc();
        this.advance();
        const name = this.expect('IDENTIFIER').value;
        this.expect('LPAREN');

        const params = [];
        while (this.currentToken && this.currentToken.type !== 'RPAREN') {
            params.push(this.expect('IDENTIFIER').value);
            if (this.currentToken.type === 'COMMA') {
                this.advance();
            }
        }

        this.expect('RPAREN');
        this.expect('COLON');

        const body = [];
        while (
            this.currentToken &&
            this.currentToken.type !== 'END' &&
            this.currentToken.type !== 'EOF'
        ) {
            body.push(this.statement());
        }

        this.expect('END');

        return { type: 'FunctionDeclaration', name, params, body, line: loc.line, column: loc.column };
    }

    returnStatement() {
        const loc = this.loc();
        this.advance();
        const value = this.comparison();
        return { type: 'Return', value, line: loc.line, column: loc.column };
    }

    forStatement() {
    const loc = this.loc();
    this.advance();

    if (this.currentToken && this.currentToken.type === 'CADA') {
        this.advance();

const varNames = [];
varNames.push(this.expect('IDENTIFIER').value);

while (this.currentToken && this.currentToken.type === 'COMMA') {
    this.advance();
    varNames.push(this.expect('IDENTIFIER').value);
}

this.expect('EM');

const iterable = this.comparison();
        this.expect('COLON');

        const body = [];
        while (this.currentToken && this.currentToken.type !== 'END' && this.currentToken.type !== 'EOF') {
            body.push(this.statement());
        }
        this.expect('END');
        return {
    type: 'ForEach',
    varNames,
    iterable,
    body,
    line: loc.line,
    column: loc.column
};
    }

    const varName = this.expect('IDENTIFIER').value;
    this.expect('DE');
    const start = this.comparison();
    this.expect('ATE');
    const end = this.comparison();
    this.expect('COLON');

    const body = [];
    while (this.currentToken && this.currentToken.type !== 'END' && this.currentToken.type !== 'EOF') {
        body.push(this.statement());
    }
    this.expect('END');
    return { type: 'For', varName, start, end, body, line: loc.line, column: loc.column };
}

    importStatement() {
    const loc = this.loc();
    this.advance();

    if (this.currentToken.type === 'LBRACE') {
        this.advance();
        const names = [];
        while (this.currentToken.type !== 'RBRACE') {
            names.push(this.expect('IDENTIFIER').value);
            if (this.currentToken.type === 'COMMA') {
                this.advance();
            }
        }
        this.advance();
        this.expect('DE');
        const source = this.expect('STRING').value;
        return { type: 'ImportNamed', names, source, line: loc.line, column: loc.column };
    }

    const name = this.expect('IDENTIFIER').value;
    this.expect('DE');
    const source = this.expect('STRING').value;
    return { type: 'Import', name, source, line: loc.line, column: loc.column };
}

    assignmentStatement(locHint = null) {
    const loc = locHint || this.loc();
    const name = this.expect('IDENTIFIER').value;
    let target = { type: 'Identifier', name, line: this.currentToken.line, column: this.currentToken.column };

    while (this.currentToken && (
        this.currentToken.type === 'DOT' ||
        this.currentToken.type === 'LBRACKET'
    )) {
        if (this.currentToken.type === 'DOT') {
            this.advance();
            const property = this.expect('IDENTIFIER').value;
            target = { type: 'PropertyAccess', object: target, property, line: loc.line, column: loc.column };
        } else if (this.currentToken.type === 'LBRACKET') {
            this.advance();
            const index = this.comparison();
            this.expect('RBRACKET');
            target = { type: 'IndexAccess', object: target, index, line: loc.line, column: loc.column };
        }
    }

    const assignOps = {
        'ASSIGN': null,
        'PLUS_ASSIGN': 'PLUS',
        'MINUS_ASSIGN': 'MINUS',
        'MULT_ASSIGN': 'MULT',
        'DIV_ASSIGN': 'DIV'
    };

    if (!this.currentToken || !(this.currentToken.type in assignOps)) {
        throw new Error(
            `❌ Erro na linha ${this.currentToken ? this.currentToken.line : '?'}: ` +
            `Esperava operador de atribuição (=, +=, -=, *=, /=)`
        );
    }

    const opType = this.currentToken.type;
    this.advance();

    let value = this.comparison();

    const binOp = assignOps[opType];
    if (binOp) {
        value = { type: 'BinaryOp', operator: binOp, left: target, right: value, line: this.currentToken.line, column: this.currentToken.column };
    }

    return { type: 'Assignment', name: target, value, line: loc.line, column: loc.column };
}

    comparison() {
        let left = this.comparisonUnit();

        while (
            this.currentToken &&
            ['AND', 'OR'].includes(this.currentToken.type)
        ) {
            const opLine = this.currentToken.line;
            const opCol = this.currentToken.column;
            const operator = this.currentToken.type;
            this.advance();
            const right = this.comparisonUnit();
            left = { type: 'LogicalOp', operator, left, right, line: opLine, column: opCol };
        }

        return left;
    }

    comparisonUnit() {
        if (this.currentToken && this.currentToken.type === 'NOT') {
            const opLine = this.currentToken.line;
            const opCol = this.currentToken.column;
            this.advance();
            const operand = this.comparisonUnit();
            return { type: 'UnaryOp', operator: 'NOT', operand, line: opLine, column: opCol };
        }

        let left = this.expression();

        if (
            this.currentToken &&
            ['GT', 'LT', 'EQ', 'NEQ', 'GTE', 'LTE', 'KW_GT', 'KW_LT', 'KW_EQ', 'KW_GTE', 'KW_LTE'].includes(this.currentToken.type)
        ) {
            const opLine = this.currentToken.line;
            const opCol = this.currentToken.column;
            let operator = this.currentToken.type;
            const kwMap = { 'KW_GT': 'GT', 'KW_LT': 'LT', 'KW_EQ': 'EQ', 'KW_GTE': 'GTE', 'KW_LTE': 'LTE' };
            if (kwMap[operator]) operator = kwMap[operator];
            this.advance();
            const right = this.expression();
            return { type: 'Comparison', operator, left, right, line: opLine, column: opCol };
        }

        return left;
    }

    expression() {
        let result = this.term();

        while (
            this.currentToken &&
            ['PLUS', 'MINUS', 'KW_PLUS', 'KW_MINUS'].includes(this.currentToken.type)
        ) {
            const opLine = this.currentToken.line;
            const opCol = this.currentToken.column;
            const op = this.currentToken.type;
            const operator = op === 'KW_PLUS' ? 'PLUS' : op === 'KW_MINUS' ? 'MINUS' : op;
            this.advance();
            const right = this.term();
            result = { type: 'BinaryOp', operator, left: result, right, line: opLine, column: opCol };
        }

        return result;
    }

    term() {
        let result = this.factor();

        while (
            this.currentToken &&
            ['MULT', 'DIV', 'KW_MULT', 'KW_DIV'].includes(this.currentToken.type)
        ) {
            const opLine = this.currentToken.line;
            const opCol = this.currentToken.column;
            const op = this.currentToken.type;
            const operator = op === 'KW_MULT' ? 'MULT' : op === 'KW_DIV' ? 'DIV' : op;
            this.advance();
            const right = this.factor();
            result = { type: 'BinaryOp', operator, left: result, right, line: opLine, column: opCol };
        }

        return result;
    }

    factor() {
        const token = this.currentToken;

        if (!token) {
            throw new Error('❌ Expressão incompleta');
        }

        if (token.type === 'NUMBER') {
            this.advance();
            return { type: 'Number', value: token.value, line: token.line, column: token.column };
        }

        if (token.type === 'STRING') {
            this.advance();
            return { type: 'String', value: token.value, line: token.line, column: token.column };
        }

        if (token.type === 'TRUE') {
            this.advance();
            return { type: 'Boolean', value: true, line: token.line, column: token.column };
        }

        if (token.type === 'FALSE') {
            this.advance();
            return { type: 'Boolean', value: false, line: token.line, column: token.column };
        }

        if (token.type === 'NULL') {
            this.advance();
            return { type: 'Null', line: token.line, column: token.column };
        }

        if (token.type === 'AWAIT') {
            this.advance();
            const expr = this.factor();
            return { type: 'Await', expression: expr, line: token.line, column: token.column };
        }

        if (token.type === 'LBRACKET') {
            return this.arrayLiteral();
        }

        if (token.type === 'LBRACE') {
            return this.objectLiteral();
        }

        if (token.type === 'FUNCTION') {
            this.advance();

            this.expect('LPAREN');
            const params = [];
            while (this.currentToken && this.currentToken.type !== 'RPAREN') {
                params.push(this.expect('IDENTIFIER').value);
                if (this.currentToken.type === 'COMMA') {
                    this.advance();
                }
            }
            this.expect('RPAREN');

            this.expect('COLON');

            const body = [];
            while (this.currentToken && this.currentToken.type !== 'END') {
                body.push(this.statement());
            }
            this.expect('END');

            return {
                type: 'FunctionLiteral',
                params: params,
                body: body,
                line: token.line,
                column: token.column
            };
        }


        if (token.type === 'IDENTIFIER') {
            let result = { type: 'Identifier', name: token.value, line: token.line, column: token.column };
            this.advance();

            if (this.currentToken && this.currentToken.type === 'LPAREN') {
                this.advance();
                const args = [];

                while (this.currentToken.type !== 'RPAREN') {
                    args.push(this.comparison());
                    if (this.currentToken.type === 'COMMA') {
                        this.advance();
                    }
                }
                this.expect('RPAREN');

                result = { type: 'FunctionCall', name: result.name, args, line: token.line, column: token.column };
            }

            if (this.currentToken && this.currentToken.type === 'LBRACKET') {
                this.advance();
                const index = this.comparison();
                this.expect('RBRACKET');
                result = { type: 'ArrayAccess', array: result, index, line: token.line, column: token.column };
            }

            while (this.currentToken && this.currentToken.type === 'DOT') {
                this.advance();
                const propertyOrMethod = this.expect('IDENTIFIER').value;

                if (this.currentToken && this.currentToken.type === 'LPAREN') {
                    this.advance();
                    const args = [];

                    while (this.currentToken.type !== 'RPAREN') {
                        args.push(this.comparison());
                        if (this.currentToken.type === 'COMMA') {
                            this.advance();
                        }
                    }
                    this.expect('RPAREN');

                    result = {
                        type: 'MethodCall',
                        object: result,
                        method: propertyOrMethod,
                        args,
                        line: token.line,
                        column: token.column
                    };
                } else {
                    result = {
                        type: 'PropertyAccess',
                        object: result,
                        property: propertyOrMethod,
                        line: token.line,
                        column: token.column
                    };
                }
            }

            return result;
        }

        if (token.type === 'LPAREN') {
            this.advance();
            const expr = this.comparison();
            this.expect('RPAREN');
            return expr;
        }

        throw new Error(
            `❌ Erro na linha ${token.line}, coluna ${token.column}: ` +
            `Token inesperado: ${token.type}`
        );
    }

    tryCatchStatement() {
    const loc = this.loc();
    this.advance();
    this.expect('COLON');

    const body = [];
    while (
        this.currentToken &&
        this.currentToken.type !== 'CAPTURAR' &&
        this.currentToken.type !== 'EOF'
    ) {
        body.push(this.statement());
    }

    this.expect('CAPTURAR');
    this.expect('LPAREN');
    const errorVar = this.expect('IDENTIFIER').value;
    this.expect('RPAREN');
    this.expect('COLON');

    const catchBody = [];
    while (
        this.currentToken &&
        this.currentToken.type !== 'END' &&
        this.currentToken.type !== 'EOF'
    ) {
        catchBody.push(this.statement());
    }

    this.expect('END');

    return { type: 'TryCatch', body, errorVar, catchBody, line: loc.line, column: loc.column };
}

    arrayLiteral() {
        const startLine = this.currentToken.line;
        const startCol = this.currentToken.column;
        this.expect('LBRACKET');
        const elements = [];

        while (this.currentToken && this.currentToken.type !== 'RBRACKET') {
            elements.push(this.comparison());
            if (this.currentToken.type === 'COMMA') {
                this.advance();
            }
        }

        this.expect('RBRACKET');

        return { type: 'ArrayLiteral', elements, line: startLine, column: startCol };
    }

    objectLiteral() {
        const startLine = this.currentToken.line;
        const startCol = this.currentToken.column;
        this.expect('LBRACE');
        const properties = {};

        while (this.currentToken && this.currentToken.type !== 'RBRACE') {
            let key;

            if (this.currentToken.type === 'IDENTIFIER') {
                key = this.currentToken.value;
                this.advance();
            } else if (this.currentToken.type === 'STRING') {
                key = this.currentToken.value;
                this.advance();
            } else {
                throw new Error(
                    `❌ Erro na linha ${this.currentToken.line}: ` +
                    `Esperava nome de propriedade (identificador ou string), ` +
                    `mas encontrou ${this.currentToken.type}`
                );
            }

            this.expect('COLON');

            const value = this.comparison();

            properties[key] = value;

            if (this.currentToken && this.currentToken.type === 'COMMA') {
                this.advance();
            }
        }

        this.expect('RBRACE');

        return { type: 'ObjectLiteral', properties, line: startLine, column: startCol };
    }
}

module.exports = Parser;
