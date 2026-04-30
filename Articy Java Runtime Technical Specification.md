# **Technical Specification: Pure Java Runtime Library for Articy:draft 3 Generic Engine Export**

## **Executive Summary and Architectural Principles**

The integration of narrative design data from articy:draft 3 into standalone applications necessitates a robust, engine-agnostic runtime architecture.1 Historically, articy:draft runtimes have been heavily coupled to specific environments, such as the Unity or Unreal engines, utilizing C\# or C++ implementations that are tightly bound to those proprietary ecosystems and their specific actor, component, or MonoBehaviour paradigms.1 While these integrations provide seamless functionality within their respective engines, they present an insurmountable barrier for developers utilizing alternative Java-based frameworks such as LibGDX, jMonkeyEngine, custom Java game servers, or headless simulation environments. The specification detailed herein outlines the architectural blueprint for a pure Java (JDK 8+) runtime library, specifically engineered to process the Articy Generic Engine Export format.4 This JSON-based export provides raw narrative and structural data completely devoid of engine-specific wrappers or proprietary structural assumptions.4

The primary objective of this architecture is to achieve total decoupling from any specific game engine's rendering, physics, or entity-component-system (ECS) logic. By implementing a Plain Old Java Object (POJO) data model, an isolated and highly concurrent state management system, and an Apache Commons JEXL-driven execution engine, the library functions as an embedded dependency. It operates entirely in memory, reacting to external interface calls and yielding control back to the host application via strictly defined callbacks.

To achieve this, the architecture relies on four fundamental pillars, each addressing a specific domain of the narrative engine's requirements:

1. **Central Data Modeling (ArticyDatabase)**: A high-performance, thread-safe concurrent lookup system designed for navigating the complex directed graph of instantiated narrative objects. This system utilizes 64-bit Hexadecimal Object IDs as the primary key space, ensuring ![][image1] constant-time lookups while minimizing memory overhead and garbage collection churn.5  
2. **State Management (ArticyVariableManager)**: A scoped variable tracking system that perfectly mirrors Articy's global variable sets. This system features a critical "Shadow State" deep-cloning mechanism, enabling recursive branch forecasting and condition evaluation without inadvertently committing side effects to the actual game state.7  
3. **Expresso Evaluation Engine**: An advanced integration of Apache Commons JEXL 3.x designed to parse, evaluate, and securely execute articy:expresso syntax. This encompasses standard mathematical and logical operations, as well as the dynamic reflection-based mapping of designer-defined custom functions extracted from script\_methods.json.9  
4. **Graph Traversal Engine (ArticyFlowPlayer)**: A sophisticated recursive evaluation mechanism capable of navigating connected nodes and pins. The traversal engine autonomously resolves transparent logical elements—such as Hubs, Jumps, Conditions, and Instructions—and yields execution control to the host application only upon encountering explicit, pausable interaction points.12

## **System Architecture and UML Blueprint**

The architectural layout isolates the internal processing core from the public-facing application programming interface (API). The host application should never need to interact directly with the underlying JSON parsing libraries (such as Jackson or Gson) or the raw Apache Commons JEXL context. Instead, the application interfaces entirely through facade classes and dedicated listener interfaces.

The following UML class diagram, represented in standard syntax, illustrates the relational boundaries, aggregation paths, and inheritance structures that govern the runtime library.

Fragment de cod

classDiagram  
    class ArticyRuntime {  
        \<\<facade\>\>  
        \+initialize(String exportDir, IScriptMethodProvider provider) ArticyDatabase  
    }

    class ArticyDatabase {  
        \-ConcurrentHashMap\~Long, ArticyObject\~ objectRegistry  
        \-ArticyVariableManager globalVariables  
        \-Map\~Long, AssetObject\~ assetRegistry  
        \+loadFromJSON(String manifestPath)  
        \+getObject(Long hexId, Class\~T\~ type) T  
        \+getVariableManager() ArticyVariableManager  
    }

    class ArticyObject {  
        \<\<abstract\>\>  
        \+Long id  
        \+String technicalName  
        \+Long parentId  
        \+List\~ArticyObject\~ children  
    }

    class FlowObject {  
        \<\<abstract\>\>  
        \+List\~Pin\~ inputPins  
        \+List\~Pin\~ outputPins  
    }  
      
    class PausableNode {  
        \<\<abstract\>\>  
    }  
      
    class TransparentNode {  
        \<\<abstract\>\>  
    }

    class DialogueFragment {  
        \+Long speakerId  
        \+String spokenText  
        \+String menuText  
    }

    class Pin {  
        \+Long pinId  
        \+String script  
        \+List\~Connection\~ connections  
    }  
      
    class Connection {  
        \+Long targetPinId  
        \+Long targetNodeId  
    }

    class ArticyVariableManager {  
        \-Map\~String, Map\~String, Object\~\~ variableSets  
        \-boolean isShadowState  
        \+createShadowState() ArticyVariableManager  
        \+getVariable(String set, String var) Object  
        \+setVariable(String set, String var, Object val)  
        \+isInShadowState() boolean  
    }

    class ExpressoEngine {  
        \-JexlEngine jexl  
        \-JexlContext context  
        \+evaluateCondition(String script, ArticyVariableManager vars) boolean  
        \+executeInstruction(String script, ArticyVariableManager vars)  
        \+registerCustomMethods(IScriptMethodProvider provider, String jsonPath)  
    }

    class ArticyFlowPlayer {  
        \-ArticyDatabase db  
        \-ExpressoEngine engine  
        \-ArticyVariableManager currentVars  
        \-Set\~Class\~ pauseOnTypes  
        \+play()  
        \+advance(Branch branch)  
        \-forecastBranches(FlowObject node) List\~Branch\~  
    }

    class IArticyFlowPlayerCallbacks {  
        \<\<interface\>\>  
        \+onFlowPlayerPaused(FlowObject object)  
        \+onBranchesUpdated(List\~Branch\~ branches)  
    }

    class IScriptMethodProvider {  
        \<\<interface\>\>  
        \+invokeCustomMethod(String name, Object... args) Object  
        \+isShadowState() boolean  
    }

    ArticyDatabase "1" \*-- "many" ArticyObject  
    ArticyObject \<|-- FlowObject  
    FlowObject \<|-- PausableNode  
    FlowObject \<|-- TransparentNode  
    PausableNode \<|-- DialogueFragment  
    FlowObject "1" \*-- "many" Pin  
    Pin "1" \*-- "many" Connection  
    ArticyDatabase "1" \*-- "1" ArticyVariableManager  
    ArticyFlowPlayer "1" o-- "1" ArticyDatabase  
    ArticyFlowPlayer "1" o-- "1" ExpressoEngine  
    ArticyFlowPlayer \--\> IArticyFlowPlayerCallbacks  
    ExpressoEngine \--\> IScriptMethodProvider

