package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.List;

import static com.craftinginterpreters.lox.TokenType.*;

public class Scanner {

    private final String source;
    private final List<Token> tokens = new ArrayList<>();

    private int start = 0 ;
    private int current = 0;
    private int line = 1;

    public Scanner(String source) {
        this.source = source;
    }

    List<Token> scanTokens() {
        while (!isAtEnd()){
            // we are at the end of the beginning of the next lexemme
            start = current ;
            scanToken();
        }

        tokens.add(new Token(EOF , "", null , line ));
        return tokens ;
    }


    private boolean isAtEnd(){
        return current >= source.length();
    }

    private void scanToken() {
        

    }

}
