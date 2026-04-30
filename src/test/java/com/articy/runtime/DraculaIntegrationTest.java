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

public class DraculaIntegrationTest {
    private ArticyDatabase db;
    private ArticyVariableManager vm;
    private static final String EXPORT_DIR = "Articy-Dracula-Export-json";

    @BeforeEach
    public void setup() throws IOException {
        db = ArticyRuntime.initialize(EXPORT_DIR, new IScriptMethodProvider() {
            @Override
            public Object invokeCustomMethod(String name, Object... args) {
                return null;
            }
        });
        vm = ArticyRuntime.getVariableManager();
    }

    @Test
    public void testDraculaFirstMeetingFlow() {
        AtomicInteger pauseCount = new AtomicInteger(0);
        AtomicInteger branchCount = new AtomicInteger(0);
        List<Branch> lastBranches = new ArrayList<>();

        ArticyFlowPlayer player = new ArticyFlowPlayer(db, vm, ArticyRuntime.getEngine(), null, new IArticyFlowPlayerCallbacks() {
            @Override
            public void onFlowPlayerPaused(FlowObject object) {
                pauseCount.incrementAndGet();
                if (object instanceof DialogueFragment) {
                    DialogueFragment df = (DialogueFragment) object;
                    System.out.println("CALLBACK: Paused on DialogueFragment: " + df.getTechnicalName());
                }
            }

            @Override
            public void onBranchesUpdated(List<Branch> branches) {
                System.out.println("CALLBACK: Branches updated, count: " + branches.size());
                branchCount.set(branches.size());
                lastBranches.clear();
                lastBranches.addAll(branches);
            }
        });

        // Start on "Dracula and Mina - First meeting" (Dlg_56A97563)
        // This is a Dialogue container. startOn should enter it and find the first DialogueFragment.
        player.startOn(0x0100000000002CC2L);

        // It should NOT have paused on the Dialogue itself, but it should have found branches (the first DialogueFragment)
        assertEquals(0, pauseCount.get(), "Should not have paused on the container node itself");
        assertFalse(lastBranches.isEmpty(), "Should have found branches from the container entry");
        
        // The first branch target should be DFr_A30F0F3E
        assertEquals("DFr_A30F0F3E", lastBranches.get(0).getTargetNode().getTechnicalName());

        // Advance to the first line
        Branch firstLineBranch = lastBranches.get(0);
        player.advance(firstLineBranch);
        
        // Now it should have paused on the first line
        assertEquals(1, pauseCount.get());
        assertEquals("DFr_A30F0F3E", player.getCurrentPausedObject().getTechnicalName());
    }

    @Test
    public void testGlobalVariablesInitialization() {
        // From global_variables.json
        // GameState.isNameRevealed: False
        // GameState.playerLevel: 1
        // Inventory.stake: True
        
        assertEquals(false, vm.getVariable("GameState", "isNameRevealed"));
        assertEquals(1, vm.getVariable("GameState", "playerLevel"));
        assertEquals(true, vm.getVariable("Inventory", "stake"));
    }
}
