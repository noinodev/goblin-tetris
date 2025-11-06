# Goblin Tetris

![goblin tetris](goblin-tetris.png)

Asymmetric multiplayer Tetris where one player drops pieces while others play as goblins 
trying to survive. Features custom P2P networking with UDP hole-punching and hand-crafted 
puzzle levels.

## Game Modes

**Asymmetric Multiplayer (PvP):**
- Host plays Tetris, trying to crush opponents
- Other players control goblins trying to survive
- Goblins can build and destroy structures for defense
- Real-time text chat
- Custom P2P networking with UDP hole-punching for low latency
- No external networking libraries - built from scratch in Java

**Goblin Tower (Single-player puzzle mode):**
- Progress through hand-crafted challenge levels
- Each level is an intentionally "bad" Tetris board state
- Crush all goblins trapped within complex structures
- Goblins wander and fall dynamically
- Must strategically clear rows to create paths to unreachable enemies
- Camera pans horizontally through tower sections

**Classic Tetris:**
- Traditional gameplay with modern visual effects
- Real-time leaderboard integration
- AI opponents using genetic algorithms

## Technical Features

**Networking (Pure Java, no external libraries):**
- Custom UDP protocol with NAT traversal via STUN server
- P2P game state synchronization for real-time multiplayer
- Asymmetric game state replication (host authority model)
- Real-time text chat protocol
- ByteBuffer serialization for game objects

**AI System:**
- Genetic algorithm-based Tetris AI for single-player
- Multithreaded heuristic evaluation
- Basic avoidant Goblin AI

**Rendering Engine:**
- Custom 2D sprite batching system (targets 240fps)
- Immediate-mode UI implementation
- Dynamic board illumination
- Screen shake and particle effects
- Smooth interpolation/tweening for all movement ('juice')

**Audio:**
- Custom stereo spatial audio system
- Dedicated high-frequency audio thread for smooth transitions

## Architecture

**Multithreaded Design:**
- Logic/Render thread: Main game loop and sprite batching
- AI Controller thread: Heuristic evaluation and move generation
- Network Listener thread: Non-blocking UDP socket handling
- Audio Manager thread: High-frequency audio control updates

## Tech Stack
- Java / Swing
- Custom UDP networking with STUN
- Custom 2D renderer with sprite batching
- Genetic algorithm AI
