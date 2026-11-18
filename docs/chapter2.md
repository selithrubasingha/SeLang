# Chapter 2 — A Map of the Territory

A concise glossary for the language implementation pipeline in *Crafting Interpreters*.

## The Big Picture

```text
Source code
   ↓ scanning
Tokens
   ↓ parsing
Syntax tree
   ↓ static analysis
Validated syntax tree / IR
   ↓ optimization (optional)
Optimized IR
   ↓ code generation
Machine code or bytecode
   ↓ runtime / virtual machine
Program output
```

## Core Terms

| Term | Concise meaning |
|---|---|
| **Source code** | The program text written by a programmer. |
| **Scanning / Lexing** | Groups source characters into tokens. |
| **Lexeme** | The source text that forms one token, such as `var` or `123`. |
| **Token** | A categorized piece of source code, such as `NUMBER` or `IDENTIFIER`. |
| **Parsing** | Turns tokens into a grammatical structure. |
| **Grammar** | Rules defining valid program syntax. |
| **Syntax** | The valid arrangement of language elements. |
| **Syntax error** | Code that breaks the language's grammar. |
| **Parse tree** | A tree representing the grammar used by the source. |
| **Abstract Syntax Tree (AST)** | A simplified tree representing the program's meaning. |
| **Static analysis** | Checks code without executing it. |
| **Binding / Resolution** | Connects a name to the declaration it refers to. |
| **Scope** | The region where a name is visible. |
| **Type checking** | Verifies that operations use compatible value types. |
| **Static typing** | Types are checked before execution. |
| **Dynamic typing** | Types are checked while the program runs. |
| **Front end** | Scanning, parsing, and static analysis. |
| **Intermediate Representation (IR)** | An internal form between source code and final code. |
| **Optimization** | Makes code faster or smaller without changing its behavior. |
| **Constant folding** | Computes constant expressions early, such as `2 * 3` → `6`. |
| **Back end** | Converts the analyzed program into executable code. |
| **Code generation** | Produces machine code, bytecode, or another target language. |
| **Instruction selection** | Chooses target instructions for each operation. |
| **Register allocation** | Decides which values are kept in CPU registers. |
| **Machine code** | Native instructions executed directly by a CPU. |
| **Bytecode** | Compact instructions for a virtual machine. |
| **Virtual Machine (VM)** | A software computer that executes bytecode. |
| **Runtime** | Services needed while a program executes. |
| **Garbage collection (GC)** | Automatically reclaims unused memory. |

## Implementation Strategies

| Strategy | Concise meaning |
|---|---|
| **Compiler** | Translates source code into another form without running it. |
| **Interpreter** | Executes a program directly. |
| **Tree-walk interpreter** | Executes code by walking its syntax tree. |
| **Single-pass compiler** | Processes code once while compiling it. |
| **Transpiler** | Translates one high-level language into another. |
| **Just-in-time compiler (JIT)** | Compiles code to machine code during execution. |
| **Ahead-of-time compiler (AOT)** | Compiles code before execution. |
| **Compiler-compiler** | Generates parts of a compiler from a language description. |

> **Compiler vs. interpreter:** compiling is translation; interpreting is execution. A language implementation can use both.

## The Two Lox Implementations

| Implementation | Approach |
|---|---|
| **jlox** | A Java tree-walk interpreter that executes the AST. |
| **clox** | A C implementation that compiles to bytecode and runs it on a VM. |

## One-Line Memory Aid

**Characters → tokens → syntax tree → analysis → IR → code → execution.**
