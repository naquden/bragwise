package se.atte.bragwise.data

/**
 * Stub data sources for the not-yet-wired repositories. Each is a placeholder
 * that the matching `*Repository` constructs but never delegates to — every
 * cloud-touching method on those repositories still returns
 * `NotImplementedError` until the corresponding callable lands in Phase D
 * (plan §5).
 *
 * Auth's data sources are real and live in their own files:
 * [AuthRemoteDataSource] (GitLive-backed) and [AuthLocalDataSource]
 * (expect/actual, SharedPreferences / NSUserDefaults).
 */
class ChallengeRemoteDataSource
class ChallengeLocalDataSource
class SocialRemoteDataSource
class SocialLocalDataSource
class ProfileRemoteDataSource
class ProfileLocalDataSource
