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
 *******************************************************************************/
package org.corpus_tools.atomic.pepper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkUtil;

import de.hu_berlin.german.korpling.saltnpepper.pepper.cli.PepperStarterConfiguration;
import de.hu_berlin.german.korpling.saltnpepper.pepper.cli.exceptions.PepperOSGiException;
import de.hu_berlin.german.korpling.saltnpepper.pepper.cli.exceptions.PepperOSGiFrameworkPluginException;
import de.hu_berlin.german.korpling.saltnpepper.pepper.cli.exceptions.PepperPropertyException;
import de.hu_berlin.german.korpling.saltnpepper.pepper.common.Pepper;
import de.hu_berlin.german.korpling.saltnpepper.pepper.common.PepperConfiguration;
import de.hu_berlin.german.korpling.saltnpepper.pepper.connectors.impl.PepperOSGiConnector;
import de.hu_berlin.german.korpling.saltnpepper.pepper.core.PepperOSGiRunner;
import de.hu_berlin.german.korpling.saltnpepper.pepper.exceptions.PepperConfigurationException;

/**
 * This class is an implementation of {@link Pepper}. It bridges between 
 * the Atomic environment (Eclipse RCP based on an Equinox OSGi environment),
 * and the Pepper OSGi environment, which is forced to use Atomic's OSGi
 * {@link BundleContext}. The {@link AtomicPepperOSGiConnector} is used
 * to install and start bundles as well as update Pepper module bundles.
 * 
 * TODO: Implement starting bundles so that the method can be called
 * e.g., from the Pepper wizards.
 * 
 * <p>@author Stephan Druskat <stephan.druskat@uni-jena.de>
 *
 */
public class AtomicPepperOSGiConnector extends PepperOSGiConnector {
	
	/** 
	 * Defines a static log variable so that it references the {@link org.apache.logging.log4j.Logger} instance named "AtomicPepperOSGiConnector".
	 */
	private static final Logger log = LogManager.getLogger(AtomicPepperOSGiConnector.class);
	
	
	private AtomicPepperConfiguration properties = null;
	
	/** Stores all bundle ids and the corresponding bundles. */
	private Map<Long, Bundle> bundleIdMap = new Hashtable<Long, Bundle>();
//	/** Stores all locations of bundles and the corresponding bundle ids **/
//	private Map<URI, Long> locationBundleIdMap = new Hashtable<URI, Long>();
	/**
	 * Contains the version of the Pepper framework. {@link #PEPPER_VERSION} is not
	 * used, on purpose. This {@link String} contains the value of the
	 * pepper-framework OSGi {@link Bundle}.
	 */
	private String frameworkVersion = null;
	/** this String contains the artifactId of pepper-framework. */
	private static final String ARTIFACT_ID_PEPPER_FRAMEWORK = "pepper-framework";
	private AtomicMavenAccessor maven = null;
	/** Determines if this object has been initialized **/
	@SuppressWarnings("unused")
	private boolean isInit = false;

	@Override
	public void init() {
		if (getAtomicPepperConfiguration().getPlugInPath() == null) {
			PepperPropertyException e = new PepperPropertyException("Cannot start Pepper, because no plugin path is given for Pepper modules.");
			log.error("No plugin path given.", e);
			throw e;
		}
		File pluginPath = new File(getAtomicPepperConfiguration().getPlugInPath());
		if (!pluginPath.exists()) {
			PepperOSGiException e = new PepperOSGiException("Cannot load any plugins, since the configured path for plugins '" + pluginPath.getAbsolutePath() + "' does not exist. Please check the entry '" + PepperStarterConfiguration.PROP_PLUGIN_PATH + "' in the Pepper configuration file at '" + getConfiguration().getConfFolder().getAbsolutePath() + "'.");
			log.error("Plugin path does not exist!", e);
			throw e;
		}
		try {
			// Disable PepperOSGiRunner and set bundle context
			System.setProperty(PepperOSGiRunner.PROP_TEST_DISABLED, Boolean.TRUE.toString());
			setBundleContext(FrameworkUtil.getBundle(this.getClass()).getBundleContext());
		} catch (Exception e) {
			log.error("OSGi environment could not be started: {}.", e.getMessage(), e);
			throw new PepperOSGiException("The OSGi environment could not have been started: " + e.getMessage(), e);
		}
		Bundle[] bundles = FrameworkUtil.getBundle(this.getClass()).getBundleContext().getBundles();
		for (int i = 0; i < bundles.length; i++) {
			Bundle bundle = bundles[i];
			String bundleName = bundle.getSymbolicName();
			if (bundleName != null && bundleName.contains(ARTIFACT_ID_PEPPER_FRAMEWORK)) {
				frameworkVersion = bundle.getVersion().toString().replace(".SNAPSHOT", "-SNAPSHOT");
			}
		}
		maven = new AtomicMavenAccessor(this);

		isInit = true;
	}
	
	@Override
	public void setConfiguration(PepperConfiguration configuration) {
		if (configuration instanceof AtomicPepperConfiguration) {
			this.properties = (AtomicPepperConfiguration) configuration;
		} else {
			PepperConfigurationException e = new PepperConfigurationException("Cannot set the given configuration, since it is not of type '" + AtomicPepperConfiguration.class.getSimpleName() + "'.");
			log.error("Wrong Pepper configuration type!", e);
			throw e;
		}
	}
	
