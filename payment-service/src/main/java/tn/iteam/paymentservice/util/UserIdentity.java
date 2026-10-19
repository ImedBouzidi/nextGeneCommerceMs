package tn.iteam.paymentservice.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Maps a JWT {@code sub} claim onto the numeric user id used across the services.
 *
 * <p>Keycloak issues {@code sub} as a UUID, while order-service stores
 * {@code Order.userId} as the MD5-derived long it computes in
 * {@code OrderService.uuidToLong}. Payment-service has to derive the same value to
 * answer "is this order mine?", so the algorithm is duplicated here deliberately.
 * If order-service ever changes it, both sides must change together — which is the
 * real fix (a shared mapper module), scheduled after Phase 0.
 */
public final class UserIdentity {

    private UserIdentity() {
    }

    /** Returns the numeric user id for a JWT subject, or {@code null} if absent. */
    public static Long toUserId(String sub) {
        if (sub == null || sub.isBlank()) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(sub.getBytes(StandardCharsets.UTF_8));
            long result = 0;
            for (int i = 0; i < 8; i++) {
                result = (result << 8) | (hash[i] & 0xFF);
            }
            return Math.abs(result);
        } catch (NoSuchAlgorithmException e) {
            // MD5 is mandated by the platform, so this is unreachable in practice.
            return (long) Math.abs(sub.hashCode());
        }
    }
}