This structural design ensures that the ArticyDatabase remains the sole source of truth for object instantiation and relational mapping, while the ArticyFlowPlayer acts as an ephemeral state machine that traverses the static graph provided by the database.

## **Data Ingestion and The Generic Engine Export Payload**

The articy:draft 3 Generic Engine Export circumvents proprietary binary formats in favor of a universally parsable, JSON-based directory structure.4 When a designer triggers the generic export, the payload is distributed across a suite of explicitly defined structural files.4 To achieve high performance, the Java runtime must ingest these files utilizing a highly optimized, memory-conscious methodology.

### **JSON File Roles and Responsibilities**

The runtime library initiates its bootstrap sequence by sequentially parsing the generic export payload. The payload is divided into logical partitions to facilitate parallel deserialization if necessary. The following table delineates the core files generated by the export ruleset and their respective processing requirements within the Java runtime.

| Export File | Data Payload Description | Runtime Processing Responsibility |
| :---- | :---- | :---- |
| manifest.json | Contains top-level project metadata, export ruleset configurations, project hierarchy entry points, and schema versions.4 | Validates export compatibility, initializes the root nodes of the ArticyDatabase, and maps the global narrative structure.16 |
| package\_objects.json | The primary data store containing the flattened definitions of all project entities, dialogues, fragments, pins, and connections.15 | Requires a streaming JSON parser (e.g., Jackson JsonParser) to sequentially instantiate POJOs without loading the entire payload into the heap simultaneously.15 |
| hierarchy.json | Dictates the parent-child relational tree, establishing the topological nesting of dialogues within flow fragments.15 | Executed during the secondary resolution pass to populate the children lists of ArticyObject instances, constructing the directed acyclic graph.15 |
| script\_methods.json | A schema defining the signatures, parameter types, and return types of all custom Expresso functions invoked in the project.18 | Ingested by the ExpressoEngine to dynamically build JEXL namespace bindings via the IScriptMethodProvider interface.9 |
| global\_variables.json | Defines the initial state, default values, and data types (Boolean, Integer, String) of all narrative tracking variables.20 | Used to construct the baseline ArticyVariableManager and enforce type-safety restrictions on the subsequent runtime mutation of variables.20 |

### **Streaming Deserialization and Memory Constraints**

In large-scale narrative projects containing hundreds of thousands of interconnected dialogue lines, loading the entire package\_objects.json into a standard Document Object Model (DOM) tree can easily exhaust the JVM heap space, triggering OutOfMemoryError exceptions. To mitigate this, the specification mandates the use of streaming JSON parsing.

The parser iterates through the JSON array sequentially. As each JSON object representing an ArticyObject is encountered, a dedicated factory class inspects the object's Type discriminator field. Based on this discriminator, the factory instantiates the corresponding concrete subclass (e.g., DialogueFragment, Condition, Hub, Entity).12

Once instantiated, the raw object is injected into the ArticyDatabase registry. At this stage of the ingestion pipeline, the relational graph remains unconnected. References to other objects—such as a Connection target ID or a DialogueFragment speaker ID—are stored strictly as raw 64-bit primitive long values.6

### **The Secondary Resolution Pass**

Following the complete streaming deserialization of the POJOs, the runtime executes a secondary resolution pass. During this phase, the system iterates through the hierarchy.json instructions.16 It queries the ArticyDatabase for parent nodes and injects references to their respective child nodes, converting the flat database into a hierarchical tree.22

This two-pass architecture eliminates cyclic dependency issues during deserialization. Because all objects are pre-registered in the ConcurrentHashMap during the first pass, any forward-referencing connections or cyclic hierarchical loops are safely resolved during the second pass without encountering NullPointerException errors.

## **Object-Relational Mapping and The Central ArticyDatabase**

The ArticyDatabase functions as the neurological center of the runtime library. It abstracts the complexities of the underlying graph topology, providing a unified, thread-safe registry capable of fielding high-velocity lookup queries from the traversal engine.23

### **The 64-Bit Hexadecimal Identification Space**

Within the articy:draft 3 Generic Engine Export, every distinct element—nodes, pins, entities, templates, and assets—is assigned a globally unique identifier formatted as a 16-character hexadecimal string, prefixed by 0x (e.g., "0x000010003B").6

While it is programmatically possible to maintain a registry using String keys, such an implementation introduces severe performance penalties. String comparisons involve iteration over character arrays, and the memory footprint of hundreds of thousands of String objects generates significant pressure on the JVM's Garbage Collector.

Therefore, the ArticyDatabase mandates the immediate parsing of these hex strings into primitive 64-bit long types during ingestion. By utilizing a ConcurrentHashMap\<Long, ArticyObject\>, the database guarantees maximum throughput.6 Lookups via primitive long keys utilize highly optimized intrinsic hashing functions, reducing the time complexity of pointer dereferencing during recursive graph traversal to an absolute absolute minimum.

### **POJO Node Classification and Graph Topology**

The fundamental building block of the narrative graph is the ArticyObject. Every parsed entity inherits from this abstract base class, which encapsulates the 64-bit ID, the technical name, the parent ID, and a list of generic child objects.22

For the purposes of flow execution, the runtime relies on a specific subclass branch known as FlowObject.12 Flow objects inherently possess connection interfaces defined as Pin objects. The topological structure adheres to strict rules: connections exclusively bridge an OutputPin on a source node to an InputPin on a target node.12

The behavior of a FlowObject during graph traversal necessitates classifying nodes into two distinct behavioral categories: Pausable Nodes and Transparent Nodes.13 The table below outlines the core flow object types, their pin configurations, and their traversal characteristics.

| Object Type | Classification | Pin Architecture | Traversal Behavior |
| :---- | :---- | :---- | :---- |
| DialogueFragment | Pausable | 1 Input Pin, 1 Output Pin | Halts traversal. Contains explicit narrative content (speaker ID, spoken text, menu text) intended for host UI rendering.25 |
| Dialogue | Transparent (Container) | Multiple Input/Output Pins | Acts as a structural boundary. Traversal enters the container, recursively resolving the inner nodes until it encounters a child DialogueFragment.27 |
| Hub | Transparent (Router) | Multiple Input/Output Pins | Routes execution across multiple paths. Often used as a focal point to branch conversations outward or merge multiple paths back together.12 |
| Condition | Transparent (Logical) | 1 Input Pin, 2 Output Pins | Evaluates an Expresso script. Routes flow to the "Green" pin if the evaluation yields true, and the "Red" pin if false.28 |
| Instruction | Transparent (Mutator) | 1 Input Pin, 1 Output Pin | Evaluates an Expresso assignment script (e.g., altering a global variable). Passes execution immediately to the target node.26 |
| Jump | Transparent (Portal) | 1 Input Pin, No Output Pins | Possesses an explicit target ID rather than a standard connection. Instantly teleports execution to the target node regardless of hierarchical boundaries.14 |

