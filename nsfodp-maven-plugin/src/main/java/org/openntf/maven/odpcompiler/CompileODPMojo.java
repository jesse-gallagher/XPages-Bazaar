/**
 * Copyright © 2018 Jesse Gallagher
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
 * Includes code derived from the JNoSQL Diana Couchbase driver and Artemis
 * extensions, copyright Otavio Santana and others and available from:
 *
 * https://github.com/eclipse/jnosql-diana-driver/tree/master/couchbase-driver
 * https://github.com/eclipse/jnosql-artemis-extension/tree/master/couchbase-extension
 */
package org.openntf.maven.odpcompiler;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultMavenProjectHelper;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.codehaus.plexus.util.IOUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Goal which touches a timestamp file.
 */
@Mojo(name="compile-odp", defaultPhase=LifecyclePhase.COMPILE)
public class CompileODPMojo extends AbstractMojo {
	
	@Parameter(defaultValue="${project}", readonly=true)
	private MavenProject project;
	
	@Component
	private WagonManager wagonManager;
	
	/**
	 * Location of the generated NSF.
	 */
	@Parameter(defaultValue="${project.build.directory}", property="outputDir", required=true)
	private File outputDirectory;
	/**
	 * File name of the generated NSF.
	 */
	@Parameter(defaultValue="${project.build.finalName}.nsf", required=true)
	private String outputFileName;
	/**
	 * Location of the ODP directory.
	 */
	@Parameter(defaultValue="odp", required=true)
	private File odpDirectory;
	/**
	 * The server id in settings.xml to use when authenticating with the compiler server, or
	 * <code>null</code> to authenticate as anonymous.
	 */
	@Parameter(property="nsfodp.compiler.server", required=false)
	private String compilerServer;
	/**
	 * The base URL of the ODP compiler server, e.g. "http://my.server".
	 */
	@Parameter(property="nsfodp.compiler.serverUrl", required=true)
	private URL compilerServerUrl;
	/**
	 * An update site whose contents to use when building the ODP.
	 */
	@Parameter(required=false)
	private File updateSite;
	
	private Log log;

	public void execute() throws MojoExecutionException {
		log = getLog();
		
		Path outputDirectory = Objects.requireNonNull(this.outputDirectory, "outputDirectory cannot be null").toPath();
		
		Path odpDirectory = Objects.requireNonNull(this.odpDirectory, "odpDirectory cannot be null").toPath();
		if(!Files.exists(odpDirectory)) {
			throw new IllegalArgumentException("Specified ODP directory does not exist: " + odpDirectory.toAbsolutePath());
		}
		if(!Files.isDirectory(odpDirectory)) {
			throw new IllegalArgumentException("Specified ODP path is not a directory: " + odpDirectory.toAbsolutePath());
		}
		Path updateSite = this.updateSite == null ? null : this.updateSite.toPath();
		if(updateSite != null) {
			if(!Files.exists(updateSite)) {
				throw new IllegalArgumentException("Specified Update Site directory does not exist: " + updateSite.toAbsolutePath());
			}
			if(!Files.isDirectory(updateSite)) {
				throw new IllegalArgumentException("Specified Update Site path is not a directory: " + updateSite.toAbsolutePath());
			}
		}
		String outputFileName = Objects.requireNonNull(this.outputFileName, "outputFileName cannot be null");
		if(outputFileName.isEmpty()) {
			throw new IllegalArgumentException("outputFileName cannot be empty");
		}
		
		try {
			if(!Files.exists(outputDirectory)) {
				Files.createDirectories(outputDirectory);
			}
			
			Path odpZip = zipDirectory(odpDirectory);
			Path updateSiteZip = null;
			if(updateSite != null) {
				updateSiteZip = zipDirectory(updateSite);
			}
			
			Path packageZip = createPackage(odpZip, updateSiteZip);
			Path result = compileOdp(packageZip);
			
			Path outputFile = outputDirectory.resolve(outputFileName);
			Files.move(result, outputFile, StandardCopyOption.REPLACE_EXISTING);
			if(log.isInfoEnabled()) {
				log.info("Generated NSF: " + outputFile);
			}
			
			MavenProjectHelper helper = new DefaultMavenProjectHelper();
			MavenProject project = Objects.requireNonNull(this.project, "Maven project cannot be null");
			helper.attachArtifact(project, outputFile.toFile(), "nsf");
		} catch(MojoExecutionException e) {
			throw e;
		} catch(Throwable t) {
			throw new MojoExecutionException("Exception while compiling the NSF", t);
		}
	}
	
