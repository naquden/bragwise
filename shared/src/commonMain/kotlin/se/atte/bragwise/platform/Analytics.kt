package se.atte.bragwise.platform

interface Analytics {
    fun log(event: AnalyticsEvent)
    fun setIsGuest(isGuest: Boolean)
}

sealed interface AnalyticsEvent {
    val name: String
    val params: Map<String, Any>

    data class OnboardingComplete(val method: String) : AnalyticsEvent {
        override val name = "onboarding_complete"
        override val params = mapOf("method" to method)
    }

    data class ChallengeCreated(
        val betCount: Int,
        val visibility: String,
        val category: String,
        val invitedCount: Int,
    ) : AnalyticsEvent {
        override val name = "challenge_created"
        override val params = mapOf(
            "bet_count" to betCount,
            "visibility" to visibility,
            "category" to category,
            "invited_count" to invitedCount,
        )
    }

    data class PredictionSubmitted(
        val predictionCount: Int,
        val isGuest: Boolean,
        val offline: Boolean,
    ) : AnalyticsEvent {
        override val name = "prediction_submitted"
        override val params = mapOf(
            "prediction_count" to predictionCount,
            "is_guest" to isGuest.toString(),
            "offline" to offline.toString(),
        )
    }

    data class ResultsPosted(val resultCount: Int) : AnalyticsEvent {
        override val name = "results_posted"
        override val params = mapOf("result_count" to resultCount)
    }

    data object FriendAdded : AnalyticsEvent {
        override val name = "friend_added"
        override val params = emptyMap<String, Any>()
    }

    data class ShareTapped(
        val shareType: String,
        val inviteCount: Int = 0,
    ) : AnalyticsEvent {
        override val name = "share_tapped"
        override val params: Map<String, Any> = buildMap {
            put("share_type", shareType)
            if (shareType == "invite") put("invite_count", inviteCount)
        }
    }
}
