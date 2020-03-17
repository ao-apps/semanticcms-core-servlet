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

import com.aoindustries.util.concurrent.ThreadLocalsCallable;
import java.util.concurrent.Callable;

/**
 * Maintains current page context for the provided callable.
 */
public class PageContextCallable<T> extends ThreadLocalsCallable<T> {

	static final ThreadLocal<?>[] threadLocals = {
		PageContext.servletContext,
		PageContext.request,
		PageContext.response,
		PageContext.out
	};

	public PageContextCallable(Callable<T> task) {
		super(task, threadLocals);
	}
}
