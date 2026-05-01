package com.articy.runtime.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an object that is part of the flow (has input and output pins).
 */
public abstract class FlowObject extends ArticyObject {
    private List<Pin> inputPins = new ArrayList<>();
    private List<Pin> outputPins = new ArrayList<>();

    public List<Pin> getInputPins() {
        return inputPins;
    }

    public List<Pin> getOutputPins() {
        return outputPins;
    }
}
