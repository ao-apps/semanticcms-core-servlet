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

import com.aoindustries.io.TempFileList;
import com.aoindustries.lang.NullArgumentException;
import com.aoindustries.servlet.filter.TempFileContext;
import com.aoindustries.servlet.http.Dispatcher;
import com.aoindustries.servlet.http.NullHttpServletResponseWrapper;
import com.aoindustries.servlet.http.ServletUtil;
import com.aoindustries.util.AoCollections;
import com.aoindustries.util.concurrent.Executor;
import com.semanticcms.core.model.Node;
import com.semanticcms.core.model.Page;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.servlet.impl.PageImpl;
import com.semanticcms.core.servlet.util.HttpServletSubRequest;
import com.semanticcms.core.servlet.util.HttpServletSubResponse;
import com.semanticcms.core.servlet.util.UnmodifiableCopyHttpServletRequest;
import com.semanticcms.core.servlet.util.UnmodifiableCopyHttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.SkipPageException;

public class CapturePage {

	private static final String CAPTURE_CONTEXT_REQUEST_ATTRIBUTE_NAME = CapturePage.class.getName()+".captureContext";

	private static final boolean CONCURRENT_TRAVERSALS_ENABLED = true;

	private static final boolean DEBUG = false;

	/**
	 * Gets the capture context or <code>null</code> if none occurring.
	 */
	public static CapturePage getCaptureContext(ServletRequest request) {
		return (CapturePage)request.getAttribute(CAPTURE_CONTEXT_REQUEST_ATTRIBUTE_NAME);
	}

	/**
	 * Captures a page.
	 * The capture is always done with a request method of "GET", even when the enclosing request is a different method.
	 * Also validates parent-child and child-parent relationships if the other related pages happened to already be captured and cached.
	 *
	 * TODO: Within the scope of one request and cache, avoid capturing the same page at the same time (CurrencyLimiter applied to sub requests), is there a reasonable way to catch deadlock conditions?
	 *
	 * @param level  The minimum page capture level, note that a higher level might be substituted, such as a META capture in place of a PAGE request.
	 */
	public static Page capturePage(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		PageRef pageRef,
		CaptureLevel level
	) throws ServletException, IOException {
		return capturePage(
			servletContext,
			request,
			response,
			pageRef,
			level,
			CapturePageCacheFilter.getCache(request)
		);
	}

