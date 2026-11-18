# Documentation

Everything about this project, versioned next to the code ("docs as code"). If the code and the docs disagree, the code is the truth, so please fix the doc.

| Document | Read it when you want to know... |
|---|---|
| [Project overview](overview.md) | What we are building, why, and the current status |
| [Architecture](architecture.md) | How the system fits together: diagrams, data flow, data model, deployment |
| [Roadmap](roadmap.md) | Phases: what is done, what is next, what is pending from whom |
| [iPhone data capture](iphone-data-capture.md) | Why iOS can't read SMS, what we do instead, how App Store finance apps work |
| [iOS Shortcut setup](ios-shortcut.md) | Step-by-step guide for forwarding bank SMS from the iPhone |
| [Decision records (ADRs)](decisions/) | *Why* each major technical choice was made, with the alternatives |
| [Plans](plans/) | Detailed designs for upcoming phases, written before coding |

## Architecture Decision Records

An ADR is a short document capturing one important decision: the context, the options, what we chose, and the consequences. Many product companies use them, because six months later nobody remembers *why*. When a decision changes, write a new ADR that supersedes the old one. Never rewrite history.

| # | Decision | Status |
|---|---|---|
| [0001](decisions/0001-java-spring-boot.md) | Java 21 + Spring Boot for the backend | Superseded by 0009 |
| [0002](decisions/0002-postgresql.md) | PostgreSQL as the database | Accepted |
| [0003](decisions/0003-thin-client-server-side-parsing.md) | Thin clients; all parsing on the server | Accepted |
| [0004](decisions/0004-raw-message-evidence-log.md) | Store every inbound message (`raw_messages`) | Accepted |
| [0005](decisions/0005-three-layer-deduplication.md) | Three-layer deduplication | Accepted |
| [0006](decisions/0006-package-by-feature.md) | Package-by-feature modular monolith | Accepted |
| [0007](decisions/0007-multi-user-google-login.md) | Multi-user with Google sign-in and our own tokens | Accepted |
| [0008](decisions/0008-react-native-expo-client.md) | React Native + Expo app (web build for iPhone, APK for Android) | Accepted |
| [0009](decisions/0009-node-typescript-fastify.md) | Node + TypeScript + Fastify replaces Java | Accepted |
| [0010](decisions/0010-free-hosting.md) | Free-tier hosting and tooling (Oracle Always Free, DuckDNS, Cloudflare Pages, GitHub Actions) | Proposed |