### **Decoupled Asset Metadata Handling**

Narrative projects frequently utilize external assets, such as character avatars, background images, or localized audio files.29 The generic export includes an optional asset bundle alongside the JSON data.4

Because the runtime library is strictly decoupled from any game engine, it fundamentally cannot load textures, allocate audio buffers, or interact with an engine-specific rendering pipeline. Instead, the ArticyDatabase implements an abstraction layer for asset metadata.

When a DialogueFragment references a speaker's avatar, it stores the 64-bit ID of the asset.25 The database resolves this ID into an AssetObject POJO. The AssetObject encapsulates pure metadata: the relative file path within the export directory, the file extension, the original filename, and optionally, localization identifiers.5

The host application retrieves this AssetObject and utilizes the metadata string to invoke its own engine-specific loading mechanism (e.g., utilizing Texture2D.load() in a custom framework based on the provided relative path). This isolation guarantees that the library remains a purely logical construct, untethered from the graphical complexities of the host environment.

## **Narrative State Management and The Variable Manager**

Interactive branching narratives derive their complexity from tracking user decisions, inventory states, and relationship metrics. Articy models this dynamic state via Global Variables, which are organized into distinct Variable Sets.21 The Java runtime manages this evolving state through the ArticyVariableManager.

### **Memory Layout and Type Enforcement**

The ArticyVariableManager maintains the state utilizing a nested map architecture: Map\<String, Map\<String, Object\>\>. The outer map is indexed by the Variable Set identifier (e.g., "GameState"), while the inner map corresponds to the specific variable names (e.g., "isAwake") and their associated values.20 This structural layout explicitly mirrors the VariableSet.Variable dot-notation syntax mandated by the articy:expresso scripting language.20

Articy enforces strict typing for its variables, limiting them to Booleans, Integers, and Strings.21 During the ingestion of global\_variables.json, the ArticyVariableManager populates the internal maps and records the declared type of each variable. This pre-computation is vital for type safety. Because the Expresso engine utilizes reflection to execute dynamic scripts, it is theoretically possible for a flawed script to attempt to assign a string to an integer variable. The ArticyVariableManager intercepts all mutation requests, validating the incoming object type against the recorded schema, thereby preventing catastrophic class cast exceptions from destabilizing the host application.

The structure of the supported variable types and their default behaviors is outlined below:

| Variable Type | Underlying Java Class | Expresso Usage Scenario | Default Value Handling |
| :---- | :---- | :---- | :---- |
| Boolean | java.lang.Boolean | Binary flags, quest completion status, binary logic gates.21 | Initializes to false unless explicitly defined in the export schema.21 |
| Integer | java.lang.Integer | Countable metrics, currency, reputation scores, loop counters.21 | Initializes to 0\. Safely handles standard arithmetic and modulo operations.21 |
| String | java.lang.String | Player-defined names, dynamic localization keys, passphrase tracking.21 | Initializes to "" (empty string). Evaluated using strictly immutable string pool references.21 |

### **The Shadow State Pattern for Branch Forecasting**

A defining characteristic of an advanced dialogue traversal engine is its capacity for branch forecasting. When the ArticyFlowPlayer arrives at a branching intersection (such as a Hub connected to multiple DialogueFragments), it must recursively look ahead down all potential paths to ascertain which branches are logically valid based on current condition scripts.1

However, Articy permits designers to embed instructions—scripts that explicitly mutate variables—directly onto output pins or within transparent instruction nodes.12 If the traversal engine evaluates three possible dialogue branches, and the first branch possesses an instruction Inventory.gold \-= 50, a naive evaluation sequence would immediately deduct 50 gold from the player simply by checking if the branch is accessible.21

To circumvent this fundamental logic flaw, the ArticyVariableManager implements the "Shadow State" architectural pattern.7 Prior to initiating a forecasting sequence, the traversal engine instructs the variable manager to generate an isolated clone of itself.

1. **Deep Cloning Mechanism**: The createShadowState() method performs a deep copy of the nested variable maps. Because the underlying values—String, Integer, and Boolean—are immutable objects in Java, the cloning process is highly efficient. The system simply creates new ConcurrentHashMap instances and copies the immutable references, avoiding the severe performance degradation associated with deep-cloning mutable objects.  
2. **Context Context Switching**: The ExpressoEngine temporarily reassigns its execution context pointer, directing all variable read/write requests to the shadow clone.  
3. **Side-Effect Isolation**: As the forecasting algorithm recursively crawls through Output Pins and Condition nodes, any assignment operations (e.g., GameState.bossDefeated \= true) exclusively mutate the Shadow State.8  
4. **State Discard and Garbage Collection**: Upon the conclusion of the forecasting phase, the valid branches are compiled and presented to the host application.1 The reference to the Shadow State is subsequently dropped. Because the cloned maps contain only references to immutable primitives, the JVM Garbage Collector reclaims the map structures highly efficiently, minimizing the risk of GC-induced stuttering in the host engine's main loop.  
5. **Execution Commitment**: When the user formally selects a branch to play, the traversal engine replays the path. This time, the engine relies on the real ArticyVariableManager, permanently committing the side effects to the canonical game state.

Furthermore, a specific isInShadowState() boolean flag is explicitly exposed via the API.7 This allows host-defined custom script functions (e.g., an UnlockAchievement() method) to silently abort execution if they detect they are being invoked during a forecasting phase, ensuring that side effects external to the variable manager are identically isolated.8

## **Expresso Engine Integration via Apache Commons JEXL**

Articy:expresso is the internal scripting language natively supported by the articy:draft 3 suite. It utilizes a C-style syntax to construct conditional evaluations and execute instructions.20 While it is feasible to implement a bespoke Abstract Syntax Tree (AST) parser to evaluate this syntax, doing so introduces massive maintenance overhead, parsing inefficiencies, and a high probability of edge-case bugs.

Consequently, this specification mandates the integration of Apache Commons JEXL (Java Expression Language), an immensely robust, open-source library specifically engineered to facilitate dynamic scripting capabilities within Java environments.11

### **JEXL 3.x Configuration and Strictness Enforcement**

To accurately emulate the behavior of Expresso, the underlying JEXL engine must be rigorously configured. By default, JEXL 3.x operates in a lenient mode, allowing null values to quietly propagate through mathematical evaluations or concatenations, substituting default values to prevent immediate failure.31 In the context of narrative game logic, an undeclared or null variable is almost universally a critical designer logic error that warrants immediate detection.

