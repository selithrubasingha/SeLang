package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.craftinginterpreters.lox.TokenType.*;

public class Scanner {

    private final String source;
    private final List<Token> tokens = new ArrayList<>();

    // keeps track of where the scanner is in the source code
    private int start = 0;
    private int current = 0;
    private int line = 1;


    // to store the keywords
    private static final Map<String , TokenType> keywords;

    static {
        keywords = new HashMap<>();
        keywords.put("and", AND);
        keywords.put("class", CLASS);
        keywords.put("else", ELSE);
        keywords.put("false", FALSE);
        keywords.put("for", FOR);
        keywords.put("fun", FUN);
        keywords.put("if", IF);
        keywords.put("nil", NIL);
        keywords.put("or", OR);
        keywords.put("print", PRINT);
        keywords.put("return", RETURN);
        keywords.put("super", SUPER);
        keywords.put("this", THIS);
        keywords.put("true", TRUE);
        keywords.put("var", VAR);
        keywords.put("while", WHILE);
    }
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
                if (isDigit(c)){
                    number();
                }else if (isAlpha(c)){
                    identifier();

                }else{
                    Lox.error(line , "Unexpected character. ");
                }
                Lox.error(line, "Unexpected character.");
                break;

        }


    }
    // identifying what keyword is is !
    private void identifier() {

       while (isAlphaNumeric(peek())) advance();

       String text = source.substring(start, current);
       TokenType type = keywords.get(text);
       if (type == null) type = IDENTIFIER;
       addToken(type);

       addToken(IDENTIFIER);
    }

    // simple helper functions
    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') ||
               (c >= 'A' && c <= 'Z') ||
               c == '_';
    }

    private boolean isAlphaNumeric(char c) {

        return isAlpha(c) || isDigit(c);
    }


    private void number() {
        while (isDigit(peek())) advance();
        // Look for a fractional part.
        if (peek() == '.' && isDigit(peekNext())) {
            // Consume the "."
            advance();
            while (isDigit(peek())) advance();
        }
        addToken(NUMBER,
                Double.parseDouble(source.substring(start, current)));
    }

    private char peekNext() {

        if (current+1 >= source.length()) return '\0';
        return source.charAt(current+1);
    }

    private boolean isDigit(char c) {
       return c>= '0' && c <= '9';
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
