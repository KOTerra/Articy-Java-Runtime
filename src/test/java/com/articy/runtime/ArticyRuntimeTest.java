package com.articy.runtime;

import com.articy.runtime.core.*;
import com.articy.runtime.logic.*;
import com.articy.runtime.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class ArticyRuntimeTest {
    private ArticyDatabase db;
    private ArticyVariableManager vm;
    private IScriptMethodProvider provider;
    private static String exportDir = "Articy-Dracula-Export-json";

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
        db = ArticyRuntime.initialize(exportDir, provider);
        vm = ArticyRuntime.getVariableManager();
    }

    @Test
    public void testDatabaseLoad() {
        assertNotNull(db);
        // Verify a specific object from the JSON
        // DFr_C5AAD3A3 -> 0x0100000000004248
        DialogueFragment df = db.getObject(0x0100000000004248L, DialogueFragment.class);
        assertNotNull(df);
        assertEquals("DFr_C5AAD3A3", df.getTechnicalName());
    }

    @Test
    public void testVariableManager() {
        assertNotNull(vm);
        // GameState.playerLevel defaults to 1
        Object val = vm.getVariable("GameState", "playerLevel");
        assertEquals(1, val);
        
        vm.setVariable("GameState", "playerLevel", 10);
        assertEquals(10, vm.getVariable("GameState", "playerLevel"));
    }

    @Test
    public void testShadowStateIsolation() {
        vm.setVariable("GameState", "playerLevel", 5);
        ArticyVariableManager shadow = vm.createShadowState();
        shadow.setVariable("GameState", "playerLevel", 100);
        
        assertEquals(100, shadow.getVariable("GameState", "playerLevel"));
        assertEquals(5, vm.getVariable("GameState", "playerLevel"));
    }

    @Test
    public void testShadowIsolationDuringForecasting() {
        // Find an Instruction node that sets a variable
        // Ins_90063CD7 -> 0x0100000000003D1C sets GameState.isNameRevealed = true
        Instruction instr = db.getObject(0x0100000000003D1CL, Instruction.class);
        assertNotNull(instr);
        
        vm.setVariable("GameState", "isNameRevealed", false);
        
        ArticyFlowPlayer player = new ArticyFlowPlayer(db, vm, ArticyRuntime.getEngine(), provider, new IArticyFlowPlayerCallbacks() {
            @Override
            public void onFlowPlayerPaused(FlowObject object) {}
            @Override
            public void onBranchesUpdated(List<Branch> branches) {}
        });
        
        // This will trigger forecastBranches which should run the instruction in shadow state
        player.startOn(0x0100000000002CC2L); // Dialogue containing the instruction
        
        // Master state should still be false
        assertEquals(false, vm.getVariable("GameState", "isNameRevealed"));
    }
}
