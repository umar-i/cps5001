Introduction 
You have been commissioned to design and develop a Predictive Emergency Response Dispatch System (PERDS) for the National Emergency Coordination Agency (NECA). The system must allocate and manage emergency response units efficiently across a network of locations, ensuring that incidents receive rapid attention based on severity, proximity, and resource availability.  Your solution should be capable of real-time decision-making, dynamic updates to  incident and unit data, and predictive analysis of potential high-demand regions. You must determine and justify the appropriate data structures and algorithms required to achieve these objectives. The implementation must be completed in Java, accompanied by a detailed report explaining and evaluating your design decisions

Requirements 
You are required to design and implement a Java-based system that simulates a national emergency response network. The solution structures and algorithms to achieve optimal performance, adaptability, and scalability.  
Your work will be assessed in accordance with the criteria specified in this brief (see the Assessment Criteria section). 

The following provides a guideline of the tasks to be completed to achieve each grade band: 
1) Emergency Network Representation 
You must model the emergency network as an interconnected structure representing cities, dispatch centers, and incidents. Each connection should include weighted factors such as distance, travel time, and resource availability. 
Your system should support dynamic updates, including: 
• Adding or removing nodes (dispatch centers or incident sites); 
• Updating edge weights to reflect real-time changes (e.g., road closures or traffic congestion). 
You are expected to select and justify an appropriate data structure for this representation, explaining its efficiency and scalability. 
2) Response Unit Allocation and Route Optimization 
Implement an algorithmic process for dispatching the nearest and most appropriate response unit to each reported incident. 
Your design should: 
• Identify optimal routes for each dispatch using suitable pathfinding algorithms. 
• Incorporate dynamic reassignment of units when incidents are completed or priorities change. 
• Ensure that allocation decisions are based on multiple criteria, such as severity, distance, and resource type.
You must justify your chosen algorithms and discuss their complexity and suitability for real-time scenarios. 
3) Predictive Analysis and Resource Pre-Positioning 
The system should include functionality to forecast high-demand areas based on historical or simulated data and pre-position response units accordingly. You must implement and justify the chosen approach, explaining how predictive insights influence system efficiency. 
4) System Adaptability and Dynamic Updates 
The system must demonstrate adaptability to real-time updates: 
• New incidents appearing dynamically; 
• Units becoming unavailable or reallocated; 
• Routes changing due to environmental conditions. 
Your design should ensure efficient handling of these updates without full system re-computation. 

Tasks 
You are required to develop a software system, using object-oriented programming and Java, that fulfils the requirements outlined above. Your work will be assessed in accordance with the criteria specified in this brief (see the Assessment Criteria section). 
The following provides a guideline of the tasks to be completed to achieve each grade band: 
Third Class (40 – 49): Basic Software Artefact Criteria 
• Implements a basic city network representation and can register simple incidents. 
• Uses appropriate data structures (e.g., basic graph). 
• Implements a simple allocation algorithm (e.g., nearest-available unit). 
• Use object-oriented design.

Lower Second Class (2:2) (50 – 59): Reasonable 
Software Artefact Criteria 
• Includes a dynamic graph-based city model, concurrent incident management, and basic reallocation. 
• Partial implementation of predictive or optimization elements.

Upper Second Class (2:1) (60 – 69): Comprehensive 
Software Artefact Criteria 
• Fully functional dispatch system with real-time updates and efficient 
allocation algorithm. 
• Incorporates initial predictive logic and resource management.

Frist Class (1st) (70 – 79): Advanced 
Software Artefact Criteria 
• Implements predictive modelling with proactive resource placement. 
• Adapts dynamically to environmental changes (e.g., congestion, incident spikes). 
• Demonstrates well-designed architecture with efficient structures (e.g., heaps, hash maps, priority queues). 

First Class (80–100): Exceptional 
Software Artefact Criteria 
• Implements dynamic predictive algorithms that learn or adapt from historical incident data.
• Demonstrates emergent adaptive behavior and highly efficient dispatch optimization under simulated real-time load. 
• Includes statistical evaluation and visualization of results.

Prohibited Approaches 
• External algorithmic frameworks (e.g., Guava Graph libraries) 
• AI/ML libraries (e.g., TensorFlow, WEKA) 
• Code generation tools without justification 
No additional external libraries or frameworks should be used without the written permission of the module convenor. This restriction ensures that the focus remains on your understanding and application of core Java concepts, data structures, and algorithms.

Encouragement 
This assessment is not just about building a working system, but about showing how you think, create, and reflect as a developer. You are encouraged to experiment with different approaches, take creative risks, and explain your design decisions clearly in your report. Marks will reward originality, thoughtful evaluation, and problem-solving, even where your final simulation may not achieve perfect 
outcomes.

Assessment Criteria 
 
Your assessment will be graded according to the rubric below. 
 
This rubric is cumulative. To achieve a higher-grade band, you must first meet the 
requirements of all lower bands. For example, you cannot achieve a 2:1 (60–69) 
without first satisfying the criteria in the 2:2 (50–59) and Pass (40–49) bands. Higher 
bands represent a progression of achievement, with each level building on the 
previous one. 
 
 
Grading criteria 
 
Fully functional system addressing all requirements. 
Highly efficient, dynamic predictive system using optimized structures and adaptive algorithms. 
Demonstrates excellent use of efficient algorithms and advanced data structures.