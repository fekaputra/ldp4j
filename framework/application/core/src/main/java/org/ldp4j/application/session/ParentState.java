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
package org.ldp4j.application.session;

import static com.google.common.base.Preconditions.checkState;

import org.ldp4j.application.resource.Resource;

import com.google.common.base.Objects;

abstract class ParentState {
	
	private static final class OrphanState extends ParentState {

		@Override
		boolean isRoot(DelegatedResourceSnapshot ctx) {
			return false;
		}

		@Override
		DelegatedResourceSnapshot parent(DelegatedResourceSnapshot ctx) {
			return null;
		}

	}

	private static class PersistentParentState extends ParentState {

		private final Resource child;

		private PersistentParentState(Resource child) {
			this.child = child;
		}
		
		@Override
		boolean isRoot(DelegatedResourceSnapshot ctx) {
			return this.child.isRoot();
		}

		@Override
		DelegatedResourceSnapshot parent(DelegatedResourceSnapshot ctx) {
			checkState(!isRoot(ctx),"Resource %s does not have parent",ctx.name());
			DelegatedResourceSnapshot parent=ctx.session().resolveResource(this.child.parentId());
			ctx.setParentState(ParentState.childOf(parent));
			return parent;
		}
		
		@Override 
		public String toString() {
			return 
				Objects.
					toStringHelper(getClass()).
						add("child.id()", this.child.id()).
						toString();
		}

	}
	
	private static class SessionParentState extends ParentState {

		private final DelegatedResourceSnapshot resource;

		private SessionParentState(DelegatedResourceSnapshot parent) {
			this.resource = parent;
		}
		
		@Override
		boolean isRoot(DelegatedResourceSnapshot ctx) {
			return this.resource==null;
		}

		@Override
		DelegatedResourceSnapshot parent(DelegatedResourceSnapshot ctx) {
			checkState(!isRoot(ctx),"Resource %s does not have parent",ctx.name());
			return this.resource;
		}
		
		@Override 
		public String toString() {
			return 
				Objects.
					toStringHelper(getClass()).
						add("resource.name()", this.resource.name()).
						toString();
		}
	}

	private ParentState() {
	}

	abstract boolean isRoot(DelegatedResourceSnapshot ctx);
	
	abstract DelegatedResourceSnapshot parent(DelegatedResourceSnapshot ctx);
	
	static ParentState orphan() {
		return new OrphanState();
	}

	static ParentState childOf(DelegatedResourceSnapshot resource) {
		return new SessionParentState(resource);
	}

	static ParentState parentOf(Resource resource) {
		return new PersistentParentState(resource);
	}

}