Therefore, the JexlEngine is instantiated using a strict JexlBuilder configuration:

Java

private static final JexlEngine jexl \= new JexlBuilder()  
   .cache(512)  
   .strict(true)  
   .silent(false)  
   .create();

*Configuration Rationale*: Activating strict(true) forces the engine to throw explicit exceptions upon encountering undefined variables, rejecting the ambiguous fallback mechanisms.31 Setting silent(false) ensures these exceptions are not swallowed by the engine, allowing them to bubble up to the host application's primary logging pipeline.31 Furthermore, JEXL variable resolution is intrinsically case-sensitive, natively mirroring Articy's strict case-sensitive variable handling without requiring additional regex preprocessing.33

### **Syntactic Translation and Operator Mapping**

JEXL was deliberately selected due to its inherent support for the vast majority of ECMAScript and C-style constructs utilized by Expresso.11 However, the translation bridge between the two formats requires precise mapping protocols to ensure absolute fidelity during evaluation.

Expresso relies heavily on arithmetic operators (+, \-, \*, /, %), assignment operators (=, \+=, \-=, \*=, /=, %=), incremental operators (++, \--), and logical operators (&&, ||, \!).18

The following table details the mapping and resolution strategies for Expresso operators within the JEXL evaluation context.

| Articy Expresso Operator | Apache Commons JEXL Mapping | Resolution Mechanics & Notes |
| :---- | :---- | :---- |
| && , || | && , || | Mapped natively without modification. JEXL 3.x evaluates logical AND/OR operations utilizing standard short-circuiting behavior.33 |
| \== , \!= | \== , \!= | Mapped natively. Evaluates object equality for Strings and value equality for numeric and boolean primitives.10 |
| \+ , \- , \* , / , % | \+ , \- , \* , / , % | Mapped natively. Enforced strictly by the JexlBuilder; attempts to perform arithmetic on null values will yield a JexlException.18 |
| \= | \= | Assignment mapped natively. Modifies the variable within the active JexlContext, successfully routing the mutation to either the primary or shadow ArticyVariableManager.18 |
| \+= , \-= , \*= , /= , %= | \+= , \-= , \*= , /= , %= | Supported inherently by JEXL 3.x assignment logic. The operation resolves the current value, computes the arithmetic mutation, and overwrites the existing entry in the variable map.30 |
| \++ , \-- (Pre/Post) | *Pre-processing required* | While standard in Expresso 18, pre/post incrementation is historically problematic or unsupported in older JEXL variants. The runtime may perform regex preprocessing (e.g., transforming var++ into var \+= 1\) prior to evaluation to guarantee absolute compatibility. |

### **JexlPermissions and Execution Security**

A critical consideration when implementing dynamic scripting languages within a host application is the risk of arbitrary code execution. Starting with version 3.3, Apache Commons JEXL significantly overhauled its security model, introducing the JexlPermissions system.34 By default, modern JEXL severely restricts access to reflection, class loaders, and file I/O operations, ensuring that malicious or malformed scripts cannot compromise the JVM.34

The runtime must instantiate a custom JexlPermissions object that tightly restricts introspection. The permissions are configured to explicitly whitelist *only* the ArticyVariableManager interface and any host-defined custom script provider namespaces. This prevents embedded scripts from utilizing reflection to break out of the narrative context and manipulate the host application's memory directly.

### **Dynamic Mapping of script\_methods.json**

Beyond basic variable assignments and condition checks, Expresso empowers designers to invoke custom, project-specific functions.18 These functions allow the narrative to trigger external events within the game engine, such as altering camera angles, granting items, or initiating combat sequences.18 The Generic Engine Export explicitly catalogs every custom function utilized within the project inside the script\_methods.json file.19 This file defines the exact string signature, the expected parameter types, and the return value type of each method.

To dynamically resolve these functions at runtime, the library exposes an IScriptMethodProvider interface, a pattern proven highly effective in the official Unity and Unreal implementations.9 The host application implements this interface to bind its native Java game logic to the Expresso scripts.

For JEXL to recognize and execute these custom functions, the runtime leverages JEXL's namespace functor architecture.36 The initialization sequence follows a precise choreography:

1. **Parsing Phase**: The runtime parses script\_methods.json to extract the catalog of valid custom function signatures.19  
2. **Verification Phase**: Utilizing Java Reflection, the runtime inspects the host application's IScriptMethodProvider implementation to verify that it possesses methods matching the documented signatures.  
3. **Namespace Registration**: The provider instance is dynamically registered into the JexlEngine using a Map\<String, Object\> applied via the setFunctions(functionMap) method. The functions are mapped to the global namespace (frequently utilizing an empty string "" or an "articy" prefix).33

When the traversal engine evaluates an Expresso script containing PlaySound("Tada"), JEXL seamlessly delegates the invocation to the corresponding playSound(String id) method on the host's provider instance.9 As discussed in the State Management section, the host implementation can consult the isShadowState() boolean to intelligently suppress the audio playback, guaranteeing that side effects are rigorously suppressed during the branch forecasting process.8

### **Built-in Expresso Function Resolution**

Articy Expresso incorporates several built-in utility functions natively, including random(min, max), isInRange(val, min, max), getSeenCounter(obj), and setSeenCounter(obj, val).18 To seamlessly support these operations, the ExpressoEngine injects a specialized internal utility class into the default JEXL namespace, specifically designed to capture and resolve these native calls.

The seenCounter mechanic warrants explicit architectural detailing. The ArticyDatabase maintains an independent, highly concurrent ConcurrentHashMap\<Long, Integer\> expressly for tracking node traversal visits.18 Every time the ArticyFlowPlayer formally enters and processes a node during a committed traversal, the visit count corresponding to that node's 64-bit ID increments.

When the JEXL context intercepts an Expresso call to getSeenCounter(self), the evaluation engine dynamically swaps the contextual keyword self for the 64-bit ID of the node currently undergoing evaluation. It queries the tracking map and returns the correct integer, allowing designers to easily author dialogue branches that organically alter their availability based on previous player interactions.18

## **ArticyFlowPlayer and Recursive Directed Graph Traversal**

The ArticyFlowPlayer operates as the primary state machine responsible for navigating the highly intricate directed graph of the narrative flow. Graph execution within Articy is rarely a straightforward linear progression; rather, it maneuvers through a web of interconnected structural elements that dictate the logical flow without explicitly presenting visual content to the user.12

### **Delineation of Node Classifications**

Understanding the complexities of traversal necessitates firmly categorizing nodes based on their pausing and rendering behavior:

