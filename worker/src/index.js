import page from "./index.html";

const STUN = [
  { urls: "stun:stun.l.google.com:19302" },
  { urls: "stun:stun.cloudflare.com:3478" }
];

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method !== "GET") return new Response("Method not allowed", { status: 405 });
    if (url.pathname === "/health") return Response.json({ ok: true, service: "MyCamera Remote" });
    if (url.pathname === "/ice") {
      let iceServers = STUN;
      if (env.METERED_APP && env.METERED_API_KEY && /^[a-zA-Z0-9-]+$/.test(env.METERED_APP)) {
        try {
          const endpoint = "https://" + env.METERED_APP +
            ".metered.live/api/v1/turn/credentials?apiKey=" + encodeURIComponent(env.METERED_API_KEY);
          const result = await fetch(endpoint, { signal: AbortSignal.timeout(6000) });
          if (result.ok) {
            const servers = await result.json();
            if (Array.isArray(servers) && servers.length) iceServers = [...STUN, ...servers];
          }
        } catch (err) { console.log("TURN unavailable: " + String(err)); }
      }
      return Response.json({ iceServers, turnConfigured: iceServers.length > STUN.length }, {
        headers: { "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff" }
      });
    }
    const match = /^\/signal\/([0-9a-f]{32})$/.exec(url.pathname);
    if (match) {
      if (request.headers.get("Upgrade")?.toLowerCase() !== "websocket") {
        return new Response("WebSocket required", { status: 426 });
      }
      const id = env.ROOMS.idFromName(match[1]);
      return env.ROOMS.get(id).fetch(request);
    }
    if (url.pathname === "/" || url.pathname === "/index.html") {
      return new Response(page, { headers: {
        "Content-Type": "text/html; charset=utf-8",
        "Cache-Control": "no-store",
        "X-Content-Type-Options": "nosniff",
        "Referrer-Policy": "no-referrer",
        "Permissions-Policy": "camera=(self), microphone=()"
      } });
    }
    return new Response("Not found", { status: 404 });
  }
};

export class Room {
  constructor(ctx, env) { this.ctx = ctx; this.env = env; }
  peer(role) {
    return this.ctx.getWebSockets().find(ws => ws.deserializeAttachment()?.role === role && ws.readyState === WebSocket.OPEN);
  }
  notify(role, type) {
    const ws = this.peer(role);
    if (ws) { try { ws.send(JSON.stringify({ type })); } catch (_) {} }
  }
  async fetch(request) {
    const role = new URL(request.url).searchParams.get("role");
    if (role !== "camera" && role !== "viewer") return new Response("Invalid role", { status: 400 });
    if (this.peer(role)) return new Response("This role is already connected", { status: 409 });
    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);
    this.ctx.acceptWebSocket(server);
    server.serializeAttachment({ role });
    server.send(JSON.stringify({ type: "connected" }));
    this.notify(role === "viewer" ? "camera" : "viewer", role === "viewer" ? "viewer-joined" : "camera-joined");
    return new Response(null, { status: 101, webSocket: client });
  }
  async webSocketMessage(ws, message) {
    if (typeof message !== "string" || message.length > 65536) return;
    let payload;
    try { payload = JSON.parse(message); } catch (_) { return; }
    const role = ws.deserializeAttachment()?.role;
    const type = payload?.type;
    if (!["offer", "answer", "candidate"].includes(type)) return;
    if (type === "offer" && role !== "camera") return;
    if (type === "answer" && role !== "viewer") return;
    if (type === "candidate" && role !== "camera" && role !== "viewer") return;
    const other = this.peer(role === "camera" ? "viewer" : "camera");
    if (other) {
      try { other.send(message); } catch (_) {}
    }
  }
  async webSocketClose(ws) {
    const role = ws.deserializeAttachment()?.role;
    this.notify(role === "camera" ? "viewer" : "camera", role === "camera" ? "camera-left" : "viewer-left");
    try { ws.close(1000, "Closed"); } catch (_) {}
  }
  async webSocketError(ws) {
    const role = ws.deserializeAttachment()?.role;
    this.notify(role === "camera" ? "viewer" : "camera", role === "camera" ? "camera-left" : "viewer-left");
    try { ws.close(); } catch (_) {}
  }
}
