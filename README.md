# Articy Java Runtime

A pure, engine-agnostic Java library (JDK 8+) for parsing and executing narrative data exported from Articy:draft 3 using the Generic Engine Export (JSON).

## Features

- **High-Performance Registry**: Uses 64-bit primitive `long` keys for constant-time object lookups.
- **Memory-Efficient Parsing**: Streams large JSON packages to minimize heap usage.
- **Shadow State Forecasting**: Predicts future narrative branches without affecting the master game state.
- **Expresso Logic Engine**: Integrated with Apache Commons JEXL 3.3 for strict, secure script evaluation.
- **Recursive Traversal**: Automatically resolves transparent nodes (Hubs, Conditions, Jumps) and halts at interaction points (DialogueFragments).

## Installation

Add the following dependencies to your `build.gradle`:

```gradle
dependencies {
    implementation 'com.fasterxml.jackson.core:jackson-databind:2.15.2'
    implementation 'org.apache.commons:commons-jexl3:3.3'
}
```

## Usage

### Initialization

To load your narrative data, simply provide the path to your export directory:

```java
ArticyDatabase db = ArticyRuntime.initialize("path/to/your/Articy-Export-json", new MyScriptMethodProvider());
ArticyVariableManager vm = ArticyRuntime.getVariableManager();
```

### Swapping Exports

To swap between different Articy projects or versions, change the directory path in the `initialize` call:

```java
// Load Project A
ArticyDatabase dbA = ArticyRuntime.initialize("exports/project_a", provider);

// Load Project B
ArticyDatabase dbB = ArticyRuntime.initialize("exports/project_b", provider);
```

### Playing the Flow

Implement `IArticyFlowPlayerCallbacks` to handle narrative events:

```java
ArticyFlowPlayer player = new ArticyFlowPlayer(db, vm, ArticyRuntime.getEngine(), provider, new IArticyFlowPlayerCallbacks() {
    @Override
    public void onFlowPlayerPaused(FlowObject object) {
        if (object instanceof DialogueFragment) {
            System.out.println(((DialogueFragment)object).getText());
        }
    }

    @Override
    public void onBranchesUpdated(List<Branch> branches) {
        // Display available choices to the player
    }
});

// Start traversal at a specific node ID
player.startOn(0x0100000000002CC2L);
```

## Testing

Run the test suite to verify your setup:

```bash
./gradlew test
```
