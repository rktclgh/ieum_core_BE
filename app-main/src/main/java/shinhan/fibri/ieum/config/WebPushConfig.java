package shinhan.fibri.ieum.config;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import shinhan.fibri.ieum.main.notification.push.WebPushProperties;
import shinhan.fibri.ieum.main.notification.push.WebPushSubscriptionValidator;

@Configuration
public class WebPushConfig {

	@Bean
	WebPushProperties webPushProperties(
		@Value("${app.web-push.enabled:false}") boolean enabled,
		@Value("${app.web-push.vapid-public-key:}") String vapidPublicKey,
		@Value("${app.web-push.allowed-endpoint-hosts:}") String allowedEndpointHosts
	) {
		return new WebPushProperties(enabled, vapidPublicKey, allowedEndpointHosts);
	}

	@Bean
	WebPushSubscriptionValidator webPushSubscriptionValidator(WebPushProperties properties) {
		return new WebPushSubscriptionValidator(properties.allowedEndpointHosts(), Clock.systemUTC());
	}
}
