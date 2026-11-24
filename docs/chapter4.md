# Chapter 4 — Scanning (Lexical Analysis)

> **SeLang development notes** · Based on Chapter 4 of Robert Nystrom's *Crafting Interpreters* (Part II: A Tree-Walk Interpreter).
>
> **Why this file exists:** If I come back to this project in four months, this should remind me **what I built, how it works, and why I made these design choices** without rereading the entire chapter.

## TL;DR — what did I actually build?

I built a **scanner** (also called a **lexer**) in Java. It takes source code as a `String` and transforms that string into a `List<Token>`.

```text
Source code (characters)       Scanner                    Tokens
────────────────────────       ───────                    ──────
var x = 42 + 8;             ────────────────►        VAR, IDENTIFIER,
                                                     EQUAL, NUMBER,
                                                     PLUS, NUMBER,
                                                     SEMICOLON, EOF
```

This is the **first phase** of the interpreter. It does **not** execute code, evaluate `42 + 8`, check whether expressions are grammatically valid, or build an abstract syntax tree. It only **recognizes meaningful pieces of text**. A later parser will consume these tokens.

---

## 1. The files and their responsibilities

The book calls its Java interpreter **jlox** and uses package `com.craftinginterpreters.lox`. My SeLang version uses the corresponding classes (for example, package `com.selang`, if that's how my source tree is configured).

| File | What it does | Important things inside |
|---|---|---|
| `Lox.java` | Program entry point, file/REPL input, and error reporting | `main()`, `runFile()`, `runPrompt()`, `run()`, `error()`, `hadError` |
| `TokenType.java` | Defines **all categories of token** | `enum TokenType` |
| `Token.java` | Represents **one recognized token** | `type`, `lexeme`, `literal`, `line` |
| `Scanner.java` | Walks over the characters and builds tokens | `scanTokens()`, `scanToken()`, cursor fields, helpers |

**Remember:** `Lox.java` is the application shell. `Scanner.java` performs the lexical analysis. `Token.java` and `TokenType.java` describe its output.

## 2. The three concepts I must not confuse

### Character vs lexeme vs token

For this statement:

```lox
var language = "lox";
```

- **Character:** one raw character, such as `v`, `a`, `r`, or `=`.
- **Lexeme:** the exact substring that forms one meaningful unit: `var`, `language`, `=`, `"lox"`, `;`.
- **Token:** the lexeme **plus metadata** such as its type, literal value, and source line.

A token has this conceptual shape:

```java
class Token {
    final TokenType type;  // e.g., NUMBER, VAR, IDENTIFIER
    final String lexeme;   // exact original text, e.g., "42" or "\"lox\""
    final Object literal;  // interpreted data, e.g., 42.0 or "lox"; otherwise null
    final int line;        // source line, used for error reporting
}
```

Examples:

| Source text | Type | Lexeme | Literal value |
|---|---|---|---|
| `var` | `VAR` | `"var"` | `null` |
| `language` | `IDENTIFIER` | `"language"` | `null` |
| `42` | `NUMBER` | `"42"` | `42.0` (Java `Double`) |
| `"lox"` | `STRING` | `"\"lox\""` | `"lox"` (quotes removed) |
| `>=` | `GREATER_EQUAL` | `">="` | `null` |

**Why both `lexeme` and `literal`?** A lexeme preserves exactly what the programmer typed; a literal holds the **usable value** that later interpreter stages can work with. Keywords like `true`, `false`, and `nil` are recognized as keyword tokens in this chapter, not converted into literal values here.

### Token types

`TokenType` is an `enum`, not a collection of random strings. Its categories include:

- **Punctuation:** parentheses, braces, comma, dot, semicolon, etc.
- **Operators:** `+`, `-`, `*`, `/`, `!`, `!=`, `=`, `==`, `<`, `<=`, `>`, `>=`.
- **Literal categories:** `IDENTIFIER`, `STRING`, `NUMBER`.
- **Reserved words:** `var`, `if`, `else`, `while`, `for`, `fun`, `return`, `print`, `class`, etc.
- **`EOF`:** special end-of-input marker added by the scanner.

`EOF` is useful to the future parser: it can detect the end of the token stream without special out-of-bounds logic everywhere.

---

## 3. The three integers that make the scanner work

Inside `Scanner.java`:

```java
private final String source;
private final List<Token> tokens = new ArrayList<>();
private int start = 0;
private int current = 0;
private int line = 1;
```

| Field | Meaning |
|---|---|
| `source` | Complete program text to scan |
| `tokens` | Tokens recognized so far |
| `start` | Index **where the current lexeme began** |
| `current` | Index of the **next character to consume** |
| `line` | Source line number, starting at 1 |

**Crucial invariant:** when adding a token, the lexeme is `source.substring(start, current)`. Java's `substring` includes `start` but **excludes** `current`.

For `var x`, while recognizing `var`:

```text
source:  v  a  r     x
index:   0  1  2  3  4
         ^        ^
       start    current (after consuming 'r')

source.substring(0, 3) == "var"
```

### Main scanning loop

```java
List<Token> scanTokens() {
    while (!isAtEnd()) {
        start = current;  // new lexeme starts here
        scanToken();      // consume and classify it
    }

    tokens.add(new Token(TokenType.EOF, "", null, line));
    return tokens;
}
```

Each iteration starts at a fresh lexeme. `scanToken()` consumes enough characters to recognize **one token**, or skips meaningless text such as spaces/comments. When source input ends, append `EOF`.

## 4. The tiny helper methods (memorize their differences)

| Method | Purpose | Moves `current`? |
|---|---|---|
| `isAtEnd()` | Test `current >= source.length()` | No |
| `advance()` | Return `source.charAt(current++)` | **Yes** |
| `peek()` | See current character without consuming it | No |
| `peekNext()` | See one character beyond the current one | No |
| `match(expected)` | If next character equals `expected`, consume it and return `true` | **Only on success** |
| `addToken(type)` | Add token without a literal (`null`) | No |
| `addToken(type, literal)` | Add token with extracted lexeme and literal | No |

`peek()` and `peekNext()` return a sentinel `\0` at the end, avoiding an out-of-range access. (The scanner does not treat `\0` as a real source character.)

**Do not mix up `match()` with `peek()`:** `match('=')` checks **and consumes** `=` if it is there; `peek()` just looks.

## 5. What `scanToken()` recognizes

This method reads the first character (`char c = advance()`) and then decides which lexical rule applies:

| Input pattern | How it is handled | Example |
|---|---|---|
| Single-character punctuation/operator | Immediately add a token | `(` → `LEFT_PAREN` |
| Potentially two-character operators | Use `match('=')` for the second char | `!` vs `!=` |
| Slash `/` | If followed by `/`, skip a line comment; otherwise emit `SLASH` | `// hello` vs `a / b` |
| Spaces, tabs, carriage returns | Ignore | ` `, `\t`, `\r` |
| Newline | Ignore as a token, but increment `line` | `\n` |
| `"` | Call `string()` | `"hello"` |
| Digit | Call `number()` | `12.5` |
| Letter/underscore | Call `identifier()` | `score`, `while` |
| Anything else | Report a scanning error | unexpected `@` |

### One-character vs two-character operators

For `!`, `=`, `<`, and `>`, consume the optional `=` to form `!=`, `==`, `<=`, `>=`.

```java
case '!' -> addToken(match('=')
    ? TokenType.BANG_EQUAL : TokenType.BANG);
```

This decision happens **during scanning**, not later in the parser.

### Comments and whitespace

- `//` begins a comment that continues **until just before** the newline or end of input.
- Comment text is consumed but **no comment token is emitted**.
- The newline is processed separately so `line` is updated correctly.
- Spaces and tabs separate lexemes but do not produce tokens.

## 6. Scanning longer lexemes

### Strings: `string()`

When encountering an opening `"`:

1. Keep consuming characters until the closing `"` or end of input.
2. Increment `line` if the string spans multiple lines (Lox **allows multiline strings**).
3. If end of input arrives first, report **`Unterminated string.`**
4. Otherwise consume the closing quote and extract `source.substring(start + 1, current - 1)`.
5. Add a `STRING` token whose `lexeme` includes quotes but `literal` does not.

Example: source text `"hello"` becomes lexeme `"\"hello\""` and literal `"hello"`.

**Limit of this chapter:** this scanner doesn't implement escape-sequence handling such as `\"` inside a quoted string.

### Numbers: `number()`

1. Consume all consecutive digits.
2. Optionally consume `.` **only if a digit follows it** (`peek() == '.' && isDigit(peekNext())`).
3. Consume remaining fractional digits.
4. Convert the whole lexeme with `Double.parseDouble(...)`.
5. Add a `NUMBER` token with the resulting Java `Double`.

```text
123      → NUMBER, literal 123.0
12.34    → NUMBER, literal 12.34
123.     → NUMBER(123.0), DOT  // dot is not part of the number here
```

Why the second lookahead? Without checking the character after `.`, we might eat a dot that belongs to a different token.

### Identifiers and keywords: `identifier()`

1. Start when the character is an ASCII letter or `_`.
2. Continue while the next character is an ASCII letter, digit, or `_`.
3. Extract the complete word.
4. Look it up in the reserved-word map (`keywords`).
5. Emit its keyword token type if found; otherwise emit `IDENTIFIER`.

```text
var       → VAR
variable  → IDENTIFIER
or        → OR
orchid    → IDENTIFIER
name_2    → IDENTIFIER
```

The chapter's helper methods deliberately define `isAlpha()`/`isDigit()` using ASCII ranges; this isn't a general Unicode identifier scanner.

---

## 7. Maximal munch — the most important scanning rule

**Maximal munch:** When more than one lexical rule could match the input, prefer the match that consumes the **most characters**.

Examples:

```text
>=       → GREATER_EQUAL       (not GREATER + EQUAL)
==       → EQUAL_EQUAL         (not EQUAL + EQUAL)
orchid   → IDENTIFIER         (not OR + IDENTIFIER("chid"))
variable → IDENTIFIER         (not VAR + IDENTIFIER("iable"))
```

**Why scan the whole identifier before checking whether it's a keyword?** Because `or` is a keyword but `orchid` is a legal identifier. Recognizing keywords by prefix would incorrectly split valid identifiers.

The scanner follows lexical rules even if that produces a token sequence the parser later rejects. **Lexing is not parsing.**

## 8. Error handling and the interpreter shell

Chapter 4 also creates the basic **application framework**:

```text
Lox.main(args)
   ├── a script path provided → runFile(path)
   │                              └── run(source)
   └── no arguments          → runPrompt()  [interactive REPL]
                                  └── run(source) per line

run(source)
   └── new Scanner(source)
          └── scanTokens()
                 └── List<Token> → print for now
```

`Lox.error(line, message)` handles reporting. The scanner **detects** the error; the `Lox` class **reports** it. This keeps presentation of errors separate from the logic that detects them.

- `hadError` tracks whether scanning encountered an error.
- In file mode, a reported error causes a nonzero process exit (book uses **65**).
- In interactive mode, reset `hadError` after each input line so one mistake doesn't end the session.
- A bad character or unterminated string is a scanning error; the scanner need not crash immediately.
- A future parser will use the token's `line` metadata for diagnostics.

**Note:** Chapter 4's basic REPL scans **each entered line separately**; it is not yet a sophisticated multi-line REPL.

## 9. Complete mental trace: what happens to actual code?

Input:

```lox
var score = 12.5;
if (score >= 10) print "pass"; // comment
```

The scanner advances character by character and produces approximately this token stream (shown here as `TYPE | lexeme | literal`):

```text
VAR           | var       | null
IDENTIFIER    | score     | null
EQUAL         | =         | null
NUMBER        | 12.5      | 12.5
SEMICOLON     | ;         | null
IF            | if        | null
LEFT_PAREN    | (         | null
IDENTIFIER    | score     | null
GREATER_EQUAL | >=        | null
NUMBER        | 10        | 10.0
RIGHT_PAREN   | )         | null
PRINT         | print     | null
STRING        | "pass"    | pass
SEMICOLON     | ;         | null
EOF           |           | null
```

The `// comment` produces **no token**. Spaces/newlines produce **no tokens**. The scanner does **not** decide whether the `if` statement is valid or whether `score >= 10` is true.

## 10. Things I'll probably forget in four months

- `start` is the beginning of the **current lexeme**; `current` is the **next unread character**.
- `advance()` consumes; `peek()` only observes; `match()` conditionally consumes.
- `substring(start, current)` excludes `current`.
- `addToken()` captures text from the **original** source, not a reconstructed string.
- `Token.lexeme` and `Token.literal` are **not** the same thing.
- `scanTokens()` appends `EOF` after the loop.
- `>=`, `<=`, `==`, `!=` must be kept intact as single tokens.
- Recognize the **entire identifier first**, **then** check the keywords map.
- `//` comments and whitespace are discarded, but newlines update `line`.
- `number()` accepts a fractional part only when `.` is followed by a digit.
- This chapter's number values are Java `Double`s, including integers like `10` → `10.0`.
- Scanning produces **tokens**, not ASTs, and does not evaluate programs.

## 11. Quick smoke tests after coming back

Try these in the SeLang input file/REPL and inspect the emitted tokens:

```lox
var count = 42;
print count + 0.5;
if (count != 0) print "ok";
orchid = true; // orchid must be IDENTIFIER, not OR
```

Then verify these edge cases:

```lox
123.          // NUMBER, DOT (not a floating-point literal 123.)
foo_2         // IDENTIFIER
"two
lines"       // one multiline STRING; line counter increments
"unfinished   // unterminated string error if input ends here
@             // unexpected character error
```

> **When revisiting:** Open `Scanner.java` and find the fields `start/current/line`, then read `scanTokens()` → `scanToken()` → `advance()/peek()/match()` → `string()/number()/identifier()`. That path reconstructs almost the whole chapter.

---

## 12. What's next?

**Chapter 5 — Representing Code:** Represent expressions as objects forming an **abstract syntax tree (AST)**. The book introduces the structure and code-generation helper for expression classes.

**Chapter 6 — Parsing Expressions:** Consume the tokens generated here and build those expression objects, using grammatical rules.

```text
Source text ──[Chapter 4: Scanner ✅]──► Tokens
                                      │
                                      ▼
                               [Chapter 6: Parser]──► AST
                                                       │
                                                       ▼
                                                [Interpretation later]
```

**Milestone:** The front end can now recognize the language's vocabulary. The next job is teaching it the grammar.

---

**Reference:** Robert Nystrom, *Crafting Interpreters*, Chapter 4, §§4.1–4.7. These are study notes describing the book's Chapter 4 implementation and the corresponding SeLang learning milestone; they are **not a verification of the exact current contents of my local Git repository**.
