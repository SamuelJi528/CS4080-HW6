//> Resolving and Binding resolver
package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
  private final Interpreter interpreter;

  private static class VariableInfo {
    boolean defined;
    boolean used;
    Token token;
    int index;

    VariableInfo(Token token) {
      this.token = token;
      this.defined = false;
      this.used = false;
      this.index = -1;
    }
  }

//> scopes-field
  private final Stack<Map<String, VariableInfo>> scopes = new Stack<>();
  private final Stack<Integer> scopeIndices = new Stack<>();
//< scopes-field
//> function-type-field
  private FunctionType currentFunction = FunctionType.NONE;
//< function-type-field

  Resolver(Interpreter interpreter) {
    this.interpreter = interpreter;
  }
//> function-type
  private enum FunctionType {
    NONE,
/* Resolving and Binding function-type < Classes function-type-method
    FUNCTION
*/
//> Classes function-type-method
    FUNCTION,
//> function-type-initializer
    INITIALIZER,
//< function-type-initializer
    METHOD
//< Classes function-type-method
  }
//< function-type
//> Classes class-type

  private enum ClassType {
    NONE,
/* Classes class-type < Inheritance class-type-subclass
    CLASS
 */
//> Inheritance class-type-subclass
    CLASS,
    SUBCLASS
//< Inheritance class-type-subclass
  }

  private ClassType currentClass = ClassType.NONE;

//< Classes class-type
//> resolve-statements
  void resolve(List<Stmt> statements) {
    for (Stmt statement : statements) {
      resolve(statement);
    }
  }
//< resolve-statements
//> visit-block-stmt
  @Override
  public Void visitBlockStmt(Stmt.Block stmt) {
    beginScope();
    resolve(stmt.statements);
    endScope();
    return null;
  }
//< visit-block-stmt

  @Override
  public Void visitBreakStmt(Stmt.Break stmt) {
    // break doesn't create variables or change scopes,
    // so there's nothing to resolve here.
    return null;
  }

//> Classes resolver-visit-class
  @Override
  public Void visitClassStmt(Stmt.Class stmt) {
    ClassType enclosingClass = currentClass;
    currentClass = ClassType.CLASS;

    declare(stmt.name);
    define(stmt.name);

    for (Expr.Variable superclass : stmt.superclasses) {
      if (stmt.name.lexeme.equals(superclass.name.lexeme)) {
        Lox.error(superclass.name,
            "A class can't inherit from itself.");
      }
    }

    if (!stmt.superclasses.isEmpty()) {
      currentClass = ClassType.SUBCLASS;
    }

    for (Expr.Variable superclass : stmt.superclasses) {
      resolve(superclass);
    }

    if (!stmt.superclasses.isEmpty()) {
      beginScope();
      VariableInfo superInfo = new VariableInfo(stmt.name);
      superInfo.defined = true;
      superInfo.used = true;
      scopes.peek().put("super", superInfo);
    }

    beginScope();
    VariableInfo thisInfo = new VariableInfo(stmt.name);
    thisInfo.defined = true;
    thisInfo.used = true;
    scopes.peek().put("this", thisInfo);

    for (Stmt.Function method : stmt.methods) {
      FunctionType declaration = FunctionType.METHOD;
      if (method.name.lexeme.equals("init")) {
        declaration = FunctionType.INITIALIZER;
      }
      resolveFunction(method, declaration);
    }

    endScope();

    if (!stmt.superclasses.isEmpty()) endScope();

    currentClass = enclosingClass;
    return null;
  }
//< Classes resolver-visit-class
//> visit-expression-stmt
  @Override
  public Void visitExpressionStmt(Stmt.Expression stmt) {
    resolve(stmt.expression);
    return null;
  }
//< visit-expression-stmt
//> visit-function-stmt
  @Override
  public Void visitFunctionStmt(Stmt.Function stmt) {
    declare(stmt.name);
    define(stmt.name);

/* Resolving and Binding visit-function-stmt < Resolving and Binding pass-function-type
    resolveFunction(stmt);
*/
//> pass-function-type
    resolveFunction(stmt, FunctionType.FUNCTION);
//< pass-function-type
    return null;
  }
