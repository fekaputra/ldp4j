/**
 * #-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=#
 *   This file is part of the LDP4j Project:
 *     http://www.ldp4j.org/
 *
 *   Center for Open Middleware
 *     http://www.centeropenmiddleware.com/
 * #-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=#
 *   Copyright (C) 2014 Center for Open Middleware.
 * #-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=#
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 * #-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=#
 *   Artifact    : org.ldp4j.framework:ldp4j-application-core:1.0.0-SNAPSHOT
 *   Bundle      : ldp4j-application-core-1.0.0-SNAPSHOT.jar
 * #-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=#
 */
package org.ldp4j.application.template;

import static com.google.common.base.Preconditions.checkNotNull;

import org.ldp4j.application.ext.ContainerHandler;
import org.ldp4j.application.ext.ResourceHandler;

import com.google.common.base.Objects;

final class HandlerId {

	private final String className;
	private final int systemHashCode;
	private final boolean container;

	private HandlerId(String className, int systemHashCode, boolean container) {
		this.className = className;
		this.systemHashCode = systemHashCode;
		this.container = container;
	}

	public String className() {
		return className;
	}

	public int systemHashCode() {
		return systemHashCode;
	}

	public boolean isContainer() {
		return container;
	}

	@Override
	public int hashCode() {
		return
			Objects.
				hashCode(
					this.className,this.systemHashCode,this.container);
	}

	@Override
	public boolean equals(Object obj) {
		boolean result=false;
		if(obj!=null && obj.getClass()==this.getClass()) {
			HandlerId that=(HandlerId)obj;
			result=
				Objects.equal(this.className,that.className) &&
				Objects.equal(this.systemHashCode,that.systemHashCode) &&
				Objects.equal(this.container,that.container);
		}
		return result;
	}

	@Override
	public String toString() {
		return
			Objects.
				toStringHelper(getClass()).
					add("className", this.className).
					add("systemHashCode", this.systemHashCode).
					add("container", this.container).
					toString();
	}

	public static HandlerId createId(Class<? extends ResourceHandler> handlerClass) {
		checkNotNull(handlerClass,"Resource handler class cannot be null");
		return
			new HandlerId(
				handlerClass.getCanonicalName(),
				System.identityHashCode(handlerClass),
				ContainerHandler.class.isAssignableFrom(handlerClass));
	}

}