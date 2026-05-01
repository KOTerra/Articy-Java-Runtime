package com.articy.runtime;

import com.articy.runtime.core.*;
import com.articy.runtime.logic.*;
import com.articy.runtime.model.*;
import java.io.IOException;
import java.util.List;
import java.util.Scanner;

public class DraculaConsoleApp {
    private static ArticyDatabase db;
    private static ArticyVariableManager vm;
    private static ArticyFlowPlayer player;
    private static List<Branch> currentBranches;
    private static boolean running = true;

    public static void main(String[] args) throws IOException {
        String exportDir = "exports/Articy-Dracula-Export-json";
        
        System.out.println("--- Articy Java Runtime: Interactive Dracula Test ---");
        
        // 1. Initialize Runtime

        IScriptMethodProvider provider = new IScriptMethodProvider() {
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
        
        // 2. Setup Flow Player
        player = new ArticyFlowPlayer(db, vm, ArticyRuntime.getEngine(), provider, new IArticyFlowPlayerCallbacks() {
            @Override
            public void onFlowPlayerPaused(FlowObject object) {
                if (object instanceof DialogueFragment) {
                    DialogueFragment df = (DialogueFragment) object;
                    String speaker = "Unknown";
                    ArticyObject speakerObj = db.getObject(df.getSpeakerId(), ArticyObject.class);
                    if (speakerObj != null) speaker = speakerObj.getTechnicalName();
                    
                    String text = ArticyRuntime.getLocalization().localize(df.getText());
                    System.out.println("\n[" + speaker + "]: " + text);
                }
            }

            @Override
            public void onBranchesUpdated(List<Branch> branches) {
                currentBranches = branches;
                if (branches.isEmpty()) {
                    System.out.println("\n--- END OF DIALOGUE ---");
                    running = false;
                } else {
                    System.out.println("\nChoices:");
                    for (int i = 0; i < branches.size(); i++) {
                        FlowObject target = branches.get(i).getTargetNode();
                        String menuText = "";
                        String idStr = "";
                        if (target instanceof DialogueFragment) {
                            DialogueFragment df = (DialogueFragment) target;
                            String localizedMenu = ArticyRuntime.getLocalization().localize(df.getMenuText());
                            String localizedText = ArticyRuntime.getLocalization().localize(df.getText());
                            
                            idStr = (df.getMenuText() == null || df.getMenuText().isEmpty()) ? df.getText() : df.getMenuText();
                            menuText = (localizedMenu == null || localizedMenu.isEmpty() || localizedMenu.equals(df.getMenuText())) ? localizedText : localizedMenu;
                            
                            if (menuText != null && menuText.length() > 80) menuText = menuText.substring(0, 77) + "...";
                        }
                        System.out.println((i + 1) + ". [" + idStr + "] " + menuText);
                    }
                }
            }
        });

        // 3. Start Dialogue
        // Start on "Dracula and Mina - First meeting"
        player.startOn(0x0100000000002CC2L);

        // 4. Input Loop
        Scanner scanner = new Scanner(System.in);
        while (running) {
            System.out.println("\nVariables Check: GameState.isNameRevealed = " + vm.getVariable("GameState", "isNameRevealed"));
            System.out.print("Enter choice number (or 'q' to quit): ");
            String input = scanner.nextLine();
            
            if ("q".equalsIgnoreCase(input)) break;
            
            try {
                int choice = Integer.parseInt(input) - 1;
                if (currentBranches != null && choice >= 0 && choice < currentBranches.size()) {
                    player.advance(currentBranches.get(choice));
                } else {
                    System.out.println("Invalid choice.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Please enter a number.");
            }
        }
        
        System.out.println("Exiting application.");
    }
}
