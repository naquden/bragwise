#!/usr/bin/env node
// Lists all challenges in Firestore (up to 20).
const https = require('https');
const fs = require('fs');

const tokenFile = process.argv[2];
if (!tokenFile) { console.error('Usage: node list-challenges.cjs <tokenFile>'); process.exit(1); }

const token = fs.readFileSync(tokenFile, 'utf8').trim();
const url = 'https://firestore.googleapis.com/v1/projects/bragwise/databases/(default)/documents/challenges?pageSize=20';

https.get(url, { headers: { Authorization: `Bearer ${token}` } }, (res) => {
  let body = '';
  res.on('data', chunk => body += chunk);
  res.on('end', () => {
    const result = JSON.parse(body);
    if (result.error) { console.log('ERROR:', result.error.message); process.exit(1); }
    const docs = result.documents || [];
    console.log(`Found ${docs.length} challenge(s):`);
    docs.forEach(doc => {
      const id = doc.name.split('/').pop();
      const f = doc.fields || {};
      const str = v => v && v.stringValue !== undefined ? v.stringValue : '?';
      console.log(`  ${id}  title=${str(f.title)}  status=${str(f.status)}  visibility=${str(f.visibility)}`);
    });
    process.exit(0);
  });
}).on('error', e => { console.error(e); process.exit(1); });
