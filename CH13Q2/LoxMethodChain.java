package com.craftinginterpreters.lox;


import java.util.List;


class LoxMethodChain implements LoxCallable {
  private final List<LoxFunction> chain;
  private final LoxInstance receiver;
  private final int index;


  LoxMethodChain(List<LoxFunction> chain, LoxInstance receiver, int index) {
    this.chain = chain;
    this.receiver = receiver;
    this.index = index;
  }


  @Override
  public int arity() {
    return chain.get(index).arity();
  }


  @Override
  public Object call(Interpreter interpreter, List<Object> arguments) {
    LoxCallable nextInner;
    if (index + 1 < chain.size()) {
      nextInner = new LoxMethodChain(chain, receiver, index + 1);
    } else {
      nextInner = new LoxCallable() {
        @Override public int arity() { return 0; }
        @Override public Object call(Interpreter i, List<Object> args) { return null; }
        @Override public String toString() { return "<inner-noop>"; }
      };
    }


    interpreter.pushInner(nextInner);
    try {
      return chain.get(index).bind(receiver).call(interpreter, arguments);
    } finally {
      interpreter.popInner();
    }
  }


  @Override
  public String toString() {
    return "<method-chain #" + index + ">";
  }
}
