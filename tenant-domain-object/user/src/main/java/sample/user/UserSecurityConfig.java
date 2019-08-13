package sample.user;


import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;

import sample.multitenancy.discriminator.TenantAspect;
import sample.multitenancy.TenantHolder;
import sample.multitenancy.Tenant;
import sample.multitenancy.web.TenantResolverFilter;
import sample.multitenancy.web.WebClientTenants;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.OAuth2IntrospectionAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.introspection.NimbusOAuth2TokenIntrospectionClient;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2TokenIntrospectionClient;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationFilter;

import static org.springframework.security.oauth2.jwt.JwtDecoders.fromIssuerLocation;

@Configuration
public class UserSecurityConfig extends WebSecurityConfigurerAdapter {

	@Value("${tenants-url}") String tenantsUrl;

	@Override
	protected void configure(HttpSecurity http) throws Exception {
		TenantResolverFilter filter =
				new TenantResolverFilter(new WebClientTenants(this.tenantsUrl));
		http
			.authorizeRequests()
				.anyRequest().authenticated()
				.and()
			.oauth2ResourceServer()
				.authenticationManagerResolver(authenticationManagerResolver())
				.and()
			.addFilterBefore(filter, BearerTokenAuthenticationFilter.class);
	}

	@Bean
	TenantAspect tenantAspect() {
		return new TenantAspect();
	}

	private AuthenticationManagerResolver<HttpServletRequest> authenticationManagerResolver() {
		Map<Tenant, AuthenticationManager> authenticationManagers = new HashMap<>();
		return request ->
			Optional.ofNullable(TenantHolder.getTenant())
					.map(tenant -> authenticationManagers.computeIfAbsent(tenant, this::fromTenant))
					.orElseThrow(() -> new IllegalArgumentException("Unknown tenant"));
	}

	AuthenticationManager fromTenant(Tenant tenant) {
		String tokenVerificationMode = tenant.getAttribute("tokenVerificationMode");
		if ("jwt".equals(tokenVerificationMode)) {
			JwtDecoder jwtDecoder = fromIssuerLocation(tenant.getAttribute("issuerUri"));
			return new JwtAuthenticationProvider(jwtDecoder)::authenticate;
		}
		if ("opaqueToken".equals(tokenVerificationMode)) {
			OAuth2TokenIntrospectionClient client =
					new NimbusOAuth2TokenIntrospectionClient(
							tenant.getAttribute("issuerUri") + "/introspect",
							tenant.getName(),
							(String) tenant.getCredentials());
			return new OAuth2IntrospectionAuthenticationProvider(client)::authenticate;
		}
		throw new IllegalArgumentException("Unsupported token verification mode " + tokenVerificationMode);
	}
}
