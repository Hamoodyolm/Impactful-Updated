const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const MAX_PLAYERS = 500;
const CHUNK = 90;
const WRITE_INTERVAL_MS = 8 * 60 * 1000;
const ACTIVE_WINDOW_MS = 25 * 60 * 1000;
const RETENTION_MS = 24 * 60 * 60 * 1000;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method !== "POST" || url.pathname !== "/presence") {
      return new Response("Not found", { status: 404 });
    }

    let body;
    try {
      body = await request.json();
    } catch {
      return new Response("Bad request", { status: 400 });
    }

    const now = Date.now();
    const self = typeof body?.self_id === "string" && UUID_RE.test(body.self_id)
      ? body.self_id.toLowerCase()
      : null;
    const players = Array.isArray(body?.player_ids)
      ? [...new Set(body.player_ids
          .filter((p) => typeof p === "string" && UUID_RE.test(p))
          .map((p) => p.toLowerCase()))].slice(0, MAX_PLAYERS)
      : [];

    const statements = [];
    if (self) {
      statements.push(env.DB.prepare(
        "INSERT INTO impactful_users (id, last_seen) VALUES (?1, ?2) " +
        "ON CONFLICT(id) DO UPDATE SET last_seen = excluded.last_seen " +
        "WHERE impactful_users.last_seen < ?3"
      ).bind(self, now, now - WRITE_INTERVAL_MS));
    }
    const firstQuery = statements.length;
    for (let i = 0; i < players.length; i += CHUNK) {
      const chunk = players.slice(i, i + CHUNK);
      const placeholders = chunk.map((_, j) => `?${j + 2}`).join(",");
      statements.push(env.DB.prepare(
        `SELECT id FROM impactful_users WHERE last_seen > ?1 AND id IN (${placeholders})`
      ).bind(now - ACTIVE_WINDOW_MS, ...chunk));
    }

    const active = [];
    if (statements.length > 0) {
      const results = await env.DB.batch(statements);
      for (let i = firstQuery; i < results.length; i++) {
        for (const row of results[i].results) active.push(row.id);
      }
    }
    return Response.json(active);
  },

  async scheduled(event, env) {
    await env.DB.prepare("DELETE FROM impactful_users WHERE last_seen < ?1")
      .bind(Date.now() - RETENTION_MS)
      .run();
  },
};