//< visit-function-stmt
//> visit-if-stmt
  @Override
  public Void visitIfStmt(Stmt.If stmt) {
    resolve(stmt.condition);
    resolve(stmt.thenBranch);
    if (stmt.elseBranch != null) resolve(stmt.elseBranch);
    return null;
  }
//< visit-if-stmt
//> visit-print-stmt
  @Override
  public Void visitPrintStmt(Stmt.Print stmt) {
    resolve(stmt.expression);
    return null;
  }
//< visit-print-stmt
//> visit-return-stmt
  @Override
  public Void visitReturnStmt(Stmt.Return stmt) {
//> return-from-top
    if (currentFunction == FunctionType.NONE) {
      Lox.error(stmt.keyword, "Can't return from top-level code.");
    }

//< return-from-top
    if (stmt.value != null) {
//> Classes return-in-initializer
      if (currentFunction == FunctionType.INITIALIZER) {
        Lox.error(stmt.keyword,
            "Can't return a value from an initializer.");
      }

//< Classes return-in-initializer
      resolve(stmt.value);
    }

    return null;
  }
//< visit-return-stmt
//> visit-var-stmt
  @Override
  public Void visitVarStmt(Stmt.Var stmt) {
    declare(stmt.name);
    if (stmt.initializer != null) {
      resolve(stmt.initializer);
    }
    define(stmt.name);
    return null;
  }
//< visit-var-stmt
//> visit-while-stmt
  @Override
  public Void visitWhileStmt(Stmt.While stmt) {
    resolve(stmt.condition);
    resolve(stmt.body);
    return null;
  }
//< visit-while-stmt
//> visit-assign-expr
  @Override
  public Void visitAssignExpr(Expr.Assign expr) {
    resolve(expr.value);
    
    if (!scopes.isEmpty()) {
      VariableInfo info = scopes.peek().get(expr.name.lexeme);
      if (info != null) {
        info.used = true;
      }
    }
    
    resolveLocal(expr, expr.name);
    return null;
  }
//< visit-assign-expr
//> visit-binary-expr
  @Override
  public Void visitBinaryExpr(Expr.Binary expr) {
    resolve(expr.left);
    resolve(expr.right);
    return null;
  }
//< visit-binary-expr
//> visit-call-expr
  @Override
  public Void visitCallExpr(Expr.Call expr) {
    resolve(expr.callee);

    for (Expr argument : expr.arguments) {
      resolve(argument);
    }

    return null;
  }
//< visit-call-expr
//> Functions resolver-visit-function-expr
  @Override
  public Void visitFunctionExpr(Expr.Function expr) {
    FunctionType enclosingFunction = currentFunction;
    currentFunction = FunctionType.FUNCTION;

    beginScope();
    for (Token param : expr.params) {
      declare(param);
      define(param);
    }
    resolve(expr.body);
    endScope();

    currentFunction = enclosingFunction;
    return null;
  }
//< Functions resolver-visit-function-expr
//> Classes resolver-visit-get
  @Override
  public Void visitGetExpr(Expr.Get expr) {
    resolve(expr.object);
    return null;
  }
//< Classes resolver-visit-get
//> visit-grouping-expr
  @Override
  public Void visitGroupingExpr(Expr.Grouping expr) {
    resolve(expr.expression);
    return null;
  }
//< visit-grouping-expr
//> visit-literal-expr
  @Override
  public Void visitLiteralExpr(Expr.Literal expr) {
    return null;
  }
//< visit-literal-expr
//> visit-logical-expr
  @Override
  public Void visitLogicalExpr(Expr.Logical expr) {
    resolve(expr.left);
    resolve(expr.right);
    return null;
  }
//< visit-logical-expr
//> Classes resolver-visit-set
  @Override
  public Void visitSetExpr(Expr.Set expr) {
    resolve(expr.value);
    resolve(expr.object);
    return null;
  }
