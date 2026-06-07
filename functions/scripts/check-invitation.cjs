#!/usr/bin/env node
// Checks if an invitation exists for a user in a challenge.
const https = require('https');
const fs = require('fs');

const challengeId = process.argv[2];
const uid = process.argv[3];
const tokenFile = process.argv[4];
if (!challengeId || !uid || !tokenFile) {
  console.error('Usage: node check-invitation.cjs <challengeId> <uid> <tokenFile>');
  process.exit(1);
}

const token = fs.readFileSync(tokenFile, 'utf8').trim();
const url = `https://firestore.googleapis.com/v1/projects/bragwise/databases/(default)/documents/challenges/${challengeId}/invitations/${uid}`;

https.get(url, { headers: { Authorization: `Bearer ${token}` } }, (res) => {
  let body = '';
  res.on('data', chunk => body += chunk);
  res.on('end', () => {
    const doc = JSON.parse(body);
    if (doc.error) { console.log(`Invitation for ${uid}: NOT FOUND (${doc.error.message})`); process.exit(0); }
    const fields = doc.fields || {};
    const str = v => v && v.stringValue !== undefined ? v.stringValue : '?';
    console.log(`Invitation for ${uid}: EXISTS`);
    console.log('  invitedUid:', str(fields.invitedUid));
    console.log('  invitedBy:', str(fields.invitedBy));
    process.exit(0);
  });
}).on('error', e => { console.error(e); process.exit(1); });
