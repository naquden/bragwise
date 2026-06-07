#!/usr/bin/env node
// Lists all player docs for a given uid across all challenges via collection group query.
// Uses Firestore REST API.
const https = require('https');
const fs = require('fs');

const uid = process.argv[2];
const tokenFile = process.argv[3];
if (!uid || !tokenFile) {
  console.error('Usage: node find-player-docs.cjs <uid> <tokenFile>');
  process.exit(1);
}

const token = fs.readFileSync(tokenFile, 'utf8').trim();

// Use runQuery to do a collectionGroup query
const url = `https://firestore.googleapis.com/v1/projects/bragwise/databases/(default)/documents:runQuery`;
const body = JSON.stringify({
  structuredQuery: {
    from: [{ collectionId: 'players', allDescendants: true }],
    where: {
      fieldFilter: {
        field: { fieldPath: 'uid' },
        op: 'EQUAL',
        value: { stringValue: uid },
      },
    },
    limit: 20,
  },
});

const options = {
  method: 'POST',
  headers: {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
    'Content-Length': Buffer.byteLength(body),
  },
};

const req = https.request(url, options, (res) => {
  let data = '';
  res.on('data', chunk => data += chunk);
  res.on('end', () => {
    const results = JSON.parse(data);
    if (!Array.isArray(results)) { console.log('Unexpected response:', data); process.exit(1); }
    const docs = results.filter(r => r.document);
    console.log(`Found ${docs.length} player doc(s) for ${uid}:`);
    docs.forEach(r => {
      const name = r.document.name;
      // name = projects/bragwise/databases/(default)/documents/challenges/{challengeId}/players/{uid}
      const parts = name.split('/');
      const challengeId = parts[parts.length - 3];
      console.log(`  challengeId: ${challengeId}`);
    });
    process.exit(0);
  });
});
req.on('error', e => { console.error(e); process.exit(1); });
req.write(body);
req.end();
