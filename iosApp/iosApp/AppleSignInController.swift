import AuthenticationServices
import CryptoKit
import UIKit
import Shared

/// Drives the native Sign in with Apple sheet and hands the result to
/// Kotlin via `registerAppleSignInPresenter` (see `IosAppleSignInBridge.kt`).
///
/// Lives in Swift, not Kotlin, because the flow needs a UIKit presentation
/// anchor (`ASAuthorizationControllerPresentationContextProviding`) and
/// CryptoKit for the nonce hash — same reasoning as the APNs registration
/// call in `iOSApp.swift`.
final class AppleSignInController: NSObject, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {

    /// Firebase requires the RAW nonce; Apple requires the SHA256 hash of it
    /// in the request. Mixing these up is the classic Sign in with Apple bug.
    private var rawNonce: String = ""
    private var completion: ((AppleIdCredential?, String?) -> Void)?

    /// Retains self for the lifetime of one sign-in attempt. The
    /// `ASAuthorizationController.delegate` property is weak, so without
    /// this the controller (and self) could be deallocated before the
    /// delegate callback fires, leaving the Kotlin coroutine suspended
    /// forever with no error.
    private var selfRetain: AppleSignInController?

    func present(completion: @escaping (AppleIdCredential?, String?) -> Void) {
        self.completion = completion
        self.selfRetain = self

        let nonce = Self.randomNonceString()
        rawNonce = nonce

        let provider = ASAuthorizationAppleIDProvider()
        let request = provider.createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = Self.sha256Hex(nonce)

        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = self
        controller.presentationContextProvider = self
        controller.performRequests()
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        defer { selfRetain = nil }

        guard let appleIdCredential = authorization.credential as? ASAuthorizationAppleIDCredential,
              let identityTokenData = appleIdCredential.identityToken,
              let identityToken = String(data: identityTokenData, encoding: .utf8) else {
            completion?(nil, "apple-sign-in-missing-identity-token")
            return
        }

        let fullName = [appleIdCredential.fullName?.givenName, appleIdCredential.fullName?.familyName]
            .compactMap { $0 }
            .joined(separator: " ")
            .trimmingCharacters(in: .whitespaces)

        // Apple sends fullName/email only on the FIRST authorization per
        // Apple ID + app pair. Log which fields actually arrived so a missing
        // display name can be attributed to Apple rather than to our write path.
        NSLog("BRAGWISE_APPLE_7f31a2 swift.authorized givenName=%@ familyName=%@ email=%@",
              appleIdCredential.fullName?.givenName ?? "nil",
              appleIdCredential.fullName?.familyName ?? "nil",
              appleIdCredential.email ?? "nil")

        let credential = AppleIdCredential(
            identityToken: identityToken,
            rawNonce: rawNonce,
            fullName: fullName.isEmpty ? nil : fullName,
            email: appleIdCredential.email
        )
        completion?(credential, nil)
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        defer { selfRetain = nil }

        if let authError = error as? ASAuthorizationError, authError.code == .canceled {
            NSLog("BRAGWISE_APPLE_7f31a2 swift.cancelled")
            completion?(nil, "cancelled")
        } else {
            NSLog("BRAGWISE_APPLE_7f31a2 swift.failed %@", error.localizedDescription)
            completion?(nil, error.localizedDescription)
        }
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow } ?? ASPresentationAnchor()
    }

    private static func randomNonceString(length: Int = 32) -> String {
        let charset: [Character] = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        var result = ""
        var remainingLength = length
        while remainingLength > 0 {
            var randomBytes = [UInt8](repeating: 0, count: 16)
            let status = SecRandomCopyBytes(kSecRandomDefault, randomBytes.count, &randomBytes)
            precondition(status == errSecSuccess, "Unable to generate secure random nonce")
            for byte in randomBytes {
                if remainingLength == 0 { break }
                if byte < charset.count {
                    result.append(charset[Int(byte)])
                    remainingLength -= 1
                }
            }
        }
        return result
    }

    private static func sha256Hex(_ input: String) -> String {
        SHA256.hash(data: Data(input.utf8))
            .compactMap { String(format: "%02x", $0) }
            .joined()
    }
}
