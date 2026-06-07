#!/usr/bin/env node
// One-shot script: reads a Firestore challenge doc and prints its fields.
// Usage: node scripts/read-challenge.cjs <challengeId>
const admin = require('firebase-admin');
const app = admin.initializeApp();
const db = admin.firestore();

const challengeId = process.argv[2];
if (!challengeId) { console.error('Usage: node read-challenge.cjs <challengeId>'); process.exit(1); }

(async () => {
  const snap = await db.doc(`challenges/${challengeId}`).get();
  if (!snap.exists) { console.log('NOT FOUND'); process.exit(0); }
  const data = snap.data();
  const bets = data.bets;
  console.log('status:', data.status);
  console.log('visibility:', data.visibility);
  console.log('title:', data.title);
  console.log('createdBy:', data.createdBy);
  console.log('bets type:', typeof bets, Array.isArray(bets) ? `array len=${bets.length}` : String(bets));
  if (Array.isArray(bets)) {
    bets.forEach((b, i) => console.log(`  bet[${i}]:`, JSON.stringify(b)));
  }
  process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
