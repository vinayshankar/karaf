/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.karaf.profile.assembly;

import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

import org.apache.karaf.features.internal.download.Downloader;
import org.apache.karaf.features.internal.download.impl.DownloadManagerHelper;
import org.apache.karaf.features.internal.service.Blacklist;
import org.apache.karaf.util.maven.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArtifactInstaller {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArtifactInstaller.class);

    private Path systemDirectory;
    private Downloader downloader;
    private Blacklist blacklist;

    public ArtifactInstaller(Path systemDirectory, Downloader downloader, List<String> blacklisted) {
        this.systemDirectory = systemDirectory;
        this.downloader = downloader;
        this.blacklist = new Blacklist(blacklisted);
    }
    
    public void installArtifact(String location) throws Exception {
        LOGGER.info("      adding maven artifact: " + location);
        location = DownloadManagerHelper.stripUrl(location);
        if (location.startsWith("mvn:")) {
            if (location.endsWith("/")) {
                // for bad formed URL (like in Camel for mustache-compiler), we remove the trailing /
                location = location.substring(0, location.length() - 1);
            }
            downloader.download(location, provider -> {
                String uri = provider.getUrl();
                if (blacklist.isBundleBlacklisted(uri)) {
                    throw new RuntimeException("Bundle " + uri + " is blacklisted");
                }
                Path path = pathFromProviderUrl(systemDirectory, uri);
                synchronized (provider) {
                    Files.createDirectories(path.getParent());
                    Files.copy(provider.getFile().toPath(), path, StandardCopyOption.REPLACE_EXISTING);
                }
            });
        } else {
            LOGGER.warn("Ignoring non maven artifact " + location);
        }
    }
    
    public static Path pathFromProviderUrl(Path systemDirectory, String url) throws MalformedURLException {
        String pathString;
        if (url.startsWith("file:")) {
            return Paths.get(URI.create(url));
        }
        else if (url.startsWith("mvn:")) {
            pathString = Parser.pathFromMaven(url);
        }
        else {
            pathString = url;
        }
        return systemDirectory.resolve(pathString);
    }
}
