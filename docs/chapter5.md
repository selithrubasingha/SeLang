# Chapter 5 — Representing Code

> SeLang · Tree-walk interpreter · Notes based on Robert Nystrom's *Crafting Interpreters*, Chapter 5.

## 1. What did we build?

In Chapter 4, the scanner converted source code into tokens. Tokens tell us what the individual pieces of code are, but not how they relate to one another. Chapter 5 introduces an **Abstract Syntax Tree (AST)** to represent those relationships in memory.

```text
Source code → Scanner → Tokens → Parser → AST → Interpreter → Result
                                   ^       ^
                              Chapter 6  Chapter 5 defines
                                         this structure
```

We have not implemented parsing or evaluation yet. Instead, we define expression classes, generate their boilerplate automatically, and test them by manually constructing and printing a tree.

## 2. Context-free grammars (Section 5.1)

A lexical grammar describes how characters form tokens. A syntactic grammar describes how tokens form expressions and other language constructs. A context-free grammar consists of production rules whose left-hand side is a single non-terminal.

- **Terminal:** a token that does not need further expansion, such as `"+"` or `NUMBER`.
- **Non-terminal:** a named rule that can be expanded, such as `expression`.
- **Production:** one possible expansion of a rule.
- **Recursion:** a rule can refer to itself, directly or indirectly, allowing arbitrarily nested expressions.

### Grammar notation

| Notation | Meaning |
| --- | --- |
| `A B` | A followed by B |
| `A \| B` | Either A or B |
| `(A B)` | Group symbols together |
| `A*` | Zero or more occurrences |
| `A+` | One or more occurrences |
| `A?` | Zero or one occurrence |

The initial expression grammar from the book is:

```text
expression → literal | unary | binary | grouping ;
literal    → NUMBER | STRING | "true" | "false" | "nil" ;
grouping   → "(" expression ")" ;
unary      → ("-" | "!") expression ;
binary     → expression operator expression ;
operator   → "==" | "!=" | "<" | "<=" | ">" | ">="
           | "+" | "-" | "*" | "/" ;
```

Quoted terminals match specific lexemes. Uppercase terminals such as `NUMBER` match token categories whose lexemes may vary. This grammar is deliberately ambiguous: it does not establish precedence or associativity. Chapter 6 addresses that problem. The notation itself is not Java code and does not need to be implemented as a separate grammar engine.

## 3. Abstract Syntax Trees (Section 5.2)

An AST preserves the meaningful structure of source code while omitting unnecessary grammar productions. A parse tree, by contrast, represents every production used in parsing.

For example, `1 + 2 * 3` can be represented as the following tree when normal arithmetic precedence is applied:

```text
        +                 Binary
       / \
      1   *               Literal, Binary
         / \
        2   3             Literal, Literal
```

The tree structure records which operands belong to each operator. The AST can represent this structure now, but automatically choosing it from tokens is the parser's job in Chapter 6.

### Expression classes

| Node | Fields | Example |
| --- | --- | --- |
| `Expr.Binary` | `Expr left`, `Token operator`, `Expr right` | `2 + 3` |
| `Expr.Unary` | `Token operator`, `Expr right` | `-5`, `!true` |
| `Expr.Literal` | `Object value` | `42`, `"hello"`, `nil` |
| `Expr.Grouping` | `Expr expression` | `(2 + 3)` |

All four classes extend the abstract `Expr` class. Recursive fields of type `Expr` allow any expression to contain other expressions. Each node has only the fields it needs, unlike a single universal class filled with irrelevant or nullable fields. Fields are final, and constructors initialize them.

## 4. Generating the AST classes

`GenerateAst.java` is a development-time tool, not part of the interpreter's runtime pipeline. It generates `Expr.java` from compact descriptions such as:

```java
"Binary : Expr left, Token operator, Expr right"
```

| Method | Responsibility |
| --- | --- |
| `main()` | Defines the node descriptions and reads the output directory |
| `defineAst()` | Writes the base class and coordinates generation |
| `defineType()` | Writes each subclass, constructor, fields and `accept()` |
| `defineVisitor()` | Writes the generic Visitor interface |