* **Pausable Nodes**: Nodes that encapsulate direct narrative or multimedia content intended for the host application to process, render, and display to the end-user. The most ubiquitous example is the DialogueFragment, which fundamentally represents a single spoken line, complete with a speaker ID, text payload, and optional menu options.25 When traversal reaches a pausable node, execution inherently halts.  
* **Transparent (Structural) Nodes**: Nodes that orchestrate flow redirection or execute logical operations, yet hold no distinct displayable content.12 These critical elements include Hubs (points of multi-directional branching) 27, Jumps (mechanisms for instantaneous teleportation to disconnected nodes) 14, Condition Nodes (if/else branching operators based on state evaluation) 28, and Instruction Nodes (pure variable mutation vectors).26

The fundamental objective of the Flow Player is to recursively advance through consecutive transparent nodes, resolving logic and tracking potential paths, until it invariably strikes a pausable node, or until the flow splinters into multiple valid, user-facing options.13

### **The Traversal and Branch Forecasting Algorithm**

When the host application issues the command flowPlayer.play() or flowPlayer.advance(branch) 13, the traversal engine engages its recursive search algorithms.

Because interactive narrative execution must anticipate the flow to present a curated list of valid upcoming choices to the user, the execution architecture heavily relies on a Depth-First Search (DFS) forecasting algorithm enveloped entirely within the protective boundary of the Shadow State.8

**Recursive Traversal Pseudo-code Strategy:**

FUNCTION getBranchesOfNode(currentNode):

List validBranches \= EMPTY\_LIST

// Step 1: Initialize isolated evaluation state to protect global variables  
ArticyVariableManager shadowVars \= globalVars.createShadowState()

// Step 2: Begin DFS search radiating from the current node's output pins  
FOR EACH outputPin IN currentNode.outputPins:  
    evaluateForecasting(outputPin, shadowVars, validBranches, EMPTY\_PATH, EMPTY\_VISITED\_SET)  
      
RETURN validBranches

FUNCTION evaluateForecasting(currentPin, shadowVars, validBranches, currentPath, visitedNodes):

// Execute output pin instructions; mutation occurs ONLY within the shadow state

IF currentPin HAS script:

engine.executeInstruction(currentPin.script, shadowVars)

// Traverse physical connections bridging the output pin to target input pins  
FOR EACH connection IN currentPin.connections:  
    InputPin targetPin \= database.getPin(connection.targetPinId)  
      
    // Evaluate input pin condition; if false, this specific pathway is closed  
    IF targetPin HAS script:  
        boolean isValid \= engine.evaluateCondition(targetPin.script, shadowVars)  
        IF NOT isValid:  
            CONTINUE   
              
    // Proceed into the targeted node  
    Node targetNode \= database.getNode(connection.targetNodeId)  
      
    // Cycle detection: Halt path if transparent node was already visited in this route  
    IF visitedNodes.CONTAINS(targetNode.id) AND targetNode IS Transparent:  
        CONTINUE   
          
    visitedNodes.ADD(targetNode.id)  
      
    // Categorical Routing  
    IF targetNode IS Pausable (e.g., DialogueFragment):  
        // A valid stopping point is discovered; instantiate a Branch and append  
        Branch newBranch \= CREATE Branch(targetNode, currentPath)  
        validBranches.ADD(newBranch)  
          
    ELSE IF targetNode IS Transparent:  
        // Jumps bypass standard connections, redirecting instantly to a target ID  
        IF targetNode IS Jump:  
            targetNode \= database.getNode(targetNode.targetId)  
              
        // Condition Nodes feature distinct binary outputs based on evaluation  
        IF targetNode IS ConditionNode:  
            boolean conditionResult \= engine.evaluateCondition(targetNode.script, shadowVars)  
            OutputPin nextPin \= conditionResult? targetNode.greenPin : targetNode.redPin  
            evaluateForecasting(nextPin, shadowVars, validBranches, currentPath \+ targetNode, visitedNodes)  
              
        ELSE:  
            // Hubs and Instructions inherently explore all their output pins  
            FOR EACH outPin IN targetNode.outputPins:  
                evaluateForecasting(outPin, shadowVars, validBranches, currentPath \+ targetNode, visitedNodes)

### **Addressing Graph Cycles and Infinite Loop Vectors**

A severe computational hazard inherent in the recursive traversal of transparent nodes is the potential for infinite cyclical loops. Designers consistently utilize Hub nodes to redirect flow backward upon itself, forging cyclical dialogue menus or retry loops.42 If the forecasting algorithm traverses a Hub that routes unequivocally back into itself without intersecting a pausable node or encountering a blocking script condition, the DFS will infinitely recurse, ultimately triggering a fatal StackOverflowError and crashing the application thread.

The recursive engine systematically addresses this specific vector by tracking a Set\<Long\> of visited transparent node IDs unique to the currently executing path. If evaluateForecasting encounters a node ID already registered within the visited set for that precise recursive branch, it instantly aborts the search along that path, isolating and neutralizing the infinite loop without impacting parallel searches.12

## **The Execution Lifecycle and Host Callbacks**

Once the getBranchesOfNode sequence comprehensively compiles the list of valid Branch objects, the ArticyFlowPlayer gracefully exits and discards the Shadow State.1 If the list contains precisely one valid branch and the player is configured for automatic traversal, it may autonomously commit the variables and seamlessly advance.13 Conversely, when distinct choices emerge or a hard pause is mandated, the player suspends internal operations and delegates control back to the host application via the strongly-typed IArticyFlowPlayerCallbacks interface.43

The callback lifecycle operates explicitly and sequentially:

1. **onFlowPlayerPaused(FlowObject object)**: Triggered when the traversal algorithms formally steps onto a pausable node (e.g., a DialogueFragment). The host UI interprets this signal to extract data and render the speaker's avatar, name, and primary dialogue text to the player.43  
2. **onBranchesUpdated(List\<Branch\> branches)**: Triggered immediately subsequent to the pause event. This callback provides the host UI with the pre-calculated, verified list of valid next steps generated by the shadow-state forecasting. The UI utilizes this array to instantiate clickable player dialogue options or navigation buttons.13

Upon receiving affirmative user input (e.g., a player clicking a dialogue option), the application invokes flowPlayer.advance(selectedBranch). At this precise juncture, the traversal engine replays the sequential path enclosed within the selectedBranch against the *canonical* ArticyVariableManager. Conditional checks are bypassed (having been securely verified during forecasting), but instructions residing on output pins and transparent nodes traversing the path are actively executed, definitively mutating the narrative state before arriving at the subsequent DialogueFragment.12

## **Public API Specification and Integration Contracts**

To ensure reliable, error-free integration across highly varied Java frameworks, the library strictly governs access to its internal graph mechanisms. The host application interacts with the architecture through distinct, tightly scoped facade classes and callback interfaces. The architecture explicitly prevents the leakage of underlying Jackson/Gson tree structures or raw Jexl instances to the consumer, promoting a clean, strongly typed operational contract.

