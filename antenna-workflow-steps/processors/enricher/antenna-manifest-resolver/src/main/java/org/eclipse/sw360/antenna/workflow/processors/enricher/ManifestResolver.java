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

package org.eclipse.sw360.antenna.workflow.processors.enricher;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.eclipse.sw360.antenna.api.workflow.AbstractProcessor;
import org.eclipse.sw360.antenna.api.configuration.AntennaContext;
import org.eclipse.sw360.antenna.model.xml.generated.BundleCoordinates;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eclipse.sw360.antenna.model.Artifact;
import org.eclipse.sw360.antenna.model.reporting.MessageType;
import org.eclipse.sw360.antenna.util.AntennaUtils;

/**
 * Class scans all filepaths of the artifacts, if a jar file is found and
 * contains a Manifest file with bundle coordinates this coordinates are added
 * to the artifact which belongs to the scanned filepath. The jar is added to
 * the artifact as well.
 */

public class ManifestResolver extends AbstractProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ManifestResolver.class);

    /**
     * Scans all file paths of the artifacts, if a jar file is found and
     * contains a Manifest file with bundle coordinates this coordinates are
     * added to the artifact which belongs to the scanned file path. The jar is
     * added to the artifact as well.
     *
     * @param artifacts List of artifacts, which will be resolved
     */
    private void resolveManifest(Collection<Artifact> artifacts) {
        for (Artifact artifact : artifacts) {
            if (artifact.getPathnames().length > 0) {
                resolveManifest(artifact);
            }
        }
    }

    private void resolveManifest(Artifact artifact){
        LOGGER.debug("Resolving {}", artifact.getPathnames()[0]);
        Path jarPath = context.getToolConfiguration().getAntennaTargetDirectory().resolve(artifact.getPathnames()[0]);

        try {
            final Path jar = resolveJarFile(jarPath);

            try (JarFile jarFile = new JarFile(jar.toFile())) {
                setBundleCoordinates(artifact, jarFile.getManifest());
            }

            artifact.setJar(jar.toFile());
        } catch (IOException e) {
            LOGGER.error("Unable to process \"{}\" because of {}", jarPath,
                    e.getMessage());
            this.reporter.addProcessingMessage(artifact.getArtifactIdentifier(),
                    MessageType.PROCESSING_FAILURE,
                    "An exeption occured while Manifest resolving:" + e.getMessage());
        }
    }

    private Path resolveJarFile(Path jarPath) throws IOException {
        Iterator<Path> jarPaths = AntennaUtils.getJarPathIteratorFromPath(jarPath);

        Path topLevelJar = jarPaths.next();

        if(!jarPaths.hasNext()){
            return topLevelJar;
        }

        Path targetJar = computeFinalJarFileName(jarPath);
        Files.createDirectories(topLevelJar.getParent());
        try(FileInputStream fis = new FileInputStream(topLevelJar.toFile())){
            extractDeepNestedJar(fis, jarPaths, targetJar);
        }
        return targetJar;
    }

    /**
     * walks recursively into nested jar/war/ear/zip files and extracts the innerest one
     */
    private void extractDeepNestedJar(InputStream zippedInputStream, Iterator<Path> nestedJars, Path finalPath) throws IOException {
        if(!nestedJars.hasNext()){
            FileUtils.copyInputStreamToFile(zippedInputStream, finalPath.toFile());
        }else {
            Path nextJarName = nestedJars.next();

            try (ZipInputStream zipInputStream = new ZipInputStream(zippedInputStream) ) {
                ZipEntry entry;
                String nextJarBaseName = nextJarName.getFileName().toString();
                while ((entry = zipInputStream.getNextEntry()) != null) {
                    if(nextJarBaseName.equals(entry.getName())) {
                        extractDeepNestedJar(zipInputStream, nestedJars, finalPath);
                        return;
                    }
                }
            }
            throw new IOException("Unable to find \""+nextJarName+"\"");
        }
    }

    private Path computeFinalJarFileName(Path jarPath){
        Path cleanedUpPath = AntennaUtils.computeInnerReplacementJarPath(jarPath);
        return cleanedUpPath.toAbsolutePath();
    }

    /**
     * Adds the result of getAttribute to the artifacts bundle values.
     *
     * @param artifact
     * @param manifest
     */
    private void setBundleCoordinates(Artifact artifact, Manifest manifest) {
        final BundleCoordinates bundleCoordinates = artifact.getArtifactIdentifier().getBundleCoordinates();
        getAttribute(manifest, "Bundle-SymbolicName")
                .ifPresent(bundleCoordinates::setSymbolicName);
        getAttribute(manifest, "Bundle-Version")
                .ifPresent(bundleCoordinates::setBundleVersion);
    }

    /**
     * @param manifest      JarFile of which the Manifest file shall be resolved.
     * @param attributeName Name of the attribute which should be found in the Manifest
     *                      file.
     * @return Returns the value of
     */
    private Optional<String> getAttribute(Manifest manifest, String attributeName) {
        return Optional.ofNullable(manifest)
                .map(Manifest::getMainAttributes)
                .flatMap(ma -> Optional.ofNullable(ma.getValue(attributeName)))
                .map(av -> av.split(";"))
                .map(av -> av[0]); // TODO: why is only the first value used?
    }

    @Override
    public Collection<Artifact> process(Collection<Artifact> artifacts) {
        LOGGER.info("Resolve manifest...");
        resolveManifest(artifacts);
        LOGGER.info("Resolve manifest... done");
        return artifacts;
    }
}