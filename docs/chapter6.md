# SeLang — Chapter 6: Parsing Expressions

> **The big idea:** Chapter 4 turned characters into tokens. Chapter 5 defined the AST classes. Chapter 6 builds those trees automatically from tokens, respecting operator precedence and reporting syntax errors. It does not execute the trees yet.

## 1. Where this chapter fits

```text
Source code: 2 + 3 * 4
       |
       v
Scanner (Chapter 4)
       |
       v
[NUMBER(2), PLUS, NUMBER(3), STAR, NUMBER(4), EOF]
       |
       v
Parser (Chapter 6) ---> uses Expr classes (Chapter 5)
       |
       v
AST:        +
           / \
          2   *
             / \
            3   4
       |
       v
Interpreter (Chapter 7) ---> 14
```

At the end of Chapter 6, we can display the tree using `AstPrinter`. We cannot calculate the result yet.

## 2. Why we changed the grammar

In Chapter 5, the binary rule was essentially `binary → expression operator expression`. Both operands could be arbitrary expressions, so `2 + 3 * 4` could produce either of these trees:

```text
       +                    *
      / \                  / \
     2   *                +   4
        / \              / \
       3   4            2   3

  2 + (3 * 4)        (2 + 3) * 4
       14                 20
```

Both trees fit the old grammar. This is **ambiguity**. We fixed it by separating operators into precedence levels.

- **Precedence:** which operator binds more tightly? `*` binds more tightly than `+`.
- **Associativity:** how do operators at the same precedence group? `10 - 3 - 2` groups as `(10 - 3) - 2`, not `10 - (3 - 2)`.

We did not need new AST node types. We changed the rules for constructing the existing ones.

## 3. The parser hierarchy (the most important part)

The parser starts at the lowest precedence and descends towards the highest:

```text
expression()          Entry point
    |
    v
 equality()           == !=
    |
    v
 comparison()         > >= < <=
    |
    v
 term()               + -
    |
    v
 factor()             * /
    |
    v
 unary()              ! - (prefix)
    |
    v
 primary()            literals and parentheses
```

| Rule | What it recognizes | Operands come from |
| --- | --- | --- |
| `expression` | Any expression | `equality` |
| `equality` | Equality / inequality | `comparison` |
| `comparison` | Relational operators | `term` |
| `term` | Addition / subtraction | `factor` |
| `factor` | Multiplication / division | `unary` |
| `unary` | Prefix negation / NOT, or a primary | `unary` / `primary` |
| `primary` | Numbers, strings, booleans, `nil`, grouping | Tokens / `expression` |

**Why start at the lowest precedence?** Because each rule asks the next, tighter-binding rule to parse its operands first. Thus, the addition rule receives a whole multiplication subtree instead of grabbing just the next number.

### Walkthrough: `2 + 3 * 4`

1. `expression()` calls `equality()`, which calls `comparison()`, which calls `term()`.
2. `term()` calls `factor()` for its left operand. Through `unary()` and `primary()`, it gets the literal `2`.
3. `factor()` sees `+`, which it does not handle, so it returns `2` without consuming `+`.
4. `term()` consumes `+` and calls `factor()` again for the right operand.
5. This time `factor()` reads `3`, sees `*`, consumes it, reads `4`, and constructs a binary multiplication node.
6. `term()` receives that entire subtree and constructs the addition node. The result passes back up to `expression()`.

The final AST is `(+ 2.0 (* 3.0 4.0))` using the book's printer. No rearrangement is needed afterwards.

A single literal such as `5` also works: each rule delegates downwards and returns the same node without finding an operator of its own.

### Parentheses and unary operators

`primary()` can consume `(`, recursively call `expression()`, then require `)`. This restarts the entire hierarchy inside the parentheses, allowing `(2 + 3) * 4` to override normal precedence.

`unary()` consumes a prefix `!` or `-` and recursively calls itself, allowing `!!true` and `--5`. It eventually stops recursing when it reaches a primary. Unlike the old grammar, a unary operand is no longer an arbitrary ungrouped expression: `-2 * 3` groups as `(-2) * 3`.

