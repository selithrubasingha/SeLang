package com.craftinginterpreters.lox;

import java.util.List;

import static com.craftinginterpreters.lox.TokenType.*;


public class Parser {

    private final List<Token> tokens;
    private int current = 0 ;


    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    private Expr expression(){
        return equality();
    }

    // Parses == and !=, wrapping comparison expressions in binary expressions.
    private Expr equality() {
        Expr expr = comparison();

        while (match(TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // Parses <, <=, >, and >=, wrapping term expressions in binary expressions.
    private Expr comparison() {
        Expr expr = term();

        while (match(GREATER, GREATER_EQUAL,
                LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // Parses + and -, wrapping factor expressions in binary expressions.
    private Expr term() {
       Expr expr = factor();

        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // Parses * and /, wrapping unary expressions in binary expressions.
    private Expr factor() {
       Expr expr = unary();

        while (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // Parses ! and unary -, wrapping another unary expression or a primary expression.
    private Expr unary() {

        if (match(BANG ,MINUS ) ){
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return primary(); 
    }

    // Parses literals or parentheses, with grouping wrapping a full expression.
    private Expr primary() {
       if (match(FALSE)) return new Expr.Literal(false);
       if (match(TRUE)) return new Expr.Literal(true);
       if (match(NIL)) return new Expr.Literal(null);
       if (match(NUMBER, STRING)) return new Expr.Literal(previous().literal);

       if (match(LEFT_PAREN)) {
           Expr expr = expression();
           consume(RIGHT_PAREN, "Expect ')' after expression.");
           return new Expr.Grouping(expr);
       }

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
