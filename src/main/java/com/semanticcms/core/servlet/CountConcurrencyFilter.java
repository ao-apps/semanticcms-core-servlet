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

import com.aoindustries.util.concurrent.Executor;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;

/**
 * Determines if concurrent processing is recommended for the current request.
 *
 * @see  CountConcurrencyFilter
 */
public class CountConcurrencyFilter extends com.aoindustries.servlet.filter.CountConcurrencyFilter {

	private static final String CONCURRENT_PROCESSING_RECOMMENDED_REQUEST_ATTRIBUTE_NAME = CountConcurrencyFilter.class.getName()+".concurrentProcessingRecommended";
	private static final String CONCURRENT_SUBREQUESTS_RECOMMENDED_REQUEST_ATTRIBUTE_NAME = CountConcurrencyFilter.class.getName()+".concurrentSubrequestsRecommended";

	private boolean concurrentSubrequests;
	private int preferredConcurrency;

	@Override
	public void init(FilterConfig config) {
		super.init(config);
		SemanticCMS semanticCMS = SemanticCMS.getInstance(config.getServletContext());
		concurrentSubrequests = semanticCMS.getConcurrentSubrequests();
		preferredConcurrency = semanticCMS.getExecutors().getPreferredConcurrency();
	}

	@Override
	protected void onConcurrencySet(ServletRequest request, int newConcurrency) {
		assert request.getAttribute(CONCURRENT_PROCESSING_RECOMMENDED_REQUEST_ATTRIBUTE_NAME) == null;
		assert request.getAttribute(CONCURRENT_SUBREQUESTS_RECOMMENDED_REQUEST_ATTRIBUTE_NAME) == null;

		boolean concurrentProcessingRecommended = (newConcurrency < preferredConcurrency);
		boolean concurrentSubrequestsRecommended = concurrentProcessingRecommended && concurrentSubrequests;

		request.setAttribute(CONCURRENT_PROCESSING_RECOMMENDED_REQUEST_ATTRIBUTE_NAME, concurrentProcessingRecommended);
		request.setAttribute(CONCURRENT_SUBREQUESTS_RECOMMENDED_REQUEST_ATTRIBUTE_NAME, concurrentSubrequestsRecommended);
	}

	@Override
	protected void onConcurrencyRemove(ServletRequest request) {
		request.removeAttribute(CONCURRENT_PROCESSING_RECOMMENDED_REQUEST_ATTRIBUTE_NAME);
		request.removeAttribute(CONCURRENT_SUBREQUESTS_RECOMMENDED_REQUEST_ATTRIBUTE_NAME);
	}

	/**
	 * Checks if concurrent processing is recommended.
	 * Recommended when the overall request concurrency is less than the preferred concurrency.
	 * This value will remain consistent throughout the processing of a request.
	 * 
	 * @see  Executors#getPreferredConcurrency()
	 */
	public static boolean isConcurrentProcessingRecommended(ServletRequest request) {
		Boolean concurrentProcessingRecommended = (Boolean)request.getAttribute(CONCURRENT_PROCESSING_RECOMMENDED_REQUEST_ATTRIBUTE_NAME);
		if(concurrentProcessingRecommended == null) throw new IllegalStateException(CountConcurrencyFilter.class.getName() + " filter not active on request");
		return concurrentProcessingRecommended;
	}

	/**
	 * Determines if concurrent subrequests are currently allowed and advised for the given request.
	 * <ol>
	 * <li>Concurrent subrequests must be enabled: {@link SemanticCMS#getConcurrentSubrequests()}</li>
	 * <li>Request concurrency must be less than the executor per-processor thread limit: {@link #isConcurrentProcessingRecommended(javax.servlet.ServletRequest)}</li>
	 * </ol>
	 */
	public static boolean useConcurrentSubrequests(ServletRequest request) {
		Boolean concurrentSubrequestsRecommended = (Boolean)request.getAttribute(CONCURRENT_SUBREQUESTS_RECOMMENDED_REQUEST_ATTRIBUTE_NAME);
		if(concurrentSubrequestsRecommended == null) throw new IllegalStateException(CountConcurrencyFilter.class.getName() + " filter not active on request");
		return concurrentSubrequestsRecommended;
	}

	/**
	 * Gets the executor to use for per-processor tasks.
	 * If {@link #isConcurrentProcessingRecommended(javax.servlet.ServletRequest)}, is {@link Executors#getPerProcessor()},
	 * otherwise is {@link Executors#getSequential()}
	 */
	public static Executor getRecommendedExecutor(ServletContext servletContext, ServletRequest request) {
		Executors executors = SemanticCMS.getInstance(servletContext).getExecutors();
		return
			isConcurrentProcessingRecommended(request)
			? executors.getPerProcessor()
			: executors.getSequential();
	}
}