This avoids repetitive Java boilerplate and makes adding node definitions easier. Later, the same generator can be reused for statement classes. Regenerate the file after changing its definitions rather than maintaining two independent versions of the generated code.

## 5. The Expression Problem (Section 5.3)

An interpreter needs to perform many different operations on the same family of AST nodes. Printing is only the first; evaluation and name resolution come later. A statically typed language might also need type checking.

Think of the implementation as a table. Each cell contains the behavior for one operation on one node type.

| Node type | Print | Evaluate | Resolve |
| --- | --- | --- | --- |
| Binary | Print operands/operator | Evaluate both operands | Resolve both operands |
| Unary | Print operator/operand | Apply unary operator | Resolve operand |
| Literal | Print value | Return value | No names to resolve |
| Grouping | Print group | Evaluate inner expression | Resolve inner expression |

The question is how to organize this table so the code is easy to extend.

### Approach A — Conventional object-oriented programming

Put all operations inside each node class. For example, a hypothetical `Binary` class could implement `print()`, `evaluate()` and `resolve()` itself.

```java
// Illustrative alternative, not our implementation.
abstract class Expr {
    abstract String print();
    abstract Object evaluate();
}
```

This groups behavior by **row** (node type). Adding a new node class is relatively straightforward because its behavior lives in that class. However, adding a new operation requires modifying the base class and every existing concrete node class. Printing, execution and semantic analysis would become mixed together, violating separation of concerns.

Normal OOP is not incapable of solving the problem. It simply organizes this particular code along the less convenient dimension.

### Approach B — Functional style / pattern matching

Keep data and operations separate. Each function handles every node type. In Java-like illustrative pseudocode:

```java
String print(Expr expr) {
    if (expr instanceof Expr.Binary b) { /* print binary */ }
    else if (expr instanceof Expr.Unary u) { /* print unary */ }
    // Other node types ...
}
```

ML-family languages provide pattern matching for this style. This groups behavior by **column** (operation). Adding an operation is easy: write another function. Adding a node type is harder because every existing operation must account for it.

A long chain of Java `instanceof` tests also performs sequential type checks. The book prefers polymorphic dispatch over that approach. Modern Java can express a similar functional organization with pattern matching and sealed hierarchies; that is an additional alternative, not the approach chosen in the chapter.

### Approach C — Visitor pattern (our choice)

Visitor uses OOP polymorphism to obtain the operation-oriented organization associated with functional programming. Each AST node contains a small dispatch method, while each operation lives in its own visitor class.

| Approach | Add a new node type | Add a new operation |
| --- | --- | --- |
| Conventional OOP | Easy | Modify existing node classes |
| Functional pattern matching | Update existing operations | Easy |
| Visitor | Update visitor interface and implementations | Add a new visitor class |

**The expression problem** is the difficulty of making both dimensions independently extensible. Visitor does not eliminate that trade-off. It deliberately favors adding operations over adding node types. We choose it because the AST is shared across multiple compiler/interpreter passes, and keeping each pass together is valuable.

## 6. How the Visitor pattern works

### Step 1 — Define the contract

The generated `Expr` class contains an interface with one method per node type:

```java
interface Visitor<R> {
    R visitBinaryExpr(Binary expr);
    R visitGroupingExpr(Grouping expr);
    R visitLiteralExpr(Literal expr);
    R visitUnaryExpr(Unary expr);
}
```

A visitor is an implementation of an operation across all expression types. The generic `R` lets different visitors return different kinds of results.

### Step 2 — Give every expression an entry point

```java
abstract <R> R accept(Visitor<R> visitor);
```

The base class declares this method, and each concrete subclass overrides it. For example, inside `Expr.Binary`:

```java
@Override
<R> R accept(Visitor<R> visitor) {
    return visitor.visitBinaryExpr(this);
}
```

The binary node does not know how to print, evaluate or resolve itself. It only knows which visitor method corresponds to its own type.

### Step 3 — Implement an operation

`AstPrinter` implements `Expr.Visitor<String>` and supplies all four visit methods. Its entry point is:

