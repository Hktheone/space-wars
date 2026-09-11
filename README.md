# Space Wars — Thread Pool Visualizer

A Space-Invaders-style game where every enemy and bullet is a real `java.util.concurrent` task,
built to make a bounded `ThreadPoolExecutor` visible: how it schedules work, queues it under
load, retires idle threads, and refuses tasks it can't handle. The game is the excuse; the
sidebar showing live worker state is the point.

## Demo video

📹 **[Watch the demo](docs/demo.mp4)**

*(drop your recording at `docs/demo.mp4` — that's the path this link and the embed below point at)*

<video src="docs/demo.mp4" controls width="640">
  Your browser can't play this video inline — use the link above instead.
</video>

## The core idea: tasks, not threads

Nothing on screen owns a raw `Thread`. An enemy or a bullet is a `Runnable` whose entire time on
screen happens inside its own `run()` — a sleep-loop that moves it down (or up) the screen for as
long as it's alive. Submitting that `Runnable` to a bounded `ThreadPoolExecutor` is what puts it
in motion, and that task occupies one pool worker for its whole lifetime, not just for an instant.
Two independent pools exist:

| Pool         | Core / Max | Queue | Backs                          |
|--------------|-----------:|------:|---------------------------------|
| Enemy pool   | 4 / 6      | 20    | every falling enemy             |
| Bullet pool  | 3 / 5      | 20    | every fired bullet              |

Because each pool is bounded, more enemies than the pool can run at once simply **wait in the
executor's queue**, invisible above the top of the screen, until a worker frees up. Shrink a
pool's slider in the UI while enemies are spawning and you can watch that backpressure happen
live — queue depth climbs, then tasks start getting rejected outright once the queue is full too.

## What the sidebar shows

Every number in the sidebar comes from the pool's own introspection API
(`getActiveCount()`, `getQueue().size()`, `getCompletedTaskCount()`, etc.) — nothing is a
hand-rolled guess about what the executor is doing.

- **Per-worker rows** (`Enemy-Worker-1`, `Bullet-Worker-1`, ...), each showing what it's doing
  right now: idle, busy with a named task, just finished, or genuinely terminated.
- **`core=`/`max=`/`queue=`** — the pool's live configuration and how full its bounded queue is.
- **`completed=`/`rejected=`** — running totals straight off the executor.
- **`stuck (frozen forever):`** — how many workers are permanently pinned by a frozen enemy that
  will never release them (see below). Turns orange once it's above zero.
- **`rejected: Enemy#N (queue full)`** — flashes for a couple of seconds whenever a spawn is
  actually refused by the pool.
- A **`Thread.State` reference** panel at the top, mapping this demo's simplified states back to
  the real JVM states they represent.

## Gameplay mechanics, and what each one demonstrates

- **Red stones** die on the first hit — a task that runs, gets shot, and returns. The simple case.
- **Blue stones** survive a first hit by *freezing*: the task stops advancing the enemy and just
  keeps sleeping in a loop, genuinely parked (`Thread.sleep`, real `TIMED_WAITING`) rather than
  finishing. The sidebar shows a live countdown, `sleep(5)` → `sleep(4)` → ... A second hit while
  frozen destroys it; otherwise it thaws after 5s and resumes falling. **A frozen enemy that's
  never hit again holds its worker forever** — a deliberate, visible demo of a stuck/blocked task
  starving a thread pool of usable capacity, not a bug.
- **Rejection**: when a pool's queue *and* its max threads are both full, a new spawn is refused
  outright (`RejectedExecutionException` under the hood) instead of blocking. A rejected task
  never becomes a thread at all, so it never appears on screen — the sidebar is the only place
  that event is visible.
- **Death, with a cause**: a task that ends because it was shot (`EndCause.KILLED`) reports
  differently in the sidebar than one that just reached the bottom of the screen or left the top
  unused (`EndCause.OFFSCREEN`) — e.g. `Enemy#42 killed - thread finished` vs.
  `Enemy#42 thread finished`.
- **On-screen death animation**: a dying enemy/bullet turns into a red **X**, then an orange **O**
  (matching the sidebar's "just finished" color), fading out over ~700ms — timed to match the
  sidebar's own "thread finished" flash, so the canvas and the sidebar read as the same event.
- **Shutdown & restart**: the button calls `shutdownNow()` on both pools (interrupting every
  running task), then rebuilds them fresh at the same core/max size and clears the board —
  demonstrating pool teardown/rebuild as its own lifecycle event, distinct from any single task
  ending.

## Running it

Requires JDK 21 and Maven.

```bash
mvn compile exec:java
```

or build a runnable jar:

```bash
mvn package
java -jar target/space-wars.jar
```

## Controls

| Key                | Action        |
|--------------------|---------------|
| `←` / `A`          | Move left     |
| `→` / `D`          | Move right    |
| `Space`            | Fire          |

The pool-size sliders and the restart button live in the sidebar and can be used mid-game.

## Project layout

```
src/main/java/com/spacewars/
├── App.java                 entry point
├── engine/
│   └── GameWorld.java        owns both pools, entity lists, collision + scoring
├── model/
│   ├── Entity.java           shared position/destroyed/EndCause state
│   ├── Enemy.java            falling task: freeze/thaw/kill logic
│   ├── EnemyKind.java        RED (one-hit) / BLUE (freeze-then-kill)
│   ├── Bullet.java           rising task
│   └── Player.java           EDT-driven, not a task
├── pool/
│   ├── VisualThreadPool.java wraps ThreadPoolExecutor: submit/resize/restart + introspection
│   ├── TrackingThreadFactory.java names workers, records real birth/death
│   ├── WorkerState.java      IDLE / BUSY / DONE / TERMINATED
│   └── WorkerSnapshot.java   one worker's point-in-time state, for painting a row
└── ui/
    ├── MainFrame.java        wires everything together, drives the game/spawn timers
    ├── GamePanel.java        rendering + keyboard input
    ├── ThreadPoolPanel.java  the live sidebar
    ├── LegendPanel.java      Thread.State reference
    └── ControlPanel.java     pool-size sliders + restart button
```

## Why this exists

Most portfolio projects that claim "concurrency experience" show a `CompletableFuture` chain and
call it done. This one makes the underlying `ThreadPoolExecutor` state — queuing, backpressure,
rejection, worker reuse, and pool teardown — something you can watch happen in real time, driven
by a game loop instead of a synthetic benchmark.
