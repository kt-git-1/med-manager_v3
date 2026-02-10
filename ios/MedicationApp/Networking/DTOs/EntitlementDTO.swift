import Foundation

// MARK: - Claim Request / Response

struct ClaimRequest: Codable, Sendable {
    let productId: String
    let signedTransactionInfo: String
    let environment: String?
}

struct ClaimResponseData: Codable, Sendable {
    let premium: Bool
    let productId: String
    let status: String
    let updatedAt: String
}

struct ClaimResponse: Codable, Sendable {
    let data: ClaimResponseData
}

// MARK: - Entitlements Response

struct EntitlementRecordDTO: Codable, Sendable {
    let productId: String
    let status: String
    let purchasedAt: String
    let originalTransactionId: String
}

struct EntitlementsResponseData: Codable, Sendable {
    let premium: Bool
    let entitlements: [EntitlementRecordDTO]
}

struct EntitlementsResponse: Codable, Sendable {
    let data: EntitlementsResponseData
}
