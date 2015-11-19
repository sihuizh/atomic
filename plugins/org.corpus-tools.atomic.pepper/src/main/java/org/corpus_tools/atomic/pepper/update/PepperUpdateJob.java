/*******************************************************************************
 * Copyright 2015 Friedrich-Schiller-Universität Jena,
 * Humboldt-Universität zu Berlin, INRIA
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
 *
 * Contributors:
 *     Stephan Druskat - initial API and implementation
 *     Martin Klotz - nested class {@link ModuleTableReader} initial API
 *     					and implementation
 *******************************************************************************/
package org.corpus_tools.atomic.pepper.update;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.corpus_tools.atomic.pepper.AtomicPepperOSGiConnector;
import org.corpus_tools.atomic.pepper.AtomicPepperStarter;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.ext.DefaultHandler2;

import de.hu_berlin.german.korpling.saltnpepper.pepper.connectors.PepperConnector;

/**
 * TODO Description
 * <p>
 * 
 * @author Stephan Druskat <stephan.druskat@uni-jena.de>
 */
public class PepperUpdateJob extends Job {

	/**
	 * Defines a static logger variable so that it references the XML{@link org.apache.logging.log4j.Logger} instance named "PepperUpdateJob".
	 */
	private static final Logger log = LogManager.getLogger(PepperUpdateJob.class);
	private PepperConnector pepper;
	private Map<String, Pair<String, String>> moduleTable;
	private static String MODULES_XML_PATH = null;

	
	/**
	 * @param name
	 */
	public PepperUpdateJob(String name) {
		super(name);
		MODULES_XML_PATH = setModulesXMLPath();
	}

	/**
	 * TODO: Description
	 *
	 * @return
	 */
	private String setModulesXMLPath() {
		Bundle atomicPepperBundle = FrameworkUtil.getBundle(this.getClass());
		URL bundleURL = FileLocator.find(atomicPepperBundle, new Path("/"), null);
		URL pepperHomeURL = null;
		String modulesXMLPath = null;
		try {
			pepperHomeURL = FileLocator.resolve(bundleURL);
			modulesXMLPath = pepperHomeURL.getPath() + "conf/modules.xml";
		}
		catch (IOException e) {
			log.error("Could not resolve pepper home URL!", e);
		}
		return modulesXMLPath;
	}

	/**
	 * Handles the actual update process.
	 * 
	 * @copydoc @see org.eclipse.core.runtime.jobs.Job#run(org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	protected IStatus run(IProgressMonitor monitor) {
		AtomicPepperStarter pepperStarter = new AtomicPepperStarter();
		pepperStarter.startPepper();

		setPepper(pepperStarter.getPepper());
		
		update();
		return Status.OK_STATUS;
	}

	/**
	 * TODO: Description
	 *
	 */
	private void update() {
		try {
			moduleTable = getModuleTable();
		}
		catch (ParserConfigurationException | SAXException | IOException e) {
			log.error("Getting the Pepper module table from the file at {} didn't succeed!", MODULES_XML_PATH, e);
		}
		List<String> lines = new ArrayList<String>();
		AtomicPepperOSGiConnector pepper = (AtomicPepperOSGiConnector) getPepper();
		for (Map.Entry<String, Pair<String, String>> entry : moduleTable.entrySet()) {
			if (pepper.update(entry.getValue().getLeft(), entry.getKey(), entry.getValue().getRight(), false, false)) {
				lines.add(entry.getKey().concat(" successfully updated."));
			} else {
				lines.add(entry.getKey().concat(" NOT updated."));
			}
		}
		Collections.<String> sort(lines);
		for (String line : lines) {
			System.err.println(line);
		}
	}

	/**
	 * TODO: Description
	 *
	 * @return
	 */
	private Map<String, Pair<String, String>> getModuleTable() throws ParserConfigurationException, SAXException, IOException {
		if (this.moduleTable != null) {
			return moduleTable;
		}
		HashMap<String, Pair<String, String>> table = new HashMap<String, Pair<String, String>>();
		SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();
		try {
			saxParser.parse(MODULES_XML_PATH, new ModuleTableReader(table));
		} catch (Exception e) {
			log.debug("Could not parse modules.xml", e);
		}
		return table;
	}
	
