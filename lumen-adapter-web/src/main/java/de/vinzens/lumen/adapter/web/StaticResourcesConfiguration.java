package de.vinzens.lumen.adapter.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

/**
 * Caching of the web UI: the Vite build puts a content hash into every file name under {@code /assets/}, so
 * those can be cached forever. Everything else (index.html, favicon) is revalidated on every load, otherwise
 * browsers keep showing the old UI after an update ({@code spring.web.resources.cache.cachecontrol.no-cache}).
 */
@Configuration(proxyBeanMethods = false)
class StaticResourcesConfiguration implements WebMvcConfigurer {

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/assets/**")
				.addResourceLocations("classpath:/static/assets/")
				.setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
	}
}
