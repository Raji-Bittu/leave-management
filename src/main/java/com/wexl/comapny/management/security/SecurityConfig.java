package com.wexl.comapny.management.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.time.Instant;

@Configuration
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/api/admin/**").hasRole("ADMIN")
						.requestMatchers("/api/manager/**").hasRole("MANAGER")
						.requestMatchers("/api/employees/me/**").hasAnyRole("EMPLOYEE", "MANAGER")
						.requestMatchers("/error").permitAll()
						.anyRequest().authenticated())
				.httpBasic(Customizer.withDefaults())
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint((request, response, e) -> {
							response.setHeader("WWW-Authenticate", "Basic realm=\"leave-management\"");
							writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized",
									"Authentication is required", request.getRequestURI());
						})
						.accessDeniedHandler((request, response, e) ->
								writeError(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
										"You do not have permission to access this resource", request.getRequestURI())));
		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/** Same shape as {@code ErrorResponse}, written by hand because these errors occur before MVC is reached. */
	private static void writeError(HttpServletResponse response, int status, String error, String message,
								   String path) throws IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write("""
				{"timestamp":"%s","status":%d,"error":"%s","message":"%s","path":"%s"}"""
				.formatted(Instant.now(), status, error, message, path.replace("\"", "")));
	}
}
