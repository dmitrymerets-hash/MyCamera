# MyCamera Remote — Cloudflare Worker

1. Create a free Cloudflare account.
2. In **Workers & Pages**, create a Worker and deploy this project using Wrangler or a GitHub integration.
3. From a computer: `cd worker && npx wrangler deploy`; Wrangler prompts for login. Node.js required only for deployment, not to use Android APKs.
4. Copy the HTTPS address, e.g. `https://mycamera-remote.YOUR-SUBDOMAIN.workers.dev`.
5. Enter **the same Worker address** in both Android apps once.
6. On the camera phone, tap **Start camera and broadcast**. Share the generated private link/code with the viewer phone (only with trusted people).
7. The camera screen stays active and shows a visible broadcasting label and stop button. Closing/locking the app stops broadcasting.
8. On the viewer phone, paste the private link or 32-character code and tap Connect.

## Pricing/limitations

Cloudflare Workers Free and Durable Objects Free have quotas. STUN can support free direct peer-to-peer calls, but **some mobile networks will not connect without TURN**. Optional Metered free-plan TURN credentials can be configured using Worker secrets:
```
npx wrangler secret put METERED_API_KEY
npx wrangler secret put METERED_APP
```
METERED_APP is your Metered app subdomain, not the full URL. Do not put credentials in the Android app. Usage caps apply; no unlimited connectivity is promised.

## Privacy

The Worker relays WebRTC offer/answer/ICE signaling, not camera video. The camera requires Android camera permission and an explicit Start button. There is a persistent in-app camera indicator. No remote starting camera, background recording, or microphone capture.
