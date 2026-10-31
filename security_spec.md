# Chess Arena Security Specification

## 1. Data Invariants
1. **User Profile Ownership (`/users/{userId}`)**: A user profile can only be created, read, updated, or deleted by the authenticated user whose `request.auth.uid == userId`. Immutable fields: `userId`, `createdAt`.
2. **Match Room Integrity (`/rooms/{roomId}`)**: A room can only be created by an authenticated host (`incoming().hostId == request.auth.uid`) with `roomCode == roomId` and `status == 'waiting'`. Once `status == 'finished'`, the room is locked against further updates (Terminal State Locking). Public listing (`allow list`) is strictly restricted to documents where `resource.data.isPublic == true`.
3. **Room Player Integrity & Authoritative Bounds (`/rooms/{roomId}/players/{playerId}`)**:
   - A player document can only be created if the parent `/rooms/{roomId}` exists (`exists(...)`), and `playerId == request.auth.uid`.
   - Coordinates (`x`, `y`, `z`) are strictly bounded to the physical 8x8 Chess Arena geometry (`[-30.0, 30.0]`), `health` is bounded to `[0, 100]`, and `ammo` is bounded to `[0, 60]`.
   - **Action-Based Updates**:
     - **Self Telemetry Update**: Only `request.auth.uid == playerId` can update their own position, angles, ammo, kills, and respawn state.
     - **Combat Hit Action**: A remote authenticated player (`request.auth.uid != playerId`) can ONLY modify `['health', 'deaths', 'lastHitBy', 'updatedAt']`, must set `lastHitBy == request.auth.uid`, and can only reduce `health` (`0 <= incoming().health < existing().health`), preventing arbitrary teleportation or stat tampering of opponents.

## 2. The "Dirty Dozen" Payloads
1. **Unauthenticated Read on `/users` or `/rooms`**: `auth = null` -> Rejected by `isSignedIn()`.
2. **Cross-User Profile Read (`/users/bob_456` by `alice_123`)**: Rejected by `request.auth.uid == userId`.
3. **Identity Spoofing on Room Create (`hostId: "bob_456"` by `alice_123`)**: Rejected by `incoming().hostId == request.auth.uid`.
4. **Ghost Field / Shadow Update (`isVerified: true` injected on `/users` update)**: Rejected by `affectedKeys().hasOnly(...)` and `keys().hasOnly(...)`.
5. **Orphaned Player Creation (creating `/rooms/NONEXISTENT/players/alice_123`)**: Rejected by `exists(/databases/$(database)/documents/rooms/$(roomId))`.
6. **Out-of-Bounds Arena TeleportHack (`x: 9999.0` in `/rooms/{roomId}/players/{playerId}`)**: Rejected by `isValidRoomPlayer` coordinate bounds `[-30.0, 30.0]`.
7. **Health / Ammo Overflow Hack (`health: 500`, `ammo: 9999`)**: Rejected by `isValidRoomPlayer` bounds (`health <= 100`, `ammo <= 60`).
8. **Remote Opponent Position Tampering (`bob_456` tries to change `alice_123`'s `x, y, z`)**: Rejected by Combat Hit Action `affectedKeys().hasOnly(['health', 'deaths', 'lastHitBy', 'updatedAt'])`.
9. **Remote Opponent Healing / Arbitrary Health Set (`bob_456` tries to increase `alice_123`'s `health` to 100)**: Rejected by `incoming().health < existing().health`.
10. **Terminal Room State Mutation (updating `/rooms/{roomId}` after `status == 'finished'`)**: Rejected by `existing().status != 'finished'`.
11. **Immutable `createdAt` Tampering on Update**: Rejected by `incoming().createdAt == existing().createdAt`.
12. **Unfiltered Query on `/rooms` (listing without `where("isPublic", "==", true)`)**: Rejected by `allow list: if isSignedIn() && resource.data.isPublic == true`.

## 3. Red Team Audit & Conflict Report
- **Shadow Update Test**: PASS (`keys().hasOnly(...)` on create and `affectedKeys().hasOnly(...)` on update block all undeclared keys).
- **Email Spoofing Test**: PASS (Rules rely strictly on `request.auth.uid`, never unverified email strings).
- **PII Blanket Test**: PASS (`/users/{userId}` restricts `get` and `list` strictly to `request.auth.uid == userId` / `resource.data.userId == request.auth.uid`; no PII fields stored).
- **Query Trust Test**: PASS (`allow list` on `/users` checks `resource.data.userId == request.auth.uid`; `allow list` on `/rooms` checks `resource.data.isPublic == true`; `allow list` on `/rooms/{roomId}/players` checks `resource.data.roomCode == roomId`).
- **Value Poisoning Test**: PASS (Every `allow update` begins with `isValid[Entity](incoming())`, enforcing types and string/numeric bounds on all allowlisted keys).
