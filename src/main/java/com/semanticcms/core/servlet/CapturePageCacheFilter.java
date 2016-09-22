/*
 * semanticcms-core-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2016  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of semanticcms-core-servlet.
 *
 * semanticcms-core-servlet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * semanticcms-core-servlet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with semanticcms-core-servlet.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.semanticcms.core.servlet;

import com.semanticcms.core.model.Page;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

/**
 * Resolves the cache to use for page captures on the current request.
 */
public class CapturePageCacheFilter implements Filter {

	private static final String CAPTURE_PAGE_CACHE_REQUEST_ATTRIBUTE_NAME = CapturePageCacheFilter.class.getName()+".capturePageCache";

	private static final String EXPORT_CACHE_CONTEXT_ATTRIBUTE_NAME = CapturePageCacheFilter.class.getName()+".exportCache";

	private static final Object getCacheLock = new Object();

	/**
	 * The number of milliseconds after the export cache is no longer considered valid.
	 */
	private static final long EXPORT_CAPTURE_PAGE_CACHE_TTL = 60 * 1000; // one minute

	/**
	 * Gets the cache to use for the current request.
	 * All uses of this cache must synchronize on the map itself.
	 *
	 * @throws IllegalStateException if the filter is not active on the current request
	 */
	static Map<CapturePage.CapturePageCacheKey,Page> getCache(ServletRequest request) throws IllegalStateException {
		@SuppressWarnings("unchecked")
		Map<CapturePage.CapturePageCacheKey,Page> cache = (Map<CapturePage.CapturePageCacheKey,Page>)request.getAttribute(CAPTURE_PAGE_CACHE_REQUEST_ATTRIBUTE_NAME);
		if(cache == null) throw new IllegalStateException("cache not active on the current request");
		return cache;
	}

	/**
	 * To speed up an export, the elements are cached between requests.
	 * The first non-exporting request will clear this cache, and it will also
	 * be removed after a given number of seconds.
	 *
	 * TODO: Consider consequences of caching once we have a security model applied
	 */
	private static class ExportPageCache {
	
		private final Object lock = new Object();

		/**
		 * The time the cache will expire.
		 */
		private long cacheStart;
		
		/**
		 * The currently active cache.
		 */
		private Map<CapturePage.CapturePageCacheKey,Page> cache;

		/**
		 * Invalidates the page cache if it has exceeded its TTL.
		 */
		void invalidateCache(long currentTime) {
			synchronized(lock) {
				if(
					cache != null
					&& (
						currentTime >= (cacheStart + EXPORT_CAPTURE_PAGE_CACHE_TTL)
						// Handle system time changes
						|| currentTime <= (cacheStart - EXPORT_CAPTURE_PAGE_CACHE_TTL)
					)
				) {
					cache = null;
				}
			}
		}

		/**
		 * Invalidates the cache, if needed, then gets the resulting cache.
		 */
		Map<CapturePage.CapturePageCacheKey,Page> getCache(long currentTime) {
			synchronized(lock) {
				invalidateCache(currentTime);
				if(cache == null) {
					cacheStart = currentTime;
					cache = new HashMap<CapturePage.CapturePageCacheKey,Page>();
					
				}
				return cache;
			}
		}
	}

	private ServletContext servletContext;

	@Override
	public void init(FilterConfig config) throws ServletException {
		servletContext = config.getServletContext();
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		@SuppressWarnings("unchecked")
		Map<CapturePage.CapturePageCacheKey,Page> cache = (Map<CapturePage.CapturePageCacheKey,Page>)request.getAttribute(CAPTURE_PAGE_CACHE_REQUEST_ATTRIBUTE_NAME);
		if(cache == null) {
			boolean isExporting;
			if(request instanceof HttpServletRequest) {
				isExporting = Headers.isExporting((HttpServletRequest)request);
			} else {
				isExporting = false;
			}
			synchronized(getCacheLock) {
				ExportPageCache exportCache = (ExportPageCache)servletContext.getAttribute(EXPORT_CACHE_CONTEXT_ATTRIBUTE_NAME);
				if(isExporting) {
					if(exportCache == null) {
						exportCache = new ExportPageCache();
						servletContext.setAttribute(EXPORT_CACHE_CONTEXT_ATTRIBUTE_NAME, exportCache);
					}
					cache = exportCache.getCache(System.currentTimeMillis());
				} else {
					// Clean-up stale export cache
					if(exportCache != null) {
						exportCache.invalidateCache(System.currentTimeMillis());
					}
					// Request-level cache when not exporting
					cache = new HashMap<CapturePage.CapturePageCacheKey,Page>();
				}
			}
			try {
				request.setAttribute(CAPTURE_PAGE_CACHE_REQUEST_ATTRIBUTE_NAME, cache);
				chain.doFilter(request, response);
			} finally {
				request.removeAttribute(CAPTURE_PAGE_CACHE_REQUEST_ATTRIBUTE_NAME);
			}
		} else {
			// Cache already set
			chain.doFilter(request, response);
		}
	}

	@Override
	public void destroy() {
		servletContext = null;
	}
}