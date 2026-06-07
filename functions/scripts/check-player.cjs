#!/usr/bin/env node
// Checks if a player doc exists in a challenge subcollection.
// Usage: node check-player.cjs <challengeId> <uid> <tokenFile>
const https = require('https');
const fs = require('fs');

const challengeId = process.argv[2];
const uid = process.argv[3];
const tokenFile = process.argv[4];
if (!challengeId || !uid || !tokenFile) {
  console.error('Usage: node check-player.cjs <challengeId> <uid> <tokenFile>');
  process.exit(1);
}

const token = fs.readFileSync(tokenFile, 'utf8').trim();
const url = `https://firestore.googleapis.com/v1/projects/bragwise/databases/(default)/documents/challenges/${challengeId}/players/${uid}`;

https.get(url, { headers: { Authorization: `Bearer ${token}` } }, (res) => {
  let body = '';
  res.on('data', chunk => body += chunk);
  res.on('end', () => {
    const doc = JSON.parse(body);
    if (doc.error) { console.log(`Player doc for ${uid}: NOT FOUND (${doc.error.message})`); process.exit(0); }
    const fields = doc.fields || {};
    const str = v => v && v.stringValue !== undefined ? v.stringValue : (v && v.booleanValue !== undefined ? String(v.booleanValue) : '?');
    console.log(`Player doc for ${uid}: EXISTS`);
    console.log('  isCreator:', str(fields.isCreator));
    console.log('  uid:', str(fields.uid));
    process.exit(0);
  });
}).on('error', e => { console.error(e); process.exit(1); });
