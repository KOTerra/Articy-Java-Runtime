# LibGDX Integration Guidelines: Articy Java Runtime

This document details how to bridge the **Articy Java Runtime** with **Ashley ECS** and **gdx-ai** within a LibGDX project.

## 1. Dependency Management

### Local Maven (Recommended)
Publish the runtime to your local Maven repository:
```bash
./gradlew publishToMavenLocal
```
In your LibGDX `build.gradle`:
```gradle
repositories {
    mavenLocal()
}
dependencies {
    implementation 'com.articy.runtime:articy-java-runtime:1.0.0'
}
```

### Multi-Project (includeBuild)
In your LibGDX `settings.gradle`:
```gradle
includeBuild('../path/to/Articy-Java-Runtime')
```

---

## 2. Ashley ECS Integration: The `ArticyComponent`

To map narrative data to game entities, define an `ArticyComponent`.

```java
public class ArticyComponent implements Component {
    // The unique 64-bit ID from Articy:draft
    public long articyId; 
    
    // Optional: technical name for easier debugging
    public String technicalName;
}
```

### Finding Entities by Articy ID
Use an Ashley `Family` match to find the entity associated with a specific Articy ID.

```java
public Entity findEntityByArticyId(long id) {
    Family family = Family.all(ArticyComponent.class).get();
    ImmutableArray<Entity> entities = engine.getEntitiesFor(family);
    for (Entity entity : entities) {
        if (ComponentMapper.getFor(ArticyComponent.class).get(entity).articyId == id) {
            return entity;
        }
    }
    return null;
}
```

---

## 3. The Bridge: `IScriptMethodProvider` Mapper

Create a `GameActionHandler` to execute Expresso scripts (e.g., `GiveItem("Player", "Sword")`) as Ashley ECS operations.

```java
public class GameActionHandler implements IScriptMethodProvider {
    private final Engine ashleyEngine;
    private final ArticyVariableManager variableManager;

    public GameActionHandler(Engine engine, ArticyVariableManager vm) {
        this.ashleyEngine = engine;
        this.variableManager = vm;
    }

    @Override
    public Object invokeCustomMethod(String name, Object... args) {
        // CRITICAL: Prevent side-effects during branch forecasting
        if (isShadowState()) return null;

        if ("GiveItem".equals(name)) {
            String targetName = (String) args[0];
            String itemName = (String) args[1];
            executeGiveItem(targetName, itemName);
        }
        return null;
    }

    @Override
    public boolean isShadowState() {
        return variableManager.isInShadowState();
    }

    private void executeGiveItem(String target, String item) {
        // 1. Find Entity via ArticyComponent or technicalName
        // 2. ashleyEngine.add(entity, new ItemComponent(item));
    }
}
```

---

## 4. Narrative-Driven AI: `gdx-ai` Integration

### State Transitions (Finite State Machines)
Bridge `ArticyVariableManager` values to `gdx-ai` State transitions.

```java
public class NPCStates extends State<NPCEntity> {
    IDLE {
        @Override
        public void update(NPCEntity npc) {
            // Query Articy Variables to trigger AI state change
            boolean isAngry = (Boolean) ArticyRuntime.getVariableManager()
                .getVariable("GameState", "npc_angry");
            
            if (isAngry) {
                npc.stateMachine.changeState(ATTACK);
            }
        }
    }
}
```

### Behavior Tree Conditions
Use a custom `gdx-ai` `LeafTask` to query the `ArticyFlowPlayer` for available narrative branches.

```java
public class CanTalkCondition extends LeafTask<NPCEntity> {
    @Override
    public Status execute() {
        ArticyFlowPlayer player = getNPC().getFlowPlayer();
        // Check if there are any valid dialogue branches currently available
        // before showing a "Talk" prompt.
        if (player.getAvailableBranches().isEmpty()) {
            return Status.FAILED;
        }
        return Status.SUCCEEDED;
    }
}
```

---

## 5. Shadow State Safety Mandate

**Rigorously protect game state from Forecasting Side-Effects.**

The `ArticyFlowPlayer` creates a **Shadow State** to predict future branches. During this phase:
1.  Variables are mutated only in a deep-cloned map.
2.  `IScriptMethodProvider.isShadowState()` will return `true`.

**Rules for your `IScriptMethodProvider` implementation:**
- **DO NOT** play sounds or trigger visual FX.
- **DO NOT** mutate Ashley Components or add/remove entities.
- **DO NOT** trigger `gdx-ai` state transitions.
- **ONLY** return mathematical or logical values required for the script to continue (e.g., `getSeenCounter`).
