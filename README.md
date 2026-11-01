# LUDO-T: Multi-User Client-Server Ludo (Java)

[![CI](https://github.com/ShariniN/ludo-multiplayer-client-server-java/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/ShariniN/ludo-multiplayer-client-server-java/actions/workflows/ci.yml)
[![Security](https://github.com/ShariniN/ludo-multiplayer-client-server-java/actions/workflows/security.yml/badge.svg?branch=main)](https://github.com/ShariniN/ludo-multiplayer-client-server-java/actions/workflows/security.yml)

A three-tier, multi-user version of "Ludo with a Twist" (mystery cells, colour strategies, three-sixes rule), built in **Java 21** for a university module on clean coding and concurrent programming (2026).

![Live match](docs/figures/gui_live_mixed_match.png)

## Architecture

| Tier | Launcher | Port(s) | What it is |
|---|---|---|---|
| 3 · Database | `run_database.bat [port] [--reset]` | 8082 | H2 SQL server over TCP; schema applied on start |
| 2 · Game server | `run_server.bat` | 8080 TCP, 8081 HTTP | Socket listener + **embedded Apache Tomcat 10.1** servlets, bounded request queue with 8 workers, one `ReentrantLock` per room, JDBC connection pool |
| 1 · Client | `run_client.bat [host port name COLOUR]` | – | Swing / Java2D desktop client with dice animation, chat and leaderboard |

![Deployment](docs/figures/fig1_deployment.png)

## Verification

| Check | Result |
|---|---|
| `test.bat`: JUnit 5 + Mockito | **280 / 280 passing** |
| Stress test, private server | 1,355 requests, 0 dropped |
| Live three tiers + stress bots | 2,141 bot requests; 4,172 enqueued = 4,172 processed, 0 dropped |
| Newman (Postman collection) | 20 requests, 38 assertions, 0 failures |
| JMeter 5.6.3 (non-GUI) | 3,021 samples at ~1,527 req/s, 0 errors; queue reported 2,020 enqueued = 2,020 processed, 0 dropped |
| SonarQube / SonarLint | used to track cyclomatic and cognitive complexity during refactoring |

![Server queue counters after a stress run](docs/figures/con_stress_external.png)

The screenshot shows `/api/stats` after a 4-bot, 10-second run. The queue counters are cumulative since the server started: 5,381 enqueued, 5,381 processed, 0 dropped.

Raw logs are in [`docs/evidence/`](docs/evidence/).

## HTTP API (Tomcat, port 8081)

```
GET  /api/room/status?roomId=ROOM-1
POST /api/room/join      roomId, playerName, preferredColor   -> clientId
POST /api/room/leave     clientId
POST /api/game/roll      clientId                             -> dice value / 409 if not your turn
POST /api/game/move      clientId, piece
GET  /api/game/events?clientId=...[&waitMs=...]               -> pending events (async long-poll)
GET  /api/game/state?roomId=ROOM-1
GET|POST /api/chat
GET  /api/leaderboard
GET  /api/stats
```

## Running it

Requires Java 21 on the PATH (Windows batch launchers).

```bat
PLAY_GAME.bat            :: compiles, starts all three tiers and four client windows
test.bat                 :: compiles and runs the JUnit suite
run_stress_test.bat      :: socket bots against a private or running server
newman run Ludo_Postman_Collection.json
jmeter -n -t ludo_jmeter_test_plan.jmx
```

## Layout

```
src/ludo/        model, game engine, strategies (domain) + network, server, servlet, db, client, stress
src/test/ludo/   JUnit 5 tests
lib/             Tomcat embed core, Tomcat annotations API, H2 (runtime); lib/test/ JUnit, Mockito
docs/            report (PDF), figures, evidence logs, diagram scripts
```
