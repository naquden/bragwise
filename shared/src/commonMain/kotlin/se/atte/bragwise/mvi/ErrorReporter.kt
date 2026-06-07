package se.atte.bragwise.mvi

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.coroutines.cancellation.CancellationException

/**
 * A backend/operation error surfaced to the user as an app-wide dialog.
 * [cause] drives the localized headline; [detail] is the raw technical
 * message shown smaller underneath for debugging/context.
 */
data class AppError(val cause: Cause, val detail: String? = null)

/**
 * App-wide error bus. Any backend failure (operation or screen-load) is
 * reported here; a single dialog host at the navigation root collects the
 * stream and shows an [se.atte.bragwise.ui.components.ErrorDialog].
 *
 * Channel-backed (not SharedFlow) so an error raised while no collector is
 * attached is queued and delivered once the host resumes, instead of dropped.
 */
class ErrorReporter {
    private val _errors = Channel<AppError>(Channel.BUFFERED)
    val errors: Flow<AppError> = _errors.receiveAsFlow()

    fun report(error: AppError) {
        _errors.trySend(error)
    }

    fun report(cause: Cause, detail: String? = null) = report(AppError(cause = cause, detail = detail))

    fun report(throwable: Throwable) {
        if (throwable is CancellationException) return
        report(AppError(cause = throwable.toCause(), detail = throwable.message))
    }
}
