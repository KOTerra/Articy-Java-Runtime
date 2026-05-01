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
    private IScriptMethodProvider provider;
    private static final String EXPORT_DIR = "exports/Articy-Dracula-Export-json";

    @BeforeEach
    public void setup() throws IOException {
        provider = new IScriptMethodProvider() {
            private ArticyVariableManager context;
            @Override
            public Object invokeCustomMethod(String name, Object... args) {
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
        };
        db = ArticyRuntime.initialize(EXPORT_DIR, provider);
        vm = ArticyRuntime.getVariableManager();
    }

    @Test
    public void testDraculaFirstMeetingFlow() {
        AtomicInteger pauseCount = new AtomicInteger(0);
        AtomicInteger branchCount = new AtomicInteger(0);
        List<Branch> lastBranches = new ArrayList<>();

        ArticyFlowPlayer player = new ArticyFlowPlayer(db, vm, ArticyRuntime.getEngine(), provider, new IArticyFlowPlayerCallbacks() {
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
        // This is a Dialogue container. startOn should enter it and auto-advance to the first DialogueFragment.
        player.startOn(0x0100000000002CC2L);

        // It should have auto-advanced to the first DialogueFragment (DFr_A30F0F3E)
        assertEquals(1, pauseCount.get(), "Should have auto-advanced to the first fragment");
        assertEquals("DFr_A30F0F3E", player.getCurrentPausedObject().getTechnicalName());
        assertFalse(lastBranches.isEmpty(), "Should have found branches from the first fragment");
        
        // Advance to the next branch (there should be only one)
        Branch nextBranch = lastBranches.get(0);
        player.advance(nextBranch);
        
        // Now it should have paused on the second node in the flow
        assertEquals(2, pauseCount.get());
    }

    @Test
    public void testGlobalVariablesInitialization() {
        assertEquals(false, vm.getVariable("GameState", "isNameRevealed"));
        assertEquals(1, vm.getVariable("GameState", "playerLevel"));
        assertEquals(true, vm.getVariable("Inventory", "stake"));
    }
}