	/**
	 * Starts all bundle being contained in the given list of bundles.
	 * 
	 * @param bundles
	 *            a list of bundles to start
	 * @throws BundleException
	 */
	protected void startBundles(Collection<Bundle> bundles) throws BundleException {
		if (bundles != null) {
			Bundle pepperBundle = null;
			for (Bundle bundle : bundles) {
				// TODO this is a workaround, to fix that module resolver is
				// loaded as last bundle, otherwise, some modules will be
				// ignored
				if ("de.hu_berlin.german.korpling.saltnpepper.pepper-framework".equalsIgnoreCase(bundle.getSymbolicName())) {
					pepperBundle = bundle;
				} else {
					start(bundle.getBundleId());
				}
			}
			try {
				if (pepperBundle != null) {
					pepperBundle.start();
				}
			} catch (BundleException e) {
				throw new PepperOSGiFrameworkPluginException("The Pepper framework bundle could not have been started. Unfortunatly Pepper cannot be started without that OSGi bundle. ", e);
			}
		}
	}
	
	/**
	 * Starts the passed bundle
	 * 
	 * @param bundle
	 */
	public void start(Long bundleId) {
		Bundle bundle = bundleIdMap.get(bundleId);
		log.debug("\t\tstarting bundle: " + bundle.getSymbolicName() + "-" + bundle.getVersion());
		if (bundle.getState() != Bundle.ACTIVE) {
			try {
				bundle.start();
			} catch (BundleException e) {
				log.warn("The bundle '" + bundle.getSymbolicName() + "-" + bundle.getVersion() + "' wasn't started correctly. This could cause other problems. For more details turn on log mode to debug and see log file. ", e);
			}
		}
		if (bundle.getState() != Bundle.ACTIVE) {
			log.error("The bundle '" + bundle.getSymbolicName() + "-" + bundle.getVersion() + "' wasn't started correctly.");
		}
	}

	/** {@inheritDoc Pepper#getConfiguration()} **/
	@Override
	public PepperConfiguration getConfiguration() {
		return properties;
	}
	
	/**
	 * @return configuration as {@link AtomicPepperConfiguration}
	 **/
	public AtomicPepperConfiguration getAtomicPepperConfiguration() {
		return properties;
	}
	
	@Override
	public String getFrameworkVersion() {
		return frameworkVersion;
	}
	
	/**
	 * This method checks the pepperModules in the modules.xml for updates and
	 * triggers the installation process if a newer version is available
	 */
	public boolean update(String groupId, String artifactId, String repositoryUrl, boolean isSnapshot, boolean ignoreFrameworkVersion) {
		return maven.update(groupId, artifactId, repositoryUrl, isSnapshot, ignoreFrameworkVersion, getBundle(groupId, artifactId, null));
	}
	
	/**
	 * Installs the given bundle and copies it to the plugin path, but does not
	 * start it. <br>
	 * If the the URI is of scheme http or https, the file will be downloaded. <br/>
	 * If the URI points to a zip file, it will be extracted and copied.
	 * 
	 * @param bundleURI
	 * @return
	 * @throws BundleException
	 * @throws IOException
	 */
	public Bundle installAndCopy(URI bundleURI) throws BundleException, IOException {
		// TODO Add logs!
		Bundle retVal = null;
		if (bundleURI != null) {
			String pluginPath = getAtomicPepperConfiguration().getPlugInPath();
			if (pluginPath != null) {
				// download file, if file is a web resource
				if (("http".equalsIgnoreCase(bundleURI.getScheme())) || ("https".equalsIgnoreCase(bundleURI.getScheme()))) {
					String tempPath = getAtomicPepperConfiguration().getTempPath().getCanonicalPath();
					URL bundleUrl = bundleURI.toURL();
					if (!tempPath.endsWith("/")) {
						tempPath = tempPath + "/";
					}
					String baseName = FilenameUtils.getBaseName(bundleUrl.toString());
					String extension = FilenameUtils.getExtension(bundleUrl.toString());
					File bundleFile = new File(tempPath + baseName + "." + extension);

					org.apache.commons.io.FileUtils.copyURLToFile(bundleURI.toURL(), bundleFile);
					bundleURI = URI.create(bundleFile.getAbsolutePath());
				}
				if (bundleURI.getPath().endsWith("zip")) {
					ZipFile zipFile = null;
					try {
						zipFile = new ZipFile(bundleURI.getPath());
						Enumeration<? extends ZipEntry> entries = zipFile.entries();
						while (entries.hasMoreElements()) {
							ZipEntry entry = entries.nextElement();
							File entryDestination = new File(pluginPath, entry.getName());
							entryDestination.getParentFile().mkdirs();
							if (entry.isDirectory()) {
								entryDestination.mkdirs();
							} else {
								InputStream in = zipFile.getInputStream(entry);
								OutputStream out = new FileOutputStream(entryDestination);
								IOUtils.copy(in, out);
								IOUtils.closeQuietly(in);
								IOUtils.closeQuietly(out);
								if (entryDestination.getName().endsWith(".jar")) {
									retVal = install(entryDestination.toURI());
								}
							}
						}
					} finally {
						zipFile.close();
					}
				} else if (bundleURI.getPath().endsWith("jar")) {
					File bundleFile = new File(bundleURI.getPath());
					File jarFile = new File(pluginPath, bundleFile.getName());
					FileUtils.copyFile(bundleFile, jarFile);
					retVal = install(jarFile.toURI());
				}
			}
		}

		return (retVal);
	}

}