## 4. Recursive descent: translating grammar into Java

Each nonterminal becomes a parsing method. The Java call stack tracks which grammar rules are currently being processed.

The grammar pattern for addition/subtraction is:

```text
term → factor ( ( "-" | "+" ) factor )* ;
```

Read it as: one mandatory factor, followed by zero or more pairs of an addition/subtraction operator and another factor.

| Grammar notation | Meaning | Java representation |
| --- | --- | --- |
| Terminal (e.g. `NUMBER`, `"+"`) | Actual token | Match and consume token |
| Nonterminal (e.g. `factor`) | Another grammar rule | Method call |
| `\|` | Alternative | `if` / `switch` |
| `*` | Zero or more repetitions | `while` loop |
| `+` (unquoted) | One or more repetitions | Mandatory match followed by loop |
| `?` | Zero or one occurrence | `if` |

Quoted `"*"` means multiplication; an unquoted `*` is grammar repetition.

### Why loops instead of left recursion?

A rule like `term → term "+" factor | factor` is left-recursive. A naive recursive descent implementation would call `term()` again before consuming anything, eventually overflowing the stack.

Instead, we parse one factor, then loop over additional operator-operand pairs. Conceptually:

```text
left = parse first operand
while the current token is an operator at this level:
    consume the operator
    right = parse next operand
    left = new binary node(left, operator, right)
return left
```

The existing tree becomes the left child every time. For `10 - 3 - 2`:

```text
10  --->  (- 10 3)  --->  (- (- 10 3) 2)
```

This left-folding construction guarantees left associativity. Repetition alone does not determine the AST shape.

## 5. What the parser stores and produces

The parser receives a `List<Token>` and maintains an integer `current` pointing to the next unconsumed token. Consuming a token advances that index; it does not delete the token from the list.

| Helper | Purpose |
| --- | --- |
| `peek()` | Inspect the current token without consuming it |
| `previous()` | Retrieve the most recently consumed token |
| `isAtEnd()` | Check whether the current token is EOF |
| `check(type)` | Test the current token's type without consuming it |
| `advance()` | Move forward and return the consumed token |
| `match(types...)` | Consume a token if it matches any given type; otherwise return false |
| `consume(type, message)` | Require a particular token; report and throw a parse error if missing |

**`match()` vs `consume()`:** `match()` asks whether an optional possibility is present. `consume()` insists that something required by the grammar is present (e.g. the closing parenthesis).

The parser constructs existing `Expr.Binary`, `Expr.Unary`, `Expr.Literal` and `Expr.Grouping` nodes. Precedence rules do not require separate AST classes. A rule can simply return the subtree produced by the next rule when no operator matches.

## 6. Syntax errors and synchronization (the other important part)

The scanner knows whether individual tokens are legal. The parser knows whether their arrangement is legal. For example, `5 + * 3` contains valid tokens but is not a valid expression.

### Reporting an error is not the same as recovering

- `error(token, message)` reports the error through the main application (including token/line information) and returns a `ParseError` object.
- Throwing that object abandons the failed parsing operation. `ParseError` is a small custom `RuntimeException` used for control flow within the parser.
- The parser catches the exception at an appropriate level. The error flag prevents later stages from executing invalid code.

### What the heck is synchronization?

**Synchronization means getting the parser back to a sensible starting point after a syntax error.** It neither repairs the source nor evaluates it. The aim is to continue checking the rest of the program without producing a flood of misleading errors.

Imagine this future SeLang program (statements are introduced in Chapter 8):

```lox
var a = 10;
var b = 5 + * 3;
var c = 20;
print c;
```

At `*`, the parser expected an operand. If it kept parsing as though nothing went wrong, the remaining tokens could trigger cascading errors caused by the first mistake. If it stopped entirely, it could not find independent errors later in the file.

Instead, panic mode recovery abandons the broken declaration and looks for the next statement.

### Two things must be synchronized

**A. The parser's call stack:** At the error, the parser is deep inside nested method calls (`primary`, `unary`, `factor`, `term`, etc.). These methods expect a valid expression. Java exception handling unwinds those calls to a statement-level recovery point. In the later statement parser, `declaration()` catches the exception.

