//> Classes lox-class
package com.craftinginterpreters.lox;

import java.util.List;
import java.util.Map;

/* Classes lox-class < Classes lox-class-callable
class LoxClass {
*/
//> lox-class-callable
class LoxClass extends LoxInstance implements LoxCallable {
//< lox-class-callable
  final String name;
  final LoxClass superclass;
  private final Map<String, LoxFunction> methods;

  LoxClass(String name,
           LoxClass superclass,
           Map<String, LoxFunction> methods) {
    super(null);
    this.name = name;
    this.superclass = superclass;
    this.methods = methods;
  }
//< lox-class-methods
//> lox-class-find-method
  LoxFunction findMethod(String name) {
    if (methods.containsKey(name)) {
      return methods.get(name);
    }

    if (superclass != null) {
      return superclass.findMethod(name);
    }

    return null;
  }
//< lox-class-find-method

  java.util.List<LoxFunction> findMethodChain(String name) {
    java.util.ArrayList<LoxFunction> chain = new java.util.ArrayList<>();

    if (superclass != null) {
      chain.addAll(superclass.findMethodChain(name));
    }

    LoxFunction here = methods.get(name);
    if (here != null) chain.add(here);

    return chain;
  }

  @Override
  public String toString() {
    return name;
  }
//> lox-class-call-arity
  @Override
  public Object call(Interpreter interpreter,
                     List<Object> arguments) {
    LoxInstance instance = new LoxInstance(this);
//> lox-class-call-initializer
    LoxFunction initializer = findMethod("init");
    if (initializer != null) {
      initializer.bind(instance).call(interpreter, arguments);
    }

//< lox-class-call-initializer
    return instance;
  }

  @Override
  public int arity() {
/* Classes lox-class-call-arity < Classes lox-initializer-arity
    return 0;
*/
//> lox-initializer-arity
    LoxFunction initializer = findMethod("init");
    if (initializer == null) return 0;
    return initializer.arity();
//< lox-initializer-arity
  }
//< lox-class-call-arity
}
