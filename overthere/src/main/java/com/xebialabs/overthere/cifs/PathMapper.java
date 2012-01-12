/* License added by: GRADLE-LICENSE-PLUGIN
 *
 * Copyright 2008-2011 XebiaLabs
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xebialabs.overthere.cifs;

import static java.lang.String.format;
import static java.util.regex.Pattern.quote;

import java.util.Map;
import java.util.SortedMap;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;

class PathMapper {
	private static final String DRIVE_DESIGNATOR = ":";
	private static final String ADMIN_SHARE_DESIGNATOR = "$";
	private static final Pattern ADMIN_SHARE_PATTERN = Pattern.compile("[a-zA-Z]" + quote(ADMIN_SHARE_DESIGNATOR));

	private final SortedMap<String, String> sharesForPaths;
	private final Map<String, String> pathsForShares;

	@VisibleForTesting
	PathMapper(final Map<String, String> mappings) {
		// longest first, so reverse lexicographical order
		ImmutableSortedMap.Builder<String, String> sharesForPath = ImmutableSortedMap.reverseOrder();
		ImmutableMap.Builder<String, String> pathsForShare = ImmutableMap.builder();
		for (Entry<String, String> mapping : mappings.entrySet()) {
			String pathPrefixToMatch = mapping.getKey();
			String shareForPathPrefix = mapping.getValue();
			sharesForPath.put(pathPrefixToMatch.toLowerCase(), shareForPathPrefix);
			pathsForShare.put(shareForPathPrefix.toLowerCase(), pathPrefixToMatch);
		}
		this.sharesForPaths = sharesForPath.build();
		this.pathsForShares = pathsForShare.build();
	}

	/**
	 * Attempts to use provided path-to-share mappings to convert the given local path to a remotely accessible path, using the longest matching prefix if
	 * available.
	 * <p>
	 * Falls back to using administrative shares if none of the explicit mappings applies to the path to convert.
	 * 
	 * @param path
	 *            the local path to convert
	 * @return the remotely accessible path (using shares) at which the local path can be accessed using SMB
	 */
	@VisibleForTesting
	String toSharedPath(String path) {
		final String lowerCasePath = path.toLowerCase();
		// assumes correct format drive: or drive:\path
		String mappedPathPrefix = Iterables.find(sharesForPaths.keySet(), new Predicate<String>() {
			@Override
			public boolean apply(String input) {
				return lowerCasePath.startsWith(input);
			}
		}, null);
		// the share + the remainder of the path if found, otherwise the path with ':' replaced by '$'
		return ((mappedPathPrefix != null) ? sharesForPaths.get(mappedPathPrefix) + path.substring(mappedPathPrefix.length()) : path.substring(0, 1) + ADMIN_SHARE_DESIGNATOR
		        + path.substring(2));
	}

	/**
	 * @param path
	 *            the remotely accessible path to convert (minus the host name, i.e. beginning with the share)
	 * @return the local path (using drive letters) corresponding to the path that is remotely accessible using SMB
	 */
	@VisibleForTesting
	String toLocalPath(String path) {
		final String lowerCasePath = path.toLowerCase();
		// assumes correct format share or share\path
		String mappedShare = Iterables.find(pathsForShares.keySet(), new Predicate<String>() {
			@Override
			public boolean apply(String input) {
				return lowerCasePath.startsWith(input);
			}
		}, null);
		if (mappedShare != null) {
			return pathsForShares.get(mappedShare) + path.substring(mappedShare.length());
		} else if ((path.length() >= 2) && ADMIN_SHARE_PATTERN.matcher(path.substring(0, 2)).matches()) {
			return path.substring(0, 1) + DRIVE_DESIGNATOR + path.substring(2);
		} else {
			throw new IllegalArgumentException(format("Remote path name '%s' uses unrecognized (i.e. neither mapped nor administrative) share", path));
		}
	}
}