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
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.servlet.impl.PageImpl;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletException;

/**
 * A page cache implemented via a map.
 */
abstract class MapPageCache extends PageCache {

	private final Map<Key,Page> pageCache;

	/**
	 * Tracks which parent pages are still not verified.
	 * <ul>
	 *   <li>Key: The parent pageRef.</li>
	 *   <li>Value: The page(s) that claim the pageRef as a parent but are still not verified.</li>
	 * </ul>
	 */
	private final Map<PageRef,Set<PageRef>> unverifiedParentsByPageRef;

	/**
	 * Tracks which child pages are still not verified.
	 * <ul>
	 *   <li>Key: The child pageRef.</li>
	 *   <li>Value: The page(s) that claim the pageRef as a child but are still not verified.</li>
	 * </ul>
	 */
	private final Map<PageRef,Set<PageRef>> unverifiedChildrenByPageRef;

	MapPageCache(
		Map<Key,Page> pageCache,
		Map<PageRef,Set<PageRef>> unverifiedParentsByPageRef,
		Map<PageRef,Set<PageRef>> unverifiedChildrenByPageRef
	) {
		this.pageCache = pageCache;
		this.unverifiedParentsByPageRef = unverifiedParentsByPageRef;
		this.unverifiedChildrenByPageRef = unverifiedChildrenByPageRef;
	}

	/**
	 * Uses default HashMap implementations.
	 */
	MapPageCache() {
		this(
			new HashMap<Key,Page>(),
			VERIFY_CACHE_PARENT_CHILD_RELATIONSHIPS ? new HashMap<PageRef,Set<PageRef>>() : null,
			VERIFY_CACHE_PARENT_CHILD_RELATIONSHIPS ? new HashMap<PageRef,Set<PageRef>>() : null
		);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Page get(Key key) {
		Page page = pageCache.get(key);
		if(page == null && key.level == CaptureLevel.PAGE) {
			// Look for meta in place of page
			page = pageCache.get(new Key(key.pageRef, CaptureLevel.META));
		}
		return page;
	}

	private static void addToSet(Map<PageRef,Set<PageRef>> map, PageRef key, PageRef pageRef) {
		Set<PageRef> pageRefs = map.get(key);
		if(pageRefs == null) {
			map.put(key, Collections.singleton(pageRef));
		} else if(pageRefs.size() == 1) {
			pageRefs = new HashSet<PageRef>(pageRefs);
			pageRefs.add(pageRef);
			map.put(key, pageRefs);
		} else {
			pageRefs.add(pageRef);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	void put(Key key, Page page) throws ServletException {
		// Check if found in other level, this is used to avoid verifying twice
		Page otherLevelPage = pageCache.get(
			new Key(key.pageRef, key.level==CaptureLevel.PAGE ? CaptureLevel.META : CaptureLevel.PAGE)
		);
		// Add to cache, verify if this page not yet put into cache
		if(pageCache.put(key, page) == null) {
			// Was added, now avoid verifying twice typically.
			// In the race condition where both levels check null then are added concurrently, this will verify twice
			// rather than verify none.
			if(VERIFY_CACHE_PARENT_CHILD_RELATIONSHIPS) {
				if(otherLevelPage == null) verifyAdded(page);
			}
		}
	}

	protected void verifyAdded(Page page) throws ServletException {
		assert VERIFY_CACHE_PARENT_CHILD_RELATIONSHIPS;
		final PageRef pageRef = page.getPageRef();
		Set<PageRef> parentPages = null; // Set when first needed
		Set<PageRef> childPages = null; // Set when first needed
		// Verify parents that happened to already be cached
		if(!page.getAllowParentMismatch()) {
			parentPages = page.getParentPages();
			for(PageRef parentRef : parentPages) {
				// Can't verify parent reference to missing book
				if(parentRef.getBook() != null) {
					// Check if parent in cache
					Page parentPage = get(parentRef, CaptureLevel.PAGE);
					if(parentPage != null) {
						PageImpl.verifyChildToParent(pageRef, parentRef, parentPage.getChildPages());
					} else {
						addToSet(unverifiedParentsByPageRef, parentRef, pageRef);
					}
				}
			}
		}
		// Verify children that happened to already be cached
		if(!page.getAllowChildMismatch()) {
			childPages = page.getChildPages();
			for(PageRef childRef : childPages) {
				// Can't verify child reference to missing book
				if(childRef.getBook() != null) {
					// Check if child in cache
					Page childPage = get(childRef, CaptureLevel.PAGE);
					if(childPage != null) {
						PageImpl.verifyParentToChild(pageRef, childRef, childPage.getParentPages());
					} else {
						addToSet(unverifiedChildrenByPageRef, childRef, pageRef);
					}
				}
			}
		}
		// Verify any pages that have claimed this page as their parent and are not yet verified
		Set<PageRef> unverifiedParents = unverifiedParentsByPageRef.remove(pageRef);
		if(unverifiedParents != null) {
			if(childPages == null) childPages = page.getChildPages();
			for(PageRef unverifiedParent : unverifiedParents) {
				PageImpl.verifyChildToParent(unverifiedParent, pageRef, childPages);
			}
		}
		// Verify any pages that have claimed this page as their child and are not yet verified
		Set<PageRef> unverifiedChildren = unverifiedChildrenByPageRef.remove(pageRef);
		if(unverifiedChildren != null) {
			if(parentPages == null) parentPages = page.getParentPages();
			for(PageRef unverifiedChild : unverifiedChildren) {
				PageImpl.verifyParentToChild(unverifiedChild, pageRef, parentPages);
			}
		}
	}
}