```java
String print(Expr expr) {
    return expr.accept(this);
}
```

Here `this` is the printer. Inside `Binary.accept()`, however, `this` is the binary expression. These are two different objects!

### Step 4 — Follow the dispatch

Suppose an `Expr` variable actually contains a `Binary` object:

```text
printer.print(expr)
    |
    v
expr.accept(printer)             Java selects Binary.accept at runtime
    |
    v
Binary.accept(printer)
    |
    v
printer.visitBinaryExpr(this)    Binary selects the correct visit method
    |
    v
AstPrinter.visitBinaryExpr(...)  Java selects the visitor implementation
```

This is commonly called **double dispatch**: the behavior depends on both the concrete expression type and the concrete visitor implementation. Java overriding supplies runtime dispatch; the distinct visit method names make the node-specific routing explicit. It is not runtime method overloading.

### Why use generics?

- `AstPrinter implements Expr.Visitor<String>` returns a string.
- A future evaluator can implement `Expr.Visitor<Object>` to return language values.
- An operation with no meaningful result can use `Expr.Visitor<Void>` and return `null`.

The same AST can accept all of these visitors without adding a separate method for each operation.

### Visitor is not tree traversal

This is an important distinction from the book: Visitor is a mechanism for selecting and organizing behavior, not an algorithm for walking a tree. It works on individual objects too. The printer traverses the tree because its visit methods explicitly call `accept()` on child expressions. Another visitor may choose a different traversal strategy or not traverse children at all.

### Trade-offs

Visitor introduces boilerplate, indirection and a less obvious call chain. Adding a new expression type requires adding a method to the interface and implementing it in existing visitors. Our generator automates the node-side boilerplate, while Java's interface checks help identify missing visitor methods at compile time. Visitor is useful here, but it is not universally superior to OOP or functional programming.

## 7. The AST printer (Section 5.4)

`AstPrinter.java` is the first concrete visitor. It produces a parenthesized prefix representation to expose the structure of an expression unambiguously.

| Method | Behavior |
| --- | --- |
| `visitBinaryExpr()` | Prints operator followed by left and right expressions |
| `visitUnaryExpr()` | Prints operator followed by its operand |
| `visitGroupingExpr()` | Prints `group` followed by its inner expression |
| `visitLiteralExpr()` | Converts the value to text; prints `nil` for null |
| `parenthesize()` | Builds a parenthesized string and recursively visits children |

The chapter's demonstration manually constructs the tree for `-123 * (45.67)`:

```text
         *             Binary
        / \
       -   group       Unary, Grouping
       |     |
      123   45.67      Literals
```

Expected output:

```text
(* (- 123) (group 45.67))
```

The printer does not calculate the answer. It exposes the tree's shape, making it useful for debugging the parser later.

## 8. Files and verification

| File | Role |
| --- | --- |
| `tool/GenerateAst.java` | Source-code generator |
| `lox/Expr.java` | Generated AST hierarchy and visitor contract |
| `lox/AstPrinter.java` | Concrete printing visitor and demonstration |
| `lox/Token.java`, `lox/TokenType.java` | Existing scanner types reused by expression nodes |

Paths above follow the book's package naming; adapt them to the actual SeLang repository.

- [ ] Run the generator with the expression package directory as its argument.
- [ ] Verify that all four expression classes and visitor methods are generated.
- [ ] Run `AstPrinter.main()` and compare the output with the expected string.
- [ ] Explain the dispatch chain without looking at the code.

These are verification steps, not claims that the repository has been tested.

## 9. What comes next?

Chapter 6 implements the parser: it consumes the scanner's tokens, resolves expression precedence and associativity, and constructs these AST nodes automatically. Chapter 7 introduces expression evaluation.

**Key takeaway:** Chapter 5 establishes the data structure and the mechanism for performing different operations on it. The AST represents the code; visitors define what to do with that representation.

## Reference

Robert Nystrom, *Crafting Interpreters*, Chapter 5, “Representing Code,” sections 5.1–5.4. These notes summarize the chapter and include explanatory examples and comparisons; optional challenges are not implemented here.
