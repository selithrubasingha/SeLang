# Chapter 3 — The Lox Language

A practical overview of how **Lox** looks and behaves, based on Chapter 3 of *Crafting Interpreters*.

## The Big Picture

Lox is a small, high-level scripting language created for the book. Its syntax is familiar to programmers who have used C, Java, or JavaScript, while its scoping and first-class functions also give it a functional side.

The language is deliberately compact enough for us to build two complete implementations:

- **jlox**, a tree-walk interpreter written in Java.
- **clox**, a bytecode virtual machine written in C.

Despite its small size, Lox includes most of the features expected in a general-purpose language: values, expressions, variables, control flow, functions, closures, classes, and inheritance.

```lox
print "Hello, world!";
```

Statements normally end with a semicolon. Braces group multiple statements into a block.

## 1. The Character of Lox

### Dynamic typing

Lox is **dynamically typed**. A variable does not have a fixed declared type; it can hold values of different types at different points in the program.

```lox
var value = "a string";
value = 42;
value = true;
```

Type errors are discovered while the program runs. For example, trying to divide a string by a number is a runtime error rather than a compile-time type error.

### Automatic memory management

Programs do not manually allocate or free memory. The implementation automatically reclaims objects that can no longer be reached. The book eventually implements a **tracing garbage collector** for clox.

### Familiar but intentionally small

Lox resembles JavaScript and other C-style languages, but avoids many complicated or unusual features. Its design is meant to teach the essential parts of an interpreter rather than compete with a production language.

## 2. Built-in Data Types

Lox has a small set of fundamental value types.

| Type | Examples | Meaning |
|---|---|---|
| **Boolean** | `true`, `false` | Logical truth values. |
| **Number** | `12`, `3.14` | All numbers are double-precision floating-point values. |
| **String** | `"hello"`, `"Lox"` | Sequences of characters enclosed in double quotes. |
| **Nil** | `nil` | Represents the absence of a value. |

Lox has only one numeric type. It can represent decimals and a useful range of integers without requiring separate integer and floating-point rules.

Strings are values in their own right:

```lox
print "Hello" + ", Lox!";
```

The `nil` value is useful for an uninitialized variable or a function that does not explicitly return a result.

### Truthiness

In a condition, `false` and `nil` are **falsey**. Every other value is **truthy**, including `0` and the empty string.

## 3. Expressions and Operators

An **expression** evaluates to a value. Lox provides arithmetic, comparison, equality, and logical expressions.

### Arithmetic

```lox
add + me;
subtract - me;
multiply * me;
divide / me;
-negateMe;
```

The arithmetic operators operate on numbers. The `-` symbol can be either a binary subtraction operator or a unary negation operator.

The `+` operator has one additional use: it concatenates two strings.

```lox
print "craft" + "ing"; // crafting
```

Both operands of `+` must be numbers or both must be strings. Lox does not silently convert between strings and numbers.

### Comparison and equality

```lox
1 < 2;
2 <= 2;
3 > 1;
3 >= 3;

"cat" == "cat";
"cat" != "dog";
123 == "123"; // false
```

The ordering operators `<`, `<=`, `>`, and `>=` compare numbers. Equality operators can compare values of any type. Values of different types are not equal, and Lox does not perform implicit conversion to make them match.

### Logical operators

```lox
!true;             // false
ready and enabled;
cached or compute;
```

- `!` reverses the truthiness of its operand.
- `and` evaluates and returns its left operand when that value is falsey; otherwise it evaluates and returns the right operand.
- `or` evaluates and returns its left operand when that value is truthy; otherwise it evaluates and returns the right operand.

Because `and` and `or` may skip their right-hand operand, they **short-circuit**. They return operand values, not necessarily Boolean values.

### Grouping and precedence

Parentheses can make the intended evaluation order explicit.

```lox
var result = (2 + 3) * 4;
```
