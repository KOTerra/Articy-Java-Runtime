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
    private static final String EXPORT_DIR = "Articy-ManicManfred-Export-json";

    public static class MockMethodProvider implements IScriptMethodProvider {
        @Override
        public Object invokeCustomMethod(String name, Object... args) {
            return null;
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
        // GameState.memory: False
        // GameState.lock_number: 0
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

        // It should find branches from the input pin
        assertFalse(lastBranches.isEmpty(), "Should have branches from entry");
        
        // One of the branches should be DFr_Awake (0x01000001000002B8)
        // or DFr_3CE3A7C7. DFr_3CE3A7C7 has a condition: getSeenCounter("DFr_Awake") > 0
        // Since getSeenCounter returns 0, only DFr_Awake should be valid if it has no condition or true condition.
        
        boolean foundAwake = false;
        for (Branch b : lastBranches) {
            if ("DFr_Awake".equals(b.getTargetNode().getTechnicalName())) {
                foundAwake = true;
                player.advance(b);
                break;
            }
        }
        
        assertTrue(foundAwake, "Should have found DFr_Awake branch");
        assertEquals(1, pauseCount.get());
        assertEquals("DFr_Awake", player.getCurrentPausedObject().getTechnicalName());
    }
}
