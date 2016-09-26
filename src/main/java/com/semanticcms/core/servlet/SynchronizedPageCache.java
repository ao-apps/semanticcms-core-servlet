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
import javax.servlet.ServletException;

/**
 * A page cache that is thread safe using synchronization on the default HashMaps.
 */
class SynchronizedPageCache extends MapPageCache {

	SynchronizedPageCache() {
	}

	@Override
	synchronized Page get(Key key) {
		return super.get(key);
	}

	@Override
	synchronized void put(Key key, Page page) throws ServletException {
		super.put(key, page);
	}

	@Override
	protected void verifyAdded(Page page) throws ServletException {
		assert Thread.holdsLock(this);
		super.verifyAdded(page);
	}
}
