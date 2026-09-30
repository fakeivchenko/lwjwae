package dev.ivchenko.lwjwae.update;

import dev.ivchenko.lwjwae.exception.UpdateRejectedException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import lombok.experimental.UtilityClass;

/**
 * Checks the Ed25519 signature of a manifest, which is Base64 text in a file of its own.
 *
 * <p>The signature covers the bytes of the manifest as the server has them, so no form of JSON has
 * to be agreed on, and HTTPS alone isn't trusted: whoever controls the server, or a cache in front
 * of it, can't make the application run a file that the private key didn't sign for.
 */
@UtilityClass
class UpdateSignatures {
  /**
   * Checks that {@code signature} is the one of {@code manifest} by {@code publicKey}.
   *
   * @throws UpdateRejectedException If it isn't, or the key or the signature isn't Base64 of one.
   */
  void verify(String publicKey, byte[] manifest, byte[] signature) {
    boolean valid;
    try {
      PublicKey key =
          KeyFactory.getInstance("Ed25519")
              .generatePublic(
                  new X509EncodedKeySpec(Base64.getDecoder().decode(publicKey.strip())));
      Signature verifier = Signature.getInstance("Ed25519");
      verifier.initVerify(key);
      verifier.update(manifest);
      valid =
          verifier.verify(
              Base64.getDecoder().decode(new String(signature, StandardCharsets.US_ASCII).strip()));
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new UpdateRejectedException("The signature of the manifest can't be checked", e);
    }
    if (!valid) {
      throw new UpdateRejectedException("The manifest isn't signed by the key of the application");
    }
  }
}
