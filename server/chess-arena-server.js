/**
 * CHESS ARENA — Authoritative Node.js + WebSocket Multiplayer Server
 * ===================================================================
 * Note: The Android application includes built-in real-time cloud multiplayer
 * via Firebase Firestore (rooms, room codes, player position sync, combat hits,
 * health, respawn, and scoreboard).
 *
 * This standalone Node.js WebSocket server (`chess-arena-server.js`) is also
 * provided for hosting dedicated low-latency UDP/WebSocket tournaments.
 *
 * How to run on any VPS / Cloud host:
 *   1. npm init -y && npm install ws
 *   2. PORT=8090 node server/chess-arena-server.js
 */

const http = require("http");
const { WebSocketServer } = require("ws");

const PORT = process.env.PORT || 8090;
const ARENA_LIMIT = 15.6;
const MAX_SPEED_METERS_PER_SEC = 9.5;
const WEAPONS = {
  PULSE_RIFLE: { magSize: 30, cooldownMs: 120, damage: 24 },
  RAIL_PISTOL: { magSize: 12, cooldownMs: 300, damage: 45 },
  SHOTGUN_ROOK: { magSize: 8, cooldownMs: 650, damage: 68 },
};

const rooms = new Map(); // roomCode -> { roomCode, hostId, mode, status, scoreLimit, whiteScore, blackScore, players: Map }

const server = http.createServer((req, res) => {
  res.writeHead(200, { "Content-Type": "application/json" });
  res.end(JSON.stringify({ status: "ok", service: "chess-arena-server", activeRooms: rooms.size }));
});

const wss = new WebSocketServer({ server });

function broadcastRoom(room) {
  const payload = JSON.stringify({
    type: "ROOM_STATE",
    roomCode: room.roomCode,
    status: room.status,
    whiteScore: room.whiteScore,
    blackScore: room.blackScore,
    winner: room.winner,
    players: Array.from(room.players.values()).map((p) => ({
      userId: p.userId,
      nickname: p.nickname,
      team: p.team,
      x: p.x,
      y: p.y,
      z: p.z,
      yaw: p.yaw,
      pitch: p.pitch,
      health: p.health,
      ammo: p.ammo,
      kills: p.kills,
      deaths: p.deaths,
      weapon: p.weapon,
    })),
  });
  for (const p of room.players.values()) {
    if (p.ws.readyState === 1) {
      p.ws.send(payload);
    }
  }
}

wss.on("connection", (ws) => {
  let boundRoomCode = null;
  let boundUserId = null;

  ws.on("message", (raw) => {
    try {
      const msg = JSON.parse(raw.toString());
      if (msg.type === "CREATE_OR_JOIN") {
        const code = String(msg.roomCode || "ARENA1").toUpperCase().slice(0, 8);
        const uid = String(msg.userId || "u_" + Math.random().toString(36).slice(2, 8));
        boundRoomCode = code;
        boundUserId = uid;

        if (!rooms.has(code)) {
          rooms.set(code, {
            roomCode: code,
            hostId: uid,
            mode: msg.mode === "ffa" ? "ffa" : "tdm",
            status: "waiting",
            scoreLimit: Number(msg.scoreLimit) || 15,
            whiteScore: 0,
            blackScore: 0,
            winner: "",
            players: new Map(),
          });
        }
        const room = rooms.get(code);
        const team = room.players.size % 2 === 0 ? "WHITE" : "BLACK";
        room.players.set(uid, {
          ws,
          userId: uid,
          nickname: String(msg.nickname || "Player").slice(0, 24),
          team,
          x: 0,
          y: 0,
          z: team === "BLACK" ? 11.5 : -11.5,
          yaw: team === "BLACK" ? 180 : 0,
          pitch: 0,
          health: 100,
          ammo: 30,
          kills: 0,
          deaths: 0,
          weapon: "PULSE_RIFLE",
          lastMoveTs: Date.now(),
          lastShotTs: 0,
        });
        broadcastRoom(room);
      } else if (msg.type === "MOVE" && boundRoomCode && boundUserId) {
        const room = rooms.get(boundRoomCode);
        const p = room?.players.get(boundUserId);
        if (!p || p.health <= 0) return;

        const now = Date.now();
        const dt = Math.max(0.016, (now - p.lastMoveTs) / 1000);
        const dx = Number(msg.x) - p.x;
        const dz = Number(msg.z) - p.z;
        const dist = Math.hypot(dx, dz);

        // Server-side speed & boundary validation
        if (dist / dt <= MAX_SPEED_METERS_PER_SEC * 1.5) {
          p.x = Math.max(-ARENA_LIMIT, Math.min(ARENA_LIMIT, Number(msg.x) || 0));
          p.y = Math.max(0, Math.min(8, Number(msg.y) || 0));
          p.z = Math.max(-ARENA_LIMIT, Math.min(ARENA_LIMIT, Number(msg.z) || 0));
        }
        p.yaw = Number(msg.yaw) || 0;
        p.pitch = Math.max(-85, Math.min(85, Number(msg.pitch) || 0));
        p.lastMoveTs = now;
        broadcastRoom(room);
      } else if (msg.type === "SHOOT" && boundRoomCode && boundUserId) {
        const room = rooms.get(boundRoomCode);
        const attacker = room?.players.get(boundUserId);
        if (!attacker || attacker.health <= 0 || attacker.ammo <= 0) return;

        const wSpec = WEAPONS[attacker.weapon] || WEAPONS.PULSE_RIFLE;
        const now = Date.now();
        if (now - attacker.lastShotTs < wSpec.cooldownMs * 0.85) return;
        attacker.lastShotTs = now;
        attacker.ammo -= 1;

        const target = room.players.get(msg.targetUserId);
        if (target && target.health > 0 && target.userId !== attacker.userId) {
          target.health = Math.max(0, target.health - wSpec.damage);
          if (target.health === 0) {
            target.deaths += 1;
            attacker.kills += 1;
            if (attacker.team === "WHITE") room.whiteScore += 1;
            else room.blackScore += 1;
            if (room.whiteScore >= room.scoreLimit || room.blackScore >= room.scoreLimit) {
              room.status = "finished";
              room.winner = attacker.team;
            }
          }
        }
        broadcastRoom(room);
      }
    } catch (_) {
      // Ignore malformed packets
    }
  });

  ws.on("close", () => {
    if (boundRoomCode && boundUserId) {
      const room = rooms.get(boundRoomCode);
      if (room) {
        room.players.delete(boundUserId);
        if (room.players.size === 0) rooms.delete(boundRoomCode);
        else broadcastRoom(room);
      }
    }
  });
});

if (require.main === module) {
  server.listen(PORT, () => {
    console.log(`Chess Arena WebSocket Server listening on port ${PORT}`);
  });
}