### **1\. Initialization and Configuration**

The primary entry point necessitates the initialization of the database system. The host application passes the absolute or relative directory path containing the generic export JSON payload, alongside the requisite provider for custom scripts.

Java

public class ArticyRuntime {  
    /\*\*  
     \* Initializes the ArticyDatabase, executes the dual-pass deserialization,   
     \* and prepares the Expresso JEXL Engine.  
     \*   
     \* @param exportDirectory Path string pointing to the unzipped Generic Engine Export directory.  
     \* @param methodProvider Host implementation detailing custom script method resolution.  
     \* @return The fully configured and populated database singleton.  
     \* @throws ArticyLoadException if the JSON payload is malformed or inaccessible.  
     \*/  
    public static ArticyDatabase initialize(String exportDirectory, IScriptMethodProvider methodProvider) throws ArticyLoadException {  
        // Core implementation dynamically parses manifest, hierarchy, packages, and scripts.  
    }  
}

### **2\. The Custom Method Provider Interface**

Applications must provide a concrete implementation handling the custom methods cataloged in script\_methods.json. This serves as the critical logic bridge between Expresso and the host Java environment.9

Java

public interface IScriptMethodProvider {  
    /\*\*  
     \* Synchronously invoked when the Expresso evaluation engine attempts to execute a method   
     \* not native to the standard Articy specification.  
     \*   
     \* @param methodName The literal string name of the method invoked (e.g., "GrantItem").  
     \* @param args The dynamic arguments passed from the Expresso script payload.  
     \* @return The resulting object to return to JEXL (can return null for void operations).  
     \*/  
    Object invokeCustomMethod(String methodName, Object... args);  
      
    /\*\*  
     \* Optional utility allowing the provider implementation to manually verify if the   
     \* traversal engine is currently engaged in shadow-state forecasting.   
     \* Essential for suppressing side-effects like UI alterations, audio playback, or achievements.  
     \*   
     \* @return true if currently predicting future branches.  
     \*/  
    default boolean isShadowState() {  
        return ArticyRuntime.getVariableManager().isInShadowState();  
    }  
}

### **3\. Database Queries and Object Resolution**

The central database provides intuitive mechanisms for querying objects via their generic identifiers or technical names. The returned ArticyObject instances expose strongly typed getters for extracting template properties, guaranteeing safe access without manual class casting.

Java

public interface ArticyDatabase {  
    /\*\*   
     \* Retrieves any instantiated object via its explicit 64-bit Hexadecimal ID.   
     \*   
     \* @param hexId The primitive representation of the unique object ID.  
     \* @param expectedType The class type the host expects to receive (e.g., DialogueFragment.class)  
     \* @return The correctly cast object, or null if the ID is missing or type mismatches.  
     \*/  
    \<T extends ArticyObject\> T getObject(long hexId, Class\<T\> expectedType);  
      
    /\*\* Retrieves an object utilizing its human-readable technical name. \*/  
    \<T extends ArticyObject\> T getObject(String technicalName, Class\<T\> expectedType);  
      
    /\*\* Returns the centralized variable manager for explicit manual mutation or UI inspection. \*/  
    ArticyVariableManager getGlobalVariables();  
}

### **4\. Flow Player Execution API**

The ArticyFlowPlayer is specifically designed to be instantiated per discrete traversal context. This architectural choice permits host applications to execute multiple simultaneous narrative flows seamlessly (for example, allowing ambient background NPC chatter to evaluate dynamically while the player is actively engaged in a main quest dialogue).

Java

public class ArticyFlowPlayer {  
      
    /\*\*  
     \* Instantiates a new independent traversal machine.  
     \* @param db The centralized database containing the graph.  
     \* @param callbacks The listener responding to pause and branch events.  
     \*/  
    public ArticyFlowPlayer(ArticyDatabase db, IArticyFlowPlayerCallbacks callbacks);

    /\*\*   
     \* Defines the specific node classifications that force the player to halt   
     \* and yield control back to the application.   
     \*/  
    public void setPauseOn(Set\<Class\<? extends FlowObject\>\> pauseOnTypes);

    /\*\* Commences directed graph traversal utilizing a specific entity or node as the origin. \*/  
    public void startOn(ArticyObject startingPoint);

    /\*\*   
     \* Formally commits a forecasted path to the primary variable state and   
     \* continues traversal down the selected logical branch.   
     \*/  
    public void advance(Branch branch);  
      
    /\*\*   
     \* Forces the immediate evaluation of any instruction scripts affixed to the   
     \* currently halted object's output pins.   
     \*/  
    public void finishCurrentPausedObject();  
}

And its associated callback listener:

Java

public interface IArticyFlowPlayerCallbacks {  
    /\*\*   
     \* Executed when traversal has discovered and locked onto a valid pause target.  
     \*   
     \* @param currentObject The node on which traversal has halted (frequently a DialogueFragment).  
     \*/  
    void onFlowPlayerPaused(FlowObject currentObject);  
      
    /\*\*  
     \* Executed post-pause or upon hitting a dead end, returning all mathematically   
     \* valid routes calculated by the shadow state forecasting.  
     \*   
     \* @param availableBranches List of options mathematically proven to be open.  
     \*/  
    void onBranchesUpdated(List\<Branch\> availableBranches);  
}

## **Performance Optimization and Threading Model**

Because this runtime library operates without the intrinsic garbage collection and memory pooling facilities often provided by heavyweight C++ game engines, explicit attention has been paid to the Java threading model and memory profile.

### **Concurrency and Thread Safety**

The ArticyDatabase and ArticyVariableManager are designed to be entirely thread-safe, acknowledging that modern Java host frameworks frequently separate render threads from logic threads. The use of ConcurrentHashMap across the architecture ensures that multiple simultaneous ArticyFlowPlayer instances can query the graph and read variables concurrently without triggering ConcurrentModificationException failures. While reading is entirely safe across threads, variable mutation via advance() or custom method invocation is localized to ensure state coherency.

### **Garbage Collection Considerations**

The Shadow State mechanism inherently produces temporary map objects during deep cloning. In an intensely branching narrative, a single user choice could trigger dozens of shadow clones. However, by strictly enforcing immutability on the underlying variable values (String, Integer, Boolean) and exclusively cloning references to those objects rather than creating newly allocated wrapper objects, the GC pressure is constrained to the top-level HashMap arrays. These transient maps rarely survive past the young generation of the JVM garbage collector, rendering the process highly performant and virtually imperceptible in latency-sensitive game loops.

The stringency of the architecture, combined with the power of Apache Commons JEXL and the highly localized forecasting model, ensures this specification operates as a completely self-contained, impeccably optimized runtime for articy:draft 3 structures across any Java environment.

#### **Lucrări citate**

