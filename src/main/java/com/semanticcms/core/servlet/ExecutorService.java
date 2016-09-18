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

import com.aoindustries.servlet.filter.FunctionContextCallable;
import com.aoindustries.servlet.filter.FunctionContextRunnable;
import com.aoindustries.util.i18n.I18nThreadLocalCallable;
import com.aoindustries.util.i18n.I18nThreadLocalRunnable;
import java.util.concurrent.Callable;

/**
 * Per-context executor service for concurrent processing.
 * Passes the following ThreadLocal-based items to submitted tasks:
 * <ul>
 *   <li>Internationalization context (via parent class): {@link I18nThreadLocalCallable} and {@link I18nThreadLocalRunnable}</li>
 *   <li>FunctionContext: {@link FunctionContextCallable} and {@link FunctionContextRunnable}</li>
 *   <li>TODO: PageContext</li>
 * </ul>
 */
public class ExecutorService extends com.aoindustries.util.concurrent.ExecutorService {

	/**
	 * Should only be created by SemanticCMS to control life cycle.
	 */
	ExecutorService() {
	}

	@Override
	protected <T> Callable<T> wrap(Callable<T> task) {
		return new FunctionContextCallable<T>(
			super.wrap(task)
		);
	}

	@Override
	protected Runnable wrap(Runnable task) {
		return new FunctionContextRunnable(
			super.wrap(task)
		);
	}
}
