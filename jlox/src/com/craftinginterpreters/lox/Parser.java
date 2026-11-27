package com.craftinginterpreters.lox;

import java.util.List;

import static com.craftinginterpreters.lox.TokenType.EOF;


public class Parser {

    private final List<Token> tokens;
    private int current = 0 ;


    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    private Expr expression(){
        return equality();
    }

    private Expr equality() {
        Expr expr = comparison();

        while (match(TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // helper : Consumes the next token if it matches any of the given types.
    private boolean match(TokenType... types){
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    // helper : Checks whether the next token has the given type without consuming it.
    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    // helper : Consumes and returns the next token unless parsing is at the end.
    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }
    // helper : Checks whether the next token is the end-of-file token.
    private boolean isAtEnd() {
        return peek().type == EOF ;
    }

    // helper : Returns the next token without consuming it.
    private Token peek() {
        return tokens.get(current);
    }

    // helper : Returns the most recently consumed token.
    private Token previous() {
        return tokens.get(current-1);
    }


}
