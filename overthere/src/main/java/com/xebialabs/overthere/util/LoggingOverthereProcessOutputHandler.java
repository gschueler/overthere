/* License added by: GRADLE-LICENSE-PLUGIN
 *
 * Copyright 2008-2012 XebiaLabs
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

package com.xebialabs.overthere.util;

import com.xebialabs.overthere.OverthereProcessOutputHandler;
import org.slf4j.Logger;

/**
 * Implementation of the {@link OverthereProcessOutputHandler} interface that sends the output to the specified logger. Regular output will be logged at the
 * INFO level, error output at the ERROR level.
 */
public class LoggingOverthereProcessOutputHandler implements OverthereProcessOutputHandler {

	private final Logger logger;

	private LoggingOverthereProcessOutputHandler(final Logger logger) {
		this.logger = logger;
	}

	@Override
	public void handleOutputLine(String line) {
		logger.info(line);
	}

	@Override
	public void handleErrorLine(String line) {
		logger.error(line);
	}

	@Override
	public void handleOutput(char c) {
		// no-op
	}

	/**
	 * Creates a {@link LoggingOverthereProcessOutputHandler}.
	 * 
	 * @param logger
	 *            the logger to send the output to.
	 * @return the created {@link LoggingOverthereProcessOutputHandler}.
	 */
	public static LoggingOverthereProcessOutputHandler loggingHandler(final Logger logger) {
		return new LoggingOverthereProcessOutputHandler(logger);
	}

}

