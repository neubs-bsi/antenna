/*
 * Copyright (c) Bosch Software Innovations GmbH 2016-2017.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.antenna.api.workflow;

import org.eclipse.sw360.antenna.api.exceptions.AntennaException;
import org.eclipse.sw360.antenna.model.artifact.Artifact;

import java.util.Collection;

public abstract class AbstractProcessor extends ConfigurableWorkflowItem implements IWorkflowable {
    final public WorkflowStepResult process(ProcessingState previousState) throws AntennaException {
        WorkflowStepResult newResult = new WorkflowStepResult(process(previousState.getArtifacts()));
        return postProcessResult(newResult);
    }

    public abstract Collection<Artifact> process(Collection<Artifact> intermediates) throws AntennaException;

    public WorkflowStepResult postProcessResult(WorkflowStepResult result) {
        return result;
    }
}
