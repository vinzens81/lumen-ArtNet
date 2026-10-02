package de.vinzens.lumen.adapter.web.access;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Set;

/**
 * Rejects configuration changes from clients that have not unlocked setup with the PIN. Reading and show
 * operation (playback, master, blackout) stay open; {@code PUT /api/mode} checks the lock itself because only
 * leaving show mode needs the PIN.
 */
@Configuration(proxyBeanMethods = false)
class ConfigLockInterceptor implements HandlerInterceptor, WebMvcConfigurer {

	private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

	private final ConfigLock lock;

	ConfigLockInterceptor(ConfigLock lock) {
		this.lock = lock;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(this)
				.addPathPatterns("/api/**")
				.excludePathPatterns("/api/playback/**", "/api/output/master", "/api/output/blackout", "/api/mode",
						"/api/access/unlock", "/api/access/lock");
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (!READ_METHODS.contains(request.getMethod())) {
			lock.requireUnlocked(request.getSession(false));
		}
		return true;
	}
}