//< Classes resolver-visit-set
//> Classes resolver-visit-this
  @Override
  public Void visitThisExpr(Expr.This expr) {
//> this-outside-of-class
    if (currentClass == ClassType.NONE) {
      Lox.error(expr.keyword,
          "Can't use 'this' outside of a class.");
      return null;
    }

//< this-outside-of-class
    resolveLocal(expr, expr.keyword);
    return null;
  }

//< Classes resolver-visit-this
  @Override
  public Void visitInnerExpr(Expr.Inner expr) {
    return null;
  }
//> visit-unary-expr
  @Override
  public Void visitUnaryExpr(Expr.Unary expr) {
    resolve(expr.right);
    return null;
  }
//< visit-unary-expr
//> visit-variable-expr
  @Override
  public Void visitVariableExpr(Expr.Variable expr) {
    if (!scopes.isEmpty()) {
      VariableInfo info = scopes.peek().get(expr.name.lexeme);
      if (info != null && !info.defined) {
        Lox.error(expr.name,
            "Can't read local variable in its own initializer.");
      }
    }

    resolveLocal(expr, expr.name);
    return null;
  }
//< visit-variable-expr
//> resolve-stmt
  private void resolve(Stmt stmt) {
    stmt.accept(this);
  }
//< resolve-stmt
//> resolve-expr
  private void resolve(Expr expr) {
    expr.accept(this);
  }
//< resolve-expr
//> resolve-function
/* Resolving and Binding resolve-function < Resolving and Binding set-current-function
  private void resolveFunction(Stmt.Function function) {
*/
//> set-current-function
  private void resolveFunction(
      Stmt.Function function, FunctionType type) {
    FunctionType enclosingFunction = currentFunction;
    currentFunction = type;

//< set-current-function
    beginScope();
    if (function.params != null) {
      for (Token param : function.params) {
        declare(param);
        define(param);
      }
    }
    resolve(function.body);
    endScope();
//> restore-current-function
    currentFunction = enclosingFunction;
//< restore-current-function
  }
//< resolve-function
//> begin-scope
  private void beginScope() {
    scopes.push(new HashMap<String, VariableInfo>());
    scopeIndices.push(0);
  }
//< begin-scope
//> end-scope
  private void endScope() {
    Map<String, VariableInfo> scope = scopes.pop();
    scopeIndices.pop();
    for (Map.Entry<String, VariableInfo> entry : scope.entrySet()) {
      VariableInfo info = entry.getValue();
      if (info.defined && !info.used) {
        Lox.error(info.token, "Local variable '" + entry.getKey() + "' is defined but never used.");
      }
    }
  }
//< end-scope
//> declare
  private void declare(Token name) {
    if (scopes.isEmpty()) return;

    Map<String, VariableInfo> scope = scopes.peek();
//> duplicate-variable
    if (scope.containsKey(name.lexeme)) {
      Lox.error(name,
          "Already a variable with this name in this scope.");
    }

//< duplicate-variable
    VariableInfo info = new VariableInfo(name);
    int currentIndex = scopeIndices.peek();
    info.index = currentIndex;
    scopeIndices.pop();
    scopeIndices.push(currentIndex + 1);
    scope.put(name.lexeme, info);
  }
//< declare
//> define
  private void define(Token name) {
    if (scopes.isEmpty()) return;
    VariableInfo info = scopes.peek().get(name.lexeme);
    if (info != null) {
      info.defined = true;
    }
  }
//< define
//> resolve-local
  private void resolveLocal(Expr expr, Token name) {
    for (int i = scopes.size() - 1; i >= 0; i--) {
      if (scopes.get(i).containsKey(name.lexeme)) {
        VariableInfo info = scopes.get(i).get(name.lexeme);
        info.used = true;
        interpreter.resolve(expr, scopes.size() - 1 - i, info.index);
        return;
      }
    }
  }
//< resolve-local
}
