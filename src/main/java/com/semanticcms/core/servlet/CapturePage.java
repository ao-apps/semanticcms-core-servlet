/*
 * semanticcms-core-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016  AO Industries, Inc.
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

import com.aoindustries.lang.NullArgumentException;
import com.aoindustries.servlet.http.Dispatcher;
import com.aoindustries.servlet.http.NullHttpServletResponseWrapper;
import com.aoindustries.servlet.http.ServletUtil;
import com.semanticcms.core.model.Node;
import com.semanticcms.core.model.Page;
import com.semanticcms.core.model.PageRef;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.SkipPageException;

public class CapturePage {

	private static final String CAPTURE_CONTEXT_REQUEST_ATTRIBUTE_NAME = CapturePage.class.getName()+".captureContext";
	
	private static final String CAPTURE_PAGE_CACHE_REQUEST_ATTRIBUTE_NAME = CapturePage.class.getName()+".capturePageCache";

	/**
	 * To speed up an export, the elements are cached between requests.
	 * The first non-exporting request will clear this cache, and it will also
	 * be remover after a given number of seconds.
	 */
	private static final String EXPORT_CAPTURE_PAGE_CACHE_CONTEXT_ATTRIBUTE_NAME = CapturePage.class.getName()+".exportCapturePageCache";
	
	/**
	 * The number of milliseconds after the export cache is no longer considered valid.
	 */
	private static final long EXPORT_CAPTURE_PAGE_CACHE_TTL = 60 * 1000; // one minute

	/**
	 * Gets the capture context or <code>null</code> if none occurring.
	 */
	public static CapturePage getCaptureContext(ServletRequest request) {
		return (CapturePage)request.getAttribute(CAPTURE_CONTEXT_REQUEST_ATTRIBUTE_NAME);
	}

	/**
	 * Caches pages that have been captured within the scope of a single request.
	 *
	 * IDEA: Could possibly substitute pages with higher level capture, such as META capture in place of PAGE capture request.
	 * IDEA: Could also cache over time, since there is currently no concept of a "user" (except whether request is trusted
	 *       127.0.0.1 or not).
	 */
	static class CapturePageCacheKey {

		final PageRef pageRef;
		final CaptureLevel level;

		CapturePageCacheKey(
			PageRef pageRef,
			CaptureLevel level
		) {
			this.pageRef = pageRef;
			assert level != CaptureLevel.BODY : "Body captures are not cached";
			this.level = level;
		}

		@Override
		public boolean equals(Object o) {
			if(!(o instanceof CapturePageCacheKey)) return false;
			CapturePageCacheKey other = (CapturePageCacheKey)o;
			return
				level==other.level
				&& pageRef.equals(other.pageRef)
			;
		}

		@Override
		public int hashCode() {
			int hash = level.hashCode();
			hash = hash * 31 + pageRef.hashCode();
			return hash;
		}

		@Override
		public String toString() {
			return '(' + level.toString() + ", " + pageRef.toString() + ')';
		}
	}

	// TODO: Consider consequences of caching once we have a security model applied
	static class ExportPageCache {
	
		private final Object lock = new Object();

		/**
		 * The time the cache will expire.
		 */
		private long cacheStart;
		
		/**
		 * The currently active cache.
		 */
		private Map<CapturePageCacheKey,Page> cache;

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
		Map<CapturePageCacheKey,Page> getCache(long currentTime) {
			synchronized(lock) {
				invalidateCache(currentTime);
				if(cache == null) {
					cacheStart = currentTime;
					cache = new HashMap<CapturePageCacheKey,Page>();
					
				}
				return cache;
			}
		}
	}

	/**
	 * Captures a page.
	 * The capture is always done with a request method of "GET", even when the enclosing request is a different method.
	 * Also validates parent-child and child-parent relationships if the other related pages happened to already be captured and cached.
	 */
	public static Page capturePage(
		final ServletContext servletContext,
		final HttpServletRequest request,
		final HttpServletResponse response,
		PageRef pageRef,
		CaptureLevel level
	) throws ServletException, IOException {
		NullArgumentException.checkNotNull(level, "level");

		// Don't use cache for full body captures
		final boolean useCache = level != CaptureLevel.BODY;

		// Find the cache to use
		Map<CapturePageCacheKey,Page> cache;
		{
			ExportPageCache exportCache = (ExportPageCache)servletContext.getAttribute(EXPORT_CAPTURE_PAGE_CACHE_CONTEXT_ATTRIBUTE_NAME);
			if(Headers.isExporting(request)) {
				// No harm done if two threads create two different caches inbetween check and set
				if(exportCache == null) {
					exportCache = new ExportPageCache();
					servletContext.setAttribute(EXPORT_CAPTURE_PAGE_CACHE_CONTEXT_ATTRIBUTE_NAME, exportCache);
				}
				cache = exportCache.getCache(System.currentTimeMillis());
			} else {
				// Clean-up stale export cache
				if(exportCache != null) {
					exportCache.invalidateCache(System.currentTimeMillis());
				}
				// Request-level cache when not exporting
				{
					@SuppressWarnings("unchecked")
					Map<CapturePageCacheKey,Page> reqCache = (Map<CapturePageCacheKey,Page>)request.getAttribute(CAPTURE_PAGE_CACHE_REQUEST_ATTRIBUTE_NAME);
					cache = reqCache;
				}
				if(cache == null) {
					cache = new HashMap<CapturePageCacheKey,Page>();
					request.setAttribute(CAPTURE_PAGE_CACHE_REQUEST_ATTRIBUTE_NAME, cache);
				}
			}
		}

		// cacheKey will be null when this capture is not to be cached
		final CapturePageCacheKey cacheKey;
		Page capturedPage;
		if(useCache) {
			// Check the cache
			cacheKey = new CapturePageCacheKey(pageRef, level);
			capturedPage = cache.get(cacheKey);
		} else {
			cacheKey = null;
			capturedPage = null;
		}

		if(capturedPage == null) {
			// Perform new capture
			Node oldNode = CurrentNode.getCurrentNode(request);
			Page oldPage = CurrentPage.getCurrentPage(request);
			try {
				// Clear request values that break captures
				if(oldNode != null) CurrentNode.setCurrentNode(request, null);
				if(oldPage != null) CurrentPage.setCurrentPage(request, null);
				CaptureLevel oldCaptureLevel = CaptureLevel.getCaptureLevel(request);
				CapturePage oldCaptureContext = CapturePage.getCaptureContext(request);
				try {
					// Set new capture context
					CaptureLevel.setCaptureLevel(request, level);
					CapturePage captureContext = new CapturePage();
					request.setAttribute(CAPTURE_CONTEXT_REQUEST_ATTRIBUTE_NAME, captureContext);
					// Include the page resource, discarding any direct output
					final String capturePath = pageRef.getServletPath();
					try {
						// Clear PageContext on include
						PageContext.newPageContextSkip(
							null,
							null,
							null,
							new PageContext.PageContextCallableSkip() {
								@Override
								public void call() throws ServletException, IOException, SkipPageException {
									Dispatcher.include(
										servletContext,
										capturePath,
										// Always capture as "GET" request
										ServletUtil.METHOD_GET.equals(request.getMethod())
											// Is already "GET"
											? request
											// Wrap to make "GET"
											: new HttpServletRequestWrapper(request) {
												@Override
												public String getMethod() {
													return ServletUtil.METHOD_GET;
												}
											},
										new NullHttpServletResponseWrapper(response)
									);
								}
							}
						);
					} catch(SkipPageException e) {
						// An individual page may throw SkipPageException which only terminates
						// the capture, not the request overall
					}
					capturedPage = captureContext.getCapturedPage();
					if(capturedPage==null) throw new ServletException("No page captured, page=" + capturePath);
					PageRef capturedPageRef = capturedPage.getPageRef();
					if(!capturedPageRef.equals(pageRef)) throw new ServletException(
						"Captured page has unexpected pageRef.  Expected "
							+ pageRef
							+ " but got "
							+ capturedPageRef
					);
				} finally {
					// Restore previous capture context
					CaptureLevel.setCaptureLevel(request, oldCaptureLevel);
					request.setAttribute(CAPTURE_CONTEXT_REQUEST_ATTRIBUTE_NAME, oldCaptureContext);
				}
			} finally {
				if(oldNode != null) CurrentNode.setCurrentNode(request, oldNode);
				if(oldPage != null) CurrentPage.setCurrentPage(request, oldPage);
			}
		}
		assert capturedPage != null;
		if(useCache) {
			// Add to cache
			cache.put(cacheKey, capturedPage);
		}
		// Verify parents that happened to already be cached
		if(!capturedPage.getAllowParentMismatch()) {
			for(PageRef parentRef : capturedPage.getParentPages()) {
				// Can't verify parent reference to missing book
				if(parentRef.getBook() != null) {
					// Check if parent in cache
					Page parentPage = cache.get(new CapturePageCacheKey(parentRef, CaptureLevel.PAGE));
					if(parentPage == null) parentPage = cache.get(new CapturePageCacheKey(parentRef, CaptureLevel.META));
					if(parentPage != null) {
						if(!parentPage.getChildPages().contains(pageRef)) {
							throw new ServletException(
								"The parent page does not have this as a child.  this="
									+ pageRef
									+ ", parent="
									+ parentRef
							);
						}
						// Verify parent's children that happened to already be cached since captures can happen in any order.
						/*
						if(!parentPage.getAllowChildMismatch()) {
							for(PageRef childRef : parentPage.getChildPages()) {
								// Can't verify child reference to missing book
								if(childRef.getBook() != null) {
									// Check if child in cache
									Page childPage = cache.get(new CapturePageCacheKey(childRef, CaptureLevel.PAGE));
									if(childPage == null) childPage = cache.get(new CapturePageCacheKey(childRef, CaptureLevel.META));
									if(childPage != null) {
										if(!childPage.getParentPages().contains(parentRef)) {
											throw new ServletException(
												"The child page does not have this as a parent.  this="
													+ parentRef
													+ ", child="
													+ childRef
											);
										}
									}
								}
							}
						}
						 */
					}
				}
			}
		}
		// Verify children that happened to already be cached
		if(!capturedPage.getAllowChildMismatch()) {
			for(PageRef childRef : capturedPage.getChildPages()) {
				// Can't verify child reference to missing book
				if(childRef.getBook() != null) {
					// Check if child in cache
					Page childPage = cache.get(new CapturePageCacheKey(childRef, CaptureLevel.PAGE));
					if(childPage == null) childPage = cache.get(new CapturePageCacheKey(childRef, CaptureLevel.META));
					if(childPage != null) {
						if(!childPage.getParentPages().contains(pageRef)) {
							throw new ServletException(
								"The child page does not have this as a parent.  this="
									+ pageRef
									+ ", child="
									+ childRef
							);
						}
						// Verify children's parents that happened to already be cached since captures can happen in any order.
						/*
						if(!childPage.getAllowParentMismatch()) {
							for(PageRef parentRef : childPage.getParentPages()) {
								// Can't verify parent reference to missing book
								if(parentRef.getBook() != null) {
									// Check if parent in cache
									Page parentPage = cache.get(new CapturePageCacheKey(parentRef, CaptureLevel.PAGE));
									if(parentPage == null) parentPage = cache.get(new CapturePageCacheKey(parentRef, CaptureLevel.META));
									if(parentPage != null) {
										if(!parentPage.getChildPages().contains(childRef)) {
											throw new ServletException(
												"The parent page does not have this as a child.  this="
													+ childRef
													+ ", parent="
													+ parentRef
											);
										}
									}
								}
							}
						}*/
					}
				}
			}
		}
		return capturedPage;
	}

	/**
	 * Captures a page in the current page context.
	 *
	 * @see  #capturePage(javax.servlet.ServletContext, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, com.semanticcms.core.model.PageRef, com.semanticcms.core.servlet.CaptureLevel)
	 * @see  PageContext
	 */
	public static Page capturePage(PageRef pageRef, CaptureLevel level) throws ServletException, IOException {
		return capturePage(
			PageContext.getServletContext(),
			PageContext.getRequest(),
			PageContext.getResponse(),
			pageRef,
			level
		);
	}

	private CapturePage() {
	}

	private Page capturedPage;
	public void setCapturedPage(Page capturedPage) {
		NullArgumentException.checkNotNull(capturedPage, "page");
		if(this.capturedPage != null) {
			throw new IllegalStateException(
				"Cannot capture more than one page: first page="
				+ this.capturedPage.getPageRef()
				+ ", second page=" + capturedPage.getPageRef()
			);
		}
		this.capturedPage = capturedPage;
	}

	private Page getCapturedPage() {
		return capturedPage;
	}
}