	private Path createPackage(Path odpZip, Path updateSiteZip) throws IOException {
		if(log.isDebugEnabled()) {
			log.debug("Creating package from odpZip=" + odpZip + ", updateSiteZip=" + updateSiteZip);
		}
		
		Path packageZip = Files.createTempFile("odpcompiler-package", ".zip");
		packageZip.toFile().deleteOnExit();
		try(OutputStream fos = Files.newOutputStream(packageZip)) {
			try(ZipOutputStream zos = new ZipOutputStream(fos)) {
				zos.setLevel(Deflater.BEST_COMPRESSION);
				ZipEntry entry = new ZipEntry("odp.zip");
				zos.putNextEntry(entry);
				Files.copy(odpZip, zos);
				
				if(updateSiteZip != null) {
					entry = new ZipEntry("site.zip");
					zos.putNextEntry(entry);
					Files.copy(updateSiteZip, zos);
				}
			}
		}
		return packageZip;
	}
	
	private Path compileOdp(Path packageZip) throws IOException, URISyntaxException, MojoExecutionException {
		if(log.isInfoEnabled()) {
			log.info("Compiling ODP");
		}
		
		URL compilerServerUrl = Objects.requireNonNull(this.compilerServerUrl);
		if(log.isDebugEnabled()) {
			log.debug("Using compiler server URL " + compilerServerUrl);
		}
		
		try(CloseableHttpClient client = HttpClients.createDefault()) {
			URI servlet = compilerServerUrl.toURI().resolve("/odpcompiler");
			if(log.isInfoEnabled()) {
				log.info("Compiling ODP on server " + servlet);
			}
			HttpPost post = new HttpPost(servlet);
			post.addHeader("Content-Type", "application/zip");
			
			String userName = addAuthenticationInfo(this.compilerServer, post);
			
			FileEntity fileEntity = new FileEntity(packageZip.toFile());
			post.setEntity(fileEntity);
			
			HttpResponse res = client.execute(post);
			int status = res.getStatusLine().getStatusCode();
			HttpEntity responseEntity = res.getEntity();
			if(log.isDebugEnabled()) {
				log.debug("Received entity: " + responseEntity);
			}
			if(status < 200 || status >= 300) {
				String errorBody;
				try(ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
					try(InputStream is = responseEntity.getContent()) {
						IOUtil.copy(is, baos);
					}
					errorBody = baos.toString();
				}
				System.err.println("Received error from server:");
				System.err.println(errorBody);
				throw new IOException("Received unexpected HTTP response: " + res.getStatusLine());
			}
			
			// Check for an auth form - Domino returns these as status 200
			if(String.valueOf(res.getFirstHeader("Content-Type").getValue()).startsWith("text/html")) {
				throw new IOException("Authentication failed for user " + userName);
			}
 			
			try(InputStream is = responseEntity.getContent()) {
				Path result = Files.createTempFile("odpcompiler-output", ".nsf");
				Files.copy(is, result, StandardCopyOption.REPLACE_EXISTING);
				return result;
			}
		}
	}
	
	private Path zipDirectory(Path path) throws IOException {
		if(log.isDebugEnabled()) {
			log.debug("Zipping path " + path.toString());
		}
		
		Path result = Files.createTempFile("odpcompiler-dir", ".zip");
		result.toFile().deleteOnExit();
		
		try(OutputStream fos = Files.newOutputStream(result)) {
			try(ZipOutputStream zos = new ZipOutputStream(fos)) {
				zos.setLevel(Deflater.BEST_COMPRESSION);
				Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
						if(attrs.isRegularFile()) {
							ZipEntry entry = new ZipEntry(path.relativize(file).toString());
							zos.putNextEntry(entry);
							Files.copy(file, zos);
						}
						return FileVisitResult.CONTINUE;
					}
				});
			}
		}
		
		return result;
	}
	
	/**
	 * Adds server credential information from the user's settings.xml, if applicable.
	 * 
	 * @param the server ID to find credentials for
	 * @param req the request to add credentials to
	 * @return the effective username of the request
	 * @throws MojoExecutionException if the server ID is specified but credentials cannot be found
	 */
	private String addAuthenticationInfo(String serverId, HttpRequest req) throws MojoExecutionException {
		String userName;
		if(serverId != null && !serverId.isEmpty()) {
			// Look up credentials for the server
			AuthenticationInfo info = wagonManager.getAuthenticationInfo(serverId);
			if(info == null) {
				throw new MojoExecutionException("Could not find server credentials for specified server ID: " + serverId);
			}
			userName = info.getUserName();
			if(userName == null || userName.isEmpty()) {
				// Then just use Anonymous
				if(log.isDebugEnabled()) {
					log.debug("Configured username is blank - acting as Anonymous");
				}
				userName = "Anonymous";
			} else {
				if(log.isDebugEnabled()) {
					log.debug("Authenticating as user " + userName);
				}
				String password = info.getPassword();
				
				// Create a Basic auth header
				// This is instead of HttpClient's credential handling because of how
				//   Domino handles the auth handshake.
				String enc = Base64.encodeBase64String((userName + ":" + password).getBytes());
				req.addHeader("Authorization", "Basic " + enc);
			}
		} else {
			if(log.isDebugEnabled()) {
				log.debug("No username specified - acting as Anonymous");
			}
			userName = "Anonymous";
		}
		return userName;
	}
}