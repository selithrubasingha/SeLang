package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.List;

import static com.craftinginterpreters.lox.TokenType.*;

public class Scanner {

    private final String source;
    private final List<Token> tokens = new ArrayList<>();

    // keeps track of where the scanner is in the source code
    private int start = 0;
    private int current = 0;
    private int line = 1;

    public Scanner(String source) {
        this.source = source;
    }

    List<Token> scanTokens() {
        while (!isAtEnd()) {
            // we are at the end of the beginning of the next lexemme
            start = current;
            scanToken();
        }

        tokens.add(new Token(EOF, "", null, line));
        return tokens;
    }

    // tells us if we've consumed all the characters ...
    private boolean isAtEnd() {
        return current >= source.length();
    }

    private void scanToken() {
        char c = advance();
        switch (c) {
            case '(':
                addToken(LEFT_PAREN);
                break;
            case ')':
                addToken(RIGHT_PAREN);
                break;
            case '{':
                addToken(LEFT_BRACE);
                break;
            case '}':
                addToken(RIGHT_BRACE);
                break;
            case ',':
                addToken(COMMA);
                break;
            case '.':
                addToken(DOT);
                break;
            case '-':
                addToken(MINUS);
                break;
            case '+':
                addToken(PLUS);
                break;
            case ';':
                addToken(SEMICOLON);
                break;
            case '*':
                addToken(STAR);
                break;
            case '!' :
                    addToken(match('=')
                    ? TokenType.BANG_EQUAL : TokenType.BANG);
            case '=' : addToken(match('=')
                    ? TokenType.EQUAL_EQUAL : TokenType.EQUAL);
            case '<' :addToken(match('=')
                    ? TokenType.LESS_EQUAL : TokenType.LESS);
            case '>' : addToken(match('=')
                    ? TokenType.GREATER_EQUAL : TokenType.GREATER);

            // the '/' needs some special handling cause / is division and // is a comment
            case '/':
                if (match('/')) {
                    // A comment goes until the end of the line.
                    while (peek() != '\n' && !isAtEnd()) advance();
                } else {
                    addToken(SLASH);
                }
                break;

            // skipping over the meaningless characters . white spaces , tabs and stuff .
            case ' ':
            case '\r':
            case '\t':
                // Ignore whitespace.
                break;
            case '\n':
                line++;
                break;

            case '"':
                string();
                break;

            default:
                Lox.error(line, "Unexpected character.");
                break;

        }


    }

    // get every between first " and the last " . which is a string ! 
    private void string() {

        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') line++;
            advance();
        }
        if (isAtEnd()) {
            Lox.error(line, "Unterminated string.");
            return;
        }
        // The closing ".
        advance();
        // Trim the surrounding quotes.
        String value = source.substring(start + 1, current - 1);
        addToken(STRING, value);


    }

    // LOOKAHEAD  , Used for inspecting the next character without consuming it.
    private char peek() {
        if (isAtEnd()) return '\0';
        return source.charAt(current);

    }

    // helper function for the <= , >= ...
    private boolean match(char expected ){
        if (isAtEnd() || source.charAt(current) != expected) return false;
        current++;
        return true;
    }

    private char advance() {
        // increments the current and returns the relavent char at the same time !
        return source.charAt(current++);
    }


    private void addToken(TokenType type) {

        addToken(type, null);
    }

    private void addToken(TokenType type, Object literal) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, literal, line));
    }

}
