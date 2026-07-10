package shinhan.fibri.ieum.main.notification.presence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class PresenceRegistryTest {

	@Test
	void seedsSnapshotIncludingNullLocationAndRemovesItOnDisconnect() {
		PresenceSeedRepository repository = mock(PresenceSeedRepository.class);
		PresenceRegistry registry = new PresenceRegistry(repository);
		when(repository.findSeedByUserId(42L)).thenReturn(Optional.of(new PresenceSeed(null, null, true, true, false, 5)));

		registry.seedOnConnect(42L);

		assertThat(registry.findByUserId(42L)).contains(new PresenceSnapshot(null, null, true, true, false, 5));
		registry.removeOnLastDisconnect(42L);
		assertThat(registry.findByUserId(42L)).isEmpty();
	}
}
