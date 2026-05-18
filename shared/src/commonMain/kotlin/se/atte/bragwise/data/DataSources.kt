package se.atte.bragwise.data

/**
 * Placeholder local data sources — SQLDelight persistence deferred to Phase 2.
 * All read flows go directly to Firestore in Phase 1; the local cache layer
 * will sit between remote and UI in Phase 2 for offline-first support.
 *
 * Auth's data sources live in their own files:
 * [AuthRemoteDataSource] and [AuthLocalDataSource] (expect/actual).
 *
 * Challenge / Social / Profile remote data sources also live in their own
 * files: [ChallengeRemoteDataSource], [SocialRemoteDataSource],
 * [ProfileRemoteDataSource].
 */
class ChallengeLocalDataSource
class SocialLocalDataSource
class ProfileLocalDataSource