1. ArticySoftware/Articy3ImporterForUnreal: Articy Importer plugin for the Unreal Engine 4 and ... \- GitHub, accesată pe aprilie 30, 2026, [https://github.com/ArticySoftware/Articy3ImporterForUnreal](https://github.com/ArticySoftware/Articy3ImporterForUnreal)  
2. Technical Exports \- Articy, accesată pe aprilie 30, 2026, [https://www.articy.com/en/articydraft/integration/techexports/](https://www.articy.com/en/articydraft/integration/techexports/)  
3. Basic Object handling \- Articy, accesată pe aprilie 30, 2026, [https://www.articy.com/articy-importer/unity/html/howto\_objects.htm](https://www.articy.com/articy-importer/unity/html/howto_objects.htm)  
4. Export to Generic Engine \- Articy Help Center, accesată pe aprilie 30, 2026, [https://www.articy.com/help/adx/Exports\_GenericEngine.html](https://www.articy.com/help/adx/Exports_GenericEngine.html)  
5. Database | articy-js, accesată pe aprilie 30, 2026, [https://scenarioworld.github.io/articy-js/classes/Database.html](https://scenarioworld.github.io/articy-js/classes/Database.html)  
6. ArticyObject | articy-js, accesată pe aprilie 30, 2026, [https://scenarioworld.github.io/articy-js/classes/ArticyObject.html](https://scenarioworld.github.io/articy-js/classes/ArticyObject.html)  
7. BaseGlobalVariables.IsInShadowState Property \- Articy, accesată pe aprilie 30, 2026, [https://www.articy.com/articy-importer/unity/html/P\_Articy\_Unity\_BaseGlobalVariables\_IsInShadowState.htm](https://www.articy.com/articy-importer/unity/html/P_Articy_Unity_BaseGlobalVariables_IsInShadowState.htm)  
8. ArticySoftware/ArticyXImporterForUnreal: articy:draft X importer plugin for the Unreal Engine 5 \- GitHub, accesată pe aprilie 30, 2026, [https://github.com/ArticySoftware/ArticyXImporterForUnreal](https://github.com/ArticySoftware/ArticyXImporterForUnreal)  
9. Can I call custom functions in articy? : r/articydraft \- Reddit, accesată pe aprilie 30, 2026, [https://www.reddit.com/r/articydraft/comments/uomoa1/can\_i\_call\_custom\_functions\_in\_articy/](https://www.reddit.com/r/articydraft/comments/uomoa1/can_i_call_custom_functions_in_articy/)  
10. Scripting and how to use it \- Articy, accesată pe aprilie 30, 2026, [https://www.articy.com/articy-importer/unity/html/howto\_script.htm](https://www.articy.com/articy-importer/unity/html/howto_script.htm)  
11. Java Expression Language (JEXL) \- Apache Commons, accesată pe aprilie 30, 2026, [https://commons.apache.org/jexl/](https://commons.apache.org/jexl/)  
12. Dialogues and Flow Traversal \- Articy, accesată pe aprilie 30, 2026, [https://www.articy.com/articy-importer/unity/html/howto\_flowplayer.htm](https://www.articy.com/articy-importer/unity/html/howto_flowplayer.htm)  
13. ArticyFlowPlayer Class, accesată pe aprilie 30, 2026, [https://www.articy.com/articy-importer/unity/html/T\_Articy\_Unity\_ArticyFlowPlayer.htm](https://www.articy.com/articy-importer/unity/html/T_Articy_Unity_ArticyFlowPlayer.htm)  
14. Jumps \- Articy Help Center, accesată pe aprilie 30, 2026, [https://www.articy.com/help/Flow\_Objects\_Jump.html](https://www.articy.com/help/Flow_Objects_Jump.html)  
15. Export to .json \- Articy Help Center, accesată pe aprilie 30, 2026, [https://www.articy.com/help/Exports\_JSON.html](https://www.articy.com/help/Exports_JSON.html)  
16. Export to .json \- Articy Help Center, accesată pe aprilie 30, 2026, [https://www.articy.com/help/legacy/Exports\_JSON.html](https://www.articy.com/help/legacy/Exports_JSON.html)  
17. Packaging / Deploying your plugin \- Articy, accesată pe aprilie 30, 2026, [https://www.articy.com/ad3mdk/html/packagingaplugin.htm](https://www.articy.com/ad3mdk/html/packagingaplugin.htm)  
18. Scripting in articy:draft \- Articy Help Center, accesată pe aprilie 30, 2026, [https://www.articy.com/help/Scripting\_in\_articy.html](https://www.articy.com/help/Scripting_in_articy.html)  
19. 3.1 (Improvement Version) \- Articy Help Center, accesată pe aprilie 30, 2026, [https://www.articy.com/help/ad3/Changes\_3\_1.html](https://www.articy.com/help/ad3/Changes_3_1.html)  
20. Scripting in articy:draft \- Articy Help Center, accesată pe aprilie 30, 2026, [https://www.articy.com/help/ad3/Scripting\_in\_articy.html](https://www.articy.com/help/ad3/Scripting_in_articy.html)  
21. articy:draft X Basics Scripting, accesată pe aprilie 30, 2026, [https://www.articy.com/en/adx\_basics\_scripting/](https://www.articy.com/en/adx_basics_scripting/)  
22. ArticyHierarchyNode Class, accesată pe aprilie 30, 2026, [https://www.articy.com/articy-importer/unity/html/T\_Articy\_Unity\_ArticyHierarchyNode.htm](https://www.articy.com/articy-importer/unity/html/T_Articy_Unity_ArticyHierarchyNode.htm)  
23. ArticyDatabase Class, accesată pe aprilie 30, 2026, [https://www.articy.com/articy-importer/unity/html/T\_Articy\_Unity\_ArticyDatabase.htm](https://www.articy.com/articy-importer/unity/html/T_Articy_Unity_ArticyDatabase.htm)  
24. Pins and connections \- Articy Help Center, accesată pe aprilie 30, 2026, [https://www.articy.com/help/Flow\_PinsConnections.html](https://www.articy.com/help/Flow_PinsConnections.html)  
25. Dialogue fragments \- Articy Help Center, accesată pe aprilie 30, 2026, [https://www.articy.com/help/ad3/Flow\_Objects\_DialogFragment.html](https://www.articy.com/help/ad3/Flow_Objects_DialogFragment.html)  
26. articy:draft X Basics Flow II, accesată pe aprilie 30, 2026, [https://www.articy.com/en/adx\_basics\_flow2/](https://www.articy.com/en/adx_basics_flow2/)  
27. Dialogues \- Articy Help Center, accesată pe aprilie 30, 2026, [https://www.articy.com/help/Flow\_Dialog.html](https://www.articy.com/help/Flow_Dialog.html)  
28. Conditions & Instructions \- Articy Help Center, accesată pe aprilie 30, 2026, [https://www.articy.com/help/Scripting\_Conditions\_Instructions.html](https://www.articy.com/help/Scripting_Conditions_Instructions.html)  
29. Features List \- Articy, accesată pe aprilie 30, 2026, [https://www.articy.com/en/downloads/features-list-ad3/](https://www.articy.com/en/downloads/features-list-ad3/)  
30. Scripting in articy:draft, accesată pe aprilie 30, 2026, [https://www.articy.com/help/legacy/Scripting\_in\_articy.html](https://www.articy.com/help/legacy/Scripting_in_articy.html)  
31. JexlEngine (Commons JEXL 2.1.1 API) \- Apache Commons, accesată pe aprilie 30, 2026, [https://commons.apache.org/proper/commons-jexl/javadocs/apidocs-2.1.1/org/apache/commons/jexl2/JexlEngine.html](https://commons.apache.org/proper/commons-jexl/javadocs/apidocs-2.1.1/org/apache/commons/jexl2/JexlEngine.html)  
32. Apache Commons JEXL Examples ? Apache Commons JEXL \- Apache Software Foundation, accesată pe aprilie 30, 2026, [https://commons.apache.org/proper/commons-jexl/reference/examples.html](https://commons.apache.org/proper/commons-jexl/reference/examples.html)  
33. Apache Commons JEXL Syntax ? Apache Commons JEXL \- Apache Software Foundation, accesată pe aprilie 30, 2026, [https://commons.apache.org/proper/commons-jexl/reference/syntax.html](https://commons.apache.org/proper/commons-jexl/reference/syntax.html)  
34. Apache Commons JEXL 3.3 Release Notes ? Apache Commons JEXL \- Apache Software Foundation, accesată pe aprilie 30, 2026, [https://commons.apache.org/jexl/relnotes33.html](https://commons.apache.org/jexl/relnotes33.html)  
35. Apache Commons JEXL 3.6.1 Release Notes ? Apache Commons JEXL \- Apache Software Foundation, accesată pe aprilie 30, 2026, [https://commons.apache.org/proper/commons-jexl/relnotes35.html](https://commons.apache.org/proper/commons-jexl/relnotes35.html)  
36. commons-jexl/src/main/java/org/apache/commons/jexl3/JexlContext.java at master \- GitHub, accesată pe aprilie 30, 2026, [https://github.com/apache/commons-jexl/blob/master/src/main/java/org/apache/commons/jexl3/JexlContext.java](https://github.com/apache/commons-jexl/blob/master/src/main/java/org/apache/commons/jexl3/JexlContext.java)  
37. Expression Language \- Jxls, accesată pe aprilie 30, 2026, [https://jxls.sourceforge.net/reference/expression\_language.html](https://jxls.sourceforge.net/reference/expression_language.html)  
38. articy:draft X Basics Flow I, accesată pe aprilie 30, 2026, [https://www.articy.com/en/adx\_basics\_flow1/](https://www.articy.com/en/adx_basics_flow1/)  
39. Flow \- What's this? \- Articy Help Center, accesată pe aprilie 30, 2026, [https://www.articy.com/help/Flow\_WhatsThis.html](https://www.articy.com/help/Flow_WhatsThis.html)  
40. ArticyFlowPlayer Methods, accesată pe aprilie 30, 2026, [https://www.articy.com/articy-importer/unity/html/Methods\_T\_Articy\_Unity\_ArticyFlowPlayer.htm](https://www.articy.com/articy-importer/unity/html/Methods_T_Articy_Unity_ArticyFlowPlayer.htm)  
41. articy:draft Importer for Unreal – Tutorial Lesson 2, accesată pe aprilie 30, 2026, [https://www.articy.com/en/importer-for-unreal-tutorial-l2/](https://www.articy.com/en/importer-for-unreal-tutorial-l2/)  
42. Feature and Template Scripting inside Dialog Fragments : r/articydraft \- Reddit, accesată pe aprilie 30, 2026, [https://www.reddit.com/r/articydraft/comments/12oxluj/feature\_and\_template\_scripting\_inside\_dialog/](https://www.reddit.com/r/articydraft/comments/12oxluj/feature_and_template_scripting_inside_dialog/)  
43. IArticyFlowPlayerCallbacks Interface, accesată pe aprilie 30, 2026, [https://www.articy.com/articy-importer/unity/ad3/html/T\_Articy\_Unity\_IArticyFlowPlayerCallbacks.htm](https://www.articy.com/articy-importer/unity/ad3/html/T_Articy_Unity_IArticyFlowPlayerCallbacks.htm)  
44. IArticyFlowPlayerCallbacks.OnFlowPlayerPaused Method, accesată pe aprilie 30, 2026, [https://www.articy.com/articy-importer/unity/html/M\_Articy\_Unity\_IArticyFlowPlayerCallbacks\_OnFlowPlayerPaused.htm](https://www.articy.com/articy-importer/unity/html/M_Articy_Unity_IArticyFlowPlayerCallbacks_OnFlowPlayerPaused.htm)

[image1]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACgAAAAYCAYAAACIhL/AAAAB00lEQVR4Xu2WzStFURTFN5LyEaUMJANFQobKAAMJhZigMPA3kFDyNTMQI4YUkRn/AVMGMjCRjIiRCCPF3vZ5OZb97j09Txn41ep119pnn9Otd/ch+ufv0YRGAC1ohNLPOmOdsxYgs2hgzaEZwCyrDc0ollkPrHzPq2O9sRo9zyeb9Yymxzbp+mQ80tf9ktJM2qgMA+aK9BClGDATrDU0mUnWMWnPqAOusqbQRF5ImxRg4MghzV/B73O+RZH7jTugIPkMmglWSAu2MACsjSwP+VFNFmlwhIGB1USe98BDrHWIvByzZpE0KMEAqKDvGxW75xHPs8B1FoOkNYUYnLggjlH6vlG1e+72PAtcZ9FBWiMv4gshiwX5B0vdgeclmtZ6nkXIHlWkNZ0YhCzOJK3ZBV8+sOLXgI+E7FFJWtODwYULorgmu6ac1O/CAAg5YDtpTT0Gmy5IRuLtrWPA5JFmwxgAIQccIK2xBsFHcEg6snz2XTYNvo/kO2gCIQfcoJiaS9KCU/ocT72sDL/IIGrzJ9Yd68bplnTOW0T1+RHzlJ7G0mMIzXQhM1TGZaosUdiVLmXkgiFXplS5Z+WimW5kmoyjGcAYqxXN3yLue2gRNyb/SZl3c+KEZSYJFsoAAAAASUVORK5CYII=>