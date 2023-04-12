package org.leanix.lx_vsm_maven;

import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

/**
 * Relays dependency data to VSM.
 * 
 *
 */
@Mojo(name = "lx-vsm-mvn", defaultPhase = LifecyclePhase.PACKAGE)
public class SyncToVsmMojo extends AbstractMojo {

  /**
   * SBOM Path to get bom.json file
   */
  @Parameter(defaultValue = "./target/bom.json", property = "sbomPath")
  private String sbomPath;

  /**
   * Skip snapshot Versions from being relayed to VSM
   */
  @Parameter(defaultValue = "true", property = "skipSnapshot")
  private boolean skipSnapshot;

  /**
   * The hosting region of your VSM workspace. Reach out to LeanIX if you don\'t
   * know. One of: eu|de|us|au|ca|ch
   */
  @Parameter(property = "region")
  private String region;

  /**
   * The DNS host of your VSM workspace. e.g. https://acme.leanix.net would be
   * "acme".
   */
  @Parameter(property = "host")
  private String host;

  /**
   * The admin technical user API Token. Note this is NOT the OAuth token, but the
   * user token.
   */
  @Parameter(property = "apiToken")
  private String apiToken;

  /**
   * Optional metadata in a simple {"key":"value"} json format.
   */
  @Parameter(property = "data", defaultValue = "{}")
  private String data;

  /**
   * Optional metadata in a simple {"key":"value"} json format.
   */
  @Parameter(property = "sourceType", defaultValue = "java")
  private String sourceType;

  /**
   * Optional metadata in a simple {"key":"value"} json format.
   */
  @Parameter(property = "sourceInstance", defaultValue = "lx-vsm-maven")
  private String sourceInstance;

  /**
   * Gives access to the Maven project information.
   */
  @Parameter(defaultValue = "${project}", required = true, readonly = true)
  private MavenProject project;

  private String apiTokenPrefix = "apitoken:";

  private String accessTokenKey = "access_token";

  private String authURL;

  private String serviceDiscoveryURL;

  private String encodedAuthToken;

  public void execute() throws MojoExecutionException, MojoFailureException {

    authURL = String.format("https://%s.leanix.net/services/mtm/v1/oauth2/token", host);
    serviceDiscoveryURL = String.format("https://%s-vsm.leanix.net/services/vsm/discovery/v1/service", region);
    encodedAuthToken = Base64.getEncoder()
      .encodeToString((apiTokenPrefix + apiToken).getBytes());

    getLog().info("LX-VSM-MVN execution begins");
    getLog().info("-----------------------------------------------------------------------");
    try {
      publishToVSM(getBomFile());
    } catch (Exception e) {
      getLog().warn("Problem relaying data to VSM due to: " + e.getMessage());
    }

  }

  public File getBomFile() throws MojoExecutionException {

    // Default to target/bom.json if bomFile is not provided in pom.xml
    if (StringUtils.isEmpty(sbomPath)) {
      File baseDirPath = project.getBasedir().getAbsoluteFile();
      getLog().info("FilePath of basedir is: " + baseDirPath.getAbsolutePath());
      String[] pathElements = { baseDirPath.getAbsolutePath(), "target", "bom.json" };
      sbomPath = String.join(File.separator, pathElements);
    }
    File file = null;
    try {
      file = new File(sbomPath);
      if (!file.exists()) {
        getLog().info("SBOM not found at '" + sbomPath + "' SKIPPING");
        return null;
      }
    } catch (Exception e) {
      getLog().info("Problem accessing sbomPath at" + sbomPath);
      throw new MojoExecutionException(e.getMessage());
    }
    getLog().info("sbomPath is " + sbomPath);

    return file;
  }