**B. The token position:** Unwinding does not move `current`! It still points at `*`. We must skip tokens separately using `synchronize()`.

```text
Error detected at:        *   3   ;   var   c   =   20   ;
                          ^
Throw/catch ParseError:   *   3   ;   var   c   =   20   ;
                          ^          (index unchanged)
Discard broken tokens:    x   x   x   var   c   =   20   ;
                                       ^
Resume parsing here:                  var c = 20;
```

### How `synchronize()` finds the next statement

The book's method first advances once to ensure progress. Then, until EOF, it:

1. Stops if the **previous** token was a semicolon (`;`): we probably just finished a statement.
2. Stops if the **current** token is a likely statement starter: `CLASS`, `FUN`, `VAR`, `FOR`, `IF`, `WHILE`, `PRINT` or `RETURN`.
3. Otherwise, advances again, discarding another token.

The distinction between previous and current is important: a semicolon is consumed, but a recognized next-statement keyword is left for normal parsing.

Checking keywords also helps if the broken statement is missing its semicolon. This is a heuristic, not a guarantee: semicolons may occur inside `for` loops, and the initial advance can skip a useful boundary token. Some genuine errors inside discarded text may be missed. The trade-off is fewer cascading errors and a chance to find independent later errors.

### IMPORTANT: We do not actually call `synchronize()` in Chapter 6!

We only support a single expression right now, not multiple statements. The method is infrastructure for Chapter 8. In Chapter 6, `parse()` catches `ParseError` and returns `null`; the application checks its error flag and skips subsequent processing.

```text
Chapter 6: error -> throw -> catch in parse() -> return null -> stop
Chapter 8: error -> throw -> catch in declaration() -> synchronize() -> continue parsing
```

Even after successful recovery, a program containing syntax errors must not execute. Recovery is for diagnostics.

## 7. What changed in the project?

Following the book's architecture:

- **`Parser.java` (new):** precedence hierarchy, token navigation, AST construction, parse-error handling and future synchronization logic.
- **Main application (`Lox.java` in the book):** token-aware error reporting and wiring the scanner output into the parser, then printing the resulting tree if no errors occurred.
- **`Expr.java`, `AstPrinter.java`, scanner and token classes:** reused from previous chapters; no new expression node types required.

These are the book-based implementation changes, not a verified audit of the SeLang repository.

## 8. Quick regression examples

Expected outputs below assume the book's AST printer and numeric formatting.

| Input | Expected AST / behavior |
| --- | --- |
| `2 + 3 * 4` | `(+ 2.0 (* 3.0 4.0))` |
| `(2 + 3) * 4` | `(* (group (+ 2.0 3.0)) 4.0)` |
| `10 - 3 - 2` | `(- (- 10.0 3.0) 2.0)` |
| `-5 * 2` | `(* (- 5.0) 2.0)` |
| `!!true` | `(! (! true))` |
| `5 + * 3` | Syntax error: expected expression |
| `(2 + 3` | Syntax error: expected closing parenthesis |

One temporary limitation: the Chapter 6 `parse()` method does not require EOF after its single expression. Inputs with trailing unrelated tokens may not be rejected yet. The full program parser in Chapter 8 addresses this.

## 9. Four-month refresher

If I forget everything else, remember these:

- **Parser = tokens in, AST out.** Not execution.
- **Hierarchy = precedence.** Each lower-precedence method obtains its operands from a higher-precedence method.
- **Loops = left-associative binary chains.** Keep wrapping the previous tree as the left operand.
- **Recursion = nested parentheses and repeated unary operators.**
- **Synchronization = abandon the failed statement, unwind the stack, skip bad tokens and resume at a likely statement boundary.** It is implemented but unused until Chapter 8.

Next: Chapter 7 will walk the AST and compute actual values using the Visitor pattern built in Chapter 5.

---

Reference: Robert Nystrom, *Crafting Interpreters*, Chapter 6 — [Parsing Expressions](https://craftinginterpreters.com/parsing-expressions.html), especially sections 6.1–6.4.
