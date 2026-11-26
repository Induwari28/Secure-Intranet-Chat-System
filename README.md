## 📁 Project Structure

```text
Secure-Intranet-Chat-System/
│
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── corporatechat/
│       │           ├── client/         # Client-side application
│       │           ├── server/         # Main chat server
│       │           ├── security/       # TLS and AES encryption
│       │           ├── concurrency/    # Thread pool management
│       │           ├── nio/            # Java NIO and ByteBuffer
│       │           ├── tasks/          # Callable background tasks
│       │           └── model/          # Shared message models
│       │
│       └── resources/
│           └── certificates/          # TLS certificates (no private keys in Git)
│
├── docs/                               # Project documentation and report
├── pom.xml                             # Maven configuration
├── README.md
└── .gitignore

##  Team Responsibilities

| Member | Assigned Concept | Main Package |
|---|---|---|
| Member 1 | JSSE / TLS | `security/` |
| Member 2 | AES Application-Layer Encryption | `security/` |
| Member 3 | Thread Pool Server Concurrency | `concurrency/` |
| Member 4 | Java NIO ByteBuffers | `nio/` |
| Member 5 | Callable Background Tasks | `tasks/` |

##  Git Collaboration Guidelines

- Each member must work on their assigned feature branch.
- Do not commit directly to `main`.
- Push your changes to your own branch.
- Create a Pull Request to merge changes into `main`.
- Coordinate shared classes and interfaces with the team before implementation.
