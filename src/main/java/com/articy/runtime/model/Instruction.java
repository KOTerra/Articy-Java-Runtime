package com.articy.runtime.model;

public class Instruction extends TransparentNode {
    private String expression;

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }
}
