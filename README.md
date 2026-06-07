# Stack Trace

A minimal single-class bullet-hell. Duke moves inside a `try { }` box and dodges glyphs drawn from the `Throwable` hierarchy. Written for the *Duke's 8-Bit Adventure* size challenge.

<img width="2559" height="1545" alt="Screenshot_20260607_144027" src="https://github.com/user-attachments/assets/29aafadf-87a5-4e40-9463-6e41150bbc8c" />

Total runtime size **18,067 bytes (17.64 KiB)** which qualifies for Golden Cartridge criteria with just one class and no asset files.

<img width="1920" height="1200" alt="Screenshot_20260607_144303" src="https://github.com/user-attachments/assets/c9a984dc-dcac-4937-91ce-cf866d2b392d" />

## Theme

Game mechanics map onto Java language constructs with the corresponding `Throwable` semantics:

- Bullets are throwables: `;`, `null`, `{` glyphs.
- White bullets are `Exception`s, a hit spends one `catch` (HP is shown as `catch N/N`). Red bullets are `Error`s: they ignore the catch buffer and end the run on contact. Red probability scales with level.
- Running out of catches, or any red hit, ends the run on a generated `Exception in thread "main"` trace. The named type reflects the cause: `NullPointerException` / `IllegalStateException` / `RuntimeException` for an exhausted catch buffer, the relevant `Error` for a red hit.
- The `finally` pickup grants a one-use shield that absorbs any single hit including a red `Error`.
- Graze charges a meter spent on two actives: `Thread.sleep()` scales bullet velocity down for a fixed window, `System.gc()` clears the live bullet array. The two alternate.
- At every tenth level the player may "attempt build": a fixed gauntlet ending in a checkmark target. Reaching it ends the run as `BUILD SUCCESSFUL`.

<img width="1918" height="1200" alt="Screenshot_20260607_143626" src="https://github.com/user-attachments/assets/29eb8379-c1f9-4a30-9599-b30b4ec4728f" />

## Controls

| Key | Action |
| --- | --- |
| <kbd>WASD</kbd> | move |
| <kbd>Q</kbd> | `Thread.sleep()` that slow bullets (50 graze) |
| <kbd>E</kbd> | `System.gc()` that clear bullets (100 graze) |
| <kbd>B</kbd> | attempt build (offered at LV 10, 20, 30, …) |
| <kbd>R</kbd> | restart after a crash |
| <kbd>Esc</kbd> | quit |

Per-level scaling raises bullet speed, red probability, and the concurrent-threat cap. Aimed, spiral, and wall attacks are queued with a fixed-frame indicator before spawning. Build mode freezes score/graze/actives, raises the spawn rate under a tighter threat cap, and adds the persistent `close()` ring (a spinning ring that contracts, tracking the player until a lock radius, then holds its center). Win by reaching the checkmark, miss it and play continues until the next milestone, take a hit and the run ends. One attempt per milestone.

<img width="1917" height="1200" alt="Screenshot_20260607_144908" src="https://github.com/user-attachments/assets/102bf4f7-e1a4-4418-b469-69b2be33d196" />

## Build & run

JDK 25 as per requirements

```
./gradlew run
```

`-Dsun.java2d.opengl=true` is set in the run configuration (frame pacing on Linux/Wayland).

## Architecture

- Single class, default package. `StackTrace extends JPanel implements KeyListener, ActionListener` it is its own listener, so no anonymous classes or listener lambdas are emitted. One `.class` results.
- Swing/Java2D only. One `javax.swing.Timer` at 16 ms is the `ActionListener` and its callback is `update(); repaint();`. Rendering is `Graphics2D` primitives with no images. The sprite is a `String[]` character grid filled cell by cell.
- Entities are structure-of-arrays primitive pools (bullets, pending attacks, pickups), each with an `int` count and swap-remove on cull.
- RNG is `xorshift64` seeded from `System.nanoTime()`.
- `main()` is a JEP 512 instance `void main()`.

## Code structure

Ordered single file: constants, fields, helpers, `update()`, `paintComponent()`, input, `main()`.

- **Pools.** Bullets are parallel arrays `positionX/Y`, `velocityX/Y`, `kind`, `grazed`, `deadly` plus `bulletCount`. `pending` holds queued attacks, `pickup` holds drops. `addBullet`/`removeBullet` (and the pending/pickup equivalents) append to the live region and swap-remove by overwriting the removed index with the last element.
- **Difficulty.** `scoreForLevel`, `cooldownForLevel`, `threatCap`, `redChance` are pure functions of `level`, isolated so the curves are adjustable in one place. `liveRainGroups` counts instant bullets toward the cap to prevent an unavoidable rain-plus-wall overlap.
- **Attacks.** `spawnPattern` selects one of four. Non-instant attacks call `queueAttack`, `firePending` converts a pending entry to bullets when its timer elapses. Geometry is fixed at queue time, so the indicator is accurate and the attack remains dodgeable.
- **`update()`.** Advances timers and Duke, then iterates the bullet pool: integrate position, cull out-of-bounds, then collision, i-frame check, `finally` shield, `deadly` for end run vs. `catch` decrement, else graze-band test scoring and charging the meter. A `building` branch runs the build state machine (`buildIntermission` -> gauntlet with `updateClose` -> checkmark resolution in `checkActive`), otherwise the normal director spawns on `spawnCooldown`.
- **`paintComponent()`.** One scale+translate maps the logical 480×520 canvas to the window (aspect-preserved, letterboxed). Draw order: menu (early return when not started), HUD, pickups, bullets, indicators with exception labels, sprite (with graze outline and i-frame blink), build overlays, win/crash screens. `deadly` bullets render red, the rest use the foreground color.
- **Input/`main()`.** `keyPressed`/`keyReleased` set movement flags and dispatch one-shot actions. `main()` builds the undecorated maximized frame and starts the `Timer`.

## Optimization

- Single class: minimizes constant-pool/class overhead and wins the fewest-classes tie-breaker.
- `-g:none` via `options.isDebug = false` in `build.gradle.kts` which strips `LineNumberTable`/`LocalVariableTable`.
- Primitive SoA pools allocated once, the loop does not allocate, so peak heap is small and flat (memory tie-breaker).
- No runtime assets.
- `int` fields rather than `byte`/`short`: narrower types add sign-extension bytecode and do not reduce class size.

## Size breakdown

Clean build, over the directories the size rules specify:

```
find build/classes/java/main build/resources/main -type f -exec cat {} + 2>/dev/null | wc -c
```


Compiled `.class` : 18,067 bytes

Runtime assets : 0 bytes

Total : 18,067 bytes (17.64 KiB)

Classes : 1

Cartridge : Golden (6.9% of 256 KiB)

## Audio

Not implemented as the added complexity was not worth it for an optional bonus. The game runs silent.
