package se.atte.bragwise.data

/** Thrown when a Firestore PERMISSION_DENIED indicates the challenge no longer exists for this user. */
class ChallengeGoneException : Exception("challenge-gone")