	private static Page capturePage(
		final ServletContext servletContext,
		final HttpServletRequest request,
		final HttpServletResponse response,
		PageRef pageRef,
		CaptureLevel level,
		PageCache cache
	) throws ServletException, IOException {
		NullArgumentException.checkNotNull(level, "level");

		// Don't use cache for full body captures
		boolean useCache = level != CaptureLevel.BODY;

		// cacheKey will be null when this capture is not to be cached
		final PageCache.Key cacheKey;
		Page capturedPage;
		if(useCache) {
			// Check the cache
			cacheKey = new PageCache.Key(pageRef, level);
			capturedPage = cache.get(cacheKey);
			// Set useCache = false to not put back into the cache unnecessarily below
			useCache = capturedPage == null;
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
		} else {
			// Body capture, performance is not the main objective, perform full child and parent verifications,
			// this will mean a "View All" will perform thorough verifications.
			if(level == CaptureLevel.BODY) {
				PageImpl.fullVerifyParentChild(servletContext, request, response, capturedPage);
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
	public static Page capturePage(
		PageRef pageRef,
		CaptureLevel level
	) throws ServletException, IOException {
		return capturePage(
			PageContext.getServletContext(),
			PageContext.getRequest(),
			PageContext.getResponse(),
			pageRef,
			level
		);
	}

	/**
	 * Captures multiple pages.
	 *
	 * @param  pageRefs  The pages that should be captured.  This set will be iterated only once during this operation.
	 *
	 * @return  map from pageRef to page, with iteration order equal to the pageRefs set.
	 *
	 * @see  #capturePage(javax.servlet.ServletContext, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, com.semanticcms.core.model.PageRef, com.semanticcms.core.servlet.CaptureLevel)
	 */
	public static Map<PageRef,Page> capturePages(
		final ServletContext servletContext,
		final HttpServletRequest request,
		final HttpServletResponse response,
		Set<? extends PageRef> pageRefs,
		final CaptureLevel level
	) throws ServletException, IOException {
		int size = pageRefs.size();
		if(size == 0) {
			return Collections.emptyMap();
		} else if(size == 1) {
			PageRef pageRef = pageRefs.iterator().next();
			return Collections.singletonMap(
				pageRef,
				capturePage(servletContext, request, response, pageRef, level)
			);
		} else {
			final PageCache cache = CapturePageCacheFilter.getCache(request);
			Map<PageRef,Page> results = new LinkedHashMap<PageRef,Page>(size * 4/3 + 1);
			List<PageRef> notCachedList = new ArrayList<PageRef>(size);
			if(level != CaptureLevel.BODY) {
				// Check cache before queuing on different threads, building list of those not in cache
				for(PageRef pageRef : pageRefs) {
					Page page = cache.get(pageRef, level);
					if(page != null) {
						// Use cached value
						results.put(pageRef, page);
					} else {
						// Will capture below
						notCachedList.add(pageRef);
					}
				}
			} else {
				notCachedList.addAll(pageRefs);
			}

			int notCachedSize = notCachedList.size();
			if(
				notCachedSize > 1
				&& CountConcurrencyFilter.useConcurrentSubrequests(request)
			) {
				// Concurrent implementation
				final TempFileList tempFileList = TempFileContext.getTempFileList(request);
				final HttpServletRequest threadSafeReq = new UnmodifiableCopyHttpServletRequest(request);
				final HttpServletResponse threadSafeResp = new UnmodifiableCopyHttpServletResponse(response);
				// Create the tasks
				List<Callable<Page>> tasks = new ArrayList<Callable<Page>>(notCachedSize);
				for(int i=0; i<notCachedSize; i++) {
					final PageRef pageRef = notCachedList.get(i);
					tasks.add(
						new Callable<Page>() {
							@Override
							public Page call() throws ServletException, IOException {
								return capturePage(
									servletContext,
									new HttpServletSubRequest(threadSafeReq),
									new HttpServletSubResponse(threadSafeResp, tempFileList),
									pageRef,
									level,
									cache
								);
							}
						}
					);
				}
				List<Page> notCachedResults;
				try {
					notCachedResults = SemanticCMS.getInstance(servletContext).getExecutors().getPerProcessor().callAll(tasks);
				} catch(InterruptedException e) {
					// Restore the interrupted status
					Thread.currentThread().interrupt();
					throw new ServletException(e);
				} catch(ExecutionException e) {
					Throwable cause = e.getCause();
					if(cause instanceof RuntimeException) throw (RuntimeException)cause;
					if(cause instanceof ServletException) throw (ServletException)cause;
					if(cause instanceof IOException) throw (IOException)cause;
					throw new ServletException(cause);
				}
				for(int i=0; i<notCachedSize; i++) {
					results.put(
						notCachedList.get(i),
						notCachedResults.get(i)
					);
				}
			} else {
				// Sequential implementation
				for(PageRef pageRef : notCachedList) {
					results.put(
						pageRef,
						capturePage(servletContext, request, response, pageRef, level, cache)
					);
				}
			}
			return Collections.unmodifiableMap(results);
		}
	}

	/**
	 * Captures multiple pages in the current page context.
	 *
	 * @see  #capturePages(javax.servlet.ServletContext, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, java.lang.Iterable, com.semanticcms.core.servlet.CaptureLevel)
	 * @see  PageContext
	 */
	public static Map<PageRef,Page> capturePages(
		Set<? extends PageRef> pageRefs,
		CaptureLevel level
	) throws ServletException, IOException {
		return capturePages(
			PageContext.getServletContext(),
			PageContext.getRequest(),
			PageContext.getResponse(),
			pageRefs,
			level
		);
	}

	public static interface TraversalEdges {
		/**
		 * Gets the child pages to consider for the given page during a traversal.
		 * This will only be called once per page per traversal.
		 * The returned collection will be iterated at most once.
		 * The returned collection will be iterated fully unless a traversal returns a value;
		 * TODO: Make this Iterable?
		 */
		Collection<PageRef> getEdges(Page page);
	}

	public static interface EdgeFilter {
		/**
		 * Each edge returned is filtered through this, must return true for the
		 * edge to be considered.  This filter is not called when the edge has
		 * already been visited, however it might be called more than once during
		 * some concurrent implementations.
		 */
		boolean applyEdge(PageRef edge);
	}

	public static interface PageHandler<T> {
		/**
		 * Called after page captured but before or after children captured.
		 *
		 * @return non-null value to terminate the traversal and return this value
		 */
		T handlePage(Page page) throws ServletException, IOException;
	}

	/**
	 * @see  #traversePagesAnyOrder(javax.servlet.ServletContext, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, com.semanticcms.core.model.Page, com.semanticcms.core.servlet.CaptureLevel, com.semanticcms.core.servlet.CapturePage.PageHandler, com.semanticcms.core.servlet.CapturePage.TraversalEdges, com.semanticcms.core.servlet.CapturePage.EdgeFilter)
	 */
	public static <T> T traversePagesAnyOrder(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		PageRef rootRef,
		CaptureLevel level,
		PageHandler<? extends T> pageHandler,
		TraversalEdges edges,
		EdgeFilter edgeFilter
	) throws ServletException, IOException {
		return traversePagesAnyOrder(
			servletContext,
			request,
			response,
			CapturePage.capturePage(
				servletContext,
				request,
				response,
				rootRef,
				level
			),
			level,
			pageHandler,
			edges,
			edgeFilter
		);
	}

	/**
	 * <p>
	 * Performs potentially concurrent traversal of the pages in any order.
	 * Each page is only visited once.
	 * </p>
	 * <p>
	 * This may at times appear to give results in a predictable order, but this must not be relied upon.
	 * For example, with all items already in cache it might end up giving results in a breadth-first order,
	 * whereas the same situation on a single-CPU system might end up in a depth-first order.  The ordering
	 * is not guaranteed in any way and should not be relied upon.
	 * </p>
	 * <p>
	 * pageHandler, edges, and edgeFilter are all called on the main thread (the thread invoking this method).
	 * <p>
	 * Returns when the first pageHandler returns a non-null object.
	 * Once a pageHandler returns non-null, no other pageHandler,
	 * edges, or edgeFilter will be called.
	 * </p>
	 * <p>
	 * Due to pageHandlers, edges, and edgeFilter all being called on the main thread, slow implementations
	 * of these methods may limit effective concurrency.  A future improvement might be to allow for concurrent
	 * execution of handlers.
	 * </p>
	 * <p>
	 * If a page is already in the cache, it is fetched directly instead of passed-off to a separate
	 * thread for capture.  Thus, if all is cached, this method will not perform with any concurrency.
	 * </p>
	 *
	 * @param level        The captureLevel.  A higher captureLevel may be returned when it is available, such
	 *                     as a META capture in place of a PAGE request.
	 *
	 * @param pageHandler  Optional, null when not needed, called before a page visits it's edges.
	 *                     If returns a non-null object, the traversal is terminated and the provided object
	 *                     is returned.
	 *
	 * @param edges        Provides the set of pages to looked from the given page.  Any edge provided that
	 *                     has already been visited will not be visited again.
	 *
	 * @param edgeFilter   Optional, null when not needed and will match all edges.
	 */
	public static <T> T traversePagesAnyOrder(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Page root,
		CaptureLevel level,
		PageHandler<? extends T> pageHandler,
		TraversalEdges edges,
		EdgeFilter edgeFilter
	) throws ServletException, IOException {
		PageCache cache = level == CaptureLevel.BODY ? null : CapturePageCacheFilter.getCache(request);
		if(
			CONCURRENT_TRAVERSALS_ENABLED
			&& CountConcurrencyFilter.useConcurrentSubrequests(request)
		) {
			return traversePagesAnyOrderConcurrent(
				servletContext,
				request,
				response,
				root,
				level,
				pageHandler,
				edges,
				edgeFilter,
				cache
			);
		} else {
			return traversePagesDepthFirstRecurseSequential(
				servletContext,
				request,
				response,
				root,
				level,
				pageHandler,
				edges,
				edgeFilter,
				null,
				TempFileContext.getTempFileList(request),
				cache,
				new HashSet<PageRef>()
			);
		}
	}

	private static <T> T traversePagesAnyOrderConcurrent(
		final ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Page page,
		final CaptureLevel level,
		PageHandler<? extends T> pageHandler,
		TraversalEdges edges,
		EdgeFilter edgeFilter,
		final PageCache cache
	) throws ServletException, IOException {
		// Created when first needed to avoid the overhead when fully operating from cache
		HttpServletRequest threadSafeReq = null;
		HttpServletResponse threadSafeResp = null;
		// Find the executor
		final Executor concurrentSubrequestExecutor;
		final int preferredConcurrency;
		{ // Scoping block
			final Executors executors = SemanticCMS.getInstance(servletContext).getExecutors();
			concurrentSubrequestExecutor = executors.getPerProcessor();
			preferredConcurrency = executors.getPreferredConcurrency();
			assert preferredConcurrency > 1 : "Single-CPU systems should never make it to this concurrent implementation";
		}
		final TempFileList tempFileList = TempFileContext.getTempFileList(request);

		int maxSize = 0;

		// The which pages have been visited
		final Set<PageRef> visited = new HashSet<PageRef>();
		// The pages that are currently ready for processing
		final List<Page> readyPages = new ArrayList<Page>();
		// Track which futures have been completed (callable put itself here once done)
		final BlockingQueue<PageRef> finishedFutures = new ArrayBlockingQueue<PageRef>(preferredConcurrency);
		// Does not immediately submit to the executor, waits until the readyPages are exhausted
		final Queue<PageRef> edgesToAdd = new LinkedList<PageRef>();
		// The futures are queued, active, or finished but not yet processed by main thread
		final Map<PageRef,Future<Page>> futures = new HashMap<PageRef,Future<Page>>(preferredConcurrency * 4/3+1);
		try {
			// Kick it off
			visited.add(page.getPageRef());
			readyPages.add(page);
			do {
				// Handle all the ready pages (note: readyPages might grow during this iteration so checking size each iteration)
				for(int i = 0; i < readyPages.size(); i++) {
					Page readyPage = readyPages.get(i);
					if(pageHandler != null) {
						T result = pageHandler.handlePage(readyPage);
						if(result != null) {
							return result;
						}
					}
					// Add any children not yet visited
					for(PageRef edge : edges.getEdges(readyPage)) {
						if(
							!visited.contains(edge)
							&& (
								edgeFilter == null
								|| edgeFilter.applyEdge(edge)
							)
						) {
							visited.add(edge);
							// Check cache before going to concurrency
							Page cached;
							if(level == CaptureLevel.BODY) {
								cached = null;
							} else {
								cached = cache.get(edge, level);
							}
							if(cached != null) {
								readyPages.add(cached);
							} else {
								edgesToAdd.add(edge);
							}
						}
					}
				}
				readyPages.clear();

				// Run on this thread if there is only one
				if(futures.isEmpty() && edgesToAdd.size() == 1) {
					if(DEBUG) System.err.println("There is only one, running on current thread");
					readyPages.add(
						capturePage(
							servletContext,
							request,
							response,
							edgesToAdd.remove(),
							level,
							cache
						)
					);
				} else {
					if(!edgesToAdd.isEmpty()) {
						if(threadSafeReq == null) {
							threadSafeReq = new UnmodifiableCopyHttpServletRequest(request);
							threadSafeResp = new UnmodifiableCopyHttpServletResponse(response);
						}
						final HttpServletRequest finalThreadSafeReq = threadSafeReq;
						final HttpServletResponse finalThreadSafeResp = threadSafeResp;
						// Submit to the futures, but only up to preferredConcurrency
						while(
							futures.size() < preferredConcurrency
							&& !edgesToAdd.isEmpty()
						) {
							final PageRef edge = edgesToAdd.remove();
							futures.put(
								edge,
								concurrentSubrequestExecutor.submit(
									new Callable<Page>() {
										@Override
										public Page call() throws ServletException, IOException, InterruptedException {
											try {
												return capturePage(
													servletContext,
													new HttpServletSubRequest(finalThreadSafeReq),
													new HttpServletSubResponse(finalThreadSafeResp, tempFileList),
													edge,
													level,
													cache
												);
											} finally {
												// This one is ready now
												// There should always be enough room in the queue since the futures are limited going in
												finishedFutures.add(edge);
											}
										}
									}
								)
							);
						}
						if(DEBUG) {
							int futuresSize = futures.size();
							int edgesToAddSize = edgesToAdd.size();
							int size = futuresSize + edgesToAddSize;
							if(size > maxSize) {
								System.err.println("futures.size()=" + futuresSize + ", edgesToAdd.size()=" + edgesToAddSize);
								maxSize = size;
							}
						}
					}
					// Continue until no more futures
					if(!futures.isEmpty()) {
						// wait until a result is available
						readyPages.add(
							futures.remove(
								finishedFutures.take()
							).get()
						);
					}
				}
			} while(!readyPages.isEmpty());
			// Traversal over, not found
			return null;
		} catch(InterruptedException e) {
			// Restore the interrupted status
			Thread.currentThread().interrupt();
			throw new ServletException(e);
		} catch(ExecutionException e) {
			Throwable cause = e.getCause();
			if(cause instanceof RuntimeException) throw (RuntimeException)cause;
			if(cause instanceof ServletException) throw (ServletException)cause;
			if(cause instanceof IOException) throw (IOException)cause;
			throw new ServletException(cause);
		} finally {
			// Always cancel unfinished futures on the way out, but do not delay for any in progress
			if(!futures.isEmpty()) {
				if(DEBUG) System.err.println("Canceling " + futures.size() + " futures");
				for(Future<Page> future : futures.values()) {
					future.cancel(false);
				}
			}
		}
	}

	/**
	 * @see  #traversePagesDepthFirst(javax.servlet.ServletContext, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, com.semanticcms.core.model.Page, com.semanticcms.core.servlet.CaptureLevel, com.semanticcms.core.servlet.CapturePage.PageHandler, com.semanticcms.core.servlet.CapturePage.TraversalEdges, com.semanticcms.core.servlet.CapturePage.PageHandler)
	 */
	public static <T> T traversePagesDepthFirst(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		PageRef rootRef,
		CaptureLevel level,
		PageHandler<? extends T> preHandler,
		TraversalEdges edges,
		EdgeFilter edgeFilter,
		PageHandler<? extends T> postHandler
	) throws ServletException, IOException {
		return traversePagesDepthFirst(
			servletContext,
			request,
			response,
			CapturePage.capturePage(
				servletContext,
				request,
				response,
				rootRef,
				level
			),
			level,
			preHandler,
			edges,
			edgeFilter,
			postHandler
		);
	}

	/**
	 * <p>
	 * Performs a consistent-ordered, potentially concurrent, depth-first traversal of the pages.
	 * Each page is only visited once.
	 * </p>
	 * <p>
	 * preHandler, edges, edgeFilter, and postHandler are all called on the main thread (the thread invoking this method).
	 * <p>
	 * Returns when the first preHandler or postHandler returns a non-null object.
	 * Once a preHandler or postHandler returns non-null, no other preHandler,
	 * edges, edgeFilter, or postHandler will be called.
	 * </p>
	 * <p>
	 * Due to preHandlers, edges, edgeFilter, and postHandler all being called on the main thread, slow implementations
	 * of these methods may limit effective concurrency.  A future improvement might be to allow for concurrent
	 * execution of handlers.
	 * </p>
	 * <p>
	 * If a page is already in the cache, it is fetched directly instead of passed-off to a separate
	 * thread for capture.  Thus, if all is cached, this method will not perform with any concurrency.
	 * </p>
	 *
	 * @param level        The captureLevel.  A higher captureLevel may be returned when it is available, such
	 *                     as a META capture in place of a PAGE request.
	 *
	 * @param preHandler   Optional, null when not needed, called before a page visits it's edges.
	 *                     If returns a non-null object, the traversal is terminated and the provided object
	 *                     is returned.
	 *
	 * @param edges        Provides the set of pages to looked from the given page.  Any edge provided that
	 *                     has already been visited will not be visited again.
	 *
	 * @param edgeFilter   Optional, null when not needed and will match all edges.
	 *
	 * @param postHandler  Optional, null when not needed, called before a page visits it's edges.
	 *                     If returns a non-null object, the traversal is terminated and the provided object
	 *                     is returned.
	 */
	public static <T> T traversePagesDepthFirst(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Page root,
		CaptureLevel level,
		PageHandler<? extends T> preHandler,
		TraversalEdges edges,
		EdgeFilter edgeFilter,
		PageHandler<? extends T> postHandler
	) throws ServletException, IOException {
		PageCache cache = level == CaptureLevel.BODY ? null : CapturePageCacheFilter.getCache(request);
		if(
			CONCURRENT_TRAVERSALS_ENABLED
			&& CountConcurrencyFilter.useConcurrentSubrequests(request)
		) {
			return traversePagesDepthFirstConcurrent(
				servletContext,
				request,
				response,
				root,
				level,
				preHandler,
				edges,
				edgeFilter,
				postHandler,
				cache
			);
		} else {
			return traversePagesDepthFirstRecurseSequential(
				servletContext,
				request,
				response,
				root,
				level,
				preHandler,
				edges,
				edgeFilter,
				postHandler,
				TempFileContext.getTempFileList(request),
				cache,
				new HashSet<PageRef>()
			);
		}
	}

	/**
	 * Simple sequential implementation.
	 */
	private static <T> T traversePagesDepthFirstRecurseSequential(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Page page,
		CaptureLevel level,
		PageHandler<? extends T> preHandler,
		TraversalEdges edges,
		EdgeFilter edgeFilter,
		PageHandler<? extends T> postHandler,
		TempFileList tempFileList,
		PageCache cache,
		Set<PageRef> visited
	) throws ServletException, IOException {
		if(!visited.add(page.getPageRef())) throw new AssertionError();
		if(preHandler != null) {
			T result = preHandler.handlePage(page);
			if(result != null) return result;
		}
		for(PageRef edge : edges.getEdges(page)) {
			if(
				!visited.contains(edge)
				&& (
					edgeFilter == null
					|| edgeFilter.applyEdge(edge)
				)
			) {
				T result = traversePagesDepthFirstRecurseSequential(
					servletContext,
					request,
					response,
					CapturePage.capturePage(
						servletContext,
						request,
						response,
						edge,
						level,
						cache
					),
					level,
					preHandler,
					edges,
					edgeFilter,
					postHandler,
					tempFileList,
					cache,
					visited
				);
				if(result != null) return result;
			}
		}
		if(postHandler != null) {
			T result = postHandler.handlePage(page);
			if(result != null) return result;
		}
		return null;
	}

	private static <T> T traversePagesDepthFirstConcurrent(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Page page,
		CaptureLevel level,
		final PageHandler<? extends T> preHandler,
		final TraversalEdges edges,
		final EdgeFilter edgeFilter,
		final PageHandler<? extends T> postHandler,
		PageCache cache
	) throws ServletException, IOException {
		// All of the edges visited or already set as a next
		final Set<PageRef> visited = new HashSet<PageRef>();
		// The already resolved parents, used for postHandler
		final List<Page> parents = new ArrayList<Page>();
		// The next node that is to be processed, highest on list is active
		final List<PageRef> nexts = new ArrayList<PageRef>();
		// Those that are to be done after what is next
		final List<Iterator<PageRef>> afters = new ArrayList<Iterator<PageRef>>();
		// The set of nodes we've received but are not yet ready to process
		final Map<PageRef,Page> received = new HashMap<PageRef,Page>();
		// Caches the results of edges call, to fit within specification that it will only be called once per page.
		// This also prevents the chance that caller can give different results or change the collection during traversal.
		final TraversalEdges cachedEdges = new TraversalEdges() {
			private final Map<PageRef,Collection<PageRef>> edgesCache = new HashMap<PageRef,Collection<PageRef>>();
			@Override
			public Collection<PageRef> getEdges(Page page) {
				PageRef pageRef = page.getPageRef();
				Collection<PageRef> result = edgesCache.get(pageRef);
				if(result == null) {
					result = edges.getEdges(page);
					edgesCache.put(pageRef, AoCollections.unmodifiableCopyCollection(result));
				}
				return result;
			}
		};

		// Kick it off
		{
			PageRef pageRef = page.getPageRef();
			visited.add(pageRef);
			nexts.add(pageRef);
			Iterator<PageRef> empty = AoCollections.emptyIterator(); // Java 1.7: Use java.util.Collections.emptyIterator()
			afters.add(empty);
		}
		// TODO: Have traversePagesAnyOrderConcurrent return in approximate depth-first ordering to get results sooner
		// TODO: Have it consider what is "next" here, in preference, move to front of next tasks if not already a queued future.
		// TODO: and exact depth-first order when it's serving fully from cache
		T result = traversePagesAnyOrderConcurrent(
			servletContext,
			request,
			response,
			page,
			level,
			new PageHandler<T>() {
				private PageRef findNext(Iterator<PageRef> after) {
					while(after.hasNext()) {
						PageRef possNext = after.next();
						if(
							!visited.contains(possNext)
							&& (
								edgeFilter == null
								|| edgeFilter.applyEdge(possNext)
							)
						) {
							return possNext;
						}
					}
					return null;
				}

				@Override
				public T handlePage(Page page) throws ServletException, IOException {
					PageRef pageRef = page.getPageRef();
					// page and pageRef match, but sometimes we have a pageRef with a null page (indicating unknown)
					int index = nexts.size() - 1;
					if(pageRef.equals(nexts.get(index))) {
						do {
							//System.out.println("pre.: " + pageRef);
							if(preHandler != null) {
								T preResult = preHandler.handlePage(page);
								if(preResult != null) return preResult;
							}
							// Find the first edge that we still need, if any
							Iterator<PageRef> after = cachedEdges.getEdges(page).iterator();
							PageRef next = findNext(after);
							if(next != null) {
								//System.out.println("next: " + next);
								// Have at least one child, not ready for our postHandler yet
								// Make sure we only look for a given edge once
								visited.add(next);
								// Push child
								parents.add(page);
								nexts.add(next);
								afters.add(after);
								index++;
								page = null;
								pageRef = next;
							} else {
								// No children to wait for, run postHandlers and move to next
								while(true) {
									//System.out.println("post: " + pageRef);
									if(postHandler != null) {
										T postResult = postHandler.handlePage(page);
										if(postResult != null) return postResult;
									}
									next = findNext(afters.get(index));
									if(next != null) {
										//System.out.println("next: " + next);
										// Make sure we only look for a given edge once
										visited.add(next);
										nexts.set(index, next);
										page = null;
										pageRef = next;
										break;
									} else {
										// Pop parent
										afters.remove(index);
										nexts.remove(index);
										index--;
										if(index < 0) {
											// Nothing left to check, all postHandlers done
											return null;
										} else {
											page = parents.remove(index);
											pageRef = page.getPageRef();
										}
									}
								}
							}
						} while(
							page != null
							|| (page = received.remove(pageRef)) != null
						);
					} else {
						received.put(pageRef, page);
						System.out.println("Received " + pageRef + ", size = " + received.size());
					}
					return null;
				}
			},
			cachedEdges,
			edgeFilter,
			cache
		);
		assert result != null || parents.isEmpty();
		assert result != null || nexts.isEmpty();
		assert result != null || afters.isEmpty();
		assert result != null || received.isEmpty();
		return result;
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
