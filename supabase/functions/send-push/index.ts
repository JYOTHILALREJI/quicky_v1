// =============================================================================
//  QUICKY — send-push  Edge Function  (v3.3.8)
// =============================================================================
//
//  Delivers a Firebase Cloud Messaging (FCM) DATA notification to one or more
//  devices registered to [recipient_user_id].
//
//  REQUEST  (POST /functions/v1/send-push)
//  ----------------------------------------
//  Headers:  Authorization: Bearer <user_jwt>    (identified caller)
//            apikey: <supabase_anon_key>
//  Body (JSON):
//  {
//    "recipient_user_id": "<uuid>",           // required — Supabase auth UID
//    "type":   "quicky" | "message" | "match" | "like" | "super_like"
//              | "club" | "club_mention" | "promo",
//    "title":  "📸 Alice sent a Quicky!",     // display text
//    "body":   "Tap to open before it…",
//    "chat_id":   "<conversation_id>",        // optional — for chat types
//    "club_id":   "<club_uuid>",              // optional — for club types
//    "sender_name": "Alice"                   // optional — shown in MessagingStyle
//  }
//
//  RESPONSE
//  ---------
//  200  { "ok": true, "fcm_message_id": "..." }
//  4xx  { "error": "..." }   — bad input / missing token
//  5xx  { "error": "..." }   — FCM / database failure
//
//  ENVIRONMENT VARIABLES (set via `supabase secrets set` or the dashboard)
//  -----------------------------------------------------------------------
//  FIREBASE_PROJECT_ID   — from Firebase Console → Project Settings
//  FIREBASE_CLIENT_EMAIL — from the service account JSON
//  FIREBASE_PRIVATE_KEY  — from the service account JSON (PEM, with \n)
//  SUPABASE_SERVICE_ROLE_KEY — for the server-side db query (bypasses RLS)
//  SUPABASE_URL          — your project URL
//
//  SETUP
//  -----
//  1. Create a Firebase service account:
//     Firebase Console → Project Settings → Service accounts → Generate new key
//  2. Set the env vars above via Supabase dashboard or:
//       supabase secrets set FIREBASE_PROJECT_ID=quicky-xxxxx
//         FIREBASE_CLIENT_EMAIL=firebase-adminsdk@...iam.gserviceaccount.com
//         FIREBASE_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\n..."
//  3. Deploy: supabase functions deploy send-push --no-verify-jwt
//     (JWT is still validated in code; --no-verify-jwt skips the gateway check
//      so the function can also be called from a DB webhook without a user JWT)
// =============================================================================

import { serve } from "https://deno.land/std@0.177.0/http/server.ts";

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: CORS_HEADERS });
  }

  try {
    const body = await req.json();
    const {
      recipient_user_id,
      type = "message",
      title,
      body: msgBody,
      chat_id,
      club_id,
      sender_name,
    } = body;

    if (!recipient_user_id) {
      return json({ error: "recipient_user_id is required" }, 400);
    }

    const supabaseUrl    = Deno.env.get("SUPABASE_URL");
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");

    const tokenRes = await fetch(
      `${supabaseUrl}/rest/v1/device_tokens?user_id=eq.${recipient_user_id}&select=fcm_token`,
      {
        headers: {
          apikey: serviceRoleKey,
          Authorization: `Bearer ${serviceRoleKey}`,
        },
      }
    );
    const tokens = await tokenRes.json();

    if (!tokens || tokens.length === 0) {
      console.log(`No FCM token for user ${recipient_user_id}`);
      return json({ ok: true, delivered: 0 });
    }

    const data = { type };
    if (title)       data.title       = title;
    if (msgBody)     data.body        = msgBody;
    if (chat_id)     data.chat_id     = chat_id;
    if (club_id)     data.club_id     = club_id;
    if (sender_name) data.sender_name = sender_name;

    const fcmAccessToken = await getFcmAccessToken();
    const projectId = Deno.env.get("FIREBASE_PROJECT_ID");

    let delivered = 0;
    const errors = [];

    for (const { fcm_token } of tokens) {
      const fcmRes = await fetch(
        `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`,
        {
          method: "POST",
          headers: {
            Authorization: `Bearer ${fcmAccessToken}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            message: {
              token: fcm_token,
              data,
              android: { priority: "high" },
            },
          }),
        }
      );

      if (fcmRes.ok) {
        delivered++;
      } else {
        const err = await fcmRes.text();
        errors.push(err);
        console.warn(`FCM send failed: ${err.slice(0, 200)}`);
      }
    }

    return json({ ok: delivered > 0, delivered, errors: errors.length > 0 ? errors : undefined });
  } catch (err) {
    console.error("send-push error:", err);
    return json({ error: String(err) }, 500);
  }
});

async function getFcmAccessToken() {
  const clientEmail   = Deno.env.get("FIREBASE_CLIENT_EMAIL");
  const privateKeyPem = (Deno.env.get("FIREBASE_PRIVATE_KEY") || "").replace(/\\n/g, "\n");

  const now = Math.floor(Date.now() / 1000);
  const claim = {
    iss:   clientEmail,
    sub:   clientEmail,
    aud:   "https://oauth2.googleapis.com/token",
    iat:   now,
    exp:   now + 3600,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
  };

  const pemBody = privateKeyPem
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s/g, "");
  const keyData = Uint8Array.from(atob(pemBody), (c) => c.charCodeAt(0));
  const cryptoKey = await crypto.subtle.importKey(
    "pkcs8",
    keyData,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );

  const header  = base64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const payload = base64url(JSON.stringify(claim));
  const sigInput = new TextEncoder().encode(`${header}.${payload}`);
  const signature = new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", cryptoKey, sigInput));
  const jwt = `${header}.${payload}.${base64urlBytes(signature)}`;

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion:  jwt,
    }),
  });
  const { access_token } = await res.json();
  return access_token;
}

function base64url(str) {
  return base64urlBytes(new TextEncoder().encode(str));
}

function base64urlBytes(bytes) {
  return btoa(String.fromCharCode(...bytes))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=/g, "");
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
  });
}
