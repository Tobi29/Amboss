# Amboss
Server management and extension infrastructure primarily targeted for Minecraft

Amboss has 4 different components which communicate with each other through
a custom network protocol, allowing multiple machines to participate in one
network. The protocol uses SSL as well as 4096-bit RSA key pairs for
authentication.

### Core
The Amboss Core is the central hub that all other clients connect to.

It hosts the server connection, hence it requires a working certificate and also
has to handle authentication.

The core allows clients to communicate with each other in various ways as well
as custom plugins to provide actual functionality and integration to other
services.

### Wrapper
The Wrapper allows an otherwise vanilla Minecraft server to connect to the core
and provides communication for logs, chat, commands, etc.

This wrapper is designed specifically for vanilla servers and will not function
properly on Spigot or Sponge!

### Kickstarter
The Kickstarter allows the starting and stopping of remote servers through the
core.

There are two versions of it:
  * A simple command-line version that can host only a single server
  * A systemD based version that can start and stop systemD user units

### Shell
The Shell allows simple command line access to the core in order to request keys
and otherwise manage servers.

This is currently very inconvenient to use, a better GUI is planned later down
the road.


## Build
The project uses Gradle to build all modules.


## Dependencies
For simply compiling and running the game only a working JDK 8 is required,
all other dependencies are automatically downloaded by Gradle.

### General
  * Java 8
  * [Kotlin](https://kotlinlang.org)
  * [ScapesEngine](https://github.com/Tobi29/ScapesEngine)

### Discord Plugin
  * [Discord4J](https://github.com/austinv11/Discord4J)
