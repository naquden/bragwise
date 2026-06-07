#!/usr/bin/env node
// Reads a Firestore challenge doc via REST API using a bearer token from file.
// Usage: node scripts/read-challenge-rest.cjs <challengeId> <tokenFile>
const https = require('https');
const fs = require('fs');

const challengeId = process.argv[2];
const tokenFile = process.argv[3];
if (!challengeId || !tokenFile) {
  console.error('Usage: node read-challenge-rest.cjs <challengeId> <tokenFile>');
  process.exit(1);
}

const token = fs.readFileSync(tokenFile, 'utf8').trim();
const url = `https://firestore.googleapis.com/v1/projects/bragwise/databases/(default)/documents/challenges/${challengeId}`;

https.get(url, { headers: { Authorization: `Bearer ${token}` } }, (res) => {
  let body = '';
  res.on('data', chunk => body += chunk);
  res.on('end', () => {
    const doc = JSON.parse(body);
    if (doc.error) { console.log('ERROR:', doc.error.message); process.exit(1); }
    const fields = doc.fields || {};
    const fmt = (v) => {
      if (!v) return 'null';
      if (v.stringValue !== undefined) return v.stringValue;
      if (v.integerValue !== undefined) return v.integerValue;
      if (v.booleanValue !== undefined) return String(v.booleanValue);
      if (v.nullValue !== undefined) return 'NULL';
      if (v.arrayValue !== undefined) return `array[${(v.arrayValue.values || []).length}]`;
      if (v.mapValue !== undefined) return 'map';
      return JSON.stringify(v);
    };
    console.log('status:', fmt(fields.status));
    console.log('visibility:', fmt(fields.visibility));
    console.log('title:', fmt(fields.title));
    console.log('createdBy:', fmt(fields.createdBy));
    const bets = fields.bets;
    console.log('bets field present:', bets !== undefined);
    console.log('bets type key:', bets ? Object.keys(bets)[0] : 'MISSING');
    console.log('bets:', fmt(bets));
    if (bets && bets.arrayValue) {
      const vals = bets.arrayValue.values || [];
      vals.forEach((b, i) => {
        const bf = b.mapValue && b.mapValue.fields;
        console.log(`  bet[${i}] kind=${bf && bf.kind ? bf.kind.stringValue : '?'} id=${bf && bf.id ? bf.id.stringValue : '?'} title=${bf && bf.title ? bf.title.stringValue : '?'}`);
      });
    }
    process.exit(0);
  });
}).on('error', e => { console.error(e); process.exit(1); });
