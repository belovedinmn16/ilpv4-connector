package org.interledger.connector.links;

import org.interledger.connector.crypto.ConnectorEncryptionService;
import org.interledger.connector.settings.ConnectorSettings;
import org.interledger.crypto.EncryptedSecret;
import org.interledger.link.LinkSettings;
import org.interledger.link.http.IlpOverHttpLinkSettings;
import org.interledger.link.http.IncomingLinkSettings;
import org.interledger.link.http.JwtAuthSettings;
import org.interledger.link.http.OutgoingLinkSettings;
import org.interledger.link.http.SimpleAuthSettings;

import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Implementation that validates that links settings have required fields and that for ILP-OVER-HTTP link settings,
 * that the incoming and/or outgoing secrets are decryptable
 */
public class DefaultLinkSettingsValidator implements LinkSettingsValidator {

  private final ConnectorEncryptionService encryptionService;
  private final Supplier<ConnectorSettings> connectorSettingsSupplier;

  public DefaultLinkSettingsValidator(ConnectorEncryptionService encryptionService,
                                      Supplier<ConnectorSettings> connectorSettingsSupplier) {
    this.encryptionService = encryptionService;
    this.connectorSettingsSupplier = connectorSettingsSupplier;
  }

  @Override
  public <T extends LinkSettings> T validateSettings(T linkSettings) {
    if (linkSettings instanceof IlpOverHttpLinkSettings) {
      return (T) validateIlpLinkSettings((IlpOverHttpLinkSettings) linkSettings);
    }
    return linkSettings;
  }

  private IlpOverHttpLinkSettings validateIlpLinkSettings(IlpOverHttpLinkSettings linkSettings) {
    Optional<IncomingLinkSettings> incomingLinkSettings = linkSettings.incomingLinkSettings()
        .map(originalIncoming ->
            IncomingLinkSettings.builder().from(originalIncoming)
              .simpleAuthSettings(validateSimpleAuthSettings(originalIncoming.simpleAuthSettings()))
              .jwtAuthSettings(validateJwtAuthSettings(originalIncoming.jwtAuthSettings()))
              .build());

    Optional<OutgoingLinkSettings> outgoingLinkSettings = linkSettings.outgoingLinkSettings()
      .map(originalOutgoing ->
        OutgoingLinkSettings.builder().from(originalOutgoing)
          .simpleAuthSettings(validateSimpleAuthSettings(originalOutgoing.simpleAuthSettings()))
          .jwtAuthSettings(validateJwtAuthSettings(originalOutgoing.jwtAuthSettings()))
          .build());

    Map<String, Object> newCustomSettings = Maps.newHashMap(linkSettings.getCustomSettings());
    incomingLinkSettings.ifPresent(settings -> newCustomSettings.putAll(settings.toCustomSettingsMap()));
    outgoingLinkSettings.ifPresent(settings -> newCustomSettings.putAll(settings.toCustomSettingsMap()));

    return IlpOverHttpLinkSettings.builder().from(linkSettings)
      .incomingLinkSettings(incomingLinkSettings)
      .outgoingLinkSettings(outgoingLinkSettings)
      .customSettings(newCustomSettings)
      .build();
  }

  private Optional<SimpleAuthSettings> validateSimpleAuthSettings(Optional<SimpleAuthSettings> maybeSettings) {
    return maybeSettings.map(settings ->
        SimpleAuthSettings.forAuthToken(validate(getOrCreateEncryptedSecret(settings.authToken())).encodedValue()));
  }

  private Optional<JwtAuthSettings> validateJwtAuthSettings(Optional<JwtAuthSettings> maybeSettings) {
    return maybeSettings.map(settings ->
      JwtAuthSettings.builder().from(settings)
        .encryptedTokenSharedSecret(settings.encryptedTokenSharedSecret()
          .map(secret -> validate(getOrCreateEncryptedSecret(secret)).encodedValue())
        )
      .build()
    );
  }


  private EncryptedSecret getOrCreateEncryptedSecret(String sharedSecret) {
    if (Strings.isNullOrEmpty(sharedSecret)) {
      throw new IllegalArgumentException("sharedSecret cannot be empty");
    }
    validateSharedSecretIsAscii(sharedSecret);
    if (sharedSecret.startsWith("enc:")) {
      return EncryptedSecret.fromEncodedValue(sharedSecret);
    } else {
      return encryptionService.encryptWithAccountSettingsKey(sharedSecret.getBytes());
    }
  }

  private EncryptedSecret validate(EncryptedSecret encryptedSecret) {
    return encryptionService.getDecryptor().withDecrypted(encryptedSecret, (decrypted) -> {
      if (connectorSettingsSupplier.get().enabledFeatures().isRequire32ByteSharedSecrets() && decrypted.length < 32) {
        throw new IllegalArgumentException("shared secret must be 32 bytes");
      }
      // make sure the passed in encryptedSecret is using the latest crypto key metadata. If it is, return the
      // updated secret so that it gets saved. Otherwise, return the passed in encryptedSecret to avoid an
      // unnecessary database update
      EncryptedSecret updated = encryptionService.encryptWithAccountSettingsKey(decrypted);
      if (updated.keyMetadata().equals(encryptedSecret.keyMetadata())) {
        return encryptedSecret;
      }
      else {
        return updated;
      }
    });
  }

  private void validateSharedSecretIsAscii(String sharedSecret) {
    Preconditions.checkArgument(CharMatcher.ascii().matchesAllOf(sharedSecret),
      "Shared secret must be ascii");
  }

}
