#!/usr/bin/env node
/**
 * One-off backfill: stamp `expireAt` on legacy `auditLog` docs that were
 * written before the TTL change (`functions/src/lib/middleware.ts`).
 *
 * `expireAt = at + 90 days`, so each doc expires relative to when it was
 * actually created. Docs already older than 90 days get a past timestamp and
 * Firestore TTL purges them on its next sweep.
 *
 * Usage (from the functions/ directory, using Application Default Credentials):
 *
 *   gcloud auth application-default login        # once
 *   node scripts/backfill-audit-ttl.cjs          # dry-run, prints what it would do
 *   node scripts/backfill-audit-ttl.cjs --apply  # actually writes
 */
const admin = require('firebase-admin');

const APPLY = process.argv.includes('--apply');
const TTL_DAYS = 90;
const TTL_MS = TTL_DAYS * 24 * 60 * 60 * 1000;
const BATCH_SIZE = 400;

admin.initializeApp({ projectId: process.env.GCLOUD_PROJECT || 'bragwise' });
const db = admin.firestore();
const { Timestamp } = admin.firestore;

function expireFor(doc) {
  const data = doc.data();
  const at = data.at;
  const baseMs =
    at && typeof at.toMillis === 'function' ? at.toMillis() : Date.now();
  return Timestamp.fromMillis(baseMs + TTL_MS);
}

async function main() {
  const snap = await db.collection('auditLog').get();
  const missing = snap.docs.filter((d) => d.get('expireAt') == null);

  console.log(
    `auditLog: ${snap.size} docs total, ${missing.length} missing expireAt.`,
  );
  if (missing.length === 0) {
    console.log('Nothing to backfill.');
    return;
  }
  if (!APPLY) {
    console.log('Dry-run (no --apply). Sample of planned updates:');
    for (const doc of missing.slice(0, 5)) {
      console.log(`  ${doc.id} -> expireAt ${expireFor(doc).toDate().toISOString()}`);
    }
    console.log('Re-run with --apply to write.');
    return;
  }

  let written = 0;
  for (let i = 0; i < missing.length; i += BATCH_SIZE) {
    const slice = missing.slice(i, i + BATCH_SIZE);
    const batch = db.batch();
    for (const doc of slice) {
      batch.update(doc.ref, { expireAt: expireFor(doc) });
    }
    await batch.commit();
    written += slice.length;
    console.log(`Committed ${written}/${missing.length}.`);
  }
  console.log(`Done. Backfilled ${written} docs.`);
}

main().then(
  () => process.exit(0),
  (err) => {
    console.error(err);
    process.exit(1);
  },
);