	/**
	 * @return the pepper
	 */
	public PepperConnector getPepper() {
		return pepper;
	}

	/**
	 * @param pepper the pepper to set
	 */
	public void setPepper(PepperConnector pepper) {
		this.pepper = pepper;
	}

	/**
	 * This class is the call back handler for reading the modules.xml file,
	 * which provides Information about the pepperModules to be updated /
	 * installed.
	 * 
	 * @author klotzmaz
	 *
	 */
	private static class ModuleTableReader extends DefaultHandler2 {
		/**
		 * all read module names are stored here Map: artifactId --> (groupId,
		 * repository)
		 * */
		private Map<String, Pair<String, String>> listedModules;
		/** this string contains the last occurred artifactId */
		private String artifactId;
		/** this string contains the group id */
		private String groupId;
		/** this string contains the repository */
		private String repo;
		/** the name of the tag between the modules are listed */
		private static final String TAG_LIST = "pepperModulesList";
		/**
		 * the name of the tag in the modules.xml file, between which the
		 * modules' properties are listed
		 */
		private static final String TAG_ITEM = "pepperModules";
		/**
		 * the name of the tag in the modules.xml file, between which the
		 * modules' groupId is written
		 */
		private static final String TAG_GROUPID = "groupId";
		/**
		 * the name of the tag in the modules.xml file, between which the
		 * modules' name is written
		 */
		private static final String TAG_ARTIFACTID = "artifactId";
		/**
		 * the name of the tag in the modules.xml file, between which the
		 * modules' source is written
		 */
		private static final String TAG_REPO = "repository";
		/** the name of the attribute for the default repository */
		private static final String ATT_DEFAULTREPO = "defaultRepository";
		/** the name of the attribute for the default groupId */
		private static final String ATT_DEFAULTGROUPID = "defaultGroupId";
		/** contains the default groupId for modules where no groupId is defined */
		private String defaultGroupId;
		/**
		 * contains the default repository for modules where no repository is
		 * defined
		 */
		private String defaultRepository;
		/** is used to read the module name character by character */
		private StringBuilder chars;

		/** this boolean says, whether characters should be read or ignored */
		// private boolean openEyes;

		public ModuleTableReader(Map<String, Pair<String, String>> artifactIdUrlMap) {
			listedModules = artifactIdUrlMap;
			chars = new StringBuilder();
			groupId = null;
			artifactId = null;
			repo = null;
			// openEyes = false;
		}

		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
			localName = qName.substring(qName.lastIndexOf(":") + 1);
			// openEyes = TAG_GROUPID.equals(localName) ||
			// TAG_ARTIFACTID.equals(localName) || TAG_REPO.equals(localName);
			if (TAG_LIST.equals(localName)) {
				defaultRepository = attributes.getValue(ATT_DEFAULTREPO);
				defaultGroupId = attributes.getValue(ATT_DEFAULTGROUPID);
			}
			chars.delete(0, chars.length());
		}

		@Override
		public void characters(char[] ch, int start, int length) throws SAXException {
			for (int i = start; i < start + length /* && openEyes */; i++) {
				chars.append(ch[i]);
			}
			// openEyes = false;
		}

		@Override
		public void endElement(java.lang.String uri, String localName, String qName) throws SAXException {
			localName = qName.substring(qName.lastIndexOf(":") + 1);
			if (TAG_ARTIFACTID.equals(localName)) {
				artifactId = chars.toString();
				chars.delete(0, chars.length());
			} else if (TAG_GROUPID.equals(localName)) {
				groupId = chars.toString();
				chars.delete(0, chars.length());
			} else if (TAG_REPO.equals(localName)) {
				repo = chars.toString();
				chars.delete(0, chars.length());
			} else if (TAG_ITEM.equals(localName)) {
				groupId = groupId == null ? defaultGroupId : groupId;
				listedModules.put(artifactId, Pair.of(groupId, (repo == null || repo.isEmpty() ? defaultRepository : repo)));
				chars.delete(0, chars.length());
				groupId = null;
				artifactId = null;
				repo = null;
			}
		}
	}

}
