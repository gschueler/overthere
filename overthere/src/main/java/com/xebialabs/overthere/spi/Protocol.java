/*
 * This file is part of Overthere.
 * 
 * Overthere is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Overthere is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Overthere.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.xebialabs.overthere.spi;

import com.xebialabs.overthere.ConnectionOptions;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Annotation used to specify that a class is an Overthere protocol. It will be registered on startup of the application and will register under the provided
 * name. It must also have a two-arg constructor ({@link String}, {@link ConnectionOptions}) and implement the {@link OverthereConnectionBuilder} interface.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Protocol {
	String name();
}

