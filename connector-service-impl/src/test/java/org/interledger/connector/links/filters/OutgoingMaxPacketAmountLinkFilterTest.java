package org.interledger.connector.links.filters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.interledger.connector.accounts.AccountId;
import org.interledger.connector.accounts.AccountSettings;
import org.interledger.core.InterledgerAddress;
import org.interledger.core.InterledgerCondition;
import org.interledger.core.InterledgerErrorCode;
import org.interledger.core.InterledgerPreparePacket;
import org.interledger.core.InterledgerRejectPacket;
import org.interledger.core.InterledgerResponsePacket;

import com.google.common.primitives.UnsignedLong;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public class OutgoingMaxPacketAmountLinkFilterTest {

  @Rule
  public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Mock
  private LinkFilterChain filterChain;

  private Supplier<InterledgerAddress> addressSupplier = () -> InterledgerAddress.of("example.source");

  @Test
  public void filterHatesNullSettings() {
    OutgoingMaxPacketAmountLinkFilter filter = createFilter();
    expectedException.expect(NullPointerException.class);
    filter.doFilter(null, createPrepareWithAmount(10), filterChain);
  }

  @Test
  public void filterHatesNullPrepare() {
    OutgoingMaxPacketAmountLinkFilter filter = createFilter();
    expectedException.expect(NullPointerException.class);
    filter.doFilter(createAccountSettingsWithMaxAmount(UnsignedLong.valueOf(100)), null, filterChain);
  }

  @Test
  public void filterHatesNullChain() {
    OutgoingMaxPacketAmountLinkFilter filter = createFilter();
    expectedException.expect(NullPointerException.class);
    filter.doFilter(createAccountSettingsWithMaxAmount(UnsignedLong.valueOf(100)), createPrepareWithAmount(10), null);
  }

  @Test
  public void passAlongWhenBelowMax() {
    OutgoingMaxPacketAmountLinkFilter filter = createFilter();
    AccountSettings settings = createAccountSettingsWithMaxAmount(UnsignedLong.valueOf(1000));
    InterledgerPreparePacket prepare = createPrepareWithAmount(999);
    filter.doFilter(settings, prepare, filterChain);
    verify(filterChain, times(1)).doFilter(settings, prepare);
  }

  @Test
  public void rejectWhenBeyondMax() {
    OutgoingMaxPacketAmountLinkFilter filter = createFilter();
    AccountSettings settings = createAccountSettingsWithMaxAmount(UnsignedLong.valueOf(1000));
    InterledgerPreparePacket prepare = createPrepareWithAmount(1001);
    InterledgerResponsePacket response = filter.doFilter(settings, prepare, filterChain);
    assertThat(response).isInstanceOf(InterledgerRejectPacket.class)
      .extracting("code", "message")
      .containsExactly(InterledgerErrorCode.F08_AMOUNT_TOO_LARGE,
        "Packet size too large: maxAmount=1000 actualAmount=1001");
    verify(filterChain, times(0)).doFilter(settings, prepare);
  }

  private OutgoingMaxPacketAmountLinkFilter createFilter() {
    return new OutgoingMaxPacketAmountLinkFilter(addressSupplier);
  }

  private AccountSettings createAccountSettingsWithMaxAmount(UnsignedLong max) {
    AccountSettings settings = mock(AccountSettings.class);
    when(settings.maximumPacketAmount()).thenReturn(Optional.of(max));
    when(settings.accountId()).thenReturn(AccountId.of(UUID.randomUUID().toString()));
    return settings;
  }

  private InterledgerPreparePacket createPrepareWithAmount(long amount) {
    return InterledgerPreparePacket.builder()
      .executionCondition(InterledgerCondition.of(new byte[32]))
      .amount(UnsignedLong.valueOf(amount))
      .expiresAt(Instant.now())
      .destination(InterledgerAddress.of("example.destination"))
      .build();
  }
}
