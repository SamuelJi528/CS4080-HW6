//> Evaluating Expressions interpreter-class
package com.craftinginterpreters.lox;
//> Statements and State import-list

//> Functions import-array-list
import java.util.ArrayList;
//< Functions import-array-list
//> Resolving and Binding import-hash-map
import java.util.HashMap;
//< Resolving and Binding import-hash-map
import java.util.List;
//< Statements and State import-list
//> Resolving and Binding import-map
import java.util.Map;
//< Resolving and Binding import-map

/* Evaluating Expressions interpreter-class < Statements and State interpreter
class Interpreter implements Expr.Visitor<Object> {
*/
//> Statements and State interpreter
class Interpreter implements Expr.Visitor<Object>,
                             Stmt.Visitor<Void> {
//< Statements and State interpreter
  private static class LocalVariable {
    final int depth;
    final int index;
    
    LocalVariable(int depth, int index) {
      this.depth = depth;
      this.index = index;
    }
  }

//> parse-error
  private static class ParseError extends RuntimeException {}
  private static class BreakException extends RuntimeException {}

//< parse-error
//> Functions global-environment
  final Environment globals = new Environment();
  private Environment environment = globals;
  private static final Object UNINITIALIZED = new Object();
//< Functions global-environment
//> Resolving and Binding locals-field
  private final Map<Expr, LocalVariable> locals = new HashMap<>();
//< Resolving and Binding locals-field
  private final java.util.Deque<LoxCallable> innerStack = new java.util.ArrayDeque<>();
//> Statements and State environment-field

//< Statements and State environment-field
//> Functions interpreter-constructor
  Interpreter() {
    globals.define("clock", new LoxCallable() {
      @Override
      public int arity() { return 0; }

      @Override
      public Object call(Interpreter interpreter,
                         List<Object> arguments) {
        return (double)System.currentTimeMillis() / 1000.0;
      }

      @Override
      public String toString() { return "<native fn>"; }
    });
    globals.define("len", new LoxCallable() {
      @Override
      public int arity() { 
        return 1; 
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments) {
        return (double) ((String) arguments.get(0)).length();
      }

      @Override
      public String toString() { 
        return "<native fn>"; 
      }
    });  }
  
//< Functions interpreter-constructor
/* Evaluating Expressions interpret < Statements and State interpret
  void interpret(Expr expression) { // [void]
    try {
      Object value = evaluate(expression);
      System.out.println(stringify(value));
    } catch (RuntimeError error) {
      Lox.runtimeError(error);
    }
  }
*/
//> Statements and State interpret
  void interpret(List<Stmt> statements) {
    try {
      for (Stmt statement : statements) {
        execute(statement);
      }
    } catch (RuntimeError error) {
      Lox.runtimeError(error);
    }
  }
//< Statements and State interpret

  String interpret(Expr expression) {
    try {
      Object value = evaluate(expression);
      return stringify(value);
    } catch (RuntimeError error) {
      Lox.runtimeError(error);
      return null;
    }
  }

  void pushInner(LoxCallable inner) { innerStack.push(inner); }
  void popInner() { innerStack.pop(); }

//> evaluate
  private Object evaluate(Expr expr) {
    return expr.accept(this);
  }
//< evaluate
//> Statements and State execute
  private void execute(Stmt stmt) {
    stmt.accept(this);
  }
//< Statements and State execute
//> Resolving and Binding resolve
  void resolve(Expr expr, int depth, int index) {
    locals.put(expr, new LocalVariable(depth, index));
  }
//< Resolving and Binding resolve
//> Statements and State execute-block
  void executeBlock(List<Stmt> statements,
                    Environment environment) {
    Environment previous = this.environment;
    try {
      this.environment = environment;

      for (Stmt statement : statements) {
        execute(statement);
      }
    } finally {
      this.environment = previous;
    }
  }
//< Statements and State execute-block
//> Statements and State visit-block
  @Override
  public Void visitBlockStmt(Stmt.Block stmt) {
    executeBlock(stmt.statements, new Environment(environment));
    return null;
  }
//< Statements and State visit-block
//> Classes interpreter-visit-class
  @Override
  public Void visitClassStmt(Stmt.Class stmt) {
    List<LoxClass> superclasses = new ArrayList<>();
    for (Expr.Variable superclass : stmt.superclasses) {
      Object superclassObj = evaluate(superclass);
      if (!(superclassObj instanceof LoxClass)) {
        throw new RuntimeError(superclass.name,
            "Superclass must be a class.");
      }
      superclasses.add((LoxClass)superclassObj);
    }

    environment.define(stmt.name.lexeme, null);

    Map<String, LoxFunction> methods = new HashMap<>();
    for (Stmt.Function method : stmt.methods) {
      LoxFunction function = new LoxFunction(method, environment,
          method.name.lexeme.equals("init"));
      methods.put(method.name.lexeme, function);
    }

    LoxClass klass = new LoxClass(stmt.name.lexeme,
        superclasses, methods);

    environment.assign(stmt.name, klass);
    return null;
  }
//< Classes interpreter-visit-class
//> Statements and State visit-expression-stmt
  @Override
  public Void visitExpressionStmt(Stmt.Expression stmt) {
    evaluate(stmt.expression);
    return null;
  }
//< Statements and State visit-expression-stmt
//> Functions visit-function
  @Override
  public Void visitFunctionStmt(Stmt.Function stmt) {
/* Functions visit-function < Functions visit-closure
    LoxFunction function = new LoxFunction(stmt);
*/
/* Functions visit-closure < Classes construct-function
    LoxFunction function = new LoxFunction(stmt, environment);
*/
//> Classes construct-function
    LoxFunction function = new LoxFunction(stmt, environment,
                                           false);
//< Classes construct-function
    environment.define(stmt.name.lexeme, function);
    return null;
  }
//< Functions visit-function
//> Control Flow visit-if
  @Override
  public Void visitIfStmt(Stmt.If stmt) {
    if (isTruthy(evaluate(stmt.condition))) {
      execute(stmt.thenBranch);
    } else if (stmt.elseBranch != null) {
      execute(stmt.elseBranch);
    }
    return null;
  }
//< Control Flow visit-if
//> Statements and State visit-print
  @Override
  public Void visitPrintStmt(Stmt.Print stmt) {
    Object value = evaluate(stmt.expression);
    System.out.println(stringify(value));
    return null;
  }
//< Statements and State visit-print
//> Functions visit-return
  @Override
  public Void visitReturnStmt(Stmt.Return stmt) {
    Object value = null;
    if (stmt.value != null) value = evaluate(stmt.value);

    throw new Return(value);
  }
//< Functions visit-return
//> Statements and State visit-var
  @Override
  public Void visitVarStmt(Stmt.Var stmt) {
    Object value = UNINITIALIZED;
    if (stmt.initializer != null) {
      value = evaluate(stmt.initializer);
    }

    environment.define(stmt.name.lexeme, value);
    return null;
  }
//< Statements and State visit-var
//> Control Flow visit-while
  @Override
  public Void visitWhileStmt(Stmt.While stmt) {
    try {
      while (isTruthy(evaluate(stmt.condition))) {
        execute(stmt.body);
      }
    } catch (BreakException ex) {
      // stop the loop
    }
    return null;
  }
//< Control Flow visit-while

  @Override
  public Void visitBreakStmt(Stmt.Break stmt) {
    throw new BreakException();
  }

//> Statements and State visit-assign
  @Override
  public Object visitAssignExpr(Expr.Assign expr) {
    Object value = evaluate(expr.value);
/* Statements and State visit-assign < Resolving and Binding resolved-assign
    environment.assign(expr.name, value);
*/
//> Resolving and Binding resolved-assign

    LocalVariable local = locals.get(expr);
    if (local != null) {
      Environment ancestor = environment.ancestor(local.depth);
      if (local.index >= 0 && local.index < ancestor.getLocalSize()) {
        ancestor.assignAtIndex(local.index, value);
      } else {
        ancestor.assign(expr.name, value);
      }
    } else {
      globals.assign(expr.name, value);
    }

//< Resolving and Binding resolved-assign
    return value;
  }
//< Statements and State visit-assign
//> visit-binary
  @Override
  public Object visitBinaryExpr(Expr.Binary expr) {
    Object left = evaluate(expr.left);
    Object right = evaluate(expr.right); // [left]

    switch (expr.operator.type) {
//> binary-equality
      case BANG_EQUAL: return !isEqual(left, right);
      case EQUAL_EQUAL: return isEqual(left, right);
//< binary-equality
//> binary-comparison
      case GREATER:
//> check-greater-operand
        checkNumberOperands(expr.operator, left, right);
//< check-greater-operand
        return (double)left > (double)right;
      case GREATER_EQUAL:
//> check-greater-equal-operand
        checkNumberOperands(expr.operator, left, right);
//< check-greater-equal-operand
        return (double)left >= (double)right;
      case LESS:
//> check-less-operand
        checkNumberOperands(expr.operator, left, right);
//< check-less-operand
        return (double)left < (double)right;
      case LESS_EQUAL:
//> check-less-equal-operand
        checkNumberOperands(expr.operator, left, right);
//< check-less-equal-operand
        return (double)left <= (double)right;
//< binary-comparison
      case MINUS:
//> check-minus-operand
        checkNumberOperands(expr.operator, left, right);
//< check-minus-operand
        return (double)left - (double)right;
//> binary-plus
      case PLUS: {
        // If either operand is a String, convert both to strings and concatenate.
        if (left instanceof String || right instanceof String) {
          return stringify(left) + stringify(right);
        }

        // Otherwise, if both are numbers, add them.
        if (left instanceof Double && right instanceof Double) {
          return (double)left + (double)right;
        }

        // Otherwise it's an error.
        throw new RuntimeError(expr.operator,
            "Operands must be two numbers or at least one string.");
      }
//< binary-plus
      case SLASH:
//> check-slash-operand
        checkNumberOperands(expr.operator, left, right);

        double divisor = (double) right;
        if (divisor == 0) {
          throw new RuntimeError(expr.operator, "Division by zero.");
        }

        return (double) left / divisor;
//< check-slash-operand
      case STAR:
//> check-star-operand
        checkNumberOperands(expr.operator, left, right);
//< check-star-operand
        return (double)left * (double)right;
      case COMMA:
        evaluate(expr.left);
        return evaluate(expr.right);
    }

    // Unreachable.
    return null;
  }
//< visit-binary
//> Functions visit-call
  @Override
  public Object visitCallExpr(Expr.Call expr) {
    Object callee = evaluate(expr.callee);

    List<Object> arguments = new ArrayList<>();
    for (Expr argument : expr.arguments) { // [in-order]
      arguments.add(evaluate(argument));
    }

//> check-is-callable
    if (!(callee instanceof LoxCallable)) {
      throw new RuntimeError(expr.paren,
          "Can only call functions and classes.");
    }

//< check-is-callable
    LoxCallable function = (LoxCallable)callee;
//> check-arity
    if (arguments.size() != function.arity()) {
      throw new RuntimeError(expr.paren, "Expected " +
          function.arity() + " arguments but got " +
          arguments.size() + ".");
    }

//< check-arity
    return function.call(this, arguments);
  }
//< Functions visit-call
//> Classes interpreter-visit-get
  @Override
  public Object visitGetExpr(Expr.Get expr) {
    Object object = evaluate(expr.object);
    if (object instanceof LoxInstance) {
      Object result = ((LoxInstance) object).get(expr.name);

      if (result instanceof LoxFunction &&
          ((LoxFunction) result).isGetter()) {
        result = ((LoxFunction) result).call(this, java.util.Collections.emptyList());
      }

      return result;
    }

    throw new RuntimeError(expr.name,
        "Only instances have properties.");
  }
//< Classes interpreter-visit-get
//> visit-grouping
  @Override
  public Object visitGroupingExpr(Expr.Grouping expr) {
    return evaluate(expr.expression);
  }
//< visit-grouping
//> visit-literal
  @Override
  public Object visitLiteralExpr(Expr.Literal expr) {
    return expr.value;
  }
//< visit-literal
//> Functions visit-function-expr
  @Override
  public Object visitFunctionExpr(Expr.Function expr) {
    return new LoxFunction(expr, environment);
  }
//< Functions visit-function-expr
//> Control Flow visit-logical
  @Override
  public Object visitLogicalExpr(Expr.Logical expr) {
    Object left = evaluate(expr.left);

    if (expr.operator.type == TokenType.OR) {
      if (isTruthy(left)) return left;
    } else {
      if (!isTruthy(left)) return left;
    }

    return evaluate(expr.right);
  }
//< Control Flow visit-logical
//> Classes interpreter-visit-set
  @Override
  public Object visitSetExpr(Expr.Set expr) {
    Object object = evaluate(expr.object);

    if (!(object instanceof LoxInstance)) { // [order]
      throw new RuntimeError(expr.name,
                             "Only instances have fields.");
    }

    Object value = evaluate(expr.value);
    ((LoxInstance)object).set(expr.name, value);
    return value;
  }
//< Classes interpreter-visit-set
//> Classes interpreter-visit-this
  @Override
  public Object visitThisExpr(Expr.This expr) {
    return lookUpVariable(expr.keyword, expr);
  }
//< Classes interpreter-visit-this
  @Override
  public Object visitInnerExpr(Expr.Inner expr) {
    if (innerStack.isEmpty()) {
      // inner() does nothing if there is no matching subclass method.
      return new LoxCallable() {
        @Override public int arity() { return 0; }
        @Override public Object call(Interpreter interpreter, java.util.List<Object> arguments) {
          return null;
        }
        @Override public String toString() { return "<inner-noop>"; }
      };
    }
    return innerStack.peek();
  }
//> visit-unary
  @Override
  public Object visitUnaryExpr(Expr.Unary expr) {
    Object right = evaluate(expr.right);

    switch (expr.operator.type) {
//> unary-bang
      case BANG:
        return !isTruthy(right);
//< unary-bang
      case MINUS:
//> check-unary-operand
        checkNumberOperand(expr.operator, right);
//< check-unary-operand
        return -(double)right;
    }

    // Unreachable.
    return null;
  }
//< visit-unary
//> Statements and State visit-variable
  @Override
  public Object visitVariableExpr(Expr.Variable expr) {
/* Statements and State visit-variable < Resolving and Binding call-look-up-variable
    return environment.get(expr.name);
*/
//> Resolving and Binding call-look-up-variable
    return lookUpVariable(expr.name, expr);
//< Resolving and Binding call-look-up-variable
  }
//> Resolving and Binding look-up-variable
  private Object lookUpVariable(Token name, Expr expr) {
    LocalVariable local = locals.get(expr);
    Object value;
    if (local != null) {
      Environment ancestor = environment.ancestor(local.depth);
      if (local.index >= 0 && local.index < ancestor.getLocalSize()) {
        value = ancestor.getAtIndex(local.index);
      } else {
        value = ancestor.get(name);
      }
    } else {
      value = globals.get(name);
    }
    
    if (value == UNINITIALIZED) {
      throw new RuntimeError(name, "Variable must be initialized before use.");
    }
    return value;
  }
//< Resolving and Binding look-up-variable
//< Statements and State visit-variable
//> check-operand
  private void checkNumberOperand(Token operator, Object operand) {
    if (operand instanceof Double) return;
    throw new RuntimeError(operator, "Operand must be a number.");
  }
//< check-operand
//> check-operands
  private void checkNumberOperands(Token operator,
                                   Object left, Object right) {
    if (left instanceof Double && right instanceof Double) return;
    // [operand]
    throw new RuntimeError(operator, "Operands must be numbers.");
  }
//< check-operands
//> is-truthy
  private boolean isTruthy(Object object) {
    if (object == null) return false;
    if (object instanceof Boolean) return (boolean)object;
    return true;
  }
//< is-truthy
//> is-equal
  private boolean isEqual(Object a, Object b) {
    if (a == null && b == null) return true;
    if (a == null) return false;

    return a.equals(b);
  }
//< is-equal
//> stringify
  private String stringify(Object object) {
    if (object == null) return "nil";

    if (object instanceof Double) {
      String text = object.toString();
      if (text.endsWith(".0")) {
        text = text.substring(0, text.length() - 2);
      }
      return text;
    }

    return object.toString();
  }
//< stringify
}
