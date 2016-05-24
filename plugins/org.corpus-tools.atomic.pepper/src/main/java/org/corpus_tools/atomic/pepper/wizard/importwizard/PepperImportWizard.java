/*******************************************************************************
 * Copyright 2013 Friedrich Schiller University Jena
 * Michael Gr�bsch
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package org.corpus_tools.atomic.pepper.wizard.importwizard;

import java.util.List;

import org.corpus_tools.atomic.pepper.wizard.AbstractPepperWizard;
import org.corpus_tools.atomic.pepper.wizard.PepperModuleRunnable;
import org.corpus_tools.atomic.pepper.wizard.PepperWizardPageDirectory;
import org.corpus_tools.atomic.pepper.wizard.PepperWizardPageFormat;
import org.corpus_tools.atomic.pepper.wizard.PepperWizardPageModule;
import org.corpus_tools.atomic.pepper.wizard.PepperWizardPageProperties;
import org.corpus_tools.pepper.modules.PepperImporter;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.EList;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IImportWizard;
import org.eclipse.ui.IWorkbench;

public class PepperImportWizard extends AbstractPepperWizard<PepperImporter> implements IImportWizard {
	protected String projectName;

	public PepperImportWizard() {
		super("Import via Pepper", WizardMode.IMPORT);
	}

	@Override
	public void init(IWorkbench workbench, IStructuredSelection selection) {
		initialize();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void addPages() {
		addPage(new PepperWizardPageModule<PepperImporter>(this, "selectImporter", "Select Import Module", DEFAULT_PAGE_IAMGE_DESCRIPTOR, "Select the pepper import module."));
		addPage(new PepperWizardPageFormat<PepperImporter>(this, "selectFormat", "Select Import Format", DEFAULT_PAGE_IAMGE_DESCRIPTOR, "Select the pepper import format."));
		addPage(new PepperWizardPageDirectory<PepperImporter>(this, "selectTargetPath", "Select Import Path", DEFAULT_PAGE_IAMGE_DESCRIPTOR, "Select the pepper import path."));
		addPage(new PepperWizardPageProperties<PepperImporter>(this, "selectProperties", "Select Import Properties", DEFAULT_PAGE_IAMGE_DESCRIPTOR, "Edit the pepper import module properties."));
		addPage(new PepperImportWizardPageProjectName(this, "selectProjectName", "Select Project Name", DEFAULT_PAGE_IAMGE_DESCRIPTOR));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected List<PepperImporter> resolvePepperModules(PepperModuleResolver pepperModuleResolver) {
		return pepperModuleResolver.getPepperImporters();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public EList<FormatDefinition> getSupportedFormats() {
		PepperImporter module = getPepperModule();
		return module != null ? module.getSupportedFormats() : new BasicEList<FormatDefinition>();
	}

	public String getProjectName() {
		return projectName;
	}

	public void setProjectName(String projectName) {
		this.projectName = projectName;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected IProject getProject() throws CoreException {
		return AtomicProjectService.getInstance().createIProject(getProjectName());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected PepperModuleRunnable createModuleRunnable(IProject project, boolean cancelable) {
		return new ImportModuleRunnable(this, project, cancelable);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected boolean canPerformFinish() {
		AtomicProjectService atomicProjectService = AtomicProjectService.getInstance();
		return super.canPerformFinish() && projectName != null && !atomicProjectService.isProjectExisting(projectName);
	}
}
