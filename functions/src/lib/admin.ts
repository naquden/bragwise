import * as admin from 'firebase-admin';

admin.initializeApp();

export const db = admin.firestore();
export const auth = admin.auth();
export const messaging = admin.messaging();

// Convenience type for Firestore server timestamp
export const { FieldValue, Timestamp } = admin.firestore;
