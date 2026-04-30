package com.articy.runtime.core;

import com.articy.runtime.model.Branch;
import com.articy.runtime.model.FlowObject;
import java.util.List;

public interface IArticyFlowPlayerCallbacks {
    void onFlowPlayerPaused(FlowObject object);
    void onBranchesUpdated(List<Branch> branches);
}
