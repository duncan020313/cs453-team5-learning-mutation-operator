# ASTraMut: Mutation Operator Generator Based on Patch Pattern Learning

## Requirements

- JDK 17 or newer
- Docker with Compose, only when using the dev container

The project uses Gradle Wrapper pinned to Gradle 9.4.1, so a local Gradle
installation is not required for normal builds.

## Build

```bash
gradle clean build --no-configuration-cache
```

## Setup

Run following command:

```bash
./setup.sh
```

What `setup.sh` does:
- Builds the `dev` Docker image and verifies that the image was created.
- Starts the `dev` container, checks that it is running, and prints the command for entering it.

Inside the container, use the same Gradle Wrapper commands:

```bash
./gradlew clean build
```