  public void publishToVSM(File bomFile) throws MojoExecutionException {

    String bearerToken = getBearerToken();

    String serviceId = project.getGroupId() + "." + project.getArtifactId();

    String name = project.getArtifactId();

    try {
      // Create a Gson object
      Gson gson = new Gson();

      JsonObject jsonObject = gson.fromJson(this.data, JsonObject.class);
      jsonObject.addProperty("version", project.getVersion());
      this.data = gson.toJson(jsonObject);

    } catch (JsonSyntaxException e) {
      getLog().info("Problem parsing the data object: " + data);
      throw new MojoExecutionException(e.getMessage());
    }
    boolean isSnapshot = project.getVersion().contains("SNAPSHOT");
    /**
     * iss 1 skip1 -> skip iss 1 skip0 -> send iss 0 skip1 -> send iss 0 skip0 ->
     * send
     */
    if (isSnapshot && skipSnapshot) {
      getLog().info("------------------------------------------------");
      getLog().info("***SKIPPING*** relaying build data to LEANIX-VSM");
      getLog().info("------------------------------------------------");
    } else {
      getLog().info("--------------LX-VSM-MAVEN PARAMS---------------");
      getLog().info("Project Description is " + project.getDescription());
      getLog().info("skipSnapshot is set to " + skipSnapshot);
      getLog().info("Project version is " + project.getVersion());
      getLog().info("------------------------------------------------");
      createOrUpdateVSMService(bearerToken, serviceId, sourceType, sourceInstance, name, data, bomFile);
    }

  }

  public void createOrUpdateVSMService(String bearerToken, String serviceId, String sourceType,
      String sourceInstance, String name, String data, File bomFile) throws MojoExecutionException {
    String description = StringUtils.isEmpty(project.getDescription()) ? "" : project.getDescription();

    OkHttpClient client = new OkHttpClient().newBuilder().build();
    MultipartBody.Builder bodyBuilder = new MultipartBody.Builder().setType(MultipartBody.FORM)
        .addFormDataPart("id", serviceId)
        .addFormDataPart("sourceType", sourceType)
        .addFormDataPart("sourceInstance", sourceInstance)
        .addFormDataPart("name", name)
        .addFormDataPart("description", description)
        .addFormDataPart("data", data);

    if (bomFile != null) {
      bodyBuilder.addFormDataPart("bom", bomFile.getName(),
          RequestBody.Companion.create(bomFile, MediaType.parse("application/json")));
    }

    RequestBody body = bodyBuilder.build();
    Request request = new Request.Builder().url(serviceDiscoveryURL).method("POST", body)
        .addHeader("accept", "*/*").addHeader("authorization", "Bearer " + bearerToken).build();
    Response response = null;
    try {
      response = client.newCall(request).execute();
      getLog().info("API to createOrUpdate VSM service's response body is: " + response.body());
      getLog().info("API to createOrUpdate VSM service's response status is: " + response.code());
      if (response.code() > 299) {
        getLog().warn("FAILURE to post to VSM, got response code:" + response.code()
            + " and message: " + response.message());
      }

    } catch (Exception e) {
      throw new MojoExecutionException(e.getMessage());
    }
  }

  public String getBearerToken() throws MojoExecutionException {
    OkHttpClient client = new OkHttpClient().newBuilder().build();
    MediaType mediaType = MediaType.parse("application/x-www-form-urlencoded");
    RequestBody body = RequestBody.create("grant_type=client_credentials", mediaType);

    Request request = new Request.Builder().url(authURL).method("POST", body)
        .addHeader("Authorization", "Basic " + encodedAuthToken).build();
    Response response = null;
    try {
      response = client.newCall(request).execute();
      getLog().info("API to get bearer token's url is: " + authURL);
      getLog().info("API to get bearer token's response body is: " + response.body());
      getLog().info("API to get bearer token's response status is: " + response.code());
      if (response.code() > 299)
        throw new MojoExecutionException("Failure to obtain bearer token from LeanIx");

    } catch (Exception e) {
      throw new MojoExecutionException(e.getMessage());
    }
    ObjectMapper objectMapper = new ObjectMapper();
    String bearerToken = "";
    try {
      Map<String, String> responseMap = objectMapper.readValue(response.body().string(), HashMap.class);
      bearerToken = responseMap.get(accessTokenKey);
    } catch (IOException e) {
      throw new MojoExecutionException(e.getMessage());
    }
    return bearerToken;
  }

}
