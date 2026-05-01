package com.articy.runtime;

import com.articy.runtime.core.*;
import com.articy.runtime.logic.*;
import com.articy.runtime.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class ManicManfredIntegrationTest {
    private ArticyDatabase db;
    private ArticyVariableManager vm;
    private MockMethodProvider provider = new MockMethodProvider();
    private static final String EXPORT_DIR = "exports/Articy-ManicManfred-Export-json";

    public static class MockMethodProvider implements IScriptMethodProvider {
        private ArticyVariableManager context;
        @Override
        public Object invokeCustomMethod(String name, Object... args) {
            if ("getSeenCounter".equals(name)) {
                return 0;
            }
            return null;
        }

        @Override
        public void setVariableContext(ArticyVariableManager vars) {
            this.context = vars;
        }

        @Override
        public boolean isShadowState() {
            return context != null && context.isInShadowState();
        }
        
        public int getSeenCounter(String id) {
            return 0;
        }
    }

    @BeforeEach
    public void setup() throws IOException {
        db = ArticyRuntime.initialize(EXPORT_DIR, provider);
        vm = ArticyRuntime.getVariableManager();
    }

    @Test
    public void testManicManfredVariables() {
        assertEquals(false, vm.getVariable("GameState", "memory"));
        assertEquals(0, vm.getVariable("GameState", "lock_number"));
    }

    @Test
    public void testTherapistDialogueFlow() {
        AtomicInteger pauseCount = new AtomicInteger(0);
        List<Branch> lastBranches = new ArrayList<>();

        ArticyFlowPlayer player = new ArticyFlowPlayer(db, vm, ArticyRuntime.getEngine(), provider, new IArticyFlowPlayerCallbacks() {
            @Override
            public void onFlowPlayerPaused(FlowObject object) {
                pauseCount.incrementAndGet();
                System.out.println("Paused on: " + object.getTechnicalName());
            }

            @Override
            public void onBranchesUpdated(List<Branch> branches) {
                lastBranches.clear();
                lastBranches.addAll(branches);
                System.out.println("Branches count: " + branches.size());
            }
        });

        // Start on "Dlg_TheTherapist" (0x01000001000002AA)
        player.startOn(0x01000001000002AAL);

        // It should have auto-advanced to the first fragment (DFr_Awake)
        assertEquals(1, pauseCount.get(), "Should have auto-advanced to first fragment");
        assertEquals("DFr_Awake", player.getCurrentPausedObject().getTechnicalName());
        
        // lastBranches should contain branches FROM DFr_Awake
        assertFalse(lastBranches.isEmpty(), "Should have branches from DFr_Awake");
    }